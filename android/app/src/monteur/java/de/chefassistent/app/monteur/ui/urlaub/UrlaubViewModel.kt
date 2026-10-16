package de.chefassistent.app.monteur.ui.urlaub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.chefassistent.app.R
import de.chefassistent.app.data.ApiFehler
import de.chefassistent.app.monteur.MonteurApp
import de.chefassistent.app.monteur.data.AbwVorschlag
import de.chefassistent.app.monteur.data.Abwesenheit
import de.chefassistent.app.monteur.data.AbwesenheitNeu
import de.chefassistent.app.monteur.data.Abwesenheiten
import de.chefassistent.app.monteur.data.MonteurRepository
import de.chefassistent.app.monteur.data.MonteurVersand
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

/** Eine kurze Rueckmeldung unten im Reiter. [fehler] = rot hinterlegt. */
data class UrlaubMeldung(val text: String, val fehler: Boolean = false)

/**
 * Was die Spracheingabe ergeben hat. Genau eins von [vorschlag], [nurZeiten],
 * [nichts] ist gesetzt; [rueckfrage] kann zu jedem dazukommen.
 */
data class VerstehenErgebnis(
    val gesagt: String,
    val vorschlag: AbwVorschlag? = null,
    val rueckfrage: String? = null,
    /** Nur Arbeitszeiten erkannt: gehoert in den Reiter Zeiten. */
    val nurZeiten: Boolean = false,
    val nichts: Boolean = false,
    /** Laeuft „Beantragen“ direkt aus der Karte. */
    val sendet: Boolean = false,
)

data class UrlaubZustand(
    val jahr: Int,
    val daten: Abwesenheiten? = null,
    val laedt: Boolean = false,
    /** Antraege, die in dieser Sitzung in den Korb gingen, als „Wartet auf Netz“. */
    val wartend: List<AbwesenheitNeu> = emptyList(),
    val formular: AntragFormular? = null,
    val versteht: Boolean = false,
    val ergebnis: VerstehenErgebnis? = null,
    /** Aufnahme, die wegen Funkloch nicht ankam: bleibt bis „Nochmal senden“ oder „Schliessen“. */
    val offeneAufnahme: File? = null,
    val offeneAufnahmeFehler: String? = null,
    val zurueckziehen: Abwesenheit? = null,
)

/**
 * Reiter „Urlaub“ (Auftrag 1.22, Abschnitt 5; API.md 5c).
 *
 * Antraege gehen **direkt nach HERO**. Deshalb: jede Anfrage traegt eine
 * `anfrage_id` (doppelt senden legt nichts doppelt an), und ein vorübergehender
 * Fehler landet im [de.chefassistent.app.monteur.data.MonteurKorb] statt verloren
 * zu gehen. Ein 401 meldet nur lokal ab, die Activity zeigt dann die Einrichtung.
 */
class UrlaubViewModel(val app: MonteurApp) : ViewModel() {

    private val _zustand = MutableStateFlow(UrlaubZustand(jahr = LocalDate.now().year))
    val zustand: StateFlow<UrlaubZustand> = _zustand.asStateFlow()

    private val _abgemeldet = MutableStateFlow(false)
    val abgemeldet: StateFlow<Boolean> = _abgemeldet.asStateFlow()

    private val _meldungen = Channel<UrlaubMeldung>(Channel.BUFFERED)
    val meldungen = _meldungen.receiveAsFlow()

    private var ladeJob: Job? = null

    init {
        laden()
        // Wird der Korb leer, sind die wartenden Antraege beim Server: Liste neu holen.
        viewModelScope.launch {
            var vorher = app.korb.anzahl.value
            app.korb.anzahl.collect { jetzt ->
                if (jetzt < vorher) {
                    if (jetzt == 0) _zustand.update { it.copy(wartend = emptyList()) }
                    laden()
                }
                vorher = jetzt
            }
        }
    }

    // --- Laden -------------------------------------------------------------------

    fun laden(jahr: Int = _zustand.value.jahr) {
        ladeJob?.cancel()
        _zustand.update { it.copy(jahr = jahr, laedt = true, daten = it.daten?.takeIf { d -> d.jahr == jahr }) }
        ladeJob = viewModelScope.launch {
            try {
                val daten = app.repository.abwesenheiten(jahr)
                _zustand.update { it.copy(daten = daten, laedt = false) }
            } catch (fehler: ApiFehler) {
                _zustand.update { it.copy(laedt = false) }
                if (!abmeldenBei(fehler)) melden(fehler.anzeigetext, fehler = true)
            }
        }
    }

    fun jahrWechseln(schritt: Int) = laden(_zustand.value.jahr + schritt)

    // --- Formular ------------------------------------------------------------------

