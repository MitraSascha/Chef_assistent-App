package de.chefassistent.app.ui.verbrauch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.chefassistent.app.data.ApiFehler
import de.chefassistent.app.data.AssistentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

data class VerbrauchZustand(
    val daten: JsonElement? = null,
    val laeuft: Boolean = false,
    val fehler: String? = null,
    val abgemeldet: Boolean = false,
)

class VerbrauchViewModel(private val repository: AssistentRepository) : ViewModel() {

    private val _zustand = MutableStateFlow(VerbrauchZustand())
    val zustand: StateFlow<VerbrauchZustand> = _zustand.asStateFlow()

    init {
        laden()
    }

    fun fehlerVerwerfen() = _zustand.update { it.copy(fehler = null) }

    fun laden() {
        _zustand.update { it.copy(laeuft = true, fehler = null) }
        viewModelScope.launch {
            try {
                _zustand.update { it.copy(daten = repository.verbrauch()) }
            } catch (fehler: ApiFehler) {
                if (fehler is ApiFehler.NichtAngemeldet) {
                    repository.abmelden()
                    _zustand.update { it.copy(abgemeldet = true, fehler = fehler.anzeigetext) }
                } else {
                    _zustand.update { it.copy(fehler = fehler.anzeigetext) }
                }
            } finally {
                _zustand.update { it.copy(laeuft = false) }
            }
        }
    }

    companion object {
        fun factory(repository: AssistentRepository) = viewModelFactory {
            initializer { VerbrauchViewModel(repository) }
        }
    }
}
