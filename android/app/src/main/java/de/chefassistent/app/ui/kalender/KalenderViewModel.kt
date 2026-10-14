package de.chefassistent.app.ui.kalender

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.chefassistent.app.data.ApiFehler
import de.chefassistent.app.data.AssistentRepository
import de.chefassistent.app.data.HeroTerminErgebnis
import de.chefassistent.app.data.KalenderAntwort
import de.chefassistent.app.data.KalenderTermin
import de.chefassistent.app.data.Personen
import de.chefassistent.app.data.TerminKategorie
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class Meldung { EINGETRAGEN, VERSCHOBEN }

data class KalenderZustand(
    val anker: LocalDate = LocalDate.now(),
    val woche: Boolean = true,
    /** Filterschluessel: `ich`, `hero:<id>` oder `alle`. */
    val auswahl: List<String> = listOf(),
    /** `null` = noch nicht geladen oder 503 (HERO-Spiegel fehlt). */
    val personen: Personen? = null,
    val monteureOffen: Boolean = false,
    val antwort: KalenderAntwort? = null,
    val laeuft: Boolean = false,
    val fehler: String? = null,
    /** Kurze Erfolgsmeldung ("In HERO eingetragen"), als Snackbar. */
    val meldung: Meldung? = null,
    val abgemeldet: Boolean = false,
    /** Offenes Formular "＋ HERO-Termin" bzw. "Verschieben", `null` = zu. */
    val formular: HeroFormular? = null,
    /** `GET /v1/kalender/kategorien`, geladen beim ersten Oeffnen des Formulars. */
    val kategorien: List<TerminKategorie> = emptyList(),
) {
    val von: LocalDate get() = zeitraum(anker, woche).first
    val bis: LocalDate get() = zeitraum(anker, woche).second
}

/**
 * Kalender mit HERO-Terminen und eigenen (`/v1/kalender`, seit v1.16.0).
 * Seit v1.18.0 lassen sich HERO-Termine anlegen und verschieben, wenn der
 * Server `hero_schreiben` meldet (`API.md` 5b). Eigene Termine aendert der Chat.
 */
class KalenderViewModel(private val repository: AssistentRepository) : ViewModel() {

    private val _zustand = MutableStateFlow(KalenderZustand(auswahl = repository.einstellungen.kalenderPersonen))
    val zustand: StateFlow<KalenderZustand> = _zustand.asStateFlow()

    private var laden: Job? = null

    init {
        personenLaden()
        laden()
    }

    fun fehlerVerwerfen() = _zustand.update { it.copy(fehler = null) }

    fun meldungVerwerfen() = _zustand.update { it.copy(meldung = null) }

    fun heute() = blaettern(LocalDate.now())

    /** [richtung] -1 zurueck, +1 vor; je nach Ansicht um einen Tag oder eine Woche. */
    fun schritt(richtung: Int) {
        val z = _zustand.value
        blaettern(if (z.woche) z.anker.plusWeeks(richtung.toLong()) else z.anker.plusDays(richtung.toLong()))
    }

    fun ansichtWaehlen(woche: Boolean) {
        if (_zustand.value.woche == woche) return
        _zustand.update { it.copy(woche = woche) }
        laden()
    }

    fun personUmschalten(schluessel: String) {
        val neu = auswahlUmschalten(_zustand.value.auswahl, schluessel)
        repository.einstellungen.kalenderPersonen = neu
        _zustand.update { it.copy(auswahl = neu) }
        laden()
    }

    fun monteureUmschalten() = _zustand.update { it.copy(monteureOffen = !it.monteureOffen) }

    /** Ziehen zum Aktualisieren: auch die Personenliste frisch holen. */
    fun aktualisieren() {
        personenLaden(neu = true)
        laden()
    }

    // --- HERO-Termine anlegen und verschieben (API.md 5b) ----------------------

    /** "＋ HERO-Termin": in der Tagesansicht mit diesem Tag, sonst mit heute. */
    fun heroTerminNeu() {
        val z = _zustand.value
        val tag = if (z.woche) LocalDate.now() else z.anker
        _zustand.update { it.copy(formular = HeroFormular.neu(tag, it.personen)) }
        kategorienLaden()
    }

    fun heroTerminVerschieben(termin: KalenderTermin) {
        val formular = HeroFormular.verschieben(termin) ?: return
        _zustand.update { it.copy(formular = formular) }
    }

    /** Jede Eingabe; die letzte Pruefung verfaellt dabei. Waehrend einer Anfrage gesperrt. */
    fun formularAendern(aenderung: HeroFormular.() -> HeroFormular) = _zustand.update { z ->
        val f = z.formular ?: return@update z
        if (f.laeuft) z else z.copy(formular = f.aendern(aenderung))
    }

