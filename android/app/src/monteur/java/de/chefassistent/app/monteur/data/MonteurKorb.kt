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

    /**
     * Foto, Video oder Dokument fuer die Projektmappe (seit 1.22.0). Die Datei wird
     * verschluesselt kopiert und danach geloescht; Videos koennen bis 1 GB gross sein,
     * der Upload laeuft deshalb immer ueber [MonteurVersand] im Hintergrund.
     */
    @Synchronized
    fun dateiEinreihen(auftrag: DateiAuftrag, datei: File) {
        val name = DATEI_VORSATZ + auftrag.anfrage_id
        tresor.verschluesseln(datei, inhaltDatei(name))
        tresor.fassungSchreiben(ordner, name, KorbEintrag(datei = auftrag), KorbEintrag.serializer())
        datei.delete()
        zaehlen()
    }

    /**
     * Eingang (1.23.0): Aufnahme und Fotos verschluesselt ablegen, danach die
     * Begleitdaten als Zeichen „fertig“. Die Dateien werden erst nach Erfolg geloescht.
     */
    @Synchronized
    fun eingangEinreihen(auftrag: EingangAuftrag, aufnahme: File?, fotos: List<File>) {
        val name = EINGANG_VORSATZ + auftrag.anfrage_id
        aufnahme?.let { tresor.verschluesseln(it, audioDatei(name)) }
        val namen = fotos.mapIndexed { i, f ->
            tresor.verschluesseln(f, fotoDatei(name, i))
            f.name
        }
        tresor.fassungSchreiben(ordner, name, KorbEintrag(eingang = auftrag.copy(fotonamen = namen)), KorbEintrag.serializer())
        aufnahme?.delete()
        fotos.forEach { it.delete() }
        zaehlen()
    }

    /** „Passt“ ohne Netz bzw. bei 502/5xx - wiederholbar, der Server bucht nie doppelt. */
    @Synchronized
    fun passtEinreihen(auftrag: PasstAuftrag) {
        tresor.fassungSchreiben(ordner, PASST_VORSATZ + auftrag.karte_id, KorbEintrag(passt = auftrag), KorbEintrag.serializer())
        zaehlen()
    }

    @Synchronized
    fun passtWartet(karteId: String): Boolean = tresor.hatFassung(ordner, PASST_VORSATZ + karteId)

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
    suspend fun senden(
        repository: MonteurRepository,
        /** Upload-Fortschritt einer Datei: Titel und Prozent, fuer Benachrichtigung und Projektmappe. */
        fortschritt: (String, Int) -> Unit = { _, _ -> },
    ): Boolean = sendeSperre.withLock { sendenGesperrt(repository, fortschritt) }

    /** Worker und Oberflaeche duerfen nicht gleichzeitig senden, sonst ginge ein Eintrag doppelt raus. */
    private val sendeSperre = Mutex()

    private suspend fun sendenGesperrt(repository: MonteurRepository, fortschritt: (String, Int) -> Unit): Boolean {
        // Aeltestes zuerst: die Namen sind zufaellige IDs, also nach Dateialter.
        val namen = synchronized(this) {
            tresor.namenMitFassung(ordner).sortedBy { n -> tresor.alleDateien(ordner, n).minOfOrNull { it.lastModified() } ?: 0L }
        }
        for (name in namen) {
            val eintrag = synchronized(this) { tresor.fassungLesen(ordner, name, KorbEintrag.serializer()) }
            if (eintrag == null) {
                synchronized(this) { wegLegen(name) }
                continue
            }
            val ergebnis = try {
                versenden(repository, name, eintrag, fortschritt)
            } catch (fehler: ApiFehler) {
                if (fehler.istVoruebergehend) {
                    zaehlen()
                    return false
                }
                melden(
                    KorbMeldung(
                        name, eintrag.titel(), fehler.anzeigetext, fertig = false,
                        berichtId = eintrag.abschluss?.bericht_id,
                        projektId = eintrag.datei?.projekt_id,
                        karteId = eintrag.passt?.karte_id,
                    )
                )
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
    private suspend fun versenden(
        repository: MonteurRepository,
        name: String,
        eintrag: KorbEintrag,
        fortschritt: (String, Int) -> Unit,
    ): KorbMeldung? {
        eintrag.eingang?.let { e ->
            val klar = mutableListOf<File>()
            try {
                val audio = audioDatei(name)
                var aufnahme: File? = null
                if (audio.exists()) {
                    when (val aus = tresor.auspacken(audio, File(cache, "$name.m4a"))) {
                        is Auspacken.Fertig -> aufnahme = aus.datei.also(klar::add)
                        Auspacken.KeinPlatz -> return null
                        is Auspacken.Defekt -> throw ApiFehler.UnlesbareAntwort(aus.ursache)
                    }
                }
                val fotos = e.fotonamen.mapIndexedNotNull { i, fotoName ->
                    val quelle = fotoDatei(name, i)
                    if (!quelle.exists()) return@mapIndexedNotNull null
                    when (val aus = tresor.auspacken(quelle, File(cache, "${name}_$i.${fotoName.substringAfterLast('.', "jpg")}"))) {
                        is Auspacken.Fertig -> aus.datei.also(klar::add)
                        Auspacken.KeinPlatz -> return null
                        is Auspacken.Defekt -> throw ApiFehler.UnlesbareAntwort(aus.ursache)
                    }
                }
                val karte = repository.eingang(
                    anfrageId = e.anfrage_id,
                    aufnahme = aufnahme,
                    text = e.text,
                    fotos = fotos,
                    terminId = e.termin_id,
                    projektNr = e.projekt_nr,
                    datum = e.datum,
                    berichtId = e.bericht_id,
                    bezugId = e.bezug_id,
                )
                return KorbMeldung(name, e.titel, "Karte ist da – bitte ansehen.", fertig = true, karteId = karte.id)
            } finally {
                klar.forEach { it.delete() }
            }
        }
        eintrag.passt?.let { p ->
            val karte = try {
                repository.passt(p.karte_id, p.passt)
            } catch (fehler: ApiFehler.Beschaeftigt) {
                // 409 = Rueckfrage offen oder Karte verworfen: wiederholt sich, der Monteur muss ran.
                throw ApiFehler.Ungueltig(fehler.anzeigetext)
            }
            return KorbMeldung(name, p.titel, "Erledigt.", fertig = true, karteId = karte.id)
        }
        eintrag.datei?.let { d ->
            val klar = when (val aus = tresor.auspacken(inhaltDatei(name), File(cache, d.dateiname.substringAfterLast('/')))) {
                is Auspacken.Fertig -> aus.datei
                Auspacken.KeinPlatz -> return null
                is Auspacken.Defekt -> throw ApiFehler.UnlesbareAntwort(aus.ursache)
            }
            try {
                val antwort = repository.hochladen(d.projekt_id, klar, d.text, d.anfrage_id) { fortschritt(d.dateiname, it) }
                return KorbMeldung(name, antwort.name.ifBlank { d.dateiname }, antwort.hinweis, fertig = true, projektId = d.projekt_id)
            } finally {
                klar.delete()
            }
        }
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

    private fun inhaltDatei(name: String) = File(ordner, "$name.datei.enc")

    private fun fotoDatei(name: String, i: Int) = File(ordner, "$name.foto$i.enc")

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

        const val DATEI_VORSATZ = "datei_"

        const val EINGANG_VORSATZ = "eingang_"

        const val PASST_VORSATZ = "passt_"
    }
}

@Serializable
data class KorbEintrag(
    val bericht: BerichtAuftrag? = null,
    val zeit: ZeitNeu? = null,
    val abwesenheit: AbwesenheitNeu? = null,
    val abschluss: AbschlussAuftrag? = null,
    val datei: DateiAuftrag? = null,
    val eingang: EingangAuftrag? = null,
    val passt: PasstAuftrag? = null,
) {
    fun titel(): String = when {
        eingang != null -> eingang.titel
        passt != null -> passt.titel
        datei != null -> datei.dateiname
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

/** Eine Eingabe an den Eingang (1.23.0), die auf Netz wartet. Aufnahme und Fotos liegen daneben. */
@Serializable
data class EingangAuftrag(
    val anfrage_id: String,
    val text: String? = null,
    val termin_id: String? = null,
    val projekt_nr: String? = null,
    val datum: String? = null,
    val bericht_id: String? = null,
    val bezug_id: String? = null,
    /** Originalnamen der Fotos (Endung zaehlt fuer den Server). */
    val fotonamen: List<String> = emptyList(),
    val titel: String = "Eingabe",
)

/** Ein „Passt“, das auf Netz wartet. */
@Serializable
data class PasstAuftrag(
    val karte_id: String,
    val passt: Passt,
    val titel: String = "Passt",
)

/** Foto, Video oder Dokument fuer die Projektmappe, das auf Netz wartet. */
@Serializable
data class DateiAuftrag(
    val anfrage_id: String,
    val projekt_id: Int,
    /** Mit Endung - der Server erkennt daran die Art. */
    val dateiname: String,
    /** Kurze Beschreibung, kommt in den Dateinamen. */
    val text: String = "",
)

/** Rueckmeldung vom Nachsenden. [berichtId] fuehrt zum fertigen Entwurf, [projektId] zur Mappe. */
data class KorbMeldung(
    val schluessel: String,
    val titel: String,
    val text: String,
    val fertig: Boolean,
    val berichtId: String? = null,
    val projektId: Int? = null,
    /** Seit 1.23.0: fuehrt zur Karte. */
    val karteId: String? = null,
)
