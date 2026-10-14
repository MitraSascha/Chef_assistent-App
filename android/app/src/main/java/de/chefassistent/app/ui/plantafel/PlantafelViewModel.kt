package de.chefassistent.app.ui.plantafel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.chefassistent.app.data.ApiFehler
import de.chefassistent.app.data.AssistentRepository
import de.chefassistent.app.data.EinsatzAenderung
import de.chefassistent.app.data.EinsatzNeu
import de.chefassistent.app.data.GewerkSetzen
import de.chefassistent.app.data.GuPlan
import de.chefassistent.app.data.GuProjekt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class PlantafelZustand(
    val projekte: List<GuProjekt>? = null,
    val plan: GuPlan? = null,
    val laeuft: Boolean = false,
    /** Ein Schreibaufruf laeuft - Knoepfe sperren. */
    val beschaeftigt: Boolean = false,
    val laedtPdf: Boolean = false,
    val fehler: String? = null,
    val abgemeldet: Boolean = false,
)

/**
 * GU-Plantafel (`/v1/bauablauf`, seit v1.9.0). Dient sowohl der Projektliste
 * als auch der Tafel eines Projekts.
 *
 * Nach jeder Aenderung wird die Tafel neu geladen (`API.md` 4d, Punkt 5):
 * Mitverschobene Einsaetze und neue Warnungen berechnet der Server.
 */
class PlantafelViewModel(private val repository: AssistentRepository) : ViewModel() {

    private val _zustand = MutableStateFlow(PlantafelZustand())
    val zustand: StateFlow<PlantafelZustand> = _zustand.asStateFlow()

    private var projektId: Int? = null

    fun fehlerVerwerfen() = _zustand.update { it.copy(fehler = null) }

    fun projekteLaden() = mitLaden {
        val projekte = repository.guProjekte()
        _zustand.update { it.copy(projekte = projekte) }
    }

    fun planLaden(id: Int) {
        if (projektId != id) {
            projektId = id
            _zustand.update { it.copy(plan = null) }
        }
        mitLaden { planHolen() }
    }

    /** Legt `GU-Projekte/<name>` an und meldet die neue ID. */
    fun projektAnlegen(name: String, beiErfolg: (Int) -> Unit) = schreiben {
        val projekt = repository.guProjektNeu(name)
        _zustand.update { z -> z.copy(projekte = listOf(projekt) + z.projekte.orEmpty().filter { it.id != projekt.id }) }
        beiErfolg(projekt.id)
    }

    fun einsatzAnlegen(neu: EinsatzNeu, beiErfolg: () -> Unit = {}) = schreiben(beiErfolg) {
        repository.einsatzNeu(projektId ?: return@schreiben, neu)
    }

    fun einsatzAendern(einsatzId: Int, aenderung: EinsatzAenderung, beiErfolg: () -> Unit = {}) = schreiben(beiErfolg) {
        repository.einsatzAendern(einsatzId, aenderung)
    }

    fun einsatzLoeschen(einsatzId: Int, beiErfolg: () -> Unit = {}) = schreiben(beiErfolg) {
        repository.einsatzLoeschen(einsatzId)
    }

    fun gewerkSetzen(gewerk: GewerkSetzen, beiErfolg: () -> Unit = {}) = schreiben(beiErfolg) {
        repository.gewerkSetzen(projektId ?: return@schreiben, gewerk)
    }

    fun gewerkLoeschen(gewerkId: Int, beiErfolg: () -> Unit = {}) = schreiben(beiErfolg) {
        repository.gewerkLoeschen(gewerkId)
    }

    /** Bauzeitenplan als PDF in den Projektordner legen, laden und uebergeben. */
    fun pdf(beiErfolg: (File) -> Unit) {
        val id = projektId ?: return
        if (_zustand.value.laedtPdf) return
        _zustand.update { it.copy(laedtPdf = true, fehler = null) }
        viewModelScope.launch {
            try {
                val pfad = repository.guPdf(id)
                beiErfolg(repository.dateiLaden(pfad))
            } catch (fehler: ApiFehler) {
                behandleFehler(fehler)
            } finally {
                _zustand.update { it.copy(laedtPdf = false) }
            }
        }
    }

    private suspend fun planHolen() {
        val id = projektId ?: return
        val plan = repository.guPlan(id)
        _zustand.update { it.copy(plan = plan) }
    }

    private fun mitLaden(block: suspend () -> Unit) {
        _zustand.update { it.copy(laeuft = true, fehler = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (fehler: ApiFehler) {
                behandleFehler(fehler)
            } finally {
                _zustand.update { it.copy(laeuft = false) }
            }
        }
    }

    /**
     * Schreibt, zeigt einen Fehler als Snackbar und laedt die Tafel in jedem Fall neu.
     * [beiErfolg] schliesst das Sheet erst, wenn der Server die Aenderung hat - im
     * Funkloch bleibt es mit allen Eingaben offen, statt sie zu verwerfen.
     */
    private fun schreiben(beiErfolg: () -> Unit = {}, block: suspend () -> Unit) {
        if (_zustand.value.beschaeftigt) return
        _zustand.update { it.copy(beschaeftigt = true, fehler = null) }
        viewModelScope.launch {
            try {
                block()
                beiErfolg()
            } catch (fehler: ApiFehler) {
                behandleFehler(fehler)
            }
            try {
                if (projektId != null) planHolen()
            } catch (fehler: ApiFehler) {
                behandleFehler(fehler)
            } finally {
                _zustand.update { it.copy(beschaeftigt = false) }
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
        fun factory(repository: AssistentRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { PlantafelViewModel(repository) }
        }
    }
}
