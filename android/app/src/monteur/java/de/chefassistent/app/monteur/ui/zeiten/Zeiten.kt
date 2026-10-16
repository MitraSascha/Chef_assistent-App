package de.chefassistent.app.monteur.ui.zeiten

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.chefassistent.app.R
import de.chefassistent.app.data.ApiFehler
import de.chefassistent.app.monteur.MonteurApp
import de.chefassistent.app.monteur.data.MonteurRepository
import de.chefassistent.app.monteur.data.MonteurVersand
import de.chefassistent.app.monteur.data.StempelAktion
import de.chefassistent.app.monteur.data.Stundenkonto
import de.chefassistent.app.monteur.data.TagTermin
import de.chefassistent.app.monteur.data.Tageszeiten
import de.chefassistent.app.monteur.data.Verstanden
import de.chefassistent.app.monteur.data.ZeitAbschnitt
import de.chefassistent.app.monteur.data.ZeitAenderung
import de.chefassistent.app.monteur.data.ZeitNeu
import de.chefassistent.app.monteur.data.Zeiteintrag
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** Ein Vorschlag aus `POST /v1/monteur/verstehen`, je Zeitabschnitt mit fester `anfrage_id`. */
data class Vorschlag(
    val verstanden: Verstanden,
    /** `YYYY-MM-DD`, an dem die `HH:MM`-Zeiten gelten. */
    val datum: String,
    val offen: List<Pair<ZeitAbschnitt, String>>,
)

/** Eine Zeit, die in diesem Lauf in den Korb ging - grau in der Liste, bis der Korb leer ist. */
data class WartendeZeit(val zeit: ZeitNeu)

data class ZeitenZustand(
    val datum: LocalDate = LocalDate.now(),
    val tag: Tageszeiten? = null,
    val laedt: Boolean = false,
    /** Ladefehler, solange noch nichts angezeigt wird. */
    val ladeFehler: String? = null,
    /** Termine des angezeigten Tages (Projektnummern, „letzter Termin vorbei“). */
    val termine: List<TagTermin> = emptyList(),
    val konto: Stundenkonto? = null,
    /** Stempeln, Speichern, Einreichen oder Übernehmen laeuft - Knoepfe sperren. */
    val sendet: Boolean = false,
    val versteht: Boolean = false,
    val vorschlag: Vorschlag? = null,
    val verstehenFehler: String? = null,
    /** Nach einem voruebergehenden Fehler liegt die Eingabe noch da: „Nochmal versuchen“. */
    val verstehenWiederholbar: Boolean = false,
    val wartend: List<WartendeZeit> = emptyList(),
) {
    val eingereicht: Boolean get() = tag?.eingereicht == true
    val istHeute: Boolean get() = datum == LocalDate.now()
}

/** Kurze Rueckmeldung; [fehler] = im Fehlerton. */
data class ZeitenMeldung(val text: String, val fehler: Boolean = false)

/**
 * Reiter „Zeiten“ (Auftrag 1.22, Abschnitt 4, API.md 5c). Die Zeiten liegen in HERO,
 * die App liest und bucht nur. Was beim Anlegen am Netz scheitert, geht in den
 * [de.chefassistent.app.monteur.data.MonteurKorb] - **nichts geht verloren**.
 * Aendern, Loeschen, Stempeln und Einreichen brauchen dagegen Netz: dort weiss
 * nur der Server, ob es noch geht (Status `neu`, Tag nicht eingereicht).
 */
class ZeitenViewModel(val app: MonteurApp) : ViewModel() {
    private val repo get() = app.repository

    private val _zustand = MutableStateFlow(ZeitenZustand())
    val zustand: StateFlow<ZeitenZustand> = _zustand.asStateFlow()

    private val _abgemeldet = MutableStateFlow(false)
    val abgemeldet: StateFlow<Boolean> = _abgemeldet.asStateFlow()

    private val meldungen = Channel<ZeitenMeldung>(Channel.BUFFERED)
    val meldung: Flow<ZeitenMeldung> = meldungen.receiveAsFlow()

    private var ladeJob: Job? = null
    private var verstehenJob: Job? = null

    /** Eingabe fuer „Nochmal versuchen“ nach einem Funkloch beim Verstehen. */
    private var wiederholung: Pair<File?, String?>? = null

