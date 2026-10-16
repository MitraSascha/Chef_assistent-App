package de.chefassistent.app.ui.angebote

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.chefassistent.app.data.Angebot
import de.chefassistent.app.data.AngebotKurz
import de.chefassistent.app.data.ApiFehler
import de.chefassistent.app.data.AssistentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class AngeboteZustand(
    val liste: List<AngebotKurz>? = null,
    val angebot: Angebot? = null,
    val laeuft: Boolean = false,
    val laedtUgl: Boolean = false,
    /** `id` des Punkts, dessen Antwort gerade unterwegs ist. */
    val antwortet: String? = null,
    /** Text aus `detail` bei 503: Angebote auf diesem Server nicht eingerichtet. */
    val nichtEingerichtet: String? = null,
    /** 404 beim Angebot: gibt es nicht (mehr). */
    val nichtGefunden: String? = null,
    /** 502: Angebotsgenerator nicht erreichbar, "Nochmal versuchen" anbieten. */
    val nichtErreichbar: String? = null,
    val fehler: String? = null,
    val abgemeldet: Boolean = false,
)

/**
 * Angebote aus dem Angebotsgenerator (`/v1/angebote`, seit v1.11.0). Dient der
 * Liste und der Vorschau eines Angebots.
 *
 * Nur lesen und herunterladen: Angebote entstehen und aendern sich im Chat
 * (`API.md` 4e).
 */
class AngeboteViewModel(private val repository: AssistentRepository) : ViewModel() {

    private val _zustand = MutableStateFlow(AngeboteZustand())
    val zustand: StateFlow<AngeboteZustand> = _zustand.asStateFlow()

    private var angebotId: String? = null

    fun fehlerVerwerfen() = _zustand.update { it.copy(fehler = null) }

    fun listeLaden() = mitLaden {
        val liste = repository.angebote()
        _zustand.update { it.copy(liste = liste) }
    }

    fun angebotLaden(id: String) {
        if (angebotId != id) {
            angebotId = id
            _zustand.update { it.copy(angebot = null, nichtGefunden = null) }
        }
        mitLaden {
            val angebot = repository.angebot(id)
            _zustand.update { it.copy(angebot = angebot, nichtGefunden = null) }
        }
    }

    /**
     * Beantwortet einen Punkt (`POST …/antworten`, seit v1.15.0). Der Server
     * schickt das ganze Angebot zurueck, das ersetzt die Ansicht. Ein 404 heisst
     * hier "Punkt schon weg" (anderes Geraet, Assistent hat eingearbeitet) - dann
     * Meldung zeigen und neu laden, nicht das ganze Angebot als verschwunden melden.
     */
    fun antworten(punkt: String, antwort: String) {
        val id = angebotId ?: return
        val text = antwort.trim()
        if (text.isEmpty() || _zustand.value.antwortet != null) return
        _zustand.update { it.copy(antwortet = punkt) }
        viewModelScope.launch {
            try {
                val angebot = repository.angebotAntworten(id, punkt, text)
                _zustand.update { it.copy(angebot = angebot) }
            } catch (fehler: ApiFehler) {
                // Das Angebot steht schon da - Fehler nur als Meldung, die Antwort bleibt im Feld.
                if (fehler is ApiFehler.NichtAngemeldet) {
                    behandleFehler(fehler)
                } else {
                    _zustand.update { it.copy(fehler = fehler.anzeigetext) }
                    if (fehler is ApiFehler.NichtGefunden) angebotLaden(id)
                }
            } finally {
                _zustand.update { it.copy(antwortet = null) }
            }
        }
    }

    /** Laedt die UGL-Datei und meldet sie zum Teilen. */
    fun uglLaden(beiFertig: (File) -> Unit) {
        val id = angebotId ?: return
        if (_zustand.value.laedtUgl) return
        _zustand.update { it.copy(laedtUgl = true) }
        viewModelScope.launch {
            try {
                beiFertig(repository.angebotUgl(id))
            } catch (fehler: ApiFehler) {
                // 404/503 hier betreffen nur die UGL-Datei, nicht das Angebot - also
                // als Meldung, nicht als "Angebot gibt es nicht" ueber der Vorschau.
                if (fehler is ApiFehler.NichtAngemeldet) behandleFehler(fehler)
                else _zustand.update { it.copy(fehler = fehler.anzeigetext) }
            } finally {
                _zustand.update { it.copy(laedtUgl = false) }
            }
        }
    }

    private fun mitLaden(block: suspend () -> Unit) {
        viewModelScope.launch {
            _zustand.update { it.copy(laeuft = true, nichtErreichbar = null) }
            try {
                block()
                _zustand.update { it.copy(nichtEingerichtet = null) }
            } catch (fehler: ApiFehler) {
                behandleFehler(fehler)
            } finally {
                _zustand.update { it.copy(laeuft = false) }
            }
        }
    }

    private fun behandleFehler(fehler: ApiFehler) {
        when {
            fehler is ApiFehler.NichtAngemeldet -> {
                repository.abmelden()
                _zustand.update { it.copy(abgemeldet = true) }
            }
            fehler is ApiFehler.Server && fehler.code == 503 ->
                _zustand.update { it.copy(nichtEingerichtet = fehler.anzeigetext) }
            fehler is ApiFehler.NichtGefunden ->
                _zustand.update { it.copy(nichtGefunden = fehler.anzeigetext) }
            fehler is ApiFehler.KiNichtErreichbar ->
                _zustand.update { it.copy(nichtErreichbar = fehler.anzeigetext) }
            else -> _zustand.update { it.copy(fehler = fehler.anzeigetext) }
        }
    }

    companion object {
        fun factory(repository: AssistentRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { AngeboteViewModel(repository) }
        }
    }
}
