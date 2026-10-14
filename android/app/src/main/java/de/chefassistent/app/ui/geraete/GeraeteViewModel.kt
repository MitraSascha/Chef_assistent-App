package de.chefassistent.app.ui.geraete

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.chefassistent.app.data.ApiFehler
import de.chefassistent.app.data.AssistentRepository
import de.chefassistent.app.data.Geraet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Warum statt einer Liste ein Hinweis erscheint. */
enum class GeraeteHinweis {
    /** Angemeldet mit altem `ca_`-Schluessel - der hat keine Sitzungen. */
    ZUGANGSSCHLUESSEL,

    /** Server vor 1.10.0 kennt `GET /v1/geraete` nicht (404). */
    NICHT_VERFUEGBAR,
}

data class GeraeteZustand(
    val geraete: List<Geraet> = emptyList(),
    val geladen: Boolean = false,
    val laeuft: Boolean = false,
    val hinweis: GeraeteHinweis? = null,
    val fehler: String? = null,
    val abgemeldet: Boolean = false,
)

/**
 * `GET /v1/geraete` und "Abmelden" je Geraet (seit v1.10.0).
 *
 * Das eigene Geraet (`dieses`) wird nicht per `DELETE` abgemeldet, sondern wie
 * der Menuepunkt "Abmelden": `POST /v1/abmelden`, Token weg, Anmeldebildschirm.
 */
class GeraeteViewModel(private val repository: AssistentRepository) : ViewModel() {

    private val _zustand = MutableStateFlow(GeraeteZustand())
    val zustand: StateFlow<GeraeteZustand> = _zustand.asStateFlow()

    init {
        laden()
    }

    fun fehlerVerwerfen() = _zustand.update { it.copy(fehler = null) }

    fun laden() {
        _zustand.update { it.copy(laeuft = true, fehler = null) }
        viewModelScope.launch {
            try {
                val liste = repository.geraete()
                    // Eigenes Geraet zuerst, danach zuletzt aktive oben.
                    .sortedWith(compareByDescending<Geraet> { it.dieses }.thenByDescending { zeitpunkt(it.zuletzt) })
                _zustand.update { it.copy(geraete = liste, geladen = true, hinweis = null) }
            } catch (fehler: ApiFehler) {
                behandleFehler(fehler, beimLaden = true)
            } finally {
                _zustand.update { it.copy(laeuft = false) }
            }
        }
    }

    fun abmelden(geraet: Geraet) {
        if (geraet.dieses) {
            repository.abmeldenUndSitzungBeenden()
            _zustand.update { it.copy(abgemeldet = true) }
            return
        }
        _zustand.update { it.copy(laeuft = true, fehler = null) }
        viewModelScope.launch {
            try {
                repository.geraetAbmelden(geraet.id)
            } catch (fehler: ApiFehler) {
                // 404: schon weg, etwa aus der Weboberflaeche - kein Fehler, nur neu laden.
                if (fehler !is ApiFehler.NichtGefunden) behandleFehler(fehler, beimLaden = false)
            } finally {
                _zustand.update { it.copy(laeuft = false) }
            }
            if (!_zustand.value.abgemeldet) laden()
        }
    }

    private fun behandleFehler(fehler: ApiFehler, beimLaden: Boolean) {
        when {
            // Ein alter Zugangsschluessel hat keine Geraeteliste. Ein 401 hier heisst
            // dann nicht "Schluessel ungueltig" - der Chat funktioniert ja weiter.
            // Abgemeldet wird erst, wenn ein anderer Aufruf 401 meldet.
            fehler is ApiFehler.NichtAngemeldet && repository.mitZugangsschluessel ->
                _zustand.update { it.copy(hinweis = GeraeteHinweis.ZUGANGSSCHLUESSEL, geladen = true) }

            fehler is ApiFehler.NichtAngemeldet -> {
                repository.abmelden()
                _zustand.update { it.copy(abgemeldet = true, fehler = fehler.anzeigetext) }
            }

            // Vor 1.10.0 gibt es den Endpunkt nicht, FastAPI antwortet dann englisch.
            fehler is ApiFehler.NichtGefunden && beimLaden ->
                _zustand.update { it.copy(hinweis = GeraeteHinweis.NICHT_VERFUEGBAR, geladen = true) }

            else -> _zustand.update { it.copy(fehler = fehler.anzeigetext) }
        }
    }

    companion object {
        fun factory(repository: AssistentRepository) = viewModelFactory {
            initializer { GeraeteViewModel(repository) }
        }
    }
}

private val ZEIT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

/**
 * Zeitpunkt aus der Geraeteliste in Geraetezone. Der Server liefert ISO 8601,
 * teils in UTC - ohne Umrechnung stuende im Sommer zwei Stunden zu wenig da.
 * Ohne Offset wird die Zeit unveraendert formatiert, Unlesbares roh gezeigt.
 */
fun ortszeit(iso: String, zone: ZoneId = ZoneId.systemDefault()): String =
    runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(zone).format(ZEIT) }
        .recoverCatching { LocalDateTime.parse(iso.replace(' ', 'T')).format(ZEIT) }
        .getOrDefault(iso)

/**
 * Zum Sortieren: als Zeitpunkt, nicht als Text - mit unterschiedlichen Offsets
 * (`Z` gegen `+02:00`) stimmt die Reihenfolge der Zeichenketten nicht.
 * Unlesbares und Fehlendes kommt ans Ende.
 */
private fun zeitpunkt(wert: String?): java.time.Instant =
    wert?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant() }.getOrNull() }
        ?: java.time.Instant.EPOCH
