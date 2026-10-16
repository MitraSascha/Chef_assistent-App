package de.chefassistent.app.ui.freihand

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.chefassistent.app.ChefAssistentApp
import de.chefassistent.app.data.ApiFehler
import de.chefassistent.app.data.AusgangAuftrag
import de.chefassistent.app.data.AssistentRepository
import de.chefassistent.app.data.AudioAufnahme
import de.chefassistent.app.data.Ausgangskorb
import de.chefassistent.app.data.Sprachausgabe
import de.chefassistent.app.data.Tonwiedergabe
import de.chefassistent.app.data.VersandArbeiter
import de.chefassistent.app.ui.markdownEntfernen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** Wo der Freihandmodus gerade steht. */
enum class Phase { BEREIT, HOERT, SENDET, SPRICHT }

data class FreihandZustand(
    val phase: Phase = Phase.BEREIT,
    val letzteFrage: String = "",
    val letzteAntwort: String = "",
    /** Aussteuerung 0..1, nur fuer die Anzeige. */
    val pegel: Float = 0f,
    /**
     * Nach mehreren stillen Runden haelt der Modus an, statt endlos
     * weiterzuhoeren. Erst ein Tipp auf "Weiter" nimmt ihn wieder auf.
     */
    val pausiert: Boolean = false,
    /** Sprachnotizen, die mangels Verbindung noch warten. */
    val wartendeNotizen: Int = 0,
    val fehler: String? = null,
    val abgemeldet: Boolean = false,
)

/**
 * Gespraech ohne Hinsehen: zuhoeren, senden, vorlesen, wieder zuhoeren.
 *
 * Gedacht fuers Auto und die Baustelle. Die Runde laeuft von selbst weiter, bis
 * der Nutzer sie beendet oder eine Weile nichts mehr sagt.
 *
 * **Kosten:** Jede Runde kostet eine Transkription und, wenn die OpenAI-Stimme
 * gewaehlt ist, zusaetzlich die Sprachausgabe. Wer viel spricht, sollte in den
 * Einstellungen die Geraetestimme waehlen.
 */