    fun urlaubBeantragen() {
        _zustand.update { it.copy(formular = AntragFormular.urlaub(MonteurRepository.neueAnfrageId())) }
    }

    fun krankMelden() {
        _zustand.update { it.copy(formular = AntragFormular.krank(MonteurRepository.neueAnfrageId(), LocalDate.now())) }
    }

    /** Jede Aenderung loescht die alte Fehlermeldung; waehrend des Sendens bleibt alles stehen. */
    fun formularAendern(aenderung: (AntragFormular) -> AntragFormular) {
        _zustand.update { z ->
            val f = z.formular ?: return@update z
            if (f.sendet) z else z.copy(formular = aenderung(f).copy(fehler = null))
        }
    }

    /** Schliessen waehrend des Sendens bricht nicht ab: der Antrag kann schon in HERO sein. */
    fun formularSchliessen() {
        _zustand.update { it.copy(formular = null) }
    }

    fun formularSenden() {
        val f = _zustand.value.formular ?: return
        if (f.sendet || !f.gueltig) return
        _zustand.update { it.copy(formular = f.copy(sendet = true, fehler = null)) }
        senden(f.zuAntrag(), f)
    }

    // --- Verstehen -----------------------------------------------------------------

    /**
     * Vom schwebenden Mikrofon oder aus „Tippen statt sprechen“. Gebucht wird hier
     * nichts, es entsteht nur ein Vorschlag. Die Aufnahme wird nach dem Senden
     * geloescht; nur bei Funkloch bleibt sie fuer „Nochmal senden“ liegen.
     */
    fun verstehen(aufnahme: File?, text: String?) {
        if (_zustand.value.versteht) {
            aufnahme?.delete()
            return
        }
        if (aufnahme == null && text.isNullOrBlank()) return
        // Eine neue Aufnahme ersetzt eine liegengebliebene.
        _zustand.value.offeneAufnahme?.takeIf { it != aufnahme }?.delete()
        _zustand.update { it.copy(versteht = true, ergebnis = null, offeneAufnahme = null, offeneAufnahmeFehler = null) }
        viewModelScope.launch {
            var behalten = false
            try {
                val v = app.repository.verstehen(aufnahme = aufnahme, text = text?.trim())
                val ergebnis = when {
                    v.abwesenheit != null -> VerstehenErgebnis(v.text, vorschlag = v.abwesenheit, rueckfrage = v.rueckfrage)
                    v.zeiten.isNotEmpty() -> VerstehenErgebnis(v.text, nurZeiten = true, rueckfrage = v.rueckfrage)
                    !v.rueckfrage.isNullOrBlank() -> VerstehenErgebnis(v.text, rueckfrage = v.rueckfrage)
                    else -> VerstehenErgebnis(v.text, nichts = true)
                }
                _zustand.update { it.copy(ergebnis = ergebnis) }
            } catch (fehler: ApiFehler) {
                when {
                    abmeldenBei(fehler) -> Unit
                    aufnahme != null && fehler.istVoruebergehend -> {
                        behalten = true
                        _zustand.update { it.copy(offeneAufnahme = aufnahme, offeneAufnahmeFehler = fehler.anzeigetext) }
                    }
                    else -> melden(fehler.anzeigetext, fehler = true)
                }
            } finally {
                if (!behalten) aufnahme?.delete()
                _zustand.update { it.copy(versteht = false) }
            }
        }
    }

    fun aufnahmeNochmal() {
        val datei = _zustand.value.offeneAufnahme ?: return
        _zustand.update { it.copy(offeneAufnahme = null, offeneAufnahmeFehler = null) }
        verstehen(datei, null)
    }

    fun aufnahmeVerwerfen() {
        _zustand.value.offeneAufnahme?.delete()
        _zustand.update { it.copy(offeneAufnahme = null, offeneAufnahmeFehler = null) }
    }

    fun ergebnisVerwerfen() {
        _zustand.update { it.copy(ergebnis = null) }
    }

    /** „Beantragen“ direkt aus der Vorschlagskarte, mit eigener neuer `anfrage_id`. */
    fun vorschlagBeantragen() {
        val e = _zustand.value.ergebnis ?: return
        val v = e.vorschlag ?: return
        if (e.sendet) return
        val id = MonteurRepository.neueAnfrageId()
        _zustand.update { it.copy(ergebnis = e.copy(sendet = true)) }
        senden(vorschlagZuAntrag(v, id), AntragFormular.ausVorschlag(v, id), ausVorschlag = true)
    }

    /** „Ändern“: Formular vorbelegt oeffnen, die Karte verschwindet. */
    fun vorschlagAendern() {
        val v = _zustand.value.ergebnis?.vorschlag ?: return
        _zustand.update {
            it.copy(ergebnis = null, formular = AntragFormular.ausVorschlag(v, MonteurRepository.neueAnfrageId()))
        }
    }