    init {
        laden()
        // Ist der Korb leer, sind die grauen „wartet“-Zeilen beim Server angekommen.
        viewModelScope.launch {
            app.korb.anzahl.collect { anzahl ->
                if (anzahl == 0 && _zustand.value.wartend.isNotEmpty()) {
                    _zustand.update { it.copy(wartend = emptyList()) }
                    laden()
                }
            }
        }
    }

    // --- Laden und Blaettern -------------------------------------------------------

    fun laden() {
        val datum = _zustand.value.datum
        ladeJob?.cancel()
        _zustand.update { it.copy(laedt = true) }
        ladeJob = viewModelScope.launch {
            // Termine und Konto sind Beiwerk: ihr Fehlen haelt die Zeiten nicht auf.
            val termine = async { nebenbei { termineAm(repo.tage(datum.toString(), datum.toString()), datum) } }
            val konto = async {
                val (von, bis) = kontoZeitraum(datum, LocalDate.now())
                nebenbei { repo.stundenkonto(von.toString(), bis.toString()) }
            }
            try {
                val tag = repo.zeiten(datum.toString())
                _zustand.update { it.copy(tag = tag, ladeFehler = null) }
            } catch (fehler: ApiFehler) {
                if (fehler is ApiFehler.NichtAngemeldet) abmelden()
                else _zustand.update { it.copy(ladeFehler = fehler.anzeigetext) }
            }
            val t = termine.await()
            val k = konto.await()
            _zustand.update { z -> z.copy(termine = t ?: z.termine, konto = k ?: z.konto, laedt = false) }
        }
    }

    fun blaettern(tage: Long) = datumSetzen(_zustand.value.datum.plusDays(tage))

    fun zuHeute() = datumSetzen(LocalDate.now())

    private fun datumSetzen(datum: LocalDate) {
        if (datum == _zustand.value.datum) return laden()
        val gleicherMonat = datum.withDayOfMonth(1) == _zustand.value.datum.withDayOfMonth(1)
        _zustand.update {
            it.copy(
                datum = datum, tag = null, ladeFehler = null, termine = emptyList(),
                konto = if (gleicherMonat) it.konto else null,
            )
        }
        laden()
    }

    // --- Stempeluhr ------------------------------------------------------------------

    /** `start` | `wechsel` | `stopp`. Die Uhrzeit nimmt der Server. */
    fun stempeln(aktion: String, art: String, projektNr: String?) = senden {
        val tag = repo.stempeln(StempelAktion(aktion = aktion, art = art, projekt_nr = projektNr?.takeIf { it.isNotBlank() }))
        if (_zustand.value.datum.toString() == tag.datum) _zustand.update { it.copy(tag = tag) }
        if (aktion != AKTION_START) laden()
    }

    // --- Anlegen, Aendern, Loeschen ---------------------------------------------------

    /**
     * Neuer Abschnitt mit eigener `anfrage_id`. Ohne Netz (oder bei 401) geht er
     * in den Korb, der Server legt ihn beim Nachsenden genau einmal an.
     */
    fun anlegen(von: LocalTime, bis: LocalTime, art: String, projektNr: String, kommentar: String, fertig: () -> Unit) {
        val (beginn, ende) = zeitraum(_zustand.value.datum, von, bis) ?: return
        val zeit = ZeitNeu(
            beginn = beginn,
            ende = ende,
            art = art,
            projekt_nr = projektNr.trim().ifBlank { null },
            kommentar = kommentar.trim(),
            anfrage_id = MonteurRepository.neueAnfrageId(),
        )
        senden(nachKorb = { listOf(zeit) to fertig }) {
            repo.zeitNeu(zeit)
            fertig()
            laden()
        }
    }

    /** `PATCH` nur mit dem, was sich geaendert hat; nur Status `neu` (sonst 409). */
    fun aendern(
        eintrag: Zeiteintrag,
        von: LocalTime,
        bis: LocalTime,
        art: String,
        projektNr: String,
        kommentar: String,
        fertig: () -> Unit,
    ) {
        val tag = zeitLesen(eintrag.beginn)?.toLocalDate() ?: _zustand.value.datum
        val (beginn, ende) = zeitraum(tag, von, bis) ?: return
        val altBeginn = zeitLesen(eintrag.beginn)?.let(::ortszeit)
        val altEnde = zeitLesen(eintrag.ende)?.let(::ortszeit)
        val nr = projektNr.trim()
        val aenderung = ZeitAenderung(
            beginn = beginn.takeIf { it != altBeginn },
            ende = ende.takeIf { it != altEnde },
            art = art.takeIf { it != eintrag.art },
            // Leeren laesst der Vertrag nicht eindeutig zu, nur eine neue Nummer geht raus.
            projekt_nr = nr.takeIf { it.isNotEmpty() && it != eintrag.projekt_nr.orEmpty() },
            kommentar = kommentar.trim().takeIf { it != eintrag.kommentar },
        )
        if (aenderung == ZeitAenderung()) return fertig()
        senden {
            repo.zeitAendern(eintrag.id, aenderung)
            fertig()
            laden()
        }
    }

