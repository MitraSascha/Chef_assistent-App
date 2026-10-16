package de.chefassistent.app.ui.auftraege

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.chefassistent.app.data.ApiFehler
import de.chefassistent.app.data.AssistentRepository
import de.chefassistent.app.data.Auftrag
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class AuftraegeZustand(
    val auftraege: List<Auftrag> = emptyList(),
    /** Fertige Auftraege, die beim Oeffnen der Ansicht noch nicht gesehen waren. */
    val neu: Set<String> = emptySet(),
    val geladen: Boolean = false,
    val laeuft: Boolean = false,
    /** Auftrag, fuer den gerade Abbrechen oder Wiederholen laeuft. */
    val aktion: String? = null,
    val laedtDatei: String? = null,
    val fehler: String? = null,
    val abgemeldet: Boolean = false,
)

/**
 * Hintergrundauftraege (`GET /v1/auftraege`, seit v1.6.0).
 *
 * Solange einer wartet oder laeuft **und die Ansicht sichtbar ist**, wird alle
 * [ABFRAGE_MS] nachgefragt (`API.md`: 15-30 s). Im Hintergrund nicht: der
 * Server arbeitet ohnehin weiter, und die Abfrage kostet nur Akku.
 */
class AuftraegeViewModel(private val repository: AssistentRepository) : ViewModel() {

    private val _zustand = MutableStateFlow(AuftraegeZustand())
    val zustand: StateFlow<AuftraegeZustand> = _zustand.asStateFlow()

    private var abfrage: Job? = null

    init {
        laden()
    }

    fun fehlerVerwerfen() = _zustand.update { it.copy(fehler = null) }

    /** Vom Bildschirm bei Resume/Pause aufgerufen. */
    fun sichtbar(ja: Boolean) {
        abfrage?.cancel()
        abfrage = if (!ja) null else viewModelScope.launch {
            // Nach der Rueckkehr (Home, andere App) gleich den aktuellen Stand holen,
            // nicht erst, wenn schon vorher etwas lief.
            laden(still = true)
            while (isActive) {
                delay(ABFRAGE_MS)
                if (_zustand.value.auftraege.any { it.istAktiv }) laden(still = true)
            }
        }
    }

    /** @param still ohne Ladebalken und ohne Fehlermeldung - fuer die Abfrage im Takt. */
    fun laden(still: Boolean = false) {
        if (!still) _zustand.update { it.copy(laeuft = true, fehler = null) }
        viewModelScope.launch {
            try {
                uebernehmen(repository.auftraege())
            } catch (fehler: ApiFehler) {
                if (!still || fehler is ApiFehler.NichtAngemeldet) behandleFehler(fehler)
            } finally {
                if (!still) _zustand.update { it.copy(laeuft = false) }
            }
        }
    }

    fun abbrechen(id: String) = aktion(id) { repository.auftragAbbrechen(id) }

    fun wiederholen(id: String) = aktion(id) { repository.auftragWiederholen(id) }

    fun dateiOeffnen(pfad: String, beiErfolg: (File) -> Unit) {
        if (_zustand.value.laedtDatei != null) return
        _zustand.update { it.copy(laedtDatei = pfad) }
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

    private fun aktion(id: String, block: suspend () -> Auftrag) {
        if (_zustand.value.aktion != null) return
        _zustand.update { it.copy(aktion = id, fehler = null) }
        viewModelScope.launch {
            try {
                val neu = block()
                _zustand.update { z -> z.copy(auftraege = z.auftraege.map { if (it.id == neu.id) neu else it }) }
            } catch (fehler: ApiFehler) {
                // 409: Stand hat sich inzwischen geaendert (z.B. schon fertig).
                // Laut Vertrag kein Fehler-Popup, sondern neu laden.
                if (fehler !is ApiFehler.Beschaeftigt) behandleFehler(fehler)
                laden(still = true)
            } finally {
                _zustand.update { it.copy(aktion = null) }
            }
        }
    }

    /**
     * Was fertig ist, gilt ab jetzt als gesehen. Hervorgehoben bleibt es aber,
     * solange die Ansicht offen ist - sonst waere die Markierung sofort weg.
     */
    private fun uebernehmen(auftraege: List<Auftrag>) {
        val einstellungen = repository.einstellungen
        val gesehen = einstellungen.geseheneAuftraege
        val fertig = auftraege.filter { it.status == Auftrag.STATUS_FERTIG }.map { it.id }
        val ungesehen = fertig.filterNot { it in gesehen }
        if (ungesehen.isNotEmpty()) einstellungen.geseheneAuftraege = gesehen + ungesehen
        _zustand.update { it.copy(auftraege = auftraege, neu = it.neu + ungesehen, geladen = true) }
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
        const val ABFRAGE_MS = 20_000L

        fun factory(repository: AssistentRepository) = viewModelFactory {
            initializer { AuftraegeViewModel(repository) }
        }
    }
}
