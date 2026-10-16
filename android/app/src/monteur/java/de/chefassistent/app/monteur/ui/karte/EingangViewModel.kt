package de.chefassistent.app.monteur.ui.karte

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.chefassistent.app.data.ApiFehler
import de.chefassistent.app.monteur.MonteurApp
import de.chefassistent.app.monteur.data.AbwVorschlag
import de.chefassistent.app.monteur.data.EingangAuftrag
import de.chefassistent.app.monteur.data.Karte
import de.chefassistent.app.monteur.data.MaterialAenderung
import de.chefassistent.app.monteur.data.MonteurRepository
import de.chefassistent.app.monteur.data.MonteurVersand
import de.chefassistent.app.monteur.data.Passt
import de.chefassistent.app.monteur.data.FotoAuswahl
import de.chefassistent.app.monteur.data.GeradeAufgenommen
import de.chefassistent.app.monteur.data.GeradeFoto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withTimeoutOrNull
import de.chefassistent.app.monteur.data.Protokoll
import de.chefassistent.app.monteur.ui.bericht.Unterschrift
import de.chefassistent.app.monteur.ui.bericht.Zeichenblock
import android.util.Base64
import de.chefassistent.app.monteur.data.PasstAuftrag
import de.chefassistent.app.monteur.data.ZeitAbschnitt
import de.chefassistent.app.monteur.data.materialJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Wo der Monteur gerade ist - geht automatisch mit, gefragt wird nie (Auftrag 1.23, 1.). */
data class EingangKontext(
    val terminId: String? = null,
    val projektNr: String? = null,
    /** `YYYY-MM-DD`, wenn auf Heute/Zeiten ein anderer Tag gewaehlt ist. */
    val datum: String? = null,
    /** Fuer die Anzeige („SAN-1243 · Böse“). */
    val titel: String? = null,
    /** Seit 1.29.0: bekanntes Projekt - Fotos gehen sofort hinein, Sprechen bezieht sich auf „gerade aufgenommen“. */
    val projektId: Int? = null,
)

enum class EingangPhase {
    /** Fotos gesammelt bzw. aus der Projektmappe: Sprechen, Tippen, Senden. */
    EINGABE,

    /** „KI denkt mit …“. */
    LAEUFT,

    /** Im Ausgangskorb, kommt nach dem naechsten Netz. */
    WARTET,

    /** Eine Karte ist da (offen oder erledigt). */
    KARTE,
}

/** Foto aus der Projektmappe als Kontext (1.27.0): geht als `foto_ids`, [vorschau] = lokale Vorschau. */
data class ProjektFotoRef(val uuid: String, val name: String = "", val vorschau: String? = null)

data class EingangZustand(
    val phase: EingangPhase = EingangPhase.EINGABE,
    val kontext: EingangKontext = EingangKontext(),
    /** Gesammelte Fotos (bis 5), lokale Pfade. */
    val fotos: List<String> = emptyList(),
    /** Fotos aus der Projektmappe - zaehlen mit [fotos] zusammen hoechstens 5. */
    val projektFotos: List<ProjektFotoRef> = emptyList(),
    val text: String = "",
    val beginn: Long = 0L,
    val karte: Karte? = null,
    /**
     * Fruehere Karten derselben Frage (Nachfragen mit `bezug_id`), aelteste zuerst -
     * erscheinen ueber der aktuellen Antwort wie ein kurzer Verlauf.
     */
    val verlauf: List<Karte> = emptyList(),
    /** Lokale Fotos zur aktuellen Karte (Meldung zeigt sie als Vorschau). */
    val kartenFotos: List<String> = emptyList(),
    /** `409`/`422` beim „Passt“ - Karte bleibt offen, Text steht darueber. */
    val passtFehler: String? = null,
    val sendet: Boolean = false,
    /** Offene Karten der letzten 7 Tage („Noch zu bestaetigen“ auf Heute). */
    val offene: List<Karte> = emptyList(),
    /** Protokollvorlagen fuer den Wechsel im Kartenkopf (1.25.0). */
    val vorlagen: List<Protokoll> = emptyList(),
    /** Nach `409` beim Protokoll: dieses Feld (`k`) anspringen. Zaehlt hoch, damit es erneut greift. */
    val springeZu: Pair<String, Int>? = null,
)