    fun loeschen(eintrag: Zeiteintrag) = senden {
        repo.zeitLoeschen(eintrag.id)
        meldung(R.string.z_geloescht)
        laden()
    }

    // --- Tag einreichen -----------------------------------------------------------------

    fun einreichen(fertig: () -> Unit) {
        val datum = _zustand.value.datum
        senden {
            val tag = repo.tagEinreichen(datum.toString())
            if (_zustand.value.datum == datum) _zustand.update { it.copy(tag = tag) }
            fertig()
            meldung(R.string.z_eingereicht_meldung)
            laden()
        }
    }

    // --- Verstehen ------------------------------------------------------------------------

    /**
     * Vom schwebenden Mikrofon oder aus „Tippen statt sprechen“: Aufnahme bzw. Text
     * -> `POST /v1/monteur/verstehen` -> Vorschlag zum Bestaetigen. Gebucht wird erst
     * mit [vorschlagUebernehmen]. Die Aufnahme wird nach dem Senden geloescht; nur
     * nach einem Funkloch bleibt sie fuer „Nochmal versuchen“ liegen.
     */
    fun verstehen(aufnahme: File?, text: String?) {
        if (aufnahme == null && text.isNullOrBlank()) return
        verstehenJob?.cancel()
        wiederholung?.first?.takeIf { it != aufnahme }?.delete()
        wiederholung = null
        val datum = _zustand.value.datum.toString()
        _zustand.update {
            it.copy(versteht = true, verstehenFehler = null, verstehenWiederholbar = false, vorschlag = null)
        }
        verstehenJob = viewModelScope.launch {
            try {
                val v = repo.verstehen(aufnahme, text, datum)
                aufnahme?.delete()
                val gilt = v.datum.ifBlank { datum }
                _zustand.update {
                    it.copy(
                        versteht = false,
                        vorschlag = Vorschlag(v, gilt, v.zeiten.map { a -> a to MonteurRepository.neueAnfrageId() }),
                    )
                }
            } catch (fehler: ApiFehler) {
                val nochmal = fehler.istVoruebergehend && fehler !is ApiFehler.NichtAngemeldet
                if (nochmal) wiederholung = aufnahme to text else aufnahme?.delete()
                _zustand.update {
                    it.copy(versteht = false, verstehenFehler = fehler.anzeigetext, verstehenWiederholbar = nochmal)
                }
                if (fehler is ApiFehler.NichtAngemeldet) abmelden()
            }
        }
    }

    fun verstehenWiederholen() {
        val (aufnahme, text) = wiederholung ?: return
        verstehen(aufnahme?.takeIf { it.exists() }, text)
    }

    fun verstehenSchliessen() {
        verstehenJob?.cancel()
        wiederholung?.first?.delete()
        wiederholung = null
        _zustand.update { it.copy(versteht = false, verstehenFehler = null, verstehenWiederholbar = false, vorschlag = null) }
    }

