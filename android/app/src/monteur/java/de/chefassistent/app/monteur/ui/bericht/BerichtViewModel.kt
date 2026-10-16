package de.chefassistent.app.monteur.ui.bericht

import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.chefassistent.app.data.ApiFehler
import de.chefassistent.app.monteur.MonteurApp
import de.chefassistent.app.monteur.data.Abschluss
import de.chefassistent.app.monteur.data.AbschlussAuftrag
import de.chefassistent.app.monteur.data.ArtikelWahl
import de.chefassistent.app.monteur.data.BerichtAenderung
import de.chefassistent.app.monteur.data.BerichtAuftrag
import de.chefassistent.app.monteur.data.MonteurBericht
import de.chefassistent.app.monteur.data.MaterialAenderung
import de.chefassistent.app.monteur.data.MonteurRepository
import de.chefassistent.app.monteur.data.PasstAuftrag
import de.chefassistent.app.monteur.data.Passt
import de.chefassistent.app.monteur.data.Karte
import de.chefassistent.app.monteur.data.MonteurVersand
import de.chefassistent.app.monteur.data.materialJson
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** Wo der Bildschirm gerade steht (Auftrag 1.22, 3.). */
enum class Phase {
    /** Vorhandener Bericht wird geladen. */
    LADEN,

    /** Laden gescheitert, mit „Nochmal“. */
    LADEFEHLER,

    /** Mikrofon oder Text. */
    EINGABE,

    /** KI schreibt den Bericht (10–40 s). */
    LAEUFT,

    /** Diktat liegt im Ausgangskorb und geht von selbst raus. */
    WARTET,

    /** Bestaetigungskarte zum Pruefen und Korrigieren. */
    KARTE,

    /** „Passt – nach HERO“ liegt im Ausgangskorb. */
    ABSCHLUSS_WARTET,

    /** In HERO angelegt. */
    FERTIG,
}

/** Fertig gezeichnete Unterschrift, bereit fuer [Abschluss.unterschrift_png]. */
class Unterschrift(val png: ByteArray, val name: String)

data class BerichtZustand(
    val phase: Phase = Phase.EINGABE,
    val bericht: MonteurBericht? = null,
    /** Aenderung, Abschluss oder Verwerfen laeuft: Knoepfe gesperrt. */
    val sendet: Boolean = false,
    /** Ergaenzung per Mikrofon laeuft (KI baut den Entwurf neu). */
    val diktatLaeuft: Boolean = false,
    /** `detail` nach 422/409 beim ersten Senden, steht ueber der Eingabe. */
    val eingabeFehler: String? = null,
    val ladeFehler: String? = null,
    /** Termin hat der Server selbst gesucht: deutlich zeigen, damit es auffaellt. */
    val automatisch: Boolean = false,
    /** Aufnahme, die nicht ankam (422/409 …) - „Nochmal senden“ statt neu sprechen. */
    val offeneAufnahme: String? = null,
    /** Ergaenzung, die nicht ankam. */
    val offenesDiktat: String? = null,
    /** Getippte Ergaenzung bzw. Antwort auf eine Rueckfrage; bleibt bei Fehlern stehen. */
    val ergaenzung: String = "",
    val unterschrift: Unterschrift? = null,
    /** Wichtige Meldung als Dialog, z. B. 409 beim Abschliessen (offene Rueckfrage). */
    val hinweisDialog: String? = null,
    /** Entwurf verworfen: Bildschirm schliessen. */
    val geschlossen: Boolean = false,
) {
    val gesperrt: Boolean get() = sendet || diktatLaeuft
}

/**
 * Bericht schreiben, pruefen und nach HERO schicken (Auftrag 1.22, 3., API.md 5c).
 *
 * **Nichts darf verloren gehen:** Jede neue Anfrage bekommt ihre `anfrage_id` vor
 * dem ersten Versuch, sie liegt im [SavedStateHandle] und geht mit in den
 * Ausgangskorb. Weil der Server dieselbe ID nie zweimal anlegt, ist Nachsenden
 * immer gefahrlos - fuer das Diktat ebenso wie fuer „Passt – nach HERO“.
 *
 * Stunden und Mengen sind entprellt: die Anzeige folgt sofort, der `PATCH` geht
 * erst nach einer kurzen Pause raus. Vor Diktat und Abschluss wird Ausstehendes
 * zuerst gesendet, sonst wuerde die KI bzw. HERO mit alten Zahlen arbeiten.
 */