/**
 * Was der Monteur auf einer Karte geaendert hat - geht bei „Passt“ mit, und nur das
 * (Auftrag 1.23, 3.). `null` heisst unveraendert. Die Karteninhalte schreiben hier hinein.
 */
class KartenEingabe {
    /** Bestellung: die vollstaendige Liste, sobald irgendetwas geaendert wurde. */
    var material by mutableStateOf<List<MaterialAenderung>?>(null)

    /** Meldung: korrigierter Text. */
    var text by mutableStateOf<String?>(null)

    /** Zeiten: korrigierte Abschnitte. */
    var zeiten by mutableStateOf<List<ZeitAbschnitt>?>(null)

    /** Urlaub: korrigierter Antrag. */
    var abwesenheit by mutableStateOf<AbwVorschlag?>(null)

    /** Foto / Frage mit Foto: geaenderter Titel (1.28.0). */
    var titel by mutableStateOf<String?>(null)

    /** Protokoll: nur geaenderte Werte `k → wert` (`null` = leeren). */
    var werte by mutableStateOf<Map<String, String?>>(emptyMap())

    /** Protokoll: Kundenunterschrift, optional. */
    var unterschrift by mutableStateOf<Unterschrift?>(null)

    fun passt(): Passt = Passt(
        text = text,
        material = material?.let(::materialJson),
        zeiten = zeiten,
        abwesenheit = abwesenheit,
        werte = werteJson(werte),
        unterschrift_png = unterschrift?.let { Base64.encodeToString(it.png, Base64.NO_WRAP) },
        unterschrift_name = unterschrift?.name?.ifBlank { null },
        titel = titel,
    )

    fun zuruecksetzen() {
        material = null
        text = null
        zeiten = null
        abwesenheit = null
        werte = emptyMap()
        unterschrift = null
        titel = null
    }
}

/**
 * Ein Eingang fuer alles (Auftrag 1.23): Mikrofon, Kamera oder Text an
 * `POST /v1/monteur/eingang`, die KI erkennt die Absicht, zurueck kommt **eine Karte**,
 * bestaetigt wird mit „Passt“. Lebt auf Ebene der Activity, damit alle Reiter und die
 * Projektmappe denselben Eingang teilen. Senden und „Passt“ gehen bei Netzproblemen in
 * den Ausgangskorb - gleiche `anfrage_id` = gleiche Karte, nie doppelt.
 */
class EingangViewModel(private val app: MonteurApp) : ViewModel() {

    private val repository = app.repository

    private val _zustand = MutableStateFlow(EingangZustand())
    val zustand: StateFlow<EingangZustand> = _zustand.asStateFlow()

    private val _abgemeldet = MutableStateFlow(false)
    val abgemeldet: StateFlow<Boolean> = _abgemeldet.asStateFlow()

    private val _meldungen = Channel<String>(Channel.BUFFERED)
    val meldungen: Flow<String> = _meldungen.receiveAsFlow()

    /** Aenderungen auf der offenen Karte; je Karte neu. */
    val eingabe = KartenEingabe()

    /** Unterschrift fuers Protokoll - gleicher Dialog wie beim Bericht. */
    val zeichenblock = Zeichenblock("") { png, name -> eingabe.unterschrift = Unterschrift(png, name) }

    private var sprung = 0

    private val _auswahl = MutableStateFlow(FotoAuswahl())

    /** Welche „gerade aufgenommen“-Fotos beim Sprechen/Tippen mitgehen (Auftrag 1.29). */
    val auswahl: StateFlow<FotoAuswahl> = _auswahl.asStateFlow()

    fun fotoUmschalten(foto: GeradeFoto) {
        val liste = app.gerade.fuerProjekt(foto.projektId)
        _auswahl.update { it.umschalten(foto, liste) }
    }

    /** Die gemeinten Fotos fuer [projektId] - markierte, sonst das letzte noch nicht verwendete. */
    fun gemeint(projektId: Int?): List<GeradeFoto> = _auswahl.value.gemeint(app.gerade.fuerProjekt(projektId))

    /** Fotos aus Kamera/Galerie mit bekanntem Projekt: sofort ablegen (Auftrag 1.29). */
    fun fotosAblegen(projektId: Int, dateien: List<File>) {
        if (dateien.isEmpty()) return
        viewModelScope.launch {
            try {
                app.fotosAblegen(projektId, dateien)
                // Ein neues Foto ist wieder automatisch „gemeint“.
                _auswahl.value = FotoAuswahl()
            } catch (_: Exception) {
                _meldungen.trySend(KORB_FEHLER)
            }
        }
    }

