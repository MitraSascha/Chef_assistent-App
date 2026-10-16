package de.chefassistent.app.ui.ablage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.chefassistent.app.data.ApiFehler
import de.chefassistent.app.data.AssistentRepository
import de.chefassistent.app.data.Ordner
import de.chefassistent.app.data.SuchErgebnis
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class AblageZustand(
    val ordner: Ordner? = null,
    /** Datei, die hervorgehoben wird - die, ueber die man hergekommen ist. */
    val markiert: String? = null,
    val laeuft: Boolean = false,
    /** Naechste Seite wird gerade geholt. */
    val laedtMehr: Boolean = false,
    /** `name`, `name_ab` oder `geaendert`. */
    val sortierung: String = SORTIERUNG_NAME,
    /** Pfad der Datei, die gerade geladen wird. */
    val laedtDatei: String? = null,
    val fehler: String? = null,
    val abgemeldet: Boolean = false,
    /** Was im Suchfeld steht. Unter [MIN_ZEICHEN] wird der Ordner gezeigt. */
    val suchtext: String = "",
    /** "Nur in diesem Ordner" - sonst ganze Ablage, wie `API.md` es vorgibt. */
    val nurHier: Boolean = false,
    val sucht: Boolean = false,
    /** Ergebnis zum aktuellen [suchtext], `null` = noch keins. */
    val suche: SuchErgebnis? = null,
) {
    /** Es gibt einen uebergeordneten Ordner - dann steht oben eine Zeile "Nach oben". */
    val kannZurueck: Boolean get() = ordner?.eltern != null

    /** Statt des Ordners steht die Trefferliste da. */
    val suchmodus: Boolean get() = suchtext.trim().length >= MIN_ZEICHEN

    companion object {
        const val MIN_ZEICHEN = 2
        const val SORTIERUNG_NAME = "name"
    }
}

/**
 * Ordneransicht der Ablage (`GET /v1/ordner`) mit Suche (`GET /v1/suche`).
 *
 * Ist der Startpfad eine Datei, liefert der Server ihren Ordner - die Datei
 * wird dann markiert, wie am PC im Explorer.
 *
 * Geladen wird seitenweise zu je [SEITE] Eintraegen (Empfehlung aus `API.md`).
 * Im HERO-Archiv liegen Hunderte Projektordner, auf einen Schlag waere das
 * spuerbar langsam.
 */
