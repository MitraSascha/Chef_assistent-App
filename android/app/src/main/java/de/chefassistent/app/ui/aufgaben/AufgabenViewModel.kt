package de.chefassistent.app.ui.aufgaben

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.chefassistent.app.data.ApiFehler
import de.chefassistent.app.data.AssistentRepository
import de.chefassistent.app.data.Aufgabe
import de.chefassistent.app.data.AufgabeAenderung
import de.chefassistent.app.data.AufgabenUebersicht
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class AufgabenZustand(
    val uebersicht: AufgabenUebersicht? = null,
    /** Angezeigte Person, `null` = ich selbst. */
    val person: String? = null,
    /** Mein eigener Name, gemerkt aus der ersten eigenen Uebersicht. */
    val ich: String? = null,
    /** Namen, die in den Aufgaben vorkamen - Auswahl fuer `?person=`. */
    val personen: List<String> = emptyList(),
    val laeuft: Boolean = false,
    /** Aufgabe, fuer die gerade ein POST laeuft. */
    val aktion: String? = null,
    val laedtDatei: String? = null,
    val fehler: String? = null,
    val abgemeldet: Boolean = false,
)

/**
 * "Was ist offen?" (`GET /v1/aufgaben`, seit v1.7.0).
 *
 * Nach jedem POST wird neu geladen: die ID einer Aufgabe haengt am Text und
 * kann sich aendern, sobald jemand die Notiz bearbeitet (`API.md` 4c).
 */
class AufgabenViewModel(private val repository: AssistentRepository) : ViewModel() {

    private val _zustand = MutableStateFlow(AufgabenZustand())
    val zustand: StateFlow<AufgabenZustand> = _zustand.asStateFlow()

    fun fehlerVerwerfen() = _zustand.update { it.copy(fehler = null) }

    /** Andere Person anzeigen, `null` = zurueck zur eigenen Uebersicht. */
    fun personWaehlen(person: String?) {
        if (person == _zustand.value.person) return
        _zustand.update { it.copy(person = person, uebersicht = null) }
        laden()
    }

    /** Laufendes Laden - ein Personenwechsel bricht es ab, sonst gewinnt die spaetere Antwort. */
    private var ladenJob: Job? = null

    fun laden() {
        ladenJob?.cancel()
        _zustand.update { it.copy(laeuft = true, fehler = null) }
        val person = _zustand.value.person
        ladenJob = viewModelScope.launch {
            try {
                val uebersicht = repository.aufgaben(person)
                // Inzwischen andere Person gewaehlt: nicht deren Liste ueberschreiben.
                if (person == _zustand.value.person) uebernehmen(uebersicht)
            } catch (fehler: ApiFehler) {
                behandleFehler(fehler)
            } finally {
                if (person == _zustand.value.person) _zustand.update { it.copy(laeuft = false) }
            }
        }
    }

    fun abhaken(aufgabe: Aufgabe, erledigt: Boolean) = aendern(
        aufgabe,
        AufgabeAenderung(status = if (erledigt) Aufgabe.STATUS_ERLEDIGT else Aufgabe.STATUS_OFFEN),
    )

    /** "Bleibt": 14 Tage keine Nachfrage mehr. */
    fun bleibt(aufgabe: Aufgabe) = aendern(aufgabe, AufgabeAenderung(noch_aktuell = true))

    /** "Weg damit". */
    fun streichen(aufgabe: Aufgabe) = aendern(aufgabe, AufgabeAenderung(status = Aufgabe.STATUS_GESTRICHEN))

    /** @param bis `YYYY-MM-DD`, `null` entfernt die Frist (geht als `""` raus). */
    fun fristSetzen(aufgabe: Aufgabe, bis: String?) = aendern(aufgabe, AufgabeAenderung(bis = bis ?: ""))

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

    private fun aendern(aufgabe: Aufgabe, aenderung: AufgabeAenderung) {
        if (_zustand.value.aktion != null) return
        _zustand.update { it.copy(aktion = aufgabe.id, fehler = null) }
        viewModelScope.launch {
            try {
                repository.aufgabeAendern(aufgabe.id, aenderung)
            } catch (fehler: ApiFehler) {
                // 404: Text inzwischen geaendert, ID neu - laut Vertrag einfach neu laden.
                if (fehler !is ApiFehler.NichtGefunden) behandleFehler(fehler)
            }
            try {
                val person = _zustand.value.person
                val uebersicht = repository.aufgaben(person)
                if (person == _zustand.value.person) uebernehmen(uebersicht)
            } catch (fehler: ApiFehler) {
                behandleFehler(fehler)
            } finally {
                _zustand.update { it.copy(aktion = null) }
            }
        }
    }

    private fun uebernehmen(uebersicht: AufgabenUebersicht) {
        _zustand.update { z ->
            val ich = z.ich ?: uebersicht.person.takeIf { z.person == null }
            val namen = uebersicht.gruppen.flatMap { (_, liste) -> liste.flatMap { listOfNotNull(it.fuer, it.von) } }
            val personen = (z.personen + namen + uebersicht.person)
                .filter { it.isNotBlank() && !it.equals(ich, ignoreCase = true) }
                .distinctBy { it.lowercase() }
                .sorted()
            z.copy(uebersicht = uebersicht, ich = ich, personen = personen)
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
        fun factory(repository: AssistentRepository) = viewModelFactory {
            initializer { AufgabenViewModel(repository) }
        }
    }
}