    private val entwurfPrefs = app.getSharedPreferences(MonteurApp.EINGABE_PREFS, android.content.Context.MODE_PRIVATE)

    init {
        // Ungesendete Fotos ueberleben Zurueck, neue Eingaben und das Beenden der App.
        val gespeichert = entwurfPrefs.getString(MonteurApp.EINGABE_FOTOS, "").orEmpty()
            .split('\n').filter { it.isNotBlank() && File(it).exists() }
        if (gespeichert.isNotEmpty()) _zustand.update { it.copy(fotos = gespeichert) }
        viewModelScope.launch {
            _zustand.map { it.fotos }.distinctUntilChanged().collect { liste ->
                entwurfPrefs.edit().putString(MonteurApp.EINGABE_FOTOS, liste.joinToString("\n")).apply()
            }
        }
        // Kam eine Karte aus dem Korb an, steht sie in den offenen Karten.
        viewModelScope.launch {
            app.korb.gemeldet.collect { liste -> if (liste.any { it.karteId != null }) offeneLaden() }
        }
    }

    // --- Eingabe -------------------------------------------------------------------------

    /** Neue Eingabe in [kontext] beginnen (z. B. aus der Projektmappe) - verwirft nichts Gesendetes. */
    fun beginnen(kontext: EingangKontext) {
        // Ungesendete Fotos bleiben stehen (nichts darf verloren gehen) - sie gehen mit der naechsten Eingabe.
        eingabe.zuruecksetzen()
        _zustand.update {
            it.copy(phase = EingangPhase.EINGABE, kontext = kontext, text = "", karte = null, verlauf = emptyList(), kartenFotos = emptyList(), passtFehler = null, projektFotos = emptyList())
        }
    }

    fun textAendern(text: String) = _zustand.update { it.copy(text = text) }

    /** Foto aus der Kamera dazu. Danach optional sprechen/tippen, dann senden. */
    fun fotoHinzu(datei: File, kontext: EingangKontext? = null) = fotosHinzu(listOf(datei), kontext)

    /**
     * Fotos aus Kamera oder Galerie dazu, hoechstens [MAX_FOTOS] (Vertrag). Was darueber
     * hinausgeht, wird **nicht** still verworfen, sondern gemeldet. Frueher flog dabei
     * unbemerkt das aelteste Foto raus.
     */
    fun fotosHinzu(dateien: List<File>, kontext: EingangKontext? = null) {
        if (dateien.isEmpty()) return
        var zuviel = 0
        _zustand.update { z ->
            val neu = if (z.phase == EingangPhase.EINGABE) z else z.copy(phase = EingangPhase.EINGABE, karte = null, verlauf = emptyList(), text = "")
            val frei = (MAX_FOTOS - neu.fotos.size - neu.projektFotos.size).coerceAtLeast(0)
            val rein = dateien.take(frei).map(::dauerhaft)
            zuviel = dateien.size - rein.size
            neu.copy(fotos = neu.fotos + rein.map(File::getAbsolutePath), kontext = kontext ?: neu.kontext)
        }
        if (zuviel > 0) {
            dateien.takeLast(zuviel).forEach(File::delete)
            _meldungen.trySend(ZU_VIELE_FOTOS.format(MAX_FOTOS, zuviel))
        }
    }

    /**
     * Foto aus der Projektmappe als Kontext (Auftrag 1.28, 1.): geht als `foto_ids` mit, der
     * Server holt es selbst. Fuer die Anzeige laedt die App nur die kleine Vorschau.
     */
    fun projektFotoHinzu(uuid: String, name: String, kontext: EingangKontext? = null) {
        val z = _zustand.value
        if (z.projektFotos.any { it.uuid == uuid }) return
        if (z.fotos.size + z.projektFotos.size >= MAX_FOTOS) {
            _meldungen.trySend(ZU_VIELE_FOTOS.format(MAX_FOTOS, 1))
            return
        }
        _zustand.update {
            val neu = if (it.phase == EingangPhase.EINGABE) it else it.copy(phase = EingangPhase.EINGABE, karte = null, verlauf = emptyList(), text = "")
            neu.copy(projektFotos = neu.projektFotos + ProjektFotoRef(uuid, name), kontext = kontext ?: neu.kontext)
        }
        viewModelScope.launch {
            val datei = try {
                repository.datei(uuid, name.ifBlank { "$uuid.jpg" }, true)
            } catch (_: ApiFehler) {
                return@launch
            }
            _zustand.update { z2 -> z2.copy(projektFotos = z2.projektFotos.map { if (it.uuid == uuid) it.copy(vorschau = datei.path) else it }) }
        }
    }