class FreihandViewModel(
    private val repository: AssistentRepository,
    private val aufnahme: AudioAufnahme,
    private val sprachausgabe: Sprachausgabe,
    private val tonwiedergabe: Tonwiedergabe,
    private val ausgangskorb: Ausgangskorb,
    private val anwendung: Context,
) : ViewModel() {

    private val _zustand = MutableStateFlow(FreihandZustand())
    val zustand: StateFlow<FreihandZustand> = _zustand.asStateFlow()

    private var gespraechId: String? = null

    /** Siehe [AusgangAuftrag.faden]: Runden ohne Netz, bevor es ein Gespraech gab. */
    private var faden: String? = null
    private var laufendeRunde: Job? = null
    private var stilleRunden = 0

    override fun onCleared() {
        beenden()
        sprachausgabe.freigeben()
        super.onCleared()
    }

    /** Startet eine Runde. Die Berechtigung holt der Bildschirm vorher ein. */
    fun zuhoeren() {
        if (_zustand.value.phase != Phase.BEREIT || _zustand.value.pausiert) return

        try {
            aufnahme.starten()
        } catch (_: Exception) {
            _zustand.update {
                it.copy(phase = Phase.BEREIT, fehler = "Das Mikrofon ist nicht verfügbar.")
            }
            return
        }

        _zustand.update { it.copy(phase = Phase.HOERT, pegel = 0f, fehler = null) }

        laufendeRunde = viewModelScope.launch {
            var hatGesprochen = false
            var stilleMs = 0L
            var gesamtMs = 0L

            while (isActive && _zustand.value.phase == Phase.HOERT) {
                delay(TAKT_MS)
                gesamtMs += TAKT_MS

                val ausschlag = aufnahme.maximalerAusschlag()
                _zustand.update { it.copy(pegel = (ausschlag / 32767f).coerceIn(0f, 1f)) }

                if (ausschlag > SCHWELLE) {
                    hatGesprochen = true
                    stilleMs = 0
                } else if (hatGesprochen) {
                    stilleMs += TAKT_MS
                }

                val fertigGesprochen = hatGesprochen && stilleMs >= STILLE_ENDE_MS
                val niemandDa = !hatGesprochen && gesamtMs >= WARTEN_MAX_MS
                val zuLang = gesamtMs >= AUFNAHME_MAX_MS

                if (niemandDa) {
                    // Keiner spricht - Runde still abbrechen statt Stille hochzuladen.
                    aufnahme.abbrechen()
                    stilleRunden++
                    _zustand.update {
                        it.copy(
                            phase = Phase.BEREIT,
                            pegel = 0f,
                            // Sonst laege das Handy in der Tasche und hoerte
                            // stundenlang weiter zu.
                            pausiert = stilleRunden >= STILLE_RUNDEN_MAX,
                        )
                    }
                    return@launch
                }
                if (fertigGesprochen || zuLang) {
                    absenden()
                    return@launch
                }
            }
        }
    }

    /** Manuell beenden, wenn die Stille-Erkennung zu lange braucht. */
    fun jetztSenden() {
        if (_zustand.value.phase != Phase.HOERT) return
        laufendeRunde?.cancel()
        viewModelScope.launch { absenden() }
    }

    private suspend fun absenden() {
        val datei = aufnahme.beenden()
        if (datei == null) {
            _zustand.update { it.copy(phase = Phase.BEREIT, pegel = 0f) }
            return
        }

        _zustand.update { it.copy(phase = Phase.SENDET, pegel = 0f) }

        // Ist eine frueher eingereihte Runde inzwischen angekommen, kennt der Korb
        // das Gespraech, das sie angelegt hat - dort geht es weiter.
        if (gespraechId == null) {
            faden?.let { f -> gespraechId = withContext(Dispatchers.IO) { ausgangskorb.gespraechZuFaden(f) } }
        }
        val gid = gespraechId
        val f = if (gid == null) faden ?: UUID.randomUUID().toString().also { faden = it } else null
        // Gleich fuer jeden Versuch, auch aus dem Korb: der Server fuehrt sie nie doppelt aus.
        val anfrageId = AssistentRepository.neueAnfrageId()

        try {
            // Wartet noch eine Runde dieses Gespraechs, darf die neue nicht vorbei.
            if (withContext(Dispatchers.IO) { ausgangskorb.wartetIn(gid, f) }) {
                einreihen(datei, gid, f, anfrageId)
                return
            }
            val antwort = repository.spracheSicher(datei, gid, anfrageId)
            gespraechId = antwort.chat?.gespraech_id ?: gespraechId

            stilleRunden = 0
            val text = antwort.chat?.antwort.orEmpty()
            _zustand.update {
                it.copy(letzteFrage = antwort.text, letzteAntwort = text)
            }

            if (text.isBlank()) {
                _zustand.update { it.copy(phase = Phase.BEREIT) }
            } else {
                vorlesen(markdownEntfernen(text))
            }
        } catch (abbruch: CancellationException) {
            // "Beenden" oder Bildschirm weg, waehrend die Runde unterwegs war. Ob
            // sie ankam, weiss niemand - lieber doppelt als verloren.
            withContext(NonCancellable) { einreihen(datei, gid, f, anfrageId) }
            throw abbruch
        } catch (fehler: ApiFehler.Ungueltig) {
            // 422 heisst hier meist: keine Sprache erkannt. Kein Grund fuer eine
            // Fehlermeldung mitten im Gespraech - einfach wieder zuhoeren.
            _zustand.update { it.copy(phase = Phase.BEREIT) }
        } catch (fehler: ApiFehler) {
            // Funkloch, Server oder KI kurz weg, abgelaufene Anmeldung: die Runde
            // bleibt erhalten und geht spaeter raus.
            if (fehler.istVoruebergehend) einreihen(datei, gid, f, anfrageId)
            if (fehler is ApiFehler.NichtAngemeldet) {
                repository.abmelden()
                _zustand.update { it.copy(abgemeldet = true, fehler = fehler.anzeigetext) }
            } else if (!fehler.istVoruebergehend) {
                _zustand.update { it.copy(phase = Phase.BEREIT, fehler = fehler.anzeigetext) }
            }
        } finally {
            datei.delete()
        }
    }

    /**
     * Im Freihandmodus faellt ein Funkloch besonders auf: der Nutzer redet weiter,
     * ohne zu merken, dass nichts ankommt. Also aufheben, anhalten und sagen.
     */
    private suspend fun einreihen(datei: java.io.File, gid: String?, f: String?, anfrageId: String) {
        val name = withContext(Dispatchers.IO) {
            ausgangskorb.ablegen(
                datei,
                AusgangAuftrag(
                    aufgenommen = System.currentTimeMillis(),
                    gespraech_id = gid,
                    faden = f,
                    antworten = true,
                    anfrage_id = anfrageId,
                ),
            )
        }
        if (name == null) {
            _zustand.update { it.copy(phase = Phase.BEREIT, fehler = "Die Aufnahme konnte nicht zwischengespeichert werden.") }
            return
        }
        VersandArbeiter.anstossen(anwendung)
        val wartend = withContext(Dispatchers.IO) { ausgangskorb.anzahl() }
        _zustand.update {
            it.copy(
                phase = Phase.BEREIT,
                pausiert = true,
                wartendeNotizen = wartend,
                fehler = "Keine Verbindung. Die Aufnahme wartet und geht später raus.",
            )
        }
    }

    private fun vorlesen(text: String) {
        _zustand.update { it.copy(phase = Phase.SPRICHT) }

        val fertig = {
            _zustand.update { alt ->
                if (alt.phase == Phase.SPRICHT) alt.copy(phase = Phase.BEREIT) else alt
            }
        }

        if (repository.einstellungen.serverStimme) {
            viewModelScope.launch {
                try {
                    val mp3 = repository.vorlesen(text, repository.einstellungen.stimme)
                    tonwiedergabe.abspielen(mp3) { fertig() }
                } catch (fehler: ApiFehler.NichtAngemeldet) {
                    // Nicht mit der Geraetestimme ueberspielen: abgelaufene Anmeldung
                    // fuehrt wie ueberall zur Anmeldung.
                    repository.abmelden()
                    _zustand.update { it.copy(phase = Phase.BEREIT, abgemeldet = true, fehler = fehler.anzeigetext) }
                } catch (_: ApiFehler) {
                    geraetestimme(text, fertig)
                }
            }
        } else {
            geraetestimme(text, fertig)
        }
    }

    private fun geraetestimme(text: String, fertig: () -> Unit) {
        if (sprachausgabe.verfuegbar) {
            sprachausgabe.vorlesen(text) { fertig() }
        } else {
            fertig()
        }
    }

    /** Nimmt den Modus nach einer Pause wieder auf. */
    fun weiter() {
        stilleRunden = 0
        _zustand.update { it.copy(pausiert = false) }
        zuhoeren()
    }

    fun fehlerVerwerfen() = _zustand.update { it.copy(fehler = null) }

    /**
     * Die App geht in den Hintergrund. Hoert der Modus gerade zu, geht raus, was
     * schon gesagt wurde (ohne Sprache passiert nichts). Danach pausiert er, statt
     * in der Hosentasche weiter Stille aufzunehmen. Eine laufende Antwort darf
     * zu Ende gesprochen werden.
     */
    fun hintergrund() {
        _zustand.update { it.copy(pausiert = true) }
        jetztSenden()
    }

    /** Alles anhalten - beim Verlassen des Bildschirms. */
    fun beenden() {
        laufendeRunde?.cancel()
        laufendeRunde = null
        aufnahme.abbrechen()
        sprachausgabe.stoppen()
        tonwiedergabe.stoppen()
        _zustand.update { it.copy(phase = Phase.BEREIT, pegel = 0f) }
    }

    companion object {
        /** Abfragetakt der Lautstaerke. */
        private const val TAKT_MS = 100L

        /**
         * Ab hier gilt es als Sprache. 32767 waere Vollausschlag; 2000 laesst
         * ruhige Umgebungsgeraeusche durchfallen, ohne leises Sprechen zu schlucken.
         */
        private const val SCHWELLE = 2000

        /** So lange Stille nach dem Sprechen beendet die Aufnahme. */
        private const val STILLE_ENDE_MS = 1500L

        /** Sagt niemand etwas, bricht die Runde ab, statt Stille hochzuladen. */
        private const val WARTEN_MAX_MS = 8_000L

        /** Notbremse gegen Daueraufnahme in der Hosentasche. */
        private const val AUFNAHME_MAX_MS = 60_000L

        /** So viele stille Runden hintereinander halten den Modus an. */
        private const val STILLE_RUNDEN_MAX = 3

        fun factory(repository: AssistentRepository, context: Context) = viewModelFactory {
            initializer {
                FreihandViewModel(
                    repository = repository,
                    aufnahme = AudioAufnahme(context.applicationContext),
                    sprachausgabe = Sprachausgabe(context.applicationContext),
                    tonwiedergabe = Tonwiedergabe(),
                    // Derselbe Korb wie der VersandArbeiter - die Sperren gelten pro Objekt.
                    ausgangskorb = (context.applicationContext as ChefAssistentApp).ausgangskorb,
                    anwendung = context.applicationContext,
                )
            }
        }
    }
}