class AblageViewModel(
    private val repository: AssistentRepository,
    startPfad: String,
) : ViewModel() {

    private val _zustand = MutableStateFlow(AblageZustand())
    val zustand: StateFlow<AblageZustand> = _zustand.asStateFlow()

    /** Besuchte Ordner, fuer die Zurueck-Taste. */
    private val verlauf = ArrayDeque<String>()

    private var ladeAuftrag: Job? = null
    private var suchAuftrag: Job? = null

    init {
        laden(startPfad, markieren = true)
    }

    fun fehlerVerwerfen() = _zustand.update { it.copy(fehler = null) }

    fun ordnerOeffnen(pfad: String) {
        _zustand.value.ordner?.let { verlauf.addLast(it.pfad) }
        // Wer aus den Treffern einen Ordner oeffnet, will dorthin und nicht
        // weiter in der Trefferliste stehen.
        sucheBeenden()
        laden(pfad, markieren = true)
    }

    fun nachOben() {
        val eltern = _zustand.value.ordner?.eltern ?: return
        ordnerOeffnen(eltern)
    }

    /**
     * `true`, wenn die Taste hier etwas getan hat - sonst verlaesst der
     * Bildschirm die Ansicht. Eine offene Suche wird zuerst geschlossen.
     */
    fun zurueck(): Boolean {
        if (_zustand.value.suchtext.isNotEmpty()) {
            sucheBeenden()
            return true
        }
        val vorher = verlauf.removeLastOrNull() ?: return false
        laden(vorher, markieren = false)
        return true
    }

    fun neuLaden() {
        if (_zustand.value.suchmodus) {
            suchen(sofort = true)
            return
        }
        val aktuell = _zustand.value.ordner?.pfad ?: return
        laden(aktuell, markieren = false)
    }

    fun sortierungWaehlen(sortierung: String) {
        if (sortierung == _zustand.value.sortierung) return
        _zustand.update { it.copy(sortierung = sortierung) }
        // Bei offener Suche auch den Ordner darunter: sonst zeigte er nach dem
        // Schliessen die alte Reihenfolge, waehrend der Chip die neue anzeigt.
        val ordner = _zustand.value.ordner?.pfad
        if (_zustand.value.suchmodus && ordner != null) laden(ordner, markieren = false)
        neuLaden()
    }

    /** Am Listenende: naechste Seite holen, solange der Server `abgeschnitten` meldet. */
    fun mehrLaden() {
        val z = _zustand.value
        val ordner = z.ordner ?: return
        if (!ordner.abgeschnitten || z.laeuft || z.laedtMehr) return
        _zustand.update { it.copy(laedtMehr = true) }
        ladeAuftrag = viewModelScope.launch {
            try {
                val seite = repository.ordner(ordner.pfad, z.sortierung, ab = ordner.eintraege.size, anzahl = SEITE)
                _zustand.update { alt ->
                    val bisher = alt.ordner ?: return@update alt
                    // Ordner inzwischen gewechselt: Seite gehoert nicht mehr hierher.
                    if (bisher.pfad != seite.pfad) return@update alt
                    val bekannt = bisher.eintraege.map { it.pfad }.toSet()
                    alt.copy(
                        ordner = seite.copy(eintraege = bisher.eintraege + seite.eintraege.filterNot { it.pfad in bekannt })
                    )
                }
            } catch (fehler: ApiFehler) {
                behandleFehler(fehler)
            } finally {
                _zustand.update { it.copy(laedtMehr = false) }
            }
        }
    }

    // --- Suche ---------------------------------------------------------------

    fun suchtextGeaendert(text: String) {
        _zustand.update { it.copy(suchtext = text) }
        suchen(sofort = false)
    }

    fun nurHierUmschalten() {
        _zustand.update { it.copy(nurHier = !it.nurHier) }
        suchen(sofort = true)
    }

    fun sucheBeenden() {
        suchAuftrag?.cancel()
        _zustand.update { it.copy(suchtext = "", suche = null, sucht = false) }
    }

    /**
     * Sucht waehrend des Tippens. Die vorige Anfrage wird abgebrochen und erst
     * nach [ENTPRELLEN_MS] Ruhe eine neue gestellt - sonst ginge pro Buchstabe
     * eine raus, und eine langsame alte Antwort koennte die neue ueberschreiben.
     */
    private fun suchen(sofort: Boolean) {
        suchAuftrag?.cancel()
        val z = _zustand.value
        val q = z.suchtext.trim()
        if (q.length < AblageZustand.MIN_ZEICHEN) {
            _zustand.update { it.copy(suche = null, sucht = false) }
            return
        }
        val ordner = if (z.nurHier) z.ordner?.pfad else null
        _zustand.update { it.copy(sucht = true) }
        suchAuftrag = viewModelScope.launch {
            if (!sofort) delay(ENTPRELLEN_MS)
            try {
                val ergebnis = repository.suche(q.take(MAX_SUCHTEXT), ordner)
                ensureActive()
                _zustand.update { it.copy(suche = ergebnis, sucht = false) }
            } catch (fehler: ApiFehler) {
                // Ein abgebrochener Aufruf kommt als Verbindungsfehler zurueck -
                // der ist nicht echt, die naechste Anfrage laeuft schon.
                ensureActive()
                _zustand.update { it.copy(sucht = false) }
                behandleFehler(fehler)
            }
        }
    }

    // --- Dateien -------------------------------------------------------------

    fun dateiOeffnen(pfad: String, beiErfolg: (File) -> Unit) {
        if (_zustand.value.laedtDatei != null) return
        _zustand.update { it.copy(laedtDatei = pfad, markiert = pfad) }
        viewModelScope.launch {
            try {
                beiErfolg(repository.dateiLaden(pfad))
            } catch (fehler: ApiFehler) {
                behandleFehler(fehler)
            } finally {
                _zustand.update { it.copy(laedtDatei = null) }
            }
        }
    }

    /**
     * Laedt einen Ordner. Mit [markieren] wird ein Datei-Pfad darin hervorgehoben
     * (Einstieg ueber "Im Ordner zeigen" oder einen Suchtreffer).
     *
     * Liegt die Datei nicht auf der ersten Seite, werden weitere Seiten geholt,
     * bis sie auftaucht - sonst stuende man im Ordner und saehe sie nicht.
     */
    private fun laden(pfad: String, markieren: Boolean) {
        ladeAuftrag?.cancel()
        _zustand.update { it.copy(laeuft = true, laedtMehr = false, fehler = null) }
        val sortierung = _zustand.value.sortierung
        ladeAuftrag = viewModelScope.launch {
            try {
                var ordner = repository.ordner(pfad, sortierung, ab = 0, anzahl = SEITE)
                if (markieren && pfad != ordner.pfad) {
                    var seiten = 1
                    while (ordner.abgeschnitten && ordner.eintraege.none { it.pfad == pfad } && seiten < MAX_SEITEN_SUCHEN) {
                        val weiter = repository.ordner(ordner.pfad, sortierung, ab = ordner.eintraege.size, anzahl = SEITE)
                        ordner = weiter.copy(eintraege = ordner.eintraege + weiter.eintraege)
                        seiten++
                    }
                }
                val fertig = ordner
                _zustand.update {
                    it.copy(
                        ordner = fertig,
                        markiert = if (markieren) pfad.takeIf { p -> fertig.eintraege.any { e -> e.pfad == p } } else null,
                    )
                }
            } catch (fehler: ApiFehler) {
                ensureActive()
                behandleFehler(fehler)
            } finally {
                // Abgebrochen heisst: ein neueres Laden laeuft schon und meldet selbst.
                if (isActive) _zustand.update { it.copy(laeuft = false) }
            }
        }
    }

    private fun behandleFehler(fehler: ApiFehler) {
        if (fehler is ApiFehler.NichtAngemeldet) {
            repository.abmelden()
            _zustand.update { it.copy(abgemeldet = true, fehler = fehler.anzeigetext) }
        } else {
            _zustand.update { it.copy(fehler = fehler.anzeigetext) }
        }
    }

    companion object {
        /** Seitengroesse fuer `GET /v1/ordner`, Empfehlung aus `API.md`. */
        const val SEITE = 100

        /** Bis zu so vielen Seiten wird nach einer zu markierenden Datei gesucht. */
        private const val MAX_SEITEN_SUCHEN = 10

        private const val ENTPRELLEN_MS = 300L

        /** Grenze fuer `q` aus dem Vertrag (`maxLength`). */
        private const val MAX_SUCHTEXT = 200

        fun factory(repository: AssistentRepository, startPfad: String) = viewModelFactory {
            initializer { AblageViewModel(repository, startPfad) }
        }
    }
}