    /** Aus dem Cache in den dauerhaften Ordner - Android raeumt den Cache sonst irgendwann leer. */
    private fun dauerhaft(datei: File): File {
        if (datei.path.startsWith(app.eingabeFotoOrdner.path)) return datei
        val ziel = File(File(app.eingabeFotoOrdner, "${System.nanoTime()}").apply { mkdirs() }, datei.name)
        return if (datei.renameTo(ziel)) ziel else runCatching { datei.copyTo(ziel); datei.delete(); ziel }.getOrDefault(datei)
    }

    fun projektFotoEntfernen(uuid: String) = _zustand.update { it.copy(projektFotos = it.projektFotos.filterNot { r -> r.uuid == uuid }) }

    fun fotoEntfernen(pfad: String) {
        File(pfad).delete()
        _zustand.update { it.copy(fotos = it.fotos - pfad) }
    }

    /**
     * Senden. Kontext geht automatisch mit: offener Bericht (`bericht_id`), offene
     * Antwort (`bezug_id`), Termin/Projekt/Datum aus [EingangKontext].
     */
    fun senden(aufnahme: File? = null, text: String? = null, kontext: EingangKontext? = null) {
        val z = _zustand.value
        val k = kontext ?: z.kontext
        if (z.phase == EingangPhase.LAEUFT) {
            // Die vorige Eingabe laeuft noch: diese geht in den Korb und kommt gleich danach dran.
            val t = text?.trim()?.takeIf { it.isNotEmpty() }
            if (aufnahme != null || t != null) {
                einreihen(MonteurRepository.neueAnfrageId(), aufnahme, t, emptyList(), k, null, null, nurKorb = true)
                _meldungen.trySend(NACHGEREIHT)
            }
            return
        }
        val getippt = (text ?: z.text).trim().takeIf { it.isNotEmpty() }
        val fotos = z.fotos.map(::File).filter { it.exists() }
        val projektFotoIds = z.projektFotos.map(ProjektFotoRef::uuid)
        // Seit 1.29.0: Sprechen/Tippen bezieht sich auf die gemeinten „gerade aufgenommen“-Fotos.
        val gerade = gemeint(k.projektId)
        if (aufnahme == null && getippt == null && fotos.isEmpty() && projektFotoIds.isEmpty()) return
        val offen = z.karte?.takeIf { it.istOffen || it.art == Karte.ART_FRAGE }
        val bezug = offen?.takeIf { it.art == Karte.ART_FRAGE }?.id
        val berichtId = offen?.takeIf { it.art == Karte.ART_BERICHT }?.bericht?.id
        val anfrage = MonteurRepository.neueAnfrageId()
        val vorherigeKarte = z.karte
        _zustand.update {
            it.copy(
                phase = EingangPhase.LAEUFT, kontext = k, beginn = System.currentTimeMillis(),
                passtFehler = null, kartenFotos = gerade.map(GeradeFoto::vorschau) + fotos.map(File::getAbsolutePath),
            )
        }
        viewModelScope.launch {
            val netz = app.hatNetz()
            // Noch nicht oben? Mit Netz kurz warten - meist dauert der Upload Sekunden. Ohne Netz nie.
            val offen = gerade.filter { it.fotoId == null }.map(GeradeFoto::id)
            if (offen.isNotEmpty() && netz) {
                withTimeoutOrNull(FOTO_ID_WARTEN_MS) {
                    app.gerade.fotos.first { l -> offen.all { id -> l.firstOrNull { it.id == id }?.fotoId != null } }
                }
            }
            val aufgeloest = gerade.map { g -> g.id to (g.fotoId ?: app.gerade.fotoIdFuer(g.id)) }
            val fotoIds = projektFotoIds + aufgeloest.mapNotNull { it.second }
            val nochOffen = aufgeloest.filter { it.second == null }.map { it.first }
            if (gerade.isNotEmpty()) {
                app.gerade.alsVerwendet(gerade.map(GeradeFoto::id))
                _auswahl.value = FotoAuswahl()
            }
            if (nochOffen.isNotEmpty() || !netz) {
                // Funkloch: sofort in den Korb, der Monteur wartet nie. Der Korb loest die IDs nach dem Upload auf.
                einreihen(anfrage, aufnahme, getippt, fotos, k, berichtId, bezug, fotoIds, nochOffen)
                return@launch
            }
            try {
                val karte = repository.eingang(
                    anfrageId = anfrage,
                    aufnahme = aufnahme,
                    text = getippt,
                    fotos = fotos,
                    terminId = k.terminId,
                    projektNr = k.projektNr,
                    datum = k.datum,
                    berichtId = berichtId,
                    bezugId = bezug,
                    fotoIds = fotoIds,
                )
                aufnahme?.delete()
                // Fotos bleiben fuer die Meldungs-Vorschau, bis die Karte geschlossen wird.
                _zustand.update {
                    it.copy(
                        fotos = emptyList(),
                        projektFotos = emptyList(),
                        text = "",
                        // Nachfrage: vorige Antwort wandert in den Verlauf.
                        verlauf = if (bezug != null && vorherigeKarte != null) it.verlauf + vorherigeKarte else emptyList(),
                    )
                }
                zeigen(karte)
            } catch (fehler: ApiFehler) {
                when {
                    fehler is ApiFehler.NichtAngemeldet || fehler.istVoruebergehend -> {
                        einreihen(anfrage, aufnahme, getippt, fotos, k, berichtId, bezug, fotoIds)
                        if (fehler is ApiFehler.NichtAngemeldet) abmelden()
                    }
                    else -> {
                        // 422 („Zu welchem Projekt?“), 413, 415: Eingabe bleibt, Text zeigen.
                        _zustand.update {
                            it.copy(phase = if (vorherigeKarte != null) EingangPhase.KARTE else EingangPhase.EINGABE, text = getippt.orEmpty())
                        }
                        aufnahme?.delete()
                        _meldungen.trySend(fehler.anzeigetext)
                    }
                }
            }
        }
    }

