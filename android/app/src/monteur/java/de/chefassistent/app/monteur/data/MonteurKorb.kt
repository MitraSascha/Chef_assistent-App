package de.chefassistent.app.monteur.data

import android.content.Context
import de.chefassistent.app.data.ApiFehler
import de.chefassistent.app.data.Auspacken
import de.chefassistent.app.data.Tresor
import de.chefassistent.app.data.auspacken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Ausgangskorb der Monteur-App: Berichte (Diktat oder Text), Abschluesse
 * („Passt – nach HERO“), Zeiten und Urlaubsantraege, die nicht rausgingen. **Ohne Netz darf nichts verloren gehen**
 * (Grundsatz wie in der Buero-App).
 *
 * Jeder Eintrag traegt seine `anfrage_id`. Der Server legt bei derselben ID nie
 * einen zweiten Bericht, Zeiteintrag oder Antrag an - Nachsenden ist also gefahrlos,
 * auch wenn die erste Anfrage doch angekommen war.
 *
 * Verschluesselt ueber [Tresor] in `filesDir/monteur_ausgang`: die Aufnahme als
 * `<id>.m4a.enc`, die Begleitdaten zuletzt als `<id>.<fassung>.json.enc` (Zeichen
 * „fertig“, siehe [Tresor]). **Nur eine Instanz** (in `MonteurApp`), die Sperren
 * gelten pro Objekt.
 */
class MonteurKorb(context: Context) {

    private val tresor = Tresor(context)
    private val ordner = File(context.filesDir, "monteur_ausgang").apply { mkdirs() }
    private val beiseite = File(context.filesDir, "monteur_ausgang_beiseite").apply { mkdirs() }
    private val cache = File(context.cacheDir, "monteur_ausgang").apply { mkdirs() }

    private val _anzahl = MutableStateFlow(0)

    /** Wartende Eintraege, fuer „Wartet auf Netz“. */
    val anzahl: StateFlow<Int> = _anzahl.asStateFlow()

    private val _gemeldet = MutableStateFlow<List<KorbMeldung>>(emptyList())

    /** Was beim Nachsenden passiert ist: fertige Berichte, endgueltige Fehler. */
    val gemeldet: StateFlow<List<KorbMeldung>> = _gemeldet.asStateFlow()

    init {
        zaehlen()
    }

    @Synchronized
    fun berichtEinreihen(auftrag: BerichtAuftrag, aufnahme: File?) {
        aufnahme?.let { tresor.verschluesseln(it, audioDatei(auftrag.anfrage_id)) }
        tresor.fassungSchreiben(ordner, auftrag.anfrage_id, KorbEintrag(bericht = auftrag), KorbEintrag.serializer())
        aufnahme?.delete()
        zaehlen()
    }

    /**
     * „Passt – nach HERO“ ohne Netz bzw. bei 502/5xx. Der Server legt bei Wiederholung
     * nie einen zweiten Bericht an (gleiche Bericht-ID), Nachsenden ist also gefahrlos.
     * Die Unterschrift liegt dabei im verschluesselten Eintrag.
     */
    @Synchronized
    fun abschlussEinreihen(auftrag: AbschlussAuftrag) {
        tresor.fassungSchreiben(ordner, ABSCHLUSS_VORSATZ + auftrag.bericht_id, KorbEintrag(abschluss = auftrag), KorbEintrag.serializer())
        zaehlen()
    }

    /** Ob fuer diesen Bericht schon ein Abschluss wartet (dann nicht erneut abschliessen lassen). */
    @Synchronized
    fun abschlussWartet(berichtId: String): Boolean = tresor.hatFassung(ordner, ABSCHLUSS_VORSATZ + berichtId)

    @Synchronized
    fun zeitEinreihen(zeit: ZeitNeu) {
        val id = requireNotNull(zeit.anfrage_id) { "Zeit ohne anfrage_id" }
        tresor.fassungSchreiben(ordner, id, KorbEintrag(zeit = zeit), KorbEintrag.serializer())
        zaehlen()
    }

    @Synchronized
    fun abwesenheitEinreihen(abwesenheit: AbwesenheitNeu) {
        val id = requireNotNull(abwesenheit.anfrage_id) { "Antrag ohne anfrage_id" }
        tresor.fassungSchreiben(ordner, id, KorbEintrag(abwesenheit = abwesenheit), KorbEintrag.serializer())
        zaehlen()
    }

    /** Ob fuer diesen Termin schon ein Bericht wartet (dann nicht doppelt aufnehmen lassen). */
    @Synchronized
    fun wartendeBerichte(): List<BerichtAuftrag> =
        tresor.namenMitFassung(ordner).mapNotNull { tresor.fassungLesen(ordner, it, KorbEintrag.serializer())?.bericht }

    fun meldungErledigt(meldung: KorbMeldung) {
        _gemeldet.value = _gemeldet.value - meldung
    }

    /**
     * Schickt alles ab, aeltestes zuerst. `true` = Korb leer bzw. nur noch
     * Endgueltiges beiseitegelegt; `false` = etwas wartet weiter (spaeter erneut).
     */
    suspend fun senden(repository: MonteurRepository): Boolean = sendeSperre.withLock { sendenGesperrt(repository) }

    /** Worker und Oberflaeche duerfen nicht gleichzeitig senden, sonst ginge ein Eintrag doppelt raus. */
    private val sendeSperre = Mutex()