    /**
     * Bucht jeden Vorschlag einzeln mit seiner festen `anfrage_id`. Was gebucht ist
     * oder im Korb liegt, verschwindet aus der Karte; nach einem endgueltigen Fehler
     * bleiben die uebrigen stehen und koennen erneut uebernommen werden.
     */
    fun vorschlagUebernehmen() {
        val vorschlag = _zustand.value.vorschlag ?: return
        if (_zustand.value.sendet) return
        _zustand.update { it.copy(sendet = true) }
        viewModelScope.launch {
            val rest = mutableListOf<Pair<ZeitAbschnitt, String>>()
            val eingereiht = mutableListOf<ZeitNeu>()
            var abbruch: ApiFehler? = null
            var gebucht = 0
            for (paar in vorschlag.offen) {
                val zeit = vorschlagZuZeit(paar.first, vorschlag.datum, paar.second)
                if (zeit == null || abbruch != null) {
                    rest += paar
                    continue
                }
                if (eingereiht.isNotEmpty()) {
                    // Reihenfolge halten: steht schon etwas im Korb, folgt der Rest dorthin.
                    eingereiht += zeit
                    continue
                }
                try {
                    repo.zeitNeu(zeit)
                    gebucht++
                } catch (fehler: ApiFehler) {
                    if (gehtInDenKorb(fehler)) {
                        eingereiht += zeit
                        if (fehler is ApiFehler.NichtAngemeldet) abbruch = fehler
                    } else {
                        abbruch = fehler
                        rest += paar
                    }
                }
            }
            // Nach einem 401 wurde die Schleife oben abgebrochen; was offen ist, geht auch in den Korb.
            if (abbruch is ApiFehler.NichtAngemeldet) {
                rest.mapNotNull { vorschlagZuZeit(it.first, vorschlag.datum, it.second) }.let(eingereiht::addAll)
                rest.clear()
            }
            if (eingereiht.isNotEmpty()) einreihen(eingereiht)
            _zustand.update {
                it.copy(sendet = false, vorschlag = if (rest.isEmpty()) null else vorschlag.copy(offen = rest))
            }
            when (val f = abbruch) {
                is ApiFehler.NichtAngemeldet -> abmelden()
                null -> if (gebucht > 0 && eingereiht.isEmpty()) meldung(R.string.z_uebernommen)
                else -> meldungen.trySend(ZeitenMeldung(f.anzeigetext, fehler = true))
            }
            laden()
        }
    }

    // --- Hilfen -----------------------------------------------------------------------------

    /** 409 heisst hier „Tag schon eingereicht“ o. ae. - das wird im Korb nicht besser. */
    private fun gehtInDenKorb(fehler: ApiFehler): Boolean =
        fehler.istVoruebergehend && fehler !is ApiFehler.Beschaeftigt

    private fun einreihen(zeiten: List<ZeitNeu>) {
        zeiten.forEach(app.korb::zeitEinreihen)
        MonteurVersand.anstossen(app)
        _zustand.update { z -> z.copy(wartend = z.wartend + zeiten.map(::WartendeZeit)) }
        meldung(R.string.z_wartet_auf_netz)
    }

    /**
     * Gemeinsamer Rahmen fuer alles, was schreibt: Knoepfe sperren, 401 abmelden,
     * 409 `detail` zeigen und neu laden. [nachKorb] liefert, was bei einem
     * voruebergehenden Fehler in den Korb soll, und was danach zu tun ist.
     */
    private fun senden(
        nachKorb: (() -> Pair<List<ZeitNeu>, () -> Unit>)? = null,
        block: suspend () -> Unit,
    ) {
        if (_zustand.value.sendet) return
        _zustand.update { it.copy(sendet = true) }
        viewModelScope.launch {
            try {
                block()
            } catch (fehler: ApiFehler) {
                val korb = nachKorb?.takeIf { gehtInDenKorb(fehler) }?.invoke()
                when {
                    korb != null -> {
                        einreihen(korb.first)
                        korb.second()
                        if (fehler is ApiFehler.NichtAngemeldet) abmelden()
                    }
                    fehler is ApiFehler.NichtAngemeldet -> abmelden()
                    fehler is ApiFehler.Beschaeftigt -> {
                        meldungen.trySend(ZeitenMeldung(fehler.anzeigetext, fehler = true))
                        laden()
                    }
                    else -> meldungen.trySend(ZeitenMeldung(fehler.anzeigetext, fehler = true))
                }
            } finally {
                _zustand.update { it.copy(sendet = false) }
            }
        }
    }

    /** Beiwerk laden: 401 meldet ab, alles andere bleibt still (`null`). */
    private suspend fun <T> nebenbei(block: suspend () -> T): T? = try {
        block()
    } catch (fehler: ApiFehler) {
        if (fehler is ApiFehler.NichtAngemeldet) abmelden()
        null
    }

    private fun meldung(@StringRes text: Int) {
        meldungen.trySend(ZeitenMeldung(app.getString(text)))
    }

    private fun abmelden() {
        if (_abgemeldet.value) return
        repo.lokalAbmelden()
        _abgemeldet.value = true
    }

    override fun onCleared() {
        wiederholung?.first?.delete()
    }

    companion object {
        const val AKTION_START = "start"
        const val AKTION_WECHSEL = "wechsel"
        const val AKTION_STOPP = "stopp"

        fun factory(app: MonteurApp) = viewModelFactory { initializer { ZeitenViewModel(app) } }
    }
}
