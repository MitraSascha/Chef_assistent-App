package de.chefassistent.app.ui.vorlieben

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.chefassistent.app.data.ApiFehler
import de.chefassistent.app.data.AssistentRepository
import de.chefassistent.app.data.VORLIEBE_FIRMA
import de.chefassistent.app.data.VORLIEBE_MAX_ZEICHEN
import de.chefassistent.app.data.Vorliebe
import de.chefassistent.app.data.VorliebenListe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VorliebenZustand(
    /** `null` = noch nicht geladen. */
    val liste: VorliebenListe? = null,
    val laeuft: Boolean = false,
    /** Eingabefeld unten. */
    val neu: String = "",
    /** Schalter "fuer die ganze Firma", nur bei `firma_aendern`. */
    val fuerFirma: Boolean = false,
    /** Server vor 1.32.0 kennt `/v1/vorlieben` nicht (404 beim Laden). */
    val nichtVerfuegbar: Boolean = false,
    /** Fehler (`detail`) oder Hinweis des Servers, als Snackbar. */
    val meldung: String? = null,
    val abgemeldet: Boolean = false,
)

/**
 * Seite "Vorlieben" (Auftrag 1.32 B, API.md 4f): was sich der Assistent dauerhaft
 * merkt. Entstehen tun Vorlieben meist im Chat ("merk dir …"), hier werden sie
 * angesehen und gepflegt. Nach jeder Aenderung wird die Liste neu geladen - die
 * Antworten liefern nur die eine Vorliebe, und Doppeltes fasst der Server zusammen.
 */
class VorliebenViewModel(private val repository: AssistentRepository) : ViewModel() {

    private val _zustand = MutableStateFlow(VorliebenZustand())
    val zustand: StateFlow<VorliebenZustand> = _zustand.asStateFlow()

    init {
        laden()
    }

    fun meldungVerwerfen() = _zustand.update { it.copy(meldung = null) }

    fun neuGeaendert(text: String) = _zustand.update { it.copy(neu = text.take(VORLIEBE_MAX_ZEICHEN)) }

    fun fuerFirmaSetzen(ja: Boolean) = _zustand.update { it.copy(fuerFirma = ja) }

    /** Darf diese Vorliebe geaendert und geloescht werden? Eigene immer, firmenweite nur mit `firma_aendern`. */
    fun aenderbar(vorliebe: Vorliebe): Boolean =
        vorliebe.fuer != VORLIEBE_FIRMA || _zustand.value.liste?.firma_aendern == true

    fun laden() {
        _zustand.update { it.copy(laeuft = true) }
        viewModelScope.launch {
            try {
                val liste = repository.vorlieben()
                _zustand.update {
                    it.copy(
                        liste = liste,
                        nichtVerfuegbar = false,
                        // Recht weg (z. B. andere Person angemeldet): Schalter zuruecksetzen.
                        fuerFirma = it.fuerFirma && liste.firma_aendern,
                    )
                }
            } catch (fehler: ApiFehler) {
                if (fehler is ApiFehler.NichtGefunden) {
                    _zustand.update { it.copy(nichtVerfuegbar = true) }
                } else {
                    behandleFehler(fehler)
                }
            } finally {
                _zustand.update { it.copy(laeuft = false) }
            }
        }
    }

    /** "Merken": `POST /v1/vorlieben`. Das Feld wird erst bei Erfolg geleert. */
    fun merken() {
        val z = _zustand.value
        val text = z.neu.trim()
        if (text.isEmpty() || z.laeuft) return
        val firma = z.fuerFirma && z.liste?.firma_aendern == true
        ausfuehren {
            val antwort = repository.vorliebeAnlegen(text, firma)
            _zustand.update { it.copy(neu = "", meldung = antwort.hinweis?.takeIf { h -> h.isNotBlank() }) }
        }
    }

    /** Stift: `PATCH /v1/vorlieben/{id}`. */
    fun aendern(vorliebe: Vorliebe, text: String) {
        val sauber = text.trim()
        if (sauber.isEmpty() || sauber == vorliebe.text) return
        ausfuehren { repository.vorliebeAendern(vorliebe.id, sauber) }
    }

    /** Muelleimer (nach Rueckfrage): `DELETE /v1/vorlieben/{id}`. 404 heisst: schon weg. */
    fun loeschen(vorliebe: Vorliebe) {
        ausfuehren {
            try {
                repository.vorliebeLoeschen(vorliebe.id)
            } catch (_: ApiFehler.NichtGefunden) {
                // Schon vergessen, etwa ueber die Weboberflaeche - nur neu laden.
            }
        }
    }

    private fun ausfuehren(block: suspend () -> Unit) {
        if (_zustand.value.laeuft) return
        _zustand.update { it.copy(laeuft = true) }
        viewModelScope.launch {
            var erfolg = false
            try {
                block()
                erfolg = true
            } catch (fehler: ApiFehler) {
                behandleFehler(fehler)
            } finally {
                _zustand.update { it.copy(laeuft = false) }
            }
            if (erfolg) laden()
        }
    }

    /** `detail` unveraendert anzeigen (403 nur Geschaeftsfuehrung, 422 zu lang, 400 zu viele). */
    private fun behandleFehler(fehler: ApiFehler) {
        if (fehler is ApiFehler.NichtAngemeldet) {
            repository.abmelden()
            _zustand.update { it.copy(abgemeldet = true, meldung = fehler.anzeigetext) }
        } else {
            _zustand.update { it.copy(meldung = fehler.anzeigetext) }
        }
    }

    companion object {
        fun factory(repository: AssistentRepository) = viewModelFactory {
            initializer { VorliebenViewModel(repository) }
        }
    }
}