    private suspend fun sendenGesperrt(repository: MonteurRepository): Boolean {
        val namen = synchronized(this) { tresor.namenMitFassung(ordner).sorted() }
        for (name in namen) {
            val eintrag = synchronized(this) { tresor.fassungLesen(ordner, name, KorbEintrag.serializer()) }
            if (eintrag == null) {
                synchronized(this) { wegLegen(name) }
                continue
            }
            val ergebnis = try {
                versenden(repository, name, eintrag)
            } catch (fehler: ApiFehler) {
                if (fehler.istVoruebergehend) {
                    zaehlen()
                    return false
                }
                melden(KorbMeldung(name, eintrag.titel(), fehler.anzeigetext, fertig = false, berichtId = eintrag.abschluss?.bericht_id))
                synchronized(this) { wegLegen(name) }
                continue
            }
            if (ergebnis == null) {
                // Aufnahme braucht mehr Platz im Cache, als frei ist: spaeter.
                zaehlen()
                return false
            }
            melden(ergebnis)
            synchronized(this) { loeschen(name) }
        }
        zaehlen()
        return true
    }

    /** `null` = jetzt nicht moeglich, spaeter erneut. */
    private suspend fun versenden(repository: MonteurRepository, name: String, eintrag: KorbEintrag): KorbMeldung? {
        eintrag.bericht?.let { b ->
            val verschluesselt = audioDatei(name)
            var klar: File? = null
            if (verschluesselt.exists()) {
                when (val aus = tresor.auspacken(verschluesselt, File(cache, "$name.m4a"))) {
                    is Auspacken.Fertig -> klar = aus.datei
                    Auspacken.KeinPlatz -> return null
                    is Auspacken.Defekt -> throw ApiFehler.UnlesbareAntwort(aus.ursache)
                }
            }
            try {
                val bericht = repository.berichtNeu(
                    anfrageId = b.anfrage_id,
                    aufnahme = klar,
                    text = b.text,
                    terminId = b.termin_id,
                    projektNr = b.projekt_nr,
                    datum = b.datum,
                )
                return KorbMeldung(name, b.titel, "Bericht-Entwurf ist da – bitte prüfen.", fertig = true, berichtId = bericht.id)
            } finally {
                klar?.delete()
            }
        }
        eintrag.abschluss?.let { a ->
            val bericht = try {
                repository.berichtAbschliessen(a.bericht_id, a.abschluss)
            } catch (fehler: ApiFehler.Beschaeftigt) {
                // 409 = offene Material-Rueckfrage: wiederholt sich bei jedem Versuch,
                // der Monteur muss sie klaeren. Beiseitelegen statt endlos senden.
                throw ApiFehler.Ungueltig(fehler.anzeigetext)
            }
            val text = if (bericht.hero_nr != null) "Bericht ${bericht.hero_nr} ist in HERO." else "Bericht ist in HERO."
            return KorbMeldung(name, a.titel, text, fertig = true, berichtId = bericht.id)
        }
        eintrag.zeit?.let { z ->
            repository.zeitNeu(z)
            return KorbMeldung(name, eintrag.titel(), "Zeit nachgetragen.", fertig = true)
        }
        eintrag.abwesenheit?.let { a ->
            repository.abwesenheitNeu(a)
            return KorbMeldung(name, eintrag.titel(), "Antrag nachgereicht.", fertig = true)
        }
        return KorbMeldung(name, "", "Leerer Eintrag verworfen.", fertig = false)
    }

    private fun melden(meldung: KorbMeldung) {
        _gemeldet.value = _gemeldet.value + meldung
    }

    private fun audioDatei(name: String) = File(ordner, "$name.m4a.enc")

    private fun loeschen(name: String) {
        tresor.alleDateien(ordner, name).forEach { it.delete() }
    }

    /** Nie loeschen, was nicht nachweislich leer ist: beiseitelegen. */
    private fun wegLegen(name: String) {
        tresor.alleDateien(ordner, name).forEach { it.renameTo(File(beiseite, it.name)) }
    }

    @Synchronized
    private fun zaehlen() {
        _anzahl.value = tresor.namenMitFassung(ordner).size
    }

    private companion object {
        /** Eigener Name je Abschluss, damit er nie mit dem Bericht-Eintrag gleicher ID zusammenfaellt. */
        const val ABSCHLUSS_VORSATZ = "abschluss_"
    }
}

@Serializable
data class KorbEintrag(
    val bericht: BerichtAuftrag? = null,
    val zeit: ZeitNeu? = null,
    val abwesenheit: AbwesenheitNeu? = null,
    val abschluss: AbschlussAuftrag? = null,
) {
    fun titel(): String = when {
        bericht != null -> bericht.titel
        abschluss != null -> abschluss.titel
        zeit != null -> "Zeit ${zeit.beginn.substringAfter('T')}–${zeit.ende.substringAfter('T')}"
        abwesenheit != null -> "Antrag ${abwesenheit.von} – ${abwesenheit.bis}"
        else -> ""
    }
}

/** Ein Bericht, der auf Netz wartet: Diktat (Datei daneben) oder Text. */
@Serializable
data class BerichtAuftrag(
    val anfrage_id: String,
    val text: String? = null,
    val termin_id: String? = null,
    val projekt_nr: String? = null,
    val datum: String? = null,
    /** Fuer die Anzeige, z. B. „Bericht SAN-1243 Böse“. */
    val titel: String = "Bericht",
)

/** Ein „Passt – nach HERO“, das auf Netz wartet. */
@Serializable
data class AbschlussAuftrag(
    val bericht_id: String,
    val abschluss: Abschluss,
    val titel: String = "Bericht",
)

/** Rueckmeldung vom Nachsenden. [berichtId] fuehrt zum fertigen Entwurf. */
data class KorbMeldung(
    val schluessel: String,
    val titel: String,
    val text: String,
    val fertig: Boolean,
    val berichtId: String? = null,
)