class BerichtViewModel(
    val app: MonteurApp,
    val start: BerichtStart,
    private val gespeichert: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    /**
     * Karte aus dem Eingang (1.23.0). Wechselt bei jeder Ergaenzung, weil der Server
     * dafuer eine neue Karte mit derselben Bericht-ID liefert; ueberlebt den Prozesstod.
     */
    private var karteId: String?
        get() = gespeichert[K_KARTE] ?: start.karteId
        set(wert) {
            gespeichert[K_KARTE] = wert
        }


    private val repository = app.repository

    private val _abgemeldet = MutableStateFlow(false)
    val abgemeldet: StateFlow<Boolean> = _abgemeldet.asStateFlow()

    private val _zustand = MutableStateFlow(BerichtZustand())
    val zustand: StateFlow<BerichtZustand> = _zustand.asStateFlow()

    private val _meldungen = Channel<String>(Channel.BUFFERED)

    /** Kurze Meldungen fuer die Snackbar (Servertext, nie eigene Fehlertexte). */
    val meldungen: Flow<String> = _meldungen.receiveAsFlow()

    /** Getippter Bericht, ueberlebt das Beenden durch Android. */
    var text by mutableStateOf(gespeichert.get<String>(K_TEXT).orEmpty())
        private set

    // --- Unterschrift: Striche im ViewModel, damit sie das Drehen ueberleben -------

    val striche = mutableStateListOf<List<Offset>>()
    var zeichenflaeche by mutableStateOf(Size.Zero)
        private set
    var unterschriftName by mutableStateOf(kundeAusTitel(start.titel))

    private var stundenJob: Job? = null
    private var mengenJob: Job? = null

    private val berichtId: String?
        get() = _zustand.value.bericht?.id ?: gespeichert[K_BERICHT] ?: start.berichtId

    init {
        val gemerkt: String? = gespeichert[K_BERICHT]
        val anfrage: String? = gespeichert[K_ANFRAGE]
        val unterwegs = gespeichert.get<Boolean>(K_UNTERWEGS) == true
        _zustand.update {
            it.copy(
                automatisch = gespeichert.get<Boolean>(K_AUTOMATISCH) == true,
                offeneAufnahme = gespeichert.get<String>(K_OFFEN)?.takeIf { p -> File(p).exists() },
            )
        }
        when {
            gemerkt != null -> laden(gemerkt)
            start.berichtId != null -> laden(start.berichtId)
            gespeichert.get<Boolean>(K_IM_KORB) == true -> _zustand.update { it.copy(phase = Phase.WARTET) }
            // Prozess wurde waehrend des Sendens beendet: erst nachsehen, ob es ankam.
            unterwegs && anfrage != null -> nachUnterbrechung(anfrage)
            start.aufnahme != null && gespeichert.get<Boolean>(K_VERBRAUCHT) != true -> {
                gespeichert[K_VERBRAUCHT] = true
                neuSenden(File(start.aufnahme), null)
            }
            korbHatTermin() -> _zustand.update { it.copy(phase = Phase.WARTET) }
            else -> _zustand.update { it.copy(phase = Phase.EINGABE) }
        }
    }

    // --- Eingabe -------------------------------------------------------------------------

    fun textAendern(neu: String) {
        text = neu
        gespeichert[K_TEXT] = neu
    }

    fun textSenden() {
        val t = text.trim()
        if (t.isEmpty()) return
        neuSenden(null, t)
    }

    fun aufnahmeSenden(datei: File) {
        // Eine neue Aufnahme ersetzt die liegengebliebene.
        _zustand.value.offeneAufnahme?.takeIf { it != datei.absolutePath }?.let { File(it).delete() }
        neuSenden(datei, null)
    }

    fun offeneAufnahmeSenden() {
        val pfad = _zustand.value.offeneAufnahme ?: return
        neuSenden(File(pfad), null)
    }

    fun offeneAufnahmeVerwerfen() {
        _zustand.value.offeneAufnahme?.let { File(it).delete() }
        offenSetzen(null)
    }

    private fun neuSenden(aufnahme: File?, text: String?) {
        if (_zustand.value.phase == Phase.LAEUFT) return
        val id: String = gespeichert[K_ANFRAGE] ?: MonteurRepository.neueAnfrageId().also { gespeichert[K_ANFRAGE] = it }
        gespeichert[K_UNTERWEGS] = true
        gespeichert[K_AUTOMATISCH] = start.terminId == null && start.projektNr == null
        aufnahme?.let { gespeichert[K_OFFEN] = it.absolutePath }
        _zustand.update { it.copy(phase = Phase.LAEUFT, eingabeFehler = null) }
        viewModelScope.launch {
            try {
                val bericht = repository.berichtNeu(
                    anfrageId = id,
                    aufnahme = aufnahme,
                    text = text,
                    terminId = start.terminId,
                    projektNr = start.projektNr,
                )
                // Angekommen: erst jetzt darf die Aufnahme weg.
                aufnahme?.delete()
                offenSetzen(null)
                gespeichert[K_UNTERWEGS] = false
                gespeichert[K_BERICHT] = bericht.id
                textAendern("")
                _zustand.update { it.copy(automatisch = gespeichert.get<Boolean>(K_AUTOMATISCH) == true) }
                zeigen(bericht)
            } catch (fehler: ApiFehler) {
                gespeichert[K_UNTERWEGS] = false
                val inDenKorb = fehler.istVoruebergehend && fehler !is ApiFehler.Beschaeftigt
                if (inDenKorb && einreihen(id, aufnahme, text)) {
                    textAendern("")
                    _zustand.update { it.copy(phase = Phase.WARTET) }
                } else {
                    // 422 (kein Termin, keine Sprache) und alles Endgueltige: Text bleibt
                    // im Feld, die Aufnahme bleibt fuer „Nochmal senden“.
                    offenSetzen(aufnahme?.absolutePath)
                    _zustand.update { it.copy(phase = Phase.EINGABE, eingabeFehler = fehler.anzeigetext) }
                }
                if (fehler is ApiFehler.NichtAngemeldet) abmelden()
            }
        }
    }

    /**
     * Legt das Diktat in den Ausgangskorb. Auch bei 401: nach der naechsten
     * Einrichtung geht es raus, statt verloren zu sein. `false`, wenn der Korb
     * nicht schreiben konnte (Speicher voll) - dann bleibt die Aufnahme liegen.
     */
    private fun einreihen(id: String, aufnahme: File?, text: String?): Boolean = try {
        app.korb.berichtEinreihen(
            BerichtAuftrag(
                anfrage_id = id,
                text = text,
                termin_id = start.terminId,
                projekt_nr = start.projektNr,
                titel = start.titel?.let { "Bericht $it" } ?: "Bericht",
            ),
            aufnahme,
        )
        offenSetzen(null)
        gespeichert[K_IM_KORB] = true
        MonteurVersand.anstossen(app)
        true
    } catch (_: Exception) {
        false
    }

    private fun nachUnterbrechung(anfrage: String) {
        _zustand.update { it.copy(phase = Phase.LAEUFT) }
        viewModelScope.launch {
            try {
                val bericht = repository.bericht(anfrage)
                gespeichert[K_UNTERWEGS] = false
                gespeichert[K_BERICHT] = bericht.id
                _zustand.value.offeneAufnahme?.let { File(it).delete() }
                offenSetzen(null)
                zeigen(bericht)
            } catch (fehler: ApiFehler) {
                gespeichert[K_UNTERWEGS] = false
                when {
                    fehler is ApiFehler.NichtAngemeldet -> abmelden()
                    // Kam nicht an: die Aufnahme liegt noch da, also einfach erneut.
                    fehler is ApiFehler.NichtGefunden && _zustand.value.offeneAufnahme != null -> {
                        _zustand.update { it.copy(phase = Phase.EINGABE) }
                        offeneAufnahmeSenden()
                    }
                    else -> _zustand.update { it.copy(phase = Phase.EINGABE) }
                }
            }
        }
    }

    private fun korbHatTermin(): Boolean {
        val termin = start.terminId ?: return false
        return runCatching { app.korb.wartendeBerichte().any { it.termin_id == termin } }.getOrDefault(false)
    }

    // --- Laden ---------------------------------------------------------------------------

    fun neuLaden() {
        berichtId?.let(::laden)
    }

    private fun laden(id: String) {
        if (_zustand.value.bericht == null) _zustand.update { it.copy(phase = Phase.LADEN, ladeFehler = null) }
        viewModelScope.launch {
            try {
                zeigen(repository.bericht(id))
            } catch (fehler: ApiFehler) {
                if (fehler is ApiFehler.NichtAngemeldet) return@launch abmelden()
                if (_zustand.value.bericht == null) {
                    _zustand.update { it.copy(phase = Phase.LADEFEHLER, ladeFehler = fehler.anzeigetext) }
                } else {
                    _meldungen.trySend(fehler.anzeigetext)
                }
            }
        }
    }

    private fun zeigen(bericht: MonteurBericht) {
        val phase = when {
            bericht.istFertig -> Phase.FERTIG
            runCatching { app.korb.abschlussWartet(bericht.id) }.getOrDefault(false) -> Phase.ABSCHLUSS_WARTET
            else -> Phase.KARTE
        }
        _zustand.update { it.copy(phase = phase, bericht = bericht, ladeFehler = null) }
    }

    // --- Korrigieren von Hand ------------------------------------------------------------

    fun taetigkeitAendern(neu: String) {
        val alt = _zustand.value.bericht?.entwurf?.taetigkeit ?: return
        if (neu.trim() == alt.trim()) return
        aendern(BerichtAenderung(taetigkeit = neu.trim()), sperren = true)
    }

    fun anfahrtSetzen(an: Boolean) {
        lokal { it.copy(entwurf = it.entwurf.copy(anfahrt = an)) }
        aendern(BerichtAenderung(anfahrt = an))
    }

    fun kleinmaterialSetzen(an: Boolean) {
        lokal { it.copy(entwurf = it.entwurf.copy(kleinmaterial = an)) }
        aendern(BerichtAenderung(kleinmaterial = an))
    }

    /** +/− an Monteur- ([helfer] = false) bzw. Helferstunden, entprellt. */
    fun stunden(helfer: Boolean, richtung: Int) {
        lokal {
            val e = it.entwurf
            it.copy(
                entwurf = if (helfer) e.copy(helfer_stunden = stundenSchritt(e.helfer_stunden, richtung))
                else e.copy(monteur_stunden = stundenSchritt(e.monteur_stunden, richtung))
            )
        }
        stundenJob?.cancel()
        stundenJob = viewModelScope.launch {
            delay(ENTPRELLEN_MS)
            stundenSenden()
        }
    }

    private suspend fun stundenSenden(): Boolean {
        val e = _zustand.value.bericht?.entwurf ?: return true
        return senden(BerichtAenderung(monteur_stunden = e.monteur_stunden, helfer_stunden = e.helfer_stunden))
    }

    /** +/− an der Menge einer Materialposition, entprellt. */
    fun menge(index: Int, richtung: Int) {
        lokal {
            val liste = it.entwurf.material.toMutableList()
            val m = liste.getOrNull(index) ?: return@lokal it
            liste[index] = m.copy(menge = mengeSchritt(m.menge, richtung))
            it.copy(entwurf = it.entwurf.copy(material = liste))
        }
        mengenJob?.cancel()
        mengenJob = viewModelScope.launch {
            delay(ENTPRELLEN_MS)
            materialSenden()
        }
    }

    /** Vorschlag antippen ([ArtikelWahl.Waehlen]) oder „Frei lassen“ ([ArtikelWahl.Frei]). */
    fun artikelWaehlen(index: Int, wahl: ArtikelWahl) {
        mengenJob?.cancel()
        sperrend { materialSenden(index, wahl) }
    }

    // --- „+ Material“ von Hand ------------------------------------------------------------

    private val _artikelTreffer = MutableStateFlow<ArtikelSuche>(ArtikelSuche())

    /** Treffer der Artikelsuche fuer den Dialog „+ Material“. */
    val artikelTreffer: StateFlow<ArtikelSuche> = _artikelTreffer.asStateFlow()

    private var sucheJob: Job? = null

    /** Ab 2 Zeichen, 300 ms Pause, vorige Anfrage abbrechen (Auftrag 1.22). */
    fun artikelSuchen(eingabe: String) {
        sucheJob?.cancel()
        val q = eingabe.trim()
        if (q.length < 2) {
            _artikelTreffer.value = ArtikelSuche()
            return
        }
        _artikelTreffer.update { it.copy(laeuft = true, fehler = null) }
        sucheJob = viewModelScope.launch {
            delay(SUCHE_PAUSE_MS)
            try {
                _artikelTreffer.value = ArtikelSuche(suchtext = q, treffer = repository.artikelSuchen(q))
            } catch (fehler: ApiFehler) {
                if (fehler is ApiFehler.NichtAngemeldet) abmelden()
                _artikelTreffer.value = ArtikelSuche(suchtext = q, fehler = fehler.anzeigetext)
            }
        }
    }

    fun artikelSucheBeenden() {
        sucheJob?.cancel()
        _artikelTreffer.value = ArtikelSuche()
    }

    /** Neue Position anhaengen: Katalogartikel oder freie Position. Die ganze Liste geht mit. */
    fun materialHinzufuegen(neu: MaterialAenderung) {
        mengenJob?.cancel()
        artikelSucheBeenden()
        sperrend {
            val material = _zustand.value.bericht?.entwurf?.material ?: return@sperrend
            senden(BerichtAenderung(material = materialJson(materialAenderungen(material) + neu)))
        }
    }

    fun materialEntfernen(index: Int) {
        mengenJob?.cancel()
        sperrend { materialSenden(index, entfernen = true) }
    }

    private suspend fun materialSenden(index: Int? = null, wahl: ArtikelWahl = ArtikelWahl.Behalten, entfernen: Boolean = false) {
        val material = _zustand.value.bericht?.entwurf?.material ?: return
        val liste = materialAenderungen(material, aenderungIndex = index, wahl = wahl, entfernen = entfernen)
        senden(BerichtAenderung(material = materialJson(liste)))
    }

    private fun aendern(aenderung: BerichtAenderung, sperren: Boolean = false) {
        if (sperren) sperrend { senden(aenderung) } else viewModelScope.launch { senden(aenderung) }
    }

    /**
     * Ein `PATCH`. Antwort uebernehmen, aber Stunden/Mengen behalten, deren
     * entprellte Aenderung noch aussteht - sonst sprangen die Zahlen kurz zurueck.
     * Fehler: Servertext zeigen und neu laden, damit die Karte wieder stimmt.
     */
    private suspend fun senden(aenderung: BerichtAenderung): Boolean {
        val id = berichtId ?: return false
        return try {
            val neu = repository.berichtAendern(id, aenderung)
            uebernehmen(neu, currentCoroutineContext()[Job])
            true
        } catch (fehler: ApiFehler) {
            if (fehler is ApiFehler.NichtAngemeldet) abmelden() else {
                _meldungen.trySend(fehler.anzeigetext)
                neuLaden()
            }
            false
        }
    }

    private fun uebernehmen(neu: MonteurBericht, ich: Job?) {
        val alt = _zustand.value.bericht?.entwurf
        var e = neu.entwurf
        if (alt != null) {
            if (stundenJob.laeuftAusser(ich)) e = e.copy(monteur_stunden = alt.monteur_stunden, helfer_stunden = alt.helfer_stunden)
            if (mengenJob.laeuftAusser(ich) && e.material.size == alt.material.size) {
                e = e.copy(material = e.material.mapIndexed { i, m -> m.copy(menge = alt.material[i].menge) })
            }
        }
        zeigen(neu.copy(entwurf = e))
    }

    private fun Job?.laeuftAusser(ich: Job?): Boolean = this != null && isActive && this !== ich

    /** Entprellte Aenderungen sofort senden (vor Diktat und Abschluss). */
    private suspend fun ausstehendeSenden(): Boolean {
        var ok = true
        if (stundenJob?.isActive == true) {
            stundenJob?.cancel()
            ok = stundenSenden()
        }
        if (mengenJob?.isActive == true) {
            mengenJob?.cancel()
            ok = materialSenden() && ok
        }
        return ok
    }

    private suspend fun materialSenden(): Boolean {
        val material = _zustand.value.bericht?.entwurf?.material ?: return true
        return senden(BerichtAenderung(material = materialJson(materialAenderungen(material))))
    }

    // --- Ergaenzen per Mikrofon ------------------------------------------------------------

    /** Ergaenzung oder Antwort auf eine Rueckfrage: die KI baut den Entwurf neu. */
    fun diktatSenden(datei: File) {
        val id = berichtId ?: return
        if (_zustand.value.gesperrt) return
        _zustand.value.offenesDiktat?.takeIf { it != datei.absolutePath }?.let { File(it).delete() }
        _zustand.update { it.copy(diktatLaeuft = true, offenesDiktat = datei.absolutePath) }
        viewModelScope.launch {
            try {
                ausstehendeSenden()
                val neu = ergaenzen(id, aufnahme = datei, text = null) ?: return@launch
                datei.delete()
                _zustand.update { it.copy(offenesDiktat = null) }
                uebernehmen(neu, null)
            } catch (fehler: ApiFehler) {
                when (fehler) {
                    is ApiFehler.NichtAngemeldet -> abmelden()
                    // Keine Sprache / zu gross: wiederholt sich, also weg damit.
                    is ApiFehler.Ungueltig, is ApiFehler.ZuGross -> {
                        datei.delete()
                        _zustand.update { it.copy(offenesDiktat = null) }
                        _meldungen.trySend(fehler.anzeigetext)
                    }
                    else -> _meldungen.trySend(fehler.anzeigetext)
                }
            } finally {
                _zustand.update { it.copy(diktatLaeuft = false) }
            }
        }
    }

    fun ergaenzungAendern(text: String) = _zustand.update { it.copy(ergaenzung = text) }

    /**
     * Wie [diktatSenden], nur getippt - fuer alle, die lieber tippen, und fuer die
     * laute Werkstatt. Gleicher Endpunkt (`…/diktat` mit `text`), die KI baut den
     * Entwurf neu. Der Text bleibt bei Fehlern im Feld.
     */
    fun ergaenzungSenden() {
        val id = berichtId ?: return
        val text = _zustand.value.ergaenzung.trim()
        if (text.isEmpty() || _zustand.value.gesperrt) return
        _zustand.update { it.copy(diktatLaeuft = true) }
        viewModelScope.launch {
            try {
                ausstehendeSenden()
                val neu = ergaenzen(id, aufnahme = null, text = text) ?: return@launch
                _zustand.update { it.copy(ergaenzung = "") }
                uebernehmen(neu, null)
            } catch (fehler: ApiFehler) {
                if (fehler is ApiFehler.NichtAngemeldet) abmelden() else _meldungen.trySend(fehler.anzeigetext)
            } finally {
                _zustand.update { it.copy(diktatLaeuft = false) }
            }
        }
    }

    /**
     * Ergaenzung senden. Mit Karte (1.23.0) ueber den Eingang mit `bericht_id` - die
     * Antwort ist eine neue Karte mit derselben Bericht-ID, die alte wird ersetzt.
     * Erkennt die KI etwas anderes als einen Bericht, bleibt der Entwurf, wie er ist,
     * und es kommt ein Hinweis (`null`).
     */
    private suspend fun ergaenzen(id: String, aufnahme: File?, text: String?): MonteurBericht? {
        if (karteId == null) return repository.berichtDiktat(id, aufnahme = aufnahme, text = text)
        val karte = repository.eingang(
            anfrageId = MonteurRepository.neueAnfrageId(),
            aufnahme = aufnahme,
            text = text,
            berichtId = id,
        )
        val bericht = karte.bericht
        if (karte.art != Karte.ART_BERICHT || bericht == null) {
            _meldungen.trySend(ANDERE_ART)
            return null
        }
        karteId = karte.id
        return bericht
    }

    fun offenesDiktatSenden() {
        _zustand.value.offenesDiktat?.let { diktatSenden(File(it)) }
    }

    fun offenesDiktatVerwerfen() {
        _zustand.value.offenesDiktat?.let { File(it).delete() }
        _zustand.update { it.copy(offenesDiktat = null) }
    }

    // --- Unterschrift ------------------------------------------------------------------------

    fun strichBeginnen(punkt: Offset) {
        striche.add(listOf(punkt))
    }

    fun strichWeiter(punkt: Offset) {
        val letzter = striche.lastOrNull() ?: return
        striche[striche.lastIndex] = letzter + punkt
    }

    fun stricheLoeschen() {
        striche.clear()
    }

    /**
     * Die Zeichenflaeche hat eine neue Groesse (Drehen): Striche gleichmaessig
     * mitskalieren, damit die Unterschrift nicht verzerrt oder aus dem Bild faellt.
     */
    fun flaecheGeaendert(neu: Size) {
        val alt = zeichenflaeche
        zeichenflaeche = neu
        if (alt == Size.Zero || alt == neu || striche.isEmpty()) return
        val f = minOf(neu.width / alt.width, neu.height / alt.height)
        val kopie = striche.map { strich -> strich.map { Offset(it.x * f, it.y * f) } }
        striche.clear()
        striche.addAll(kopie)
    }

    fun unterschriftSetzen(png: ByteArray, name: String) {
        _zustand.update { it.copy(unterschrift = Unterschrift(png, name.trim())) }
    }

    fun unterschriftEntfernen() {
        striche.clear()
        _zustand.update { it.copy(unterschrift = null) }
    }

    // --- Abschliessen und Verwerfen ----------------------------------------------------------

    /** „Passt – nach HERO“. Ohne Unterschrift fragt die Oberflaeche vorher nach. */
    fun abschliessen() {
        val bericht = _zustand.value.bericht ?: return
        if (_zustand.value.gesperrt) return
        val u = _zustand.value.unterschrift
        val abschluss = Abschluss(
            unterschrift_png = u?.let { Base64.encodeToString(it.png, Base64.NO_WRAP) },
            unterschrift_name = u?.name?.ifBlank { null },
            zeiten_buchen = true,
        )
        sperrend {
            if (!ausstehendeSenden()) return@sperrend
            try {
                val karte = karteId
                val fertig = if (karte != null) {
                    // 1.23.0: einziger Bestaetigungsweg ist „Passt“ an der Karte.
                    val antwort = repository.passt(
                        karte,
                        Passt(
                            unterschrift_png = abschluss.unterschrift_png,
                            unterschrift_name = abschluss.unterschrift_name,
                            zeiten_buchen = abschluss.zeiten_buchen,
                        ),
                    )
                    val b = antwort.bericht ?: repository.bericht(bericht.id)
                    b.copy(
                        hero_nr = b.hero_nr ?: antwort.ergebnisText("hero_nr"),
                        hinweise = (b.hinweise + antwort.hinweise).distinct(),
                    )
                } else {
                    repository.berichtAbschliessen(bericht.id, abschluss)
                }
                _zustand.update { it.copy(unterschrift = null) }
                striche.clear()
                zeigen(fertig)
            } catch (fehler: ApiFehler) {
                when {
                    // Offene Material-Rueckfrage: zurueck zur Karte, frisch geladen.
                    fehler is ApiFehler.Beschaeftigt -> {
                        _zustand.update { it.copy(hinweisDialog = fehler.anzeigetext) }
                        neuLaden()
                    }
                    fehler.istVoruebergehend -> {
                        abschlussEinreihen(bericht, abschluss, fehler)
                        if (fehler is ApiFehler.NichtAngemeldet) abmelden()
                    }
                    else -> _zustand.update { it.copy(hinweisDialog = fehler.anzeigetext) }
                }
            }
        }
    }

    /** Kein Netz oder 502/5xx: Abschluss merken, der Korb wiederholt ihn mit derselben Bericht-ID. */
    private fun abschlussEinreihen(bericht: MonteurBericht, abschluss: Abschluss, fehler: ApiFehler) {
        try {
            val titel = listOfNotNull(bericht.projekt?.nr?.ifBlank { null }, kundeAusTitel(start.titel).ifBlank { null })
                .joinToString(" · ").ifBlank { null }
            val karte = karteId
            if (karte != null) {
                app.korb.passtEinreihen(
                    PasstAuftrag(
                        karte,
                        Passt(abschluss.unterschrift_png, abschluss.unterschrift_name, abschluss.zeiten_buchen),
                        titel?.let { "Bericht $it" } ?: "Bericht",
                    )
                )
            } else {
                app.korb.abschlussEinreihen(AbschlussAuftrag(bericht.id, abschluss, titel?.let { "Bericht $it" } ?: "Bericht"))
            }
            MonteurVersand.anstossen(app)
            _zustand.update { it.copy(phase = Phase.ABSCHLUSS_WARTET, unterschrift = null) }
        } catch (_: Exception) {
            _zustand.update { it.copy(hinweisDialog = fehler.anzeigetext) }
        }
    }

    fun hinweisGelesen() {
        _zustand.update { it.copy(hinweisDialog = null) }
    }

    /** `DELETE` - nur Entwuerfe. Die Oberflaeche fragt vorher nach. */
    fun verwerfen() {
        val id = berichtId ?: return
        sperrend {
            try {
                val karte = karteId
                if (karte != null) repository.karteVerwerfen(karte) else repository.berichtVerwerfen(id)
                schliessen()
            } catch (fehler: ApiFehler) {
                when (fehler) {
                    is ApiFehler.NichtAngemeldet -> abmelden()
                    is ApiFehler.NichtGefunden -> schliessen()
                    else -> {
                        _meldungen.trySend(fehler.anzeigetext)
                        neuLaden()
                    }
                }
            }
        }
    }

    private fun schliessen() {
        gespeichert.remove<String>(K_BERICHT)
        _zustand.update { it.copy(geschlossen = true) }
    }

    // --- Hilfen ------------------------------------------------------------------------------

    private fun lokal(aenderung: (MonteurBericht) -> MonteurBericht) {
        _zustand.update { z -> z.bericht?.let { z.copy(bericht = aenderung(it)) } ?: z }
    }

    private fun sperrend(block: suspend () -> Unit) {
        if (_zustand.value.sendet) return
        _zustand.update { it.copy(sendet = true) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                _zustand.update { it.copy(sendet = false) }
            }
        }
    }

    private fun offenSetzen(pfad: String?) {
        gespeichert[K_OFFEN] = pfad
        _zustand.update { it.copy(offeneAufnahme = pfad) }
    }

    private fun abmelden() {
        repository.lokalAbmelden()
        _abgemeldet.value = true
    }

    companion object {
        /** Pause nach dem letzten +/−, bevor der `PATCH` rausgeht. */
        private const val ENTPRELLEN_MS = 700L

        private const val K_ANFRAGE = "bericht_anfrage"
        private const val K_BERICHT = "bericht_id"
        private const val K_TEXT = "bericht_text"
        private const val K_UNTERWEGS = "bericht_unterwegs"
        private const val K_IM_KORB = "bericht_im_korb"
        private const val K_VERBRAUCHT = "bericht_aufnahme_verbraucht"
        private const val K_OFFEN = "bericht_offene_aufnahme"
        private const val K_AUTOMATISCH = "bericht_automatisch"
        private const val K_KARTE = "bericht_karte"

        /** Ergaenzung wurde nicht als Bericht erkannt. */
        private const val ANDERE_ART = "Das klang nicht nach einer Ergänzung zum Bericht. Für Fragen, Material oder Infos ans Büro den Mikrofon-Knopf auf „Heute“ nehmen."

        fun factory(app: MonteurApp, start: BerichtStart) = viewModelFactory {
            initializer { BerichtViewModel(app, start, createSavedStateHandle()) }
        }
    }
}

/** Stand der Artikelsuche im Dialog „+ Material“. */
data class ArtikelSuche(
    val suchtext: String = "",
    val treffer: List<de.chefassistent.app.monteur.data.Artikel> = emptyList(),
    val laeuft: Boolean = false,
    val fehler: String? = null,
)

private const val SUCHE_PAUSE_MS = 300L
