package de.chefassistent.app.ui.anmeldung

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.chefassistent.app.data.Anmeldung
import de.chefassistent.app.data.ApiFehler
import de.chefassistent.app.data.AssistentRepository
import de.chefassistent.app.data.Einstellungen
import de.chefassistent.app.data.Netzwerk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Seit v1.10.0 meldet sich die App mit Name, Passwort und Code an. Der alte Weg
 * mit Zugangsschluessel `ca_…` bleibt als Uebergang erhalten - er gilt nur noch
 * ueber Tailscale, haelt aber bestehende Installationen und Tests am Laufen.
 */
enum class AnmeldeArt { PASSWORT, SCHLUESSEL }

data class AnmeldungZustand(
    val art: AnmeldeArt = AnmeldeArt.PASSWORT,
    val url: String = "",
    val name: String = "",
    /** Lebt nur hier im Speicher, solange der Bildschirm offen ist - nie auf der Platte. */
    val passwort: String = "",
    val code: String = "",
    val token: String = "",
    val laeuft: Boolean = false,
    val fehler: String? = null,
) {
    val kannVerbinden: Boolean
        get() = !laeuft && url.isNotBlank() && when (art) {
            AnmeldeArt.PASSWORT -> name.isNotBlank() && passwort.isNotEmpty() && code.length == Anmeldung.CODE_LAENGE
            AnmeldeArt.SCHLUESSEL -> token.isNotBlank()
        }

    /** Welche der beiden bekannten Adressen gerade eingetragen ist, fuer die Chips. */
    val istInternet: Boolean get() = Netzwerk.normalisiereUrl(url) == Einstellungen.STANDARD_BASIS_URL
    val istTailscale: Boolean get() = Netzwerk.normalisiereUrl(url) == Einstellungen.TAILSCALE_BASIS_URL
}

class AnmeldungViewModel(private val repository: AssistentRepository) : ViewModel() {

    private val _zustand = MutableStateFlow(
        AnmeldungZustand(
            url = repository.einstellungen.basisUrl,
            name = repository.einstellungen.name.orEmpty(),
        )
    )
    val zustand: StateFlow<AnmeldungZustand> = _zustand.asStateFlow()

    fun urlGeaendert(wert: String) = _zustand.update { it.copy(url = wert, fehler = null) }

    fun nameGeaendert(wert: String) = _zustand.update { it.copy(name = wert, fehler = null) }

    fun passwortGeaendert(wert: String) = _zustand.update { it.copy(passwort = wert, fehler = null) }

    /** Nur Ziffern, hoechstens sechs - Einfuegen aus der Zwischenablage mit Leerzeichen klappt so auch. */
    fun codeGeaendert(wert: String) = _zustand.update {
        it.copy(code = wert.filter(Char::isDigit).take(Anmeldung.CODE_LAENGE), fehler = null)
    }

    fun tokenGeaendert(wert: String) = _zustand.update { it.copy(token = wert, fehler = null) }

    /**
     * Wechselt zwischen Passwort und Zugangsschluessel. Beim Wechsel zum Schluessel
     * wird die Tailscale-Adresse vorgeschlagen, wenn noch die Internet-Adresse
     * eingetragen ist - ueber die gilt ein `ca_`-Schluessel laut `API.md` nicht.
     * Eine selbst eingetragene Adresse bleibt unangetastet.
     */
    fun artWechseln(art: AnmeldeArt) = _zustand.update {
        val url = if (art == AnmeldeArt.SCHLUESSEL && it.istInternet) Einstellungen.TAILSCALE_BASIS_URL else it.url
        it.copy(art = art, url = url, fehler = null)
    }

    /**
     * Meldet an. Erst wenn `GET /v1/ich` danach klappt, gilt der Nutzer als
     * angemeldet - so landet kein unbrauchbares Token im Speicher.
     *
     * Ein 401 hier ist **kein** Anlass zum globalen Abmelden: es heisst nur
     * "Name, Passwort oder Code falsch". Dann wird der Code geleert - jeder Code
     * gilt ohnehin nur einmal.
     */
    fun verbinden(beiErfolg: () -> Unit) {
        val jetzt = _zustand.value
        if (!jetzt.kannVerbinden) return

        _zustand.update { it.copy(laeuft = true, fehler = null) }
        viewModelScope.launch {
            try {
                when (jetzt.art) {
                    AnmeldeArt.PASSWORT ->
                        repository.anmeldenMitPasswort(jetzt.url, jetzt.name, jetzt.passwort, jetzt.code)
                    AnmeldeArt.SCHLUESSEL ->
                        repository.anmelden(jetzt.url, jetzt.token)
                }
                _zustand.update { it.copy(laeuft = false, passwort = "", code = "", token = "") }
                beiErfolg()
            } catch (fehler: ApiFehler) {
                _zustand.update {
                    it.copy(
                        laeuft = false,
                        fehler = fehler.anzeigetext,
                        code = if (fehler is ApiFehler.NichtAngemeldet) "" else it.code,
                    )
                }
            }
        }
    }

    companion object {
        fun factory(repository: AssistentRepository) = viewModelFactory {
            initializer { AnmeldungViewModel(repository) }
        }
    }
}
