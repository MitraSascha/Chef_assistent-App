package de.chefassistent.app.monteur.push

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.chefassistent.app.data.ApiFehler
import de.chefassistent.app.monteur.MonteurApp
import de.chefassistent.app.monteur.data.MonteurNachricht
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NachrichtenZustand(
    /** Ungelesene, neueste zuerst - die Leiste oben auf „Heute“. */
    val ungelesen: List<MonteurNachricht> = emptyList(),
    val anzahl: Int = 0,
    val pushAktiv: Boolean = false,
    /** Zaehlt hoch, wenn Termine sich geaendert haben - „Heute“ laedt dann neu. */
    val stand: Int = 0,
)

/**
 * Nachrichten zum Plan fuer die Leiste auf „Heute“ und die Zahl am Reiter
 * (Auftrag 1.24, 7.). Keine eigene Seite, keine Glocke.
 */
class NachrichtenViewModel(private val app: MonteurApp) : ViewModel() {

    private val _zustand = MutableStateFlow(NachrichtenZustand())
    val zustand: StateFlow<NachrichtenZustand> = _zustand.asStateFlow()

    private val _abgemeldet = MutableStateFlow(false)
    val abgemeldet: StateFlow<Boolean> = _abgemeldet.asStateFlow()

    init {
        // Push im Vordergrund oder der Abhol-Arbeiter hat etwas Neues: neu laden.
        viewModelScope.launch { app.nachrichtenSignal.drop(1).collect { laden() } }
    }

    /** Beim Start und bei jeder Rueckkehr in den Vordergrund. */
    fun laden() {
        if (!app.einstellungen.istAngemeldet) return
        viewModelScope.launch {
            try {
                val antwort = Nachrichtendienst.abholen(app, benachrichtigen = false)
                val ungelesen = antwort.nachrichten.filter { !it.gelesen && it.id > app.nachrichtenSpeicher.gelesenAusstehend }
                val vorher = _zustand.value.ungelesen.map { it.id }.toSet()
                val neu = ungelesen.any { it.id !in vorher }
                _zustand.update {
                    it.copy(
                        ungelesen = ungelesen,
                        anzahl = ungelesen.size,
                        pushAktiv = antwort.push_aktiv,
                        stand = if (neu) it.stand + 1 else it.stand,
                    )
                }
            } catch (fehler: ApiFehler) {
                // 503 = Server ohne Benachrichtigungen: still. 401 meldet ab.
                if (fehler is ApiFehler.NichtAngemeldet) {
                    app.repository.lokalAbmelden()
                    _abgemeldet.value = true
                }
            }
        }
    }

    /**
     * Gelesen bis [nachricht] (der Vertrag markiert alle bis zu dieser id). Lokal sofort
     * ausgeblendet; geht es nicht raus, holt der naechste Abruf es nach.
     */
    fun gelesen(nachricht: MonteurNachricht) = gelesenBis(nachricht.id)

    fun gelesenBis(id: Int) {
        _zustand.update { z ->
            val rest = z.ungelesen.filter { it.id > id }
            z.copy(ungelesen = rest, anzahl = rest.size)
        }
        Benachrichtigungen.entfernen(app, id)
        viewModelScope.launch {
            try {
                app.repository.gelesen(id)
            } catch (_: ApiFehler) {
                app.nachrichtenSpeicher.gelesenAusstehend = maxOf(app.nachrichtenSpeicher.gelesenAusstehend, id)
            }
        }
    }

    companion object {
        fun factory(app: MonteurApp) = viewModelFactory { initializer { NachrichtenViewModel(app) } }
    }
}