    // --- Zurueckziehen ---------------------------------------------------------------

    fun zurueckziehenFragen(eintrag: Abwesenheit) {
        if (istZurueckziehbar(eintrag.status)) _zustand.update { it.copy(zurueckziehen = eintrag) }
    }

    fun zurueckziehenAbbrechen() {
        _zustand.update { it.copy(zurueckziehen = null) }
    }

    fun zurueckziehen() {
        val eintrag = _zustand.value.zurueckziehen ?: return
        val jahr = jahrZumZurueckziehen(eintrag.von, _zustand.value.jahr)
        _zustand.update { it.copy(zurueckziehen = null) }
        viewModelScope.launch {
            try {
                app.repository.abwesenheitZurueck(eintrag.id, jahr)
                melden(app.getString(R.string.u_zurueckgezogen))
            } catch (fehler: ApiFehler) {
                when (fehler) {
                    // Schon weg: still neu laden.
                    is ApiFehler.NichtGefunden -> Unit
                    is ApiFehler.NichtAngemeldet -> {
                        abmeldenBei(fehler)
                        return@launch
                    }
                    else -> melden(fehler.anzeigetext, fehler = true)
                }
            }
            laden()
        }
    }

    // --- Senden ------------------------------------------------------------------------

    /**
     * Gemeinsamer Weg fuer Formular und Vorschlag. [formular] ist, was bei einem
     * Eingabefehler (422 u. a.) mit `detail` stehen bleibt bzw. geoeffnet wird.
     *
     * - 409: `detail` zeigen und neu laden (der Stand hat sich geaendert).
     * - voruebergehend (ohne 401/409): in den Korb, „Wartet auf Netz“.
     */
    private fun senden(antrag: AbwesenheitNeu, formular: AntragFormular, ausVorschlag: Boolean = false) {
        viewModelScope.launch {
            try {
                app.repository.abwesenheitNeu(antrag)
                _zustand.update { it.copy(formular = null, ergebnis = if (ausVorschlag) null else it.ergebnis) }
                melden(app.getString(if (istKrankArt(antrag.art)) R.string.u_krank_meldung else R.string.u_beantragt_meldung))
                laden()
            } catch (fehler: ApiFehler) {
                when {
                    abmeldenBei(fehler) -> Unit
                    fehler is ApiFehler.Beschaeftigt -> {
                        fehlerZeigen(formular, fehler.anzeigetext, ausVorschlag)
                        laden()
                    }
                    fehler.istVoruebergehend -> einreihen(antrag, ausVorschlag)
                    else -> fehlerZeigen(formular, fehler.anzeigetext, ausVorschlag)
                }
            }
        }
    }

    /** Fehler im Formular, die Eingaben bleiben. Aus dem Vorschlag heraus oeffnet es sich vorbelegt. */
    private fun fehlerZeigen(formular: AntragFormular, text: String, ausVorschlag: Boolean) {
        _zustand.update {
            it.copy(
                formular = formular.copy(sendet = false, fehler = text),
                ergebnis = if (ausVorschlag) null else it.ergebnis,
            )
        }
    }

    private fun einreihen(antrag: AbwesenheitNeu, ausVorschlag: Boolean) {
        try {
            app.korb.abwesenheitEinreihen(antrag)
        } catch (fehler: Exception) {
            // Speicher voll o. ae.: das Formular offen lassen, damit nichts verloren geht.
            val f = _zustand.value.formular
            if (f != null) _zustand.update { it.copy(formular = f.copy(sendet = false, fehler = fehler.message)) }
            else _zustand.update { it.copy(ergebnis = it.ergebnis?.copy(sendet = false)) }
            return
        }
        MonteurVersand.anstossen(app)
        _zustand.update {
            it.copy(
                formular = null,
                ergebnis = if (ausVorschlag) null else it.ergebnis,
                wartend = it.wartend + antrag,
            )
        }
        melden(app.getString(R.string.u_wartet_meldung))
    }

    // --- Hilfen -----------------------------------------------------------------------

    /** 401 ueberall: nur lokal abmelden, das Token kennt der Server nicht mehr. */
    private fun abmeldenBei(fehler: ApiFehler): Boolean {
        if (fehler !is ApiFehler.NichtAngemeldet) return false
        app.repository.lokalAbmelden()
        _abgemeldet.value = true
        return true
    }

    private fun melden(text: String, fehler: Boolean = false) {
        _meldungen.trySend(UrlaubMeldung(text, fehler))
    }

    override fun onCleared() {
        _zustand.value.offeneAufnahme?.delete()
    }

    companion object {
        fun factory(app: MonteurApp) = viewModelFactory { initializer { UrlaubViewModel(app) } }
    }
}