    private fun einreihen(
        anfrage: String,
        aufnahme: File?,
        text: String?,
        fotos: List<File>,
        k: EingangKontext,
        berichtId: String?,
        bezug: String?,
        fotoIds: List<String> = emptyList(),
        geradeIds: List<String> = emptyList(),
        /** `true`: die laufende Eingabe nicht anfassen (Phase bleibt, wie sie ist). */
        nurKorb: Boolean = false,
    ) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    app.korb.eingangEinreihen(
                        EingangAuftrag(
                            anfrage_id = anfrage, text = text, termin_id = k.terminId, projekt_nr = k.projektNr,
                            datum = k.datum, bericht_id = berichtId, bezug_id = bezug,
                            titel = k.titel ?: text?.take(40) ?: "Eingabe",
                            foto_ids = fotoIds,
                            gerade_ids = geradeIds,
                        ),
                        aufnahme,
                        fotos,
                    )
                }
                MonteurVersand.anstossen(app)
                if (!nurKorb) {
                    _zustand.update { it.copy(phase = EingangPhase.WARTET, fotos = emptyList(), projektFotos = emptyList(), text = "") }
                }
            } catch (_: Exception) {
                if (!nurKorb) _zustand.update { it.copy(phase = EingangPhase.EINGABE) }
                _meldungen.trySend(KORB_FEHLER)
            }
        }
    }

    // --- Karte ---------------------------------------------------------------------------

    /** Karte aus „Noch zu bestaetigen“ oder aus einer Korb-Meldung oeffnen. */
    fun oeffnen(karte: Karte) {
        eingabe.zuruecksetzen()
        zeichenblockVorbereiten(karte)
        _zustand.update { it.copy(phase = EingangPhase.KARTE, karte = karte, verlauf = emptyList(), kartenFotos = emptyList(), passtFehler = null) }
    }

    fun oeffnen(id: String) {
        viewModelScope.launch {
            try {
                oeffnen(repository.karte(id))
            } catch (fehler: ApiFehler) {
                if (fehler is ApiFehler.NichtAngemeldet) abmelden() else _meldungen.trySend(fehler.anzeigetext)
            }
        }
    }

    private fun zeigen(karte: Karte) {
        eingabe.zuruecksetzen()
        zeichenblockVorbereiten(karte)
        _zustand.update { it.copy(phase = EingangPhase.KARTE, karte = karte, passtFehler = null) }
        offeneLaden()
    }

    /**
     * „Passt“ mit nur den geaenderten Feldern. 409/422 → Karte bleibt offen, Text zeigen.
     * Netz/502/5xx → Korb, gleiches Ergebnis spaeter.
     */
    fun passt() {
        val karte = _zustand.value.karte ?: return
        if (_zustand.value.sendet) return
        val daten = eingabe.passt()
        _zustand.update { it.copy(sendet = true, passtFehler = null) }
        viewModelScope.launch {
            try {
                zeigen(repository.passt(karte.id, daten))
            } catch (fehler: ApiFehler) {
                when {
                    // Protokoll: Pflichtwerte fehlen. Die Werte sind gespeichert - Karte neu laden.
                    fehler is ApiFehler.Beschaeftigt && karte.art == Karte.ART_PROTOKOLL -> protokollNachladen(karte, fehler.anzeigetext)
                    // Protokoll: HERO hat abgelehnt. Karte bleibt, „Passt“ wiederholt (gleiche Werte).
                    fehler is ApiFehler.KiNichtErreichbar && karte.art == Karte.ART_PROTOKOLL ->
                        _zustand.update { it.copy(passtFehler = fehler.anzeigetext) }
                    fehler is ApiFehler.Beschaeftigt || fehler is ApiFehler.Ungueltig ->
                        _zustand.update { it.copy(passtFehler = fehler.anzeigetext) }
                    fehler is ApiFehler.NichtAngemeldet || fehler.istVoruebergehend -> {
                        app.korb.passtEinreihen(PasstAuftrag(karte.id, daten, kartenTitel(karte)))
                        MonteurVersand.anstossen(app)
                        _zustand.update { it.copy(phase = EingangPhase.WARTET) }
                        if (fehler is ApiFehler.NichtAngemeldet) abmelden()
                    }
                    else -> _zustand.update { it.copy(passtFehler = fehler.anzeigetext) }
                }
            } finally {
                _zustand.update { it.copy(sendet = false) }
            }
        }
    }

    // --- Protokoll (1.25.0) ----------------------------------------------------------------

    private fun zeichenblockVorbereiten(karte: Karte) {
        zeichenblock.stricheLoeschen()
        zeichenblock.unterschriftName = karte.projekt?.kunde.orEmpty()
    }

    /** Nach `409`: gespeicherten Stand holen, lokale Werte sind jetzt auf dem Server. */
    private suspend fun protokollNachladen(karte: Karte, text: String) {
        _meldungen.trySend(text)
        val neu = try {
            repository.karte(karte.id)
        } catch (_: ApiFehler) {
            null
        }
        if (neu != null) {
            eingabe.werte = emptyMap()
            val erstes = neu.protokoll?.let { p -> p.felder.firstOrNull { it.l in p.fehlend }?.k }
            _zustand.update {
                // Kein roter Kasten: „Es fehlen noch …“ steht live in der Karte und bleibt aktuell.
                it.copy(karte = neu, passtFehler = null, springeZu = erstes?.let { k -> k to ++sprung })
            }
        } else {
            _zustand.update { it.copy(passtFehler = text) }
        }
    }

    /** Vorlagen fuer das Dropdown, einmal je Programmlauf. Fehler still. */
    fun vorlagenLaden() {
        if (_zustand.value.vorlagen.isNotEmpty()) return
        viewModelScope.launch {
            try {
                val liste = repository.protokollVorlagen()
                _zustand.update { it.copy(vorlagen = liste) }
            } catch (fehler: ApiFehler) {
                if (fehler is ApiFehler.NichtAngemeldet) abmelden()
            }
        }
    }

    /** Andere Vorlage: sofort speichern (`nur_speichern`), nichts geht nach HERO. */
    fun vorlageWechseln(vorlage: String) {
        val karte = _zustand.value.karte ?: return
        if (karte.protokoll?.vorlage == vorlage) return
        speichern(karte, Passt(werte = werteJson(eingabe.werte), vorlage = vorlage, nur_speichern = true)) {}
    }

    /** Vor der Vorschau: Geaendertes speichern, dann das PDF zeigen. */
    fun vorschau(beiFertig: (karteId: String) -> Unit) {
        val karte = _zustand.value.karte ?: return
        val werte = werteJson(eingabe.werte)
        if (!karte.istOffen || werte == null) return beiFertig(karte.id)
        speichern(karte, Passt(werte = werte, nur_speichern = true)) { beiFertig(karte.id) }
    }

    private fun speichern(karte: Karte, daten: Passt, danach: () -> Unit) {
        if (_zustand.value.sendet) return
        _zustand.update { it.copy(sendet = true, passtFehler = null) }
        viewModelScope.launch {
            try {
                val neu = repository.passt(karte.id, daten)
                eingabe.werte = emptyMap()
                _zustand.update { it.copy(karte = neu) }
                danach()
            } catch (fehler: ApiFehler) {
                if (fehler is ApiFehler.NichtAngemeldet) abmelden() else _meldungen.trySend(fehler.anzeigetext)
            } finally {
                _zustand.update { it.copy(sendet = false) }
            }
        }
    }

    /** `DELETE` - die Oberflaeche fragt bei Berichten vorher nach. */
    fun verwerfen(beiFertig: () -> Unit) {
        val karte = _zustand.value.karte ?: return beiFertig()
        _zustand.update { it.copy(sendet = true) }
        viewModelScope.launch {
            try {
                repository.karteVerwerfen(karte.id)
                schliessen()
                beiFertig()
            } catch (fehler: ApiFehler) {
                when (fehler) {
                    is ApiFehler.NichtGefunden -> {
                        schliessen()
                        beiFertig()
                    }
                    is ApiFehler.NichtAngemeldet -> abmelden()
                    else -> _meldungen.trySend(fehler.anzeigetext)
                }
            } finally {
                _zustand.update { it.copy(sendet = false) }
            }
        }
    }

    /** Karte zu, lokale Fotos weg. */
    fun schliessen() {
        // Vorschauen „gerade aufgenommen“ gehoeren der Leiste, nicht der Karte.
        // Nur Fotos, die mit der Karte gesendet wurden - der Entwurf (fotos) bleibt unangetastet.
        _zustand.value.kartenFotos.filterNot { "/${GeradeAufgenommen.ORDNER}/" in it || it in _zustand.value.fotos }.forEach { File(it).delete() }
        eingabe.zuruecksetzen()
        _zustand.update { it.copy(phase = EingangPhase.EINGABE, karte = null, verlauf = emptyList(), kartenFotos = emptyList(), text = "", passtFehler = null, projektFotos = emptyList()) }
        offeneLaden()
    }

    /** `GET /v1/monteur/eingang`, offene Karten als Erinnerung auf „Heute“. Fehler bleiben still. */
    fun offeneLaden() {
        viewModelScope.launch {
            try {
                val liste = repository.karten()
                _zustand.update { it.copy(offene = liste.filter(Karte::istOffen)) }
            } catch (fehler: ApiFehler) {
                if (fehler is ApiFehler.NichtAngemeldet) abmelden()
            }
        }
    }

    private fun abmelden() {
        repository.lokalAbmelden()
        _abgemeldet.value = true
    }

    companion object {
        /** Seit 1.29.0 bis 50 neue Fotos je Eingang; die KI sieht die letzten 5. */
        const val MAX_FOTOS = 50

        /** So lange wartet Sprechen auf die `foto_id` eines gerade hochladenden Fotos. */
        const val FOTO_ID_WARTEN_MS = 5_000L
        private const val NACHGEREIHT = "Die vorige Eingabe läuft noch – diese kommt gleich danach dran."
        private const val ZU_VIELE_FOTOS = "Höchstens %d Fotos auf einmal – %d nicht übernommen."
        private const val KORB_FEHLER = "Konnte nicht zwischengespeichert werden. Bitte nochmal senden."

        fun factory(app: MonteurApp) = viewModelFactory { initializer { EingangViewModel(app) } }

        fun kartenTitel(karte: Karte): String =
            listOfNotNull(karte.projekt?.nr?.ifBlank { null }, karte.projekt?.kunde?.ifBlank { null }).joinToString(" · ").ifBlank { karte.art }
    }
}