    /**
     * Eine laufende Pruefung wird abgebrochen. Ein laufendes Eintragen nicht:
     * das ist womoeglich schon in HERO angekommen, und der Kalender soll danach neu laden.
     */
    fun formularSchliessen() {
        if (_zustand.value.formular?.geprueft == null) formularJob?.cancel()
        _zustand.update { it.copy(formular = null) }
    }

    private var formularJob: Job? = null

    /**
     * Ohne Pruefung: `nur_pruefen=true`. Mit Pruefung: dieselbe Anfrage scharf.
     * Der Knopf ist gesperrt, solange etwas laeuft - doppelt geht nichts raus.
     */
    fun formularAbschicken() {
        val f = _zustand.value.formular ?: return
        if (f.laeuft || f.luecke != null) return
        val nurPruefen = f.geprueft == null
        _zustand.update { it.copy(formular = f.copy(laeuft = true, fehler = null)) }
        formularJob = viewModelScope.launch {
            try {
                val termin = f.verschieben
                val ergebnis = if (termin != null) {
                    repository.heroTerminVerschieben(requireNotNull(termin.heroId), f.anfrageVerschieben(), nurPruefen)
                } else {
                    repository.heroTerminAnlegen(f.anfrageNeu(), nurPruefen)
                }
                if (nurPruefen) {
                    _zustand.update { it.copy(formular = it.formular?.copy(laeuft = false, geprueft = ergebnis)) }
                } else {
                    val meldung = if (ergebnis.status == HeroTerminErgebnis.STATUS_VERSCHOBEN || termin != null) {
                        Meldung.VERSCHOBEN
                    } else {
                        Meldung.EINGETRAGEN
                    }
                    _zustand.update { it.copy(formular = null, meldung = meldung) }
                    laden()
                }
            } catch (fehler: ApiFehler) {
                if (fehler is ApiFehler.NichtAngemeldet) {
                    behandleFehler(fehler)
                } else {
                    // 422 und alles andere: `detail` im Formular, die Eingaben bleiben.
                    _zustand.update { it.copy(formular = it.formular?.copy(laeuft = false, fehler = fehler.anzeigetext)) }
                }
            }
        }
    }

    private fun kategorienLaden() {
        if (_zustand.value.kategorien.isNotEmpty()) return
        viewModelScope.launch {
            try {
                val kategorien = repository.kalenderKategorien()
                _zustand.update { it.copy(kategorien = kategorien) }
            } catch (fehler: ApiFehler) {
                if (fehler is ApiFehler.NichtAngemeldet) {
                    behandleFehler(fehler)
                } else {
                    _zustand.update { it.copy(formular = it.formular?.copy(fehler = fehler.anzeigetext)) }
                }
            }
        }
    }

    private fun blaettern(anker: LocalDate) {
        _zustand.update { it.copy(anker = anker) }
        laden()
    }

    private fun personenLaden(neu: Boolean = false) {
        viewModelScope.launch {
            try {
                val personen = repository.personen(neu)
                _zustand.update { it.copy(personen = personen) }
            } catch (fehler: ApiFehler) {
                // 503: kein HERO-Spiegel - dann gibt es nur "Ich", das ist kein Fehler.
                if (fehler is ApiFehler.Server && fehler.code == 503) return@launch
                behandleFehler(fehler)
            }
        }
    }

    /** Laedt den angezeigten Zeitraum. Ein schneller Wischer bricht die vorige Anfrage ab. */
    fun laden() {
        laden?.cancel()
        val z = _zustand.value
        _zustand.update { it.copy(laeuft = true) }
        laden = viewModelScope.launch {
            try {
                val antwort = repository.kalender(z.von.toString(), z.bis.toString(), z.auswahl)
                _zustand.update { it.copy(antwort = antwort) }
            } catch (fehler: ApiFehler) {
                behandleFehler(fehler)
            } finally {
                if (isActive) _zustand.update { it.copy(laeuft = false) }
            }
        }
    }

    private fun behandleFehler(fehler: ApiFehler) {
        if (fehler is ApiFehler.NichtAngemeldet) {
            repository.abmelden()
            _zustand.update { it.copy(abgemeldet = true) }
        } else {
            _zustand.update { it.copy(fehler = fehler.anzeigetext) }
        }
    }

    companion object {
        fun factory(repository: AssistentRepository) = viewModelFactory {
            initializer { KalenderViewModel(repository) }
        }
    }
}
