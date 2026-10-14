package de.chefassistent.app.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.chefassistent.app.R
import de.chefassistent.app.data.ApiFehler
import de.chefassistent.app.data.AssistentRepository
import de.chefassistent.app.data.AudioAufnahme
import de.chefassistent.app.ChefAssistentApp
import de.chefassistent.app.data.AusgangAuftrag
import de.chefassistent.app.data.Auftrag
import de.chefassistent.app.data.Ausgangskorb
import de.chefassistent.app.data.Rueckmeldung
import de.chefassistent.app.data.ChatAntwort
import de.chefassistent.app.data.DateiLink
import de.chefassistent.app.data.EntscheidungAntwort
import androidx.media3.common.util.UnstableApi
import androidx.work.WorkInfo
import androidx.work.WorkManager
import de.chefassistent.app.data.ABSCHREIBEN_WERTE
import de.chefassistent.app.data.FotoAufbereitung
import de.chefassistent.app.data.MedienAuftrag
import de.chefassistent.app.data.Medienkorb
import de.chefassistent.app.data.VideoAufbereitung
import de.chefassistent.app.data.GespraechKurz
import de.chefassistent.app.data.Nachricht
import de.chefassistent.app.data.Notizen
import de.chefassistent.app.data.Quelle
import de.chefassistent.app.data.Sprachausgabe
import de.chefassistent.app.data.Tonwiedergabe
import de.chefassistent.app.data.VersandArbeiter
import de.chefassistent.app.ui.markdownEntfernen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** Eine Zeile im Gespraech. */
sealed interface ChatEintrag {
    val schluessel: String
}

/**
 * Eigene Nachricht. [dateien] sind seit v1.5.0 die Fotos, die der Assistent
 * dazu ansehen sollte - im Chat als Vorschaubild.
 */
data class NutzerEintrag(
    override val schluessel: String,
    val text: String,
    val dateien: List<String> = emptyList(),
    /** Name im [Ausgangskorb], solange die Nachricht auf Netz wartet. */
    val wartet: String? = null,
) : ChatEintrag

data class AssistentEintrag(
    override val schluessel: String,
    val text: String,
    val dateien: List<String> = emptyList(),
    /** Seit v1.13.0. Ist die Liste da, zeigt die Blase Karten statt der Chips aus [dateien]. */
    val links: List<DateiLink> = emptyList(),
    val quellen: List<Quelle> = emptyList(),
) : ChatEintrag

/**
 * Systemzeile ("Ausgefuehrt: ... verschoben") - klein und grau. Ein abgelegtes
 * Foto kommt ebenfalls als Hinweis, mit dem Foto in [dateien].
 */
data class HinweisEintrag(
    override val schluessel: String,
    val text: String,
    val dateien: List<String> = emptyList(),
) : ChatEintrag

/** Was eine andere App per "Teilen" an den Chat gegeben hat. */
sealed interface Geteilt {
    data class Dokument(val uri: Uri) : Geteilt
    data class Text(val text: String) : Geteilt

    /** Aus der App selbst, z. B. "Antworten einarbeiten" im Angebot: in einem neuen Gespraech senden. */
    data class Senden(val text: String) : Geteilt
}

/** Foto, Video oder Dokument, das noch auf Unterschrift und Ablage wartet. */
data class MedienEntwurf(
    val datei: File,
    val istVideo: Boolean = false,
    /**
     * Nur Dokument: Originalname mit Endung, z.B. "LV Bad.pdf". Der Server legt
     * die Datei darunter ab und erkennt daran den Typ.
     */
    val dokument: String? = null,
    /**
     * Nur Foto: statt nur abzulegen den Assistenten dazu fragen (seit v1.5.0).
     * [text] ist dann die Frage, nicht die Bildunterschrift.
     */
    val fragen: Boolean = false,
    val text: String = "",
    /** Pfad einer eigenen Notiz, `null` = neueste von heute. */
    val notiz: String? = null,
    /** Nur Video: einer aus [ABSCHREIBEN_WERTE]. */
    val abschreiben: String = ABSCHREIBEN_WERTE.first(),
    /** Wird hochgeladen, verkleinert oder abgelegt - Knoepfe gesperrt. */
    val arbeitet: Boolean = false,
    /** Nur Video: Fortschritt des Verkleinerns in Prozent. */
    val fortschritt: Int? = null,
    /** Fehler bleibt im Dialog stehen - die Aufnahme soll nicht verlorengehen. */
    val fehler: String? = null,
) {
    val istDokument: Boolean get() = dokument != null
}

/** Offene Ja/Nein-Frage des Servers. */
data class FrageEintrag(
    override val schluessel: String,
    val id: String,
    val beschreibung: String,
    val werkzeug: String,
    val zustand: FrageZustand = FrageZustand.Offen,
) : ChatEintrag

sealed interface FrageZustand {
    data object Offen : FrageZustand
    data object Laeuft : FrageZustand
    data class Erledigt(val text: String, val erfolgreich: Boolean) : FrageZustand
}

data class ChatZustand(
    val eintraege: List<ChatEintrag> = emptyList(),
    val entwurf: String = "",
    val gespraechId: String? = null,
    /** Im Gespraech laeuft eine Anfrage. Laut Vertrag darf nur eine gleichzeitig laufen. */
    val laeuft: Boolean = false,
    val nimmtAuf: Boolean = false,
    /** Schluessel des Eintrags, der gerade vorgelesen wird. */
    val liestVor: String? = null,
    val gespraeche: List<GespraechKurz> = emptyList(),
    val gespraecheLaden: Boolean = false,
    /** true = Stimme vom Server (`POST /v1/vorlesen`), false = Android-Sprachausgabe. */
    val serverStimme: Boolean = true,
    /** Gewaehlte OpenAI-Stimme, `null` = Servervorgabe. */
    val stimme: String? = null,
    /** Erkannten Text erst zeigen statt sofort zu senden. */
    val diktatPruefen: Boolean = true,
    /** Sprachnotizen, die mangels Verbindung noch warten. */
    val wartendeNotizen: Int = 0,
    /**
     * Angehefteter Projektkontext, z.B. "HEZ-2003 Schulz". Ist er gesetzt,
     * bekommt jede Nachricht eine Bezugszeile vorangestellt.
     */
    val kontext: String? = null,
    /** Zuletzt benutzte Kontexte zur schnellen Auswahl. */
    val letzteKontexte: List<String> = emptyList(),
    /**
     * Ordner, der zum angehefteten Projekt gehoert. Ist er gesetzt, gehen Fotos
     * und Videos ohne weiteres Zutun dorthin.
     */
    val kontextZiel: String? = null,
    /** Eigene Notizen und Ordner aus `GET /v1/notizen`, fuer die Auswahl "Wohin?". */
    val ziele: Notizen? = null,
    val zieleLaden: Boolean = false,
    val medium: MedienEntwurf? = null,
    /** Notiz der letzten Aufnahme - Folgeaufnahmen landen in derselben. */
    val letzteMedienNotiz: String? = null,
    /**
     * Serie fuer Fotos, die ohne feste Notiz im Korb warten. Alle bekommen die
     * Notiz, die der Server dem ersten davon zuweist.
     */
    val medienSerie: String? = null,
    val wartendeMedien: Int = 0,
    /**
     * Dokumente, die abgelegt wurden, als es noch kein Gespraech gab. Ohne
     * `gespraech_id` erfaehrt der Assistent nichts davon - die naechste Nachricht
     * nennt sie deshalb (wie der Projektkontext: als Satz, nicht als Feld).
     */
    val ungemeldeteDokumente: List<String> = emptyList(),
    /** Hintergrundauftraege, die laufen oder fertig und noch nicht angesehen sind. */
    val auftraegeOffen: Int = 0,
    /** Eigene offene Aufgaben (`offen` aus `GET /v1/aufgaben`) - Zaehler in der Schublade. */
    val aufgabenOffen: Int = 0,
    /** `false`, wenn der Server bei `GET /v1/angebote` 503 meldet: Angebote dort nicht eingerichtet. */
    val angeboteEingerichtet: Boolean = true,
    /** Fortschritt des laufenden Video-Uploads im Hintergrund, `null` = keiner. */
    val hochladen: Int? = null,
    /**
     * Ohne [gespraechId]: Kennung fuer Nachrichten, die in diesem neuen Chat ohne
     * Netz entstanden. Die erste legt beim Nachsenden das Gespraech an, die
     * uebrigen folgen ihr dorthin.
     */
    val faden: String? = null,
    /** Was der Versand im Hintergrund zurueckgemeldet hat: Diktate, Abgelehntes, Hinweise. */
    val rueckmeldungen: List<Rueckmeldung> = emptyList(),
    val fehler: String? = null,
    val abgemeldet: Boolean = false,
) {
    val kannSenden: Boolean get() = !laeuft && !nimmtAuf && entwurf.isNotBlank()
    val kannAufnehmen: Boolean get() = !laeuft
    val kannFotografieren: Boolean get() = !laeuft && !nimmtAuf && medium == null

    /**
     * Notizen, an die eine Aufnahme gehen kann: die der letzten und alle
     * `.md`-Dateien, die im Gespraech aufgetaucht sind. Ob es eine eigene ist,
     * entscheidet der Server (403).
     */
    val medienNotizen: List<String>
        get() = buildList {
            medium?.notiz?.let(::add)
            kontextZiel?.let(::add)
            letzteMedienNotiz?.let(::add)
            eintraege.filterIsInstance<AssistentEintrag>()
                .flatMap { it.dateien }
                .filter { it.endsWith(".md", ignoreCase = true) }
                .asReversed()
                .forEach(::add)
        }.distinct()
}

class ChatViewModel(
    private val repository: AssistentRepository,
    private val aufnahme: AudioAufnahme,
    private val sprachausgabe: Sprachausgabe,
    private val tonwiedergabe: Tonwiedergabe,
    private val ausgangskorb: Ausgangskorb,
    private val medienkorb: Medienkorb,
    private val anwendung: Context,
    /** Ueberlebt, dass Android die App im Hintergrund beendet (z. B. waehrend die Kamera offen ist). */
    private val gespeichert: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val _zustand = MutableStateFlow(
        ChatZustand(
            serverStimme = repository.einstellungen.serverStimme,
            stimme = repository.einstellungen.stimme,
            diktatPruefen = repository.einstellungen.diktatPruefen,
            letzteKontexte = repository.einstellungen.letzteKontexte,
        )
    )
    val zustand: StateFlow<ChatZustand> = _zustand.asStateFlow()

    private val zaehler = AtomicLong(0)

    /** Letzter bekannter Stand der Auftraege, um "gerade fertig geworden" zu erkennen. */
    private var auftraegeStand: List<Auftrag> = emptyList()
    private var auftragsAbfrage: Job? = null

    /**
     * Zaehlt bei jedem Gespraechswechsel hoch. Eine Antwort, die erst nach dem
     * Wechsel ankommt, darf den neuen Chat nicht anfassen - sonst haengte sie
     * sich an ein fremdes Gespraech und setzte dessen `gespraech_id` zurueck.
     */
    private var sitzung = 0

    /** Laufendes Vorlesen vom Server, damit "Stopp" auch das Laden abbricht. */
    private var vorlesenJob: Job? = null

    init {
        zieleLaden()
        offeneBestaetigungenLaden()
        gespraecheLaden()
        notizenZaehlen()
        versandBeobachten()
        angebotePruefen()
        wartendesAnstossen()
        wiederherstellen()
        viewModelScope.launch { _zustand.collect(::sichern) }
    }

    /**
     * Was nicht verloren gehen darf, wenn Android die App im Hintergrund beendet:
     * der Entwurf, das offene Gespraech und ein Foto, Video oder Dokument, das im
     * Dialog auf "Ablegen" wartet. Den Verlauf selbst hat der Server.
     */
    private fun sichern(z: ChatZustand) {
        gespeichert[S_ENTWURF] = z.entwurf
        gespeichert[S_GESPRAECH] = z.gespraechId
        gespeichert[S_FADEN] = z.faden
        val m = z.medium
        gespeichert[S_MEDIUM] = m?.datei?.path
        gespeichert[S_MEDIUM_VIDEO] = m?.istVideo
        gespeichert[S_MEDIUM_DOKUMENT] = m?.dokument
        gespeichert[S_MEDIUM_FRAGEN] = m?.fragen
        gespeichert[S_MEDIUM_TEXT] = m?.text
        gespeichert[S_MEDIUM_NOTIZ] = m?.notiz
        gespeichert[S_MEDIUM_ABSCHREIBEN] = m?.abschreiben
    }

    private fun wiederherstellen() {
        val entwurf = gespeichert.get<String>(S_ENTWURF).orEmpty()
        val gespraech = gespeichert.get<String>(S_GESPRAECH)
        val faden = gespeichert.get<String>(S_FADEN)
        val medium = gespeichert.get<String>(S_MEDIUM)?.let(::File)?.takeIf { it.exists() }?.let { datei ->
            MedienEntwurf(
                datei = datei,
                istVideo = gespeichert.get<Boolean>(S_MEDIUM_VIDEO) == true,
                dokument = gespeichert.get<String>(S_MEDIUM_DOKUMENT),
                fragen = gespeichert.get<Boolean>(S_MEDIUM_FRAGEN) == true,
                text = gespeichert.get<String>(S_MEDIUM_TEXT).orEmpty(),
                notiz = gespeichert.get<String>(S_MEDIUM_NOTIZ),
                abschreiben = gespeichert.get<String>(S_MEDIUM_ABSCHREIBEN) ?: ABSCHREIBEN_WERTE.first(),
            )
        }
        if (entwurf.isEmpty() && gespraech == null && faden == null && medium == null) return
        // Schon vorab gesetzt: scheitert das Nachladen (Funkloch), geht die
        // naechste Nachricht trotzdem ins richtige Gespraech.
        _zustand.update { it.copy(entwurf = entwurf, gespraechId = gespraech, faden = faden, medium = medium) }
        gespraech?.let(::gespraechOeffnen)
    }

    /**
     * Ohne Anmeldung gibt der Versand auf (jeder Versuch waere ein 401). Der Chat
     * entsteht erst nach der Anmeldung - hier geht es also weiter. Auch nach einem
     * Neustart der App, falls WorkManager den Versand laenger hinauszoegert.
     */
    private fun wartendesAnstossen() {
        viewModelScope.launch {
            val wartet = withContext(Dispatchers.IO) { ausgangskorb.anzahl() + medienkorb.anzahl() > 0 }
            if (wartet) VersandArbeiter.anstossen(anwendung)
        }
    }

    /**
     * 503 bei `GET /v1/angebote` heisst laut `API.md` 4e: auf diesem Server nicht
     * eingerichtet, Menuepunkt ausblenden. Alles andere laesst ihn stehen - ein
     * Netzfehler beim Start soll den Eintrag nicht verschwinden lassen.
     */
    private fun angebotePruefen() {
        viewModelScope.launch {
            val eingerichtet = try {
                repository.angebote()
                true
            } catch (fehler: ApiFehler) {
                if (fehler is ApiFehler.NichtAngemeldet) behandleFehler(fehler)
                !(fehler is ApiFehler.Server && fehler.code == 503)
            }
            _zustand.update { it.copy(angeboteEingerichtet = eingerichtet) }
        }
    }

    // --- Projektkontext ------------------------------------------------------

    /**
     * Heftet einen Projektkontext an, oder loest ihn mit `null`.
     *
     * Der Kontext wird **nicht** dauerhaft gespeichert - nur die Liste der
     * zuletzt benutzten. Ein Kontext von gestern, der heute unbemerkt an jeder
     * Frage haengt, waere gefaehrlicher als ein Tipp zum erneuten Setzen.
     */
    fun kontextSetzen(neuer: String?, ziel: String? = null) {
        val sauber = neuer?.trim()?.takeIf { it.isNotBlank() }
        if (sauber != null) {
            repository.einstellungen.kontextGemerkt(sauber)
        }
        // Heisst der Kontext wie einer der eigenen Ordner, gehoert der Ordner dazu -
        // auch wenn er von Hand getippt oder aus "zuletzt benutzt" gewaehlt wurde.
        val ordner = ziel ?: sauber?.let { name ->
            _zustand.value.ziele?.ordner?.firstOrNull { it.name.equals(name, ignoreCase = true) }?.pfad
        }
        _zustand.update {
            val gewechselt = ordner != it.kontextZiel
            it.copy(
                kontext = sauber,
                kontextZiel = if (sauber == null) null else ordner,
                letzteKontexte = repository.einstellungen.letzteKontexte,
                // Neues Projekt, neue Ablage: die Notiz des vorigen Projekts
                // darf nicht unbemerkt das naechste Foto bekommen.
                letzteMedienNotiz = if (gewechselt) null else it.letzteMedienNotiz,
                medienSerie = if (gewechselt) null else it.medienSerie,
            )
        }
    }

    /** Laedt die eigenen Notizen und Ordner fuer die Auswahl "Wohin?". */
    fun zieleLaden() {
        if (_zustand.value.zieleLaden) return
        _zustand.update { it.copy(zieleLaden = true) }
        viewModelScope.launch {
            try {
                val ziele = repository.notizen()
                _zustand.update { it.copy(ziele = ziele) }
            } catch (fehler: ApiFehler) {
                behandleFehler(fehler)
            } finally {
                _zustand.update { it.copy(zieleLaden = false) }
            }
        }
    }

    /**
     * Stellt der Nachricht die Bezugszeile voran.
     *
     * Bewusst als Satz und nicht als Stichwort: der Assistent liest es als
     * normale Anweisung, ohne dass der Vertrag ein eigenes Feld braeuchte.
     */
    private fun mitKontext(text: String): String =
        listOfNotNull(vorspann(), text).joinToString("\n\n")

    /** Die Bezugszeilen allein, `null` wenn es keine gibt. */
    private fun vorspann(): String? {
        val zustand = _zustand.value
        val zeilen = buildList {
            zustand.kontext?.let { add("Bezieht sich auf Projekt $it.") }
            zustand.ungemeldeteDokumente.forEach { add("Ich habe dir das Dokument `$it` geschickt.") }
        }
        return zeilen.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    // --- Hintergrundauftraege -------------------------------------------------

    /**
     * Vom Bildschirm bei Resume/Pause aufgerufen. Beim Sichtbarwerden einmal
     * abfragen (auch nach der Rueckkehr aus der Auftragsansicht, damit der
     * Zaehler stimmt), danach nur, solange etwas laeuft.
     */
    fun auftraegeBeobachten(ja: Boolean) {
        auftragsAbfrage?.cancel()
        auftragsAbfrage = if (!ja) null else viewModelScope.launch {
            launch { aufgabenZaehlen() }
            auftraegeAbfragen()
            while (isActive) {
                delay(AUFTRAG_ABFRAGE_MS)
                if (auftraegeStand.any { it.istAktiv }) auftraegeAbfragen()
            }
        }
    }

    /**
     * Still: ein Funkloch soll hier keine Meldung ausloesen, der Chat hat
     * Wichtigeres anzuzeigen. Nur 401 zaehlt.
     */
    private suspend fun auftraegeAbfragen() {
        val neu = try {
            repository.auftraege()
        } catch (fehler: ApiFehler) {
            if (fehler is ApiFehler.NichtAngemeldet) behandleFehler(fehler)
            return
        }
        val vorher = auftraegeStand.filter { it.istAktiv }.map { it.id }.toSet()
        auftraegeStand = neu
        val gesehen = repository.einstellungen.geseheneAuftraege
        val offen = neu.count { it.istAktiv || (it.status == Auftrag.STATUS_FERTIG && it.id !in gesehen) }
        _zustand.update { it.copy(auftraegeOffen = offen) }

        // Laut API.md steht das Ergebnis als `hinweis` im Gespraech. Ist genau
        // das offen, den Verlauf nachladen - aber nicht mitten in etwas hinein.
        val z = _zustand.value
        val beendetHier = neu.any { it.id in vorher && !it.istAktiv && it.gespraech_id == z.gespraechId }
        if (beendetHier && z.gespraechId != null && !z.laeuft && !z.nimmtAuf && z.liestVor == null && z.medium == null) {
            gespraechOeffnen(z.gespraechId)
        }
    }

    // --- Aufgaben -------------------------------------------------------------

    /**
     * Zaehler fuer "Offen". Beim Sichtbarwerden und nach jeder Antwort, weil
     * Aufgaben im Chat entstehen und abgehakt werden. Still wie die Auftraege.
     */
    private suspend fun aufgabenZaehlen() {
        val offen = try {
            repository.aufgaben().offen
        } catch (fehler: ApiFehler) {
            if (fehler is ApiFehler.NichtAngemeldet) behandleFehler(fehler)
            return
        }
        _zustand.update { it.copy(aufgabenOffen = offen) }
    }

    fun notizenZaehlen() {
        viewModelScope.launch { ausgangAbgleichen() }
    }

    /**
     * Stand der Koerbe in den Zustand holen: Zaehler, Rueckmeldungen und ob
     * Nachrichten dieses Chats inzwischen angekommen sind. Dann zeigt der Verlauf
     * vom Server, was daraus geworden ist - samt Antwort.
     */
    private suspend fun ausgangAbgleichen() {
        val (wartend, medien, rueckmeldungen) = withContext(Dispatchers.IO) {
            Triple(ausgangskorb.liste(), medienkorb.anzahl(), ausgangskorb.rueckmeldungen())
        }
        val noch = wartend.map { it.name }.toSet()
        val faden = _zustand.value.faden
        val fadenGespraech = faden?.let { withContext(Dispatchers.IO) { ausgangskorb.gespraechZuFaden(it) } }

        _zustand.update {
            it.copy(
                wartendeNotizen = wartend.size,
                wartendeMedien = medien,
                rueckmeldungen = rueckmeldungen,
            )
        }

        val z = _zustand.value
        val angekommen = z.eintraege.any { it is NutzerEintrag && it.wartet != null && it.wartet !in noch }
        if (!angekommen) return
        val gespraech = z.gespraechId ?: fadenGespraech
        if (gespraech != null && !z.laeuft && !z.nimmtAuf && z.medium == null) {
            gespraechOeffnen(gespraech)
        } else {
            // Gerade beschaeftigt: nur die Markierung abnehmen, der Verlauf kommt
            // beim naechsten Oeffnen.
            _zustand.update { alt ->
                alt.copy(
                    eintraege = alt.eintraege.map { e ->
                        if (e is NutzerEintrag && e.wartet != null && e.wartet !in noch) e.copy(wartet = null) else e
                    }
                )
            }
        }
    }

    /** Wartende Nachrichten und Medien von Hand losschicken, ohne auf WorkManager zu warten. */
    fun wartendeNotizenSenden() {
        VersandArbeiter.anstossen(anwendung)
        _zustand.update { it.copy(fehler = anwendung.getString(R.string.ausgang_angestossen)) }
    }

    // --- Rueckmeldungen aus dem Versand ---------------------------------------

    /**
     * Diktat oder abgelehnten Text ins Eingabefeld holen. Ein Entwurf wird nicht
     * ueberschrieben, der Text haengt sich an.
     */
    fun rueckmeldungUebernehmen(id: String) {
        val rueckmeldung = _zustand.value.rueckmeldungen.firstOrNull { it.id == id } ?: return
        geteilterText(rueckmeldung.text)
        rueckmeldungErledigt(id)
    }

    fun rueckmeldungErledigt(id: String) {
        _zustand.update { z -> z.copy(rueckmeldungen = z.rueckmeldungen.filterNot { it.id == id }) }
        viewModelScope.launch(Dispatchers.IO) { ausgangskorb.rueckmeldungErledigt(id) }
    }

    override fun onCleared() {
        _zustand.value.medium?.datei?.delete()
        vorlesenJob?.cancel()
        aufnahme.abbrechen()
        sprachausgabe.freigeben()
        tonwiedergabe.stoppen()
        super.onCleared()
    }

    private fun naechsterSchluessel(praefix: String) = "$praefix-${zaehler.incrementAndGet()}"

    fun entwurfGeaendert(wert: String) = _zustand.update { it.copy(entwurf = wert) }

    fun fehlerVerwerfen() = _zustand.update { it.copy(fehler = null) }

    /**
     * Leerer Chat. Waehrend einer Aufnahme oder eines Foto-Dialogs passiert nichts:
     * beides wuerde sonst ohne Rueckfrage verworfen. Eine laufende Anfrage laeuft
     * weiter, ihre Antwort landet aber nur im alten Gespraech ([sitzung]).
     */
    fun neuesGespraech() {
        val alt = _zustand.value
        if (alt.nimmtAuf) {
            fehlerZeigen(R.string.chat_erst_aufnahme_beenden)
            return
        }
        if (alt.medium != null) return
        sitzung++
        sprechenBeenden()
        _zustand.update {
            ChatZustand(
                gespraeche = it.gespraeche,
                serverStimme = it.serverStimme,
                stimme = it.stimme,
                diktatPruefen = it.diktatPruefen,
                letzteKontexte = it.letzteKontexte,
                wartendeNotizen = it.wartendeNotizen,
                wartendeMedien = it.wartendeMedien,
                hochladen = it.hochladen,
                rueckmeldungen = it.rueckmeldungen,
                ziele = it.ziele,
                auftraegeOffen = it.auftraegeOffen,
                aufgabenOffen = it.aufgabenOffen,
                angeboteEingerichtet = it.angeboteEingerichtet,
                // Getipptes bleibt stehen, und abgelegte Dokumente soll der
                // Assistent mit der naechsten Nachricht trotzdem erfahren.
                entwurf = it.entwurf,
                ungemeldeteDokumente = it.ungemeldeteDokumente,
            )
        }
    }

    /** Menuepunkt "Abmelden": beendet auch die Sitzung auf dem Server (seit v1.10.0). */
    fun abmelden() {
        sprechenBeenden()
        repository.abmeldenUndSitzungBeenden()
        _zustand.update { it.copy(abgemeldet = true) }
    }

    // --- Bestaetigungen ------------------------------------------------------

    /**
     * Holt beim Start die noch offenen Fragen, damit eine Bestaetigung nicht
     * verlorengeht, nur weil die App zwischendurch geschlossen wurde.
     */
    fun offeneBestaetigungenLaden() {
        viewModelScope.launch {
            try {
                val offene = repository.offeneBestaetigungen()
                if (offene.isEmpty()) return@launch
                _zustand.update { alt ->
                    val bekannt = alt.eintraege.filterIsInstance<FrageEintrag>().map { it.id }.toSet()
                    val neue = offene.filterNot { it.id in bekannt }.map {
                        FrageEintrag(
                            schluessel = naechsterSchluessel("frage"),
                            id = it.id,
                            beschreibung = it.beschreibung,
                            werkzeug = it.werkzeug,
                        )
                    }
                    alt.copy(eintraege = alt.eintraege + neue)
                }
            } catch (fehler: ApiFehler) {
                behandleFehler(fehler)
            }
        }
    }

    fun entscheiden(id: String, annehmen: Boolean) {
        setzeFrageZustand(id, FrageZustand.Laeuft)

        viewModelScope.launch {
            try {
                val antwort = repository.entscheiden(id, annehmen)
                setzeFrageZustand(
                    id,
                    FrageZustand.Erledigt(
                        text = antwort.fehler?.let { "Fehlgeschlagen: $it" } ?: statusText(antwort.status),
                        erfolgreich = antwort.istAusgefuehrt,
                    ),
                )
                if (antwort.dateien.isNotEmpty()) {
                    _zustand.update { alt ->
                        alt.copy(
                            eintraege = alt.eintraege + AssistentEintrag(
                                schluessel = naechsterSchluessel("assistent"),
                                text = antwort.beschreibung,
                                dateien = antwort.dateien,
                            )
                        )
                    }
                }
            } catch (fehler: ApiFehler.Beschaeftigt) {
                // 409 heisst hier: schon beantwortet oder nach 24 h abgelaufen.
                // Kein Fehler fuer den Nutzer, nur ein veralteter Knopf.
                setzeFrageZustand(
                    id,
                    FrageZustand.Erledigt("Diese Frage ist nicht mehr offen.", erfolgreich = false),
                )
            } catch (fehler: ApiFehler) {
                setzeFrageZustand(id, FrageZustand.Offen)
                behandleFehler(fehler)
            }
        }
    }

    private fun setzeFrageZustand(id: String, neuerZustand: FrageZustand) {
        _zustand.update { alt ->
            alt.copy(
                eintraege = alt.eintraege.map {
                    if (it is FrageEintrag && it.id == id) it.copy(zustand = neuerZustand) else it
                }
            )
        }
    }

    // --- Text senden ---------------------------------------------------------

    /**
     * Wohin eine Nachricht gehoert - festgehalten, bevor gesendet wird. Wechselt
     * der Nutzer inzwischen das Gespraech, geht sie trotzdem dorthin, wo sie
     * geschrieben wurde.
     */
    private data class Ziel(
        val gespraechId: String?,
        val faden: String?,
        val vorspann: String?,
        /** Kennung beim Server (API.md 1b) - dieselbe fuer jeden Versuch, auch aus dem Korb. */
        val anfrageId: String = AssistentRepository.neueAnfrageId(),
        val bilder: List<String> = emptyList(),
    ) {
        fun mitVorspann(text: String) = listOfNotNull(vorspann, text).joinToString("\n\n")
    }

    private fun ziel(vorspann: String?): Ziel {
        val z = _zustand.value
        val faden = if (z.gespraechId == null) z.faden ?: UUID.randomUUID().toString() else null
        return Ziel(z.gespraechId, faden, vorspann)
    }

    /**
     * Ohne Netz geht nichts verloren: die Nachricht wandert in den [Ausgangskorb]
     * und steht im Chat als "wartet". Ebenso bei 401 - sonst waere sie mit dem
     * Ruecksprung zur Anmeldung weg. Lehnt der Server dagegen ab oder ist kurz
     * nicht erreichbar, kommt der Text zurueck ins Eingabefeld.
     */
    fun senden() {
        val jetzt = _zustand.value
        if (!jetzt.kannSenden) return
        val text = jetzt.entwurf.trim()
        val schluessel = naechsterSchluessel("nutzer")
        val meine = sitzung
        // Der Nutzer sieht oben seinen eigenen Wortlaut; an den Server geht er
        // mit vorangestellter Bezugszeile.
        val ziel = ziel(vorspann())

        _zustand.update {
            it.copy(
                eintraege = it.eintraege + NutzerEintrag(schluessel, text),
                entwurf = "",
                laeuft = true,
                fehler = null,
            )
        }

        viewModelScope.launch {
            try {
                // Wartet in diesem Gespraech noch etwas, darf die neue Nachricht
                // nicht vorbeiziehen - sonst stimmt die Reihenfolge nicht.
                if (withContext(Dispatchers.IO) { ausgangskorb.wartetIn(ziel.gespraechId, ziel.faden) }) {
                    textEinreihen(schluessel, text, ziel, meine)
                    return@launch
                }
                val antwort = repository.chatSicher(ziel.mitVorspann(text), ziel.gespraechId, ziel.anfrageId)
                if (meine == sitzung) verarbeiteAntwort(antwort)
                gespraecheLaden()
            } catch (abbruch: CancellationException) {
                // Der Chat wird abgeraeumt (etwa Abmeldung von einer anderen
                // Ansicht), waehrend die Anfrage noch lief. Ob sie ankam, weiss
                // niemand - lieber doppelt als verloren.
                withContext(NonCancellable) { textEinreihen(schluessel, text, ziel, meine) }
                throw abbruch
            } catch (fehler: ApiFehler) {
                if (fehler is ApiFehler.KeineVerbindung || fehler is ApiFehler.NichtAngemeldet) {
                    textEinreihen(schluessel, text, ziel, meine)
                    if (fehler is ApiFehler.NichtAngemeldet) behandleFehler(fehler)
                } else {
                    zurueckInsFeld(schluessel, text, meine)
                    behandleFehler(fehler)
                    // 409: im Gespraech laeuft noch etwas (anderes Geraet). Den
                    // Stand holen, statt nur zu melden.
                    if (fehler is ApiFehler.Beschaeftigt && meine == sitzung && ziel.gespraechId != null) {
                        _zustand.update { it.copy(laeuft = false) }
                        gespraechOeffnen(ziel.gespraechId)
                    }
                }
            } finally {
                if (meine == sitzung) _zustand.update { it.copy(laeuft = false) }
            }
        }
    }

    /** Text in den [Ausgangskorb]. Scheitert selbst das, kommt er zurueck ins Eingabefeld. */
    private suspend fun textEinreihen(schluessel: String, text: String, ziel: Ziel, meine: Int) {
        val name = withContext(Dispatchers.IO) {
            ausgangskorb.ablegenText(
                AusgangAuftrag(
                    art = AusgangAuftrag.ART_TEXT,
                    aufgenommen = System.currentTimeMillis(),
                    gespraech_id = ziel.gespraechId,
                    faden = ziel.faden,
                    vorspann = ziel.vorspann,
                    text = text,
                    anfrage_id = ziel.anfrageId,
                    bilder = ziel.bilder,
                )
            )
        }
        if (name == null) {
            zurueckInsFeld(schluessel, text, meine)
            fehlerZeigen(R.string.ausgang_nicht_gespeichert)
            return
        }
        VersandArbeiter.anstossen(anwendung)
        eingereiht(meine, ziel)
        _zustand.update { z ->
            z.copy(
                eintraege = z.eintraege.map { if (it.schluessel == schluessel && it is NutzerEintrag) it.copy(wartet = name) else it },
                fehler = anwendung.getString(R.string.ausgang_text_wartet),
            )
        }
    }

    /** Gemeinsam fuer Text und Sprache: Faden merken, Vorspann ist mitgegangen, Zaehler hoch. */
    private fun eingereiht(meine: Int, ziel: Ziel) {
        _zustand.update { z ->
            val hier = meine == sitzung
            z.copy(
                faden = if (hier && z.gespraechId == null) ziel.faden else z.faden,
                ungemeldeteDokumente = if (hier && ziel.vorspann != null) emptyList() else z.ungemeldeteDokumente,
                wartendeNotizen = z.wartendeNotizen + 1,
            )
        }
    }

    /** Blase wieder weg, Text zurueck ins Eingabefeld - vor einen schon getippten Entwurf. */
    private fun zurueckInsFeld(schluessel: String, text: String, meine: Int) {
        _zustand.update { z ->
            z.copy(
                eintraege = if (meine == sitzung) z.eintraege.filterNot { it.schluessel == schluessel } else z.eintraege,
                entwurf = listOf(text, z.entwurf.trim()).filter { it.isNotBlank() }.joinToString("\n"),
            )
        }
    }

    // --- Fotos und Videos ----------------------------------------------------

    /**
     * Die Kamera hat ein Foto oder Video geliefert. Ein getippter oder diktierter
     * Entwurf wird zur Unterschrift - so laesst sie sich vorher per Mikrofon
     * sprechen, wie `API.md` es vorschlaegt.
     */
    fun medienAufgenommen(datei: File, istVideo: Boolean, fragen: Boolean = false) {
        _zustand.update {
            it.copy(
                medium = MedienEntwurf(
                    datei = datei,
                    istVideo = istVideo,
                    fragen = fragen && !istVideo,
                    text = it.entwurf.trim(),
                    notiz = it.letzteMedienNotiz ?: it.kontextZiel,
                ),
                entwurf = "",
            )
        }
    }

    /**
     * Foto oder Video aus der Galerie. Die Auswahl liefert nur einen Uri, der
     * nach dem Zurueckkehren ungueltig werden kann - deshalb sofort in den
     * Cache kopieren und ab dann wie eine eigene Aufnahme behandeln.
     */
    fun ausGalerie(uri: Uri) {
        viewModelScope.launch {
            val aufloeser = anwendung.contentResolver
            val typ = aufloeser.getType(uri).orEmpty()
            val istVideo = typ.startsWith("video/")
            val endung = MimeTypeMap.getSingleton().getExtensionFromMimeType(typ)
                ?: if (istVideo) "mp4" else "jpg"

            val datei = withContext(Dispatchers.IO) {
                val ordner = File(anwendung.cacheDir, if (istVideo) "videos" else "fotos").apply { mkdirs() }
                val ziel = File(ordner, "galerie_${System.currentTimeMillis()}.$endung")
                try {
                    aufloeser.openInputStream(uri)!!.use { ein -> ziel.outputStream().use { ein.copyTo(it) } }
                    ziel
                } catch (abbruch: CancellationException) {
                    ziel.delete()
                    throw abbruch
                } catch (_: Exception) {
                    // Quelle weg oder Speicher voll - Meldung folgt unten.
                    ziel.delete()
                    null
                }
            }

            if (datei == null || datei.length() == 0L) {
                datei?.delete()
                _zustand.update { it.copy(fehler = anwendung.getString(R.string.galerie_unlesbar)) }
            } else {
                medienAufgenommen(datei, istVideo)
            }
        }
    }

    /**
     * Dokument aus der Dateiauswahl oder ueber "Teilen" aus einer anderen App.
     * Wie bei der Galerie sofort in den Cache kopieren - geteilte Uris gelten
     * oft nur, solange die Activity laeuft.
     */
    fun dokumentGewaehlt(uri: Uri) {
        // Ein offener Dialog haelt schon eine Aufnahme - die darf nicht
        // stillschweigend ersetzt werden.
        if (_zustand.value.medium != null) {
            fehlerZeigen(R.string.dokument_dialog_offen)
            return
        }
        viewModelScope.launch {
            val aufloeser = anwendung.contentResolver
            val ergebnis: Pair<File?, String>? = withContext(Dispatchers.IO) {
                var ziel: File? = null
                try {
                    val name = dokumentName(uri)
                    val groesse = aufloeser.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                        ?.use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }
                    // Vorab abfangen statt 50 MB zu kopieren und dann ein 413 zu bekommen.
                    if (groesse != null && groesse > AssistentRepository.MAX_DOKUMENT_BYTES) {
                        return@withContext null to name
                    }
                    val ordner = File(anwendung.cacheDir, "dokumente").apply { mkdirs() }
                    val endung = name.substringAfterLast('.', "").filter { it.isLetterOrDigit() }.ifEmpty { "bin" }
                    val neu = File(ordner, "dokument_${System.currentTimeMillis()}.$endung")
                    ziel = neu
                    // Ohne Groessenangabe der Quelle: nach der Grenze aufhoeren, statt
                    // einen beliebig grossen Strom in den Cache zu schreiben.
                    val kopiert = aufloeser.openInputStream(uri)!!.use { ein ->
                        neu.outputStream().use { aus -> kopierenBis(ein, aus, AssistentRepository.MAX_DOKUMENT_BYTES + 1) }
                    }
                    if (kopiert > AssistentRepository.MAX_DOKUMENT_BYTES) {
                        neu.delete()
                        null to name
                    } else {
                        neu to name
                    }
                } catch (abbruch: CancellationException) {
                    ziel?.delete()
                    throw abbruch
                } catch (_: Exception) {
                    ziel?.delete()
                    null
                }
            }

            val (datei, name) = ergebnis ?: (null to null)
            when {
                ergebnis == null || name == null -> fehlerZeigen(R.string.dokument_unlesbar)
                datei == null || datei.length() > AssistentRepository.MAX_DOKUMENT_BYTES -> {
                    datei?.delete()
                    fehlerZeigen(R.string.dokument_zu_gross)
                }
                datei.length() == 0L -> {
                    datei.delete()
                    fehlerZeigen(R.string.dokument_unlesbar)
                }
                else -> _zustand.update {
                    it.copy(
                        medium = MedienEntwurf(
                            datei = datei,
                            dokument = name,
                            text = it.entwurf.trim(),
                            notiz = it.letzteMedienNotiz ?: it.kontextZiel,
                        ),
                        entwurf = "",
                    )
                }
            }
        }
    }

    /** Kopiert hoechstens [grenze] Bytes und gibt zurueck, wie viele es waren. */
    private fun kopierenBis(ein: java.io.InputStream, aus: java.io.OutputStream, grenze: Long): Long {
        val puffer = ByteArray(64 * 1024)
        var gesamt = 0L
        while (gesamt < grenze) {
            val gelesen = ein.read(puffer, 0, minOf(puffer.size.toLong(), grenze - gesamt).toInt())
            if (gelesen < 0) break
            aus.write(puffer, 0, gelesen)
            gesamt += gelesen
        }
        return gesamt
    }

    /**
     * Anzeigename mit Endung. Fehlt die Endung, kommt sie aus dem Mime-Typ -
     * der Server erkennt den Typ nur am Namen.
     */
    private fun dokumentName(uri: Uri): String {
        val aufloeser = anwendung.contentResolver
        val angezeigt = aufloeser.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null }
            ?: uri.lastPathSegment?.substringAfterLast('/')
        val name = angezeigt?.substringAfterLast('/')?.trim()?.takeIf { it.isNotEmpty() } ?: "Dokument"
        if (name.contains('.')) return name
        val endung = aufloeser.getType(uri)?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        return if (endung != null) "$name.$endung" else name
    }

    /**
     * Nachricht aus einer anderen Ansicht (Angebot: "Antworten einarbeiten").
     * Das Angebot traegt keine `gespraech_id`, deshalb ein neues Gespraech; der
     * Text nennt den Titel, daran erkennt der Assistent das Angebot. Laeuft gerade
     * eine Anfrage, landet der Text nur im Eingabefeld, statt sie zu stoeren.
     */
    fun vonAussenSenden(text: String) {
        if (_zustand.value.laeuft || _zustand.value.nimmtAuf) {
            geteilterText(text)
            return
        }
        neuesGespraech()
        _zustand.update { it.copy(entwurf = text) }
        senden()
    }

    /** Text aus "Teilen" (Link, Mailausschnitt) - haengt sich an den Entwurf. */
    fun geteilterText(text: String) {
        val neu = text.trim().takeIf { it.isNotEmpty() } ?: return
        _zustand.update {
            it.copy(entwurf = if (it.entwurf.isBlank()) neu else it.entwurf.trimEnd() + "\n" + neu)
        }
    }

    private fun fehlerZeigen(text: Int) =
        _zustand.update { it.copy(fehler = anwendung.getString(text)) }

    fun medienTextGeaendert(text: String) =
        _zustand.update { it.copy(medium = it.medium?.copy(text = text, fehler = null)) }

    fun medienNotizWaehlen(notiz: String?) =
        _zustand.update { it.copy(medium = it.medium?.copy(notiz = notiz, fehler = null)) }

    fun medienFragenSetzen(fragen: Boolean) =
        _zustand.update { it.copy(medium = it.medium?.copy(fragen = fragen, fehler = null)) }

    fun medienAbschreibenWaehlen(wert: String) =
        _zustand.update { it.copy(medium = it.medium?.copy(abschreiben = wert)) }

    /** Verwirft die Aufnahme. Die Unterschrift wandert zurueck ins Eingabefeld. */
    fun medienVerwerfen() {
        val medium = _zustand.value.medium ?: return
        if (medium.arbeitet) return
        medium.datei.delete()
        _zustand.update { it.copy(medium = null, entwurf = medium.text) }
    }

    fun medienAblegen() {
        val medium = _zustand.value.medium ?: return
        if (medium.arbeitet) return
        when {
            medium.istDokument -> dokumentAblegen(medium)
            medium.istVideo -> videoAblegen(medium)
            medium.fragen -> fotoFragen(medium)
            else -> fotoAblegen(medium)
        }
    }

    /**
     * Foto an den Assistenten (`API.md` 4a): erst ablegen, dann mit dem Pfad in
     * `bilder` fragen. Das Foto landet also immer auch in der Notiz.
     *
     * Anders als beim reinen Ablegen gibt es hier keinen Korb: eine Frage, die
     * erst Stunden spaeter beantwortet wird, hilft vor dem Heizkessel niemandem.
     * Ohne Netz bleibt der Dialog offen, "Nur ablegen" reiht das Foto ein.
     * Aus demselben Grund stellt sich das Foto nicht hinter wartende Aufnahmen.
     */
    private fun fotoFragen(foto: MedienEntwurf) {
        val frage = foto.text.trim().ifBlank { anwendung.getString(R.string.foto_frage_vorgabe) }
        _zustand.update { it.copy(medium = foto.copy(arbeitet = true, fehler = null), laeuft = true) }

        viewModelScope.launch {
            val abgelegt = try {
                repository.foto(
                    bild = foto.datei,
                    notiz = foto.notiz,
                    // Die Frage ist keine Bildunterschrift - sie gehoert in den Chat.
                    text = "",
                    gespraechId = _zustand.value.gespraechId,
                )
            } catch (fehler: ApiFehler) {
                when (fehler) {
                    is ApiFehler.NichtAngemeldet -> behandleFehler(fehler)
                    is ApiFehler.KeineVerbindung -> mediumFehler(anwendung.getString(R.string.foto_frage_kein_netz))
                    else -> mediumFehler(fehler.anzeigetext)
                }
                _zustand.update { it.copy(laeuft = false) }
                return@launch
            }

            foto.datei.delete()
            val schluessel = naechsterSchluessel("nutzer")
            val meine = sitzung
            val ziel = ziel(vorspann()).copy(bilder = listOf(abgelegt.foto))
            _zustand.update {
                it.copy(
                    medium = null,
                    letzteMedienNotiz = abgelegt.notiz,
                    eintraege = it.eintraege +
                        HinweisEintrag(
                            schluessel = naechsterSchluessel("hinweis"),
                            text = anwendung.getString(R.string.foto_abgelegt, notizname(abgelegt.notiz)),
                        ) +
                        NutzerEintrag(
                            schluessel = schluessel,
                            text = frage,
                            dateien = listOf(abgelegt.foto),
                        ),
                )
            }

            try {
                val antwort = repository.chatSicher(ziel.mitVorspann(frage), ziel.gespraechId, ziel.anfrageId, ziel.bilder)
                if (meine == sitzung) verarbeiteAntwort(antwort)
                gespraecheLaden()
            } catch (abbruch: CancellationException) {
                withContext(NonCancellable) { textEinreihen(schluessel, frage, ziel, meine) }
                throw abbruch
            } catch (fehler: ApiFehler) {
                // Das Foto liegt schon in der Notiz. Die Frage geht ohne Netz in den
                // Korb (mit dem Foto in `bilder`), sonst bleibt sie als Meldung stehen.
                if (fehler is ApiFehler.KeineVerbindung || fehler is ApiFehler.NichtAngemeldet) {
                    textEinreihen(schluessel, frage, ziel, meine)
                }
                if (fehler !is ApiFehler.KeineVerbindung) behandleFehler(fehler)
            } finally {
                if (meine == sitzung) _zustand.update { it.copy(laeuft = false) }
            }
        }
    }

    /**
     * Fotos gehen direkt raus, wenn Netz da ist - das Ergebnis sieht man sofort.
     * Ohne Netz, oder wenn schon etwas wartet, wandern sie in den [Medienkorb].
     */
    private fun fotoAblegen(foto: MedienEntwurf) {
        _zustand.update { it.copy(medium = foto.copy(arbeitet = true, fehler = null), laeuft = true) }

        viewModelScope.launch {
            try {
                // Warten schon Aufnahmen, stellt sich das Foto hinten an. Direkt
                // gesendet kaeme es vor den aelteren an, und die Reihenfolge in
                // der Notiz stimmte nicht.
                if (withContext(Dispatchers.IO) { medienkorb.anzahl() } > 0) {
                    einreihen(foto, R.string.foto_hinten_an)
                    return@launch
                }
                val antwort = repository.foto(
                    bild = foto.datei,
                    notiz = foto.notiz,
                    text = foto.text,
                    gespraechId = _zustand.value.gespraechId,
                )
                foto.datei.delete()
                val name = notizname(antwort.notiz)
                val text = anwendung.getString(
                    if (antwort.notiz_neu) R.string.foto_abgelegt_neu else R.string.foto_abgelegt,
                    name,
                )
                _zustand.update {
                    it.copy(
                        medium = null,
                        letzteMedienNotiz = antwort.notiz,
                        eintraege = it.eintraege + HinweisEintrag(
                            schluessel = naechsterSchluessel("hinweis"),
                            text = text,
                            dateien = listOf(antwort.foto),
                        ),
                    )
                }
            } catch (fehler: ApiFehler.NichtAngemeldet) {
                behandleFehler(fehler)
            } catch (_: ApiFehler.KeineVerbindung) {
                einreihen(foto, R.string.foto_wartet)
            } catch (fehler: ApiFehler) {
                // Dialog bleibt offen: das Foto ist noch da, "Ablegen" versucht es erneut.
                mediumFehler(fehler.anzeigetext)
            } finally {
                _zustand.update { it.copy(laeuft = false) }
            }
        }
    }

    /**
     * Dokumente gehen wie Fotos direkt raus, wenn Netz da ist: ob der Server den
     * Text lesen kann (`lesbar`), soll man sofort sehen - und gleich danach
     * "Mach mir daraus ein Angebot" schreiben koennen. Ohne Netz, oder wenn schon
     * etwas wartet, wandern sie in den [Medienkorb].
     */
    private fun dokumentAblegen(dokument: MedienEntwurf) {
        val dateiname = dokument.dokument ?: return
        _zustand.update { it.copy(medium = dokument.copy(arbeitet = true, fortschritt = 0, fehler = null), laeuft = true) }

        viewModelScope.launch {
            try {
                if (withContext(Dispatchers.IO) { medienkorb.anzahl() } > 0) {
                    einreihen(dokument, R.string.dokument_hinten_an)
                    return@launch
                }
                val gespraechId = _zustand.value.gespraechId
                val antwort = repository.dokument(
                    datei = dokument.datei,
                    dateiname = dateiname,
                    notiz = dokument.notiz,
                    text = dokument.text,
                    gespraechId = gespraechId,
                    fortschritt = { prozent ->
                        _zustand.update { it.copy(medium = it.medium?.copy(fortschritt = prozent)) }
                    },
                )
                dokument.datei.delete()
                val name = notizname(antwort.notiz)
                _zustand.update {
                    it.copy(
                        medium = null,
                        letzteMedienNotiz = antwort.notiz,
                        ungemeldeteDokumente = if (gespraechId == null) {
                            it.ungemeldeteDokumente + antwort.dokument
                        } else {
                            it.ungemeldeteDokumente
                        },
                        eintraege = it.eintraege + buildList {
                            add(
                                HinweisEintrag(
                                    schluessel = naechsterSchluessel("hinweis"),
                                    text = anwendung.getString(
                                        if (antwort.notiz_neu) R.string.dokument_abgelegt_neu else R.string.dokument_abgelegt,
                                        name,
                                    ),
                                    dateien = listOf(antwort.dokument),
                                )
                            )
                            if (!antwort.lesbar) {
                                add(
                                    HinweisEintrag(
                                        schluessel = naechsterSchluessel("hinweis"),
                                        text = antwort.hinweis.ifBlank { anwendung.getString(R.string.dokument_nicht_lesbar) },
                                    )
                                )
                            }
                        },
                    )
                }
            } catch (fehler: ApiFehler.NichtAngemeldet) {
                behandleFehler(fehler)
            } catch (_: ApiFehler.KeineVerbindung) {
                einreihen(dokument, R.string.dokument_wartet)
            } catch (fehler: ApiFehler) {
                // 415 sagt im detail schon, was zu tun ist ("bitte als PDF speichern").
                mediumFehler(fehler.anzeigetext)
            } finally {
                _zustand.update { it.copy(laeuft = false) }
            }
        }
    }

    /**
     * Videos werden erst auf 720p verkleinert und dann **immer** eingereiht.
     * Den Upload uebernimmt der [VersandArbeiter] - bei 50-100 MB soll niemand
     * mit offener App warten muessen.
     */
    @androidx.annotation.OptIn(UnstableApi::class)
    private fun videoAblegen(video: MedienEntwurf) {
        _zustand.update { it.copy(medium = video.copy(arbeitet = true, fortschritt = 0, fehler = null)) }

        viewModelScope.launch {
            val masse = withContext(Dispatchers.IO) { VideoAufbereitung.masse(video.datei) }
            if (masse != null && masse.dauerMs > MAX_VIDEO_MS) {
                mediumFehler(anwendung.getString(R.string.video_zu_lang))
                return@launch
            }

            val klein = try {
                VideoAufbereitung.verkleinern(anwendung, video.datei) { prozent ->
                    _zustand.update { it.copy(medium = it.medium?.copy(fortschritt = prozent)) }
                }
            } catch (abbruch: CancellationException) {
                throw abbruch
            } catch (_: Exception) {
                // Manche Geraete koennen bestimmte Formate nicht umrechnen. Dann
                // geht das Original raus - groesser, aber besser als gar nicht.
                video.datei
            }

            if (klein.length() > AssistentRepository.MAX_VIDEO_BYTES) {
                if (klein != video.datei) klein.delete()
                mediumFehler(anwendung.getString(R.string.video_zu_gross))
                return@launch
            }

            val eingereiht = einreihen(video.copy(datei = klein), R.string.video_eingereiht)
            // Das Original braucht es nur, solange der Dialog noch offen ist.
            if (klein != video.datei) (if (eingereiht) video.datei else klein).delete()
        }
    }

    /**
     * Legt die Aufnahme in den [Medienkorb] und schliesst den Dialog. Ohne feste
     * Notiz bekommt sie die laufende Serie, damit alles aus einer Begehung in
     * derselben Notiz landet - auch wenn es erst am naechsten Morgen rausgeht.
     */
    private suspend fun einreihen(medium: MedienEntwurf, meldung: Int): Boolean {
        val serie = if (medium.notiz == null) {
            _zustand.value.medienSerie ?: UUID.randomUUID().toString()
        } else {
            null
        }
        val auftrag = MedienAuftrag(
            art = when {
                medium.istDokument -> MedienAuftrag.ART_DOKUMENT
                medium.istVideo -> MedienAuftrag.ART_VIDEO
                else -> MedienAuftrag.ART_FOTO
            },
            dateiname = medium.dokument,
            aufgenommen = System.currentTimeMillis(),
            notiz = medium.notiz,
            text = medium.text.trim(),
            gespraech_id = _zustand.value.gespraechId,
            serie = serie,
            abschreiben = medium.abschreiben.takeIf { medium.istVideo },
        )

        val (abgelegt, wartend) = withContext(Dispatchers.IO) {
            // Fotos werden vor dem Ablegen verkleinert - im Korb liegt nur, was
            // spaeter genau so hochgeladen wird.
            val datei = if (medium.istVideo || medium.istDokument) {
                medium.datei
            } else {
                FotoAufbereitung.verkleinern(medium.datei)
            }
            val ok = medienkorb.ablegen(datei, auftrag)
            // Gescheitert: die verkleinerte Kopie weg, das Original bleibt im Dialog.
            if (datei != medium.datei) (if (ok) medium.datei else datei).delete()
            ok to medienkorb.anzahl()
        }
        if (!abgelegt) {
            mediumFehler(anwendung.getString(R.string.medium_nicht_gespeichert))
            return false
        }

        VersandArbeiter.anstossen(anwendung)
        _zustand.update {
            it.copy(
                medium = null,
                medienSerie = serie ?: it.medienSerie,
                wartendeMedien = wartend,
                eintraege = it.eintraege + HinweisEintrag(
                    schluessel = naechsterSchluessel("hinweis"),
                    text = anwendung.getString(meldung),
                ),
            )
        }
        return true
    }

    private fun mediumFehler(text: String) = _zustand.update {
        it.copy(medium = it.medium?.copy(arbeitet = false, fortschritt = null, fehler = text))
    }

    private fun notizname(pfad: String) = pfad.substringAfterLast('/').removeSuffix(".md")

    /**
     * Verfolgt den [VersandArbeiter]: Fortschritt des laufenden Video-Uploads und
     * eine Hinweiszeile, sobald nachgesendete Aufnahmen angekommen sind.
     */
    private fun versandBeobachten() {
        val erledigt = mutableSetOf<UUID>()
        var erstesMal = true

        viewModelScope.launch {
            WorkManager.getInstance(anwendung)
                .getWorkInfosForUniqueWorkFlow(VersandArbeiter.NAME)
                .collect { infos ->
                    val laufend = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }
                    val prozent = laufend?.progress
                        ?.getInt(VersandArbeiter.FORTSCHRITT_PROZENT, -1)
                        ?.takeIf { it >= 0 }

                    val neu = infos.filter { it.state == WorkInfo.State.SUCCEEDED && it.id !in erledigt }
                    erledigt += neu.map { it.id }
                    // Was vor dem Start dieses Bildschirms fertig wurde, ist alt.
                    val melden = if (erstesMal) emptyList() else neu
                    erstesMal = false

                    val hinweise = melden.flatMap { versandHinweise(it) }
                    val notiz = melden.mapNotNull { it.outputData.getString(VersandArbeiter.ERGEBNIS_NOTIZ) }
                        .lastOrNull()
                    val korbLeer = withContext(Dispatchers.IO) { medienkorb.anzahl() == 0 }

                    _zustand.update {
                        it.copy(
                            hochladen = prozent,
                            eintraege = it.eintraege + hinweise,
                            // Ist alles draussen, steht die Notiz fest: Folgefotos
                            // gehen direkt dorthin, eine neue Serie beginnt.
                            letzteMedienNotiz = if (notiz != null && korbLeer) notiz else it.letzteMedienNotiz,
                            medienSerie = if (notiz != null && korbLeer) null else it.medienSerie,
                        )
                    }
                    // Jeder Zustandswechsel des Versands - auch ein "nochmal
                    // spaeter" nach halb erledigtem Korb - kann Nachrichten
                    // zugestellt oder Diktate zurueckgebracht haben.
                    ausgangAbgleichen()
                }
        }
    }

    private fun versandHinweise(info: WorkInfo): List<HinweisEintrag> {
        val daten = info.outputData
        val notiz = daten.getString(VersandArbeiter.ERGEBNIS_NOTIZ) ?: return emptyList()
        val name = notizname(notiz)
        val fotos = daten.getInt(VersandArbeiter.ERGEBNIS_FOTOS, 0)
        val videos = daten.getInt(VersandArbeiter.ERGEBNIS_VIDEOS, 0)
        val abschriften = daten.getInt(VersandArbeiter.ERGEBNIS_ABSCHRIFTEN, 0)
        val dokumente = daten.getInt(VersandArbeiter.ERGEBNIS_DOKUMENTE, 0)
        val unlesbar = daten.getString(VersandArbeiter.ERGEBNIS_UNLESBAR)

        return buildList {
            if (videos > 0) {
                val text = anwendung.resources.getQuantityString(
                    if (abschriften > 0) R.plurals.videos_abgelegt_abschrift else R.plurals.videos_abgelegt,
                    videos, videos, name,
                )
                add(HinweisEintrag(naechsterSchluessel("hinweis"), text))
            }
            if (fotos > 0) {
                val text = anwendung.resources.getQuantityString(R.plurals.fotos_nachgesendet, fotos, fotos, name)
                add(HinweisEintrag(naechsterSchluessel("hinweis"), text))
            }
            if (dokumente > 0) {
                val text = anwendung.resources.getQuantityString(R.plurals.dokumente_nachgesendet, dokumente, dokumente, name)
                add(HinweisEintrag(naechsterSchluessel("hinweis"), text))
            }
            if (unlesbar != null) {
                add(
                    HinweisEintrag(
                        naechsterSchluessel("hinweis"),
                        unlesbar.ifBlank { anwendung.getString(R.string.dokument_nicht_lesbar) },
                    )
                )
            }
        }
    }

    // --- Sprache -------------------------------------------------------------

    /**
     * Start und Stopp der Aufnahme in einem Knopf. Beim Stopp geht die Aufnahme
     * direkt an `POST /v1/sprache`; der erkannte Text erscheint als Nutzerzeile.
     *
     * Die Berechtigung RECORD_AUDIO holt der Bildschirm vorher ein.
     */
    fun aufnahmeUmschalten() {
        if (_zustand.value.nimmtAuf) aufnahmeBeenden() else aufnahmeStarten()
    }

    private fun aufnahmeStarten() {
        if (!_zustand.value.kannAufnehmen) return
        sprechenBeenden()
        try {
            aufnahme.starten()
            _zustand.update { it.copy(nimmtAuf = true, liestVor = null, fehler = null) }
        } catch (_: Exception) {
            // Mikrofon belegt, Berechtigung fehlt, Geraet verweigert die Aufnahme.
            _zustand.update {
                it.copy(nimmtAuf = false, fehler = "Die Aufnahme konnte nicht gestartet werden.")
            }
        }
    }

    /**
     * Die App ist waehrend der Aufnahme in den Hintergrund geraten (Bildschirm
     * gesperrt, Anruf, App gewechselt). Ab Android 11 liefert das Mikrofon dann
     * nur noch Stille - weiterlaufen zu lassen hiesse, der Nutzer spricht ins
     * Leere, ohne es zu merken.
     *
     * Was bis dahin gesagt wurde, bleibt erhalten, landet aber immer im
     * Eingabefeld statt rauszugehen: der Nutzer hat die Aufnahme nicht selbst
     * beendet und soll sehen, wo sie abgeschnitten wurde.
     */
    fun aufnahmeUnterbrochen() {
        if (!_zustand.value.nimmtAuf) return
        _zustand.update {
            it.copy(
                eintraege = it.eintraege + HinweisEintrag(
                    schluessel = naechsterSchluessel("hinweis"),
                    text = "Aufnahme beendet, weil die App in den Hintergrund ging. " +
                        "Was bis dahin gesagt wurde, steht im Eingabefeld.",
                )
            )
        }
        aufnahmeBeenden(unterbrochen = true)
    }

    private fun aufnahmeBeenden(unterbrochen: Boolean = false) {
        val datei = aufnahme.beenden()
        _zustand.update { it.copy(nimmtAuf = false) }

        if (datei == null) {
            _zustand.update { it.copy(fehler = "Die Aufnahme war zu kurz.") }
            return
        }

        val pruefen = _zustand.value.diktatPruefen || unterbrochen
        val hatKontext = _zustand.value.kontext != null

        // Ein gesetzter Kontext muss auch fuer Sprache gelten. Dafuer muss der
        // Text erst zu uns zurueck, damit die Bezugszeile davorkommt - sonst
        // waere der angeheftete Kontext bei jeder Sprachnachricht wirkungslos.
        val nurErkennen = pruefen || hatKontext
        val ziel = ziel(if (hatKontext) vorspann() else null)
        val meine = sitzung
        val schluessel = naechsterSchluessel("nutzer")

        _zustand.update { it.copy(laeuft = true, fehler = null) }

        viewModelScope.launch {
            // Gesetzt, sobald die Erkennung durch ist - scheitert danach der Chat,
            // muss nicht noch einmal transkribiert werden.
            var erkannt: String? = null
            try {
                // Geht die Aufnahme direkt an den Assistenten, darf sie nicht an
                // wartenden Nachrichten dieses Gespraechs vorbeiziehen.
                if (!pruefen && withContext(Dispatchers.IO) { ausgangskorb.wartetIn(ziel.gespraechId, ziel.faden) }) {
                    spracheEinreihen(datei, ziel, antworten = true, meine = meine, schluessel = schluessel)
                    return@launch
                }

                // `antworten = false` laesst den Server nur transkribieren. Das ist
                // schneller und billiger, und vor allem kann er nichts ausfuehren,
                // was falsch verstanden wurde.
                val antwort = if (nurErkennen) {
                    repository.sprache(aufnahme = datei, gespraechId = ziel.gespraechId, antworten = false)
                } else {
                    // Mit Kennung: bricht die Verbindung ab, wird das Ergebnis
                    // abgeholt statt die Aufnahme ein zweites Mal auszufuehren.
                    repository.spracheSicher(datei, ziel.gespraechId, ziel.anfrageId)
                }

                if (!pruefen && hatKontext) {
                    // Kein Pruefschritt gewuenscht: erkannten Text ohne Zutun
                    // weiterschicken, nur eben mit Bezugszeile.
                    erkannt = antwort.text
                    if (meine == sitzung) {
                        _zustand.update {
                            it.copy(eintraege = it.eintraege + NutzerEintrag(schluessel, antwort.text))
                        }
                    }
                    val chat = repository.chatSicher(ziel.mitVorspann(antwort.text), ziel.gespraechId, ziel.anfrageId)
                    if (meine == sitzung) verarbeiteAntwort(chat)
                    gespraecheLaden()
                } else if (pruefen) {
                    // Erkannter Text landet im Eingabefeld. An einen bereits
                    // getippten Entwurf wird angehaengt, statt ihn zu verwerfen.
                    _zustand.update { alt ->
                        val vorhanden = alt.entwurf.trim()
                        val zusammen = if (vorhanden.isEmpty()) {
                            antwort.text
                        } else {
                            "$vorhanden ${antwort.text}"
                        }
                        alt.copy(entwurf = zusammen)
                    }
                } else if (antwort.text.isBlank() && antwort.chat != null) {
                    // Nach einem Abbruch abgeholt: den erkannten Text kennt nur der
                    // Verlauf, also das Gespraech neu laden.
                    if (meine == sitzung) {
                        _zustand.update { it.copy(laeuft = false) }
                        gespraechOeffnen(antwort.chat.gespraech_id)
                    }
                } else {
                    if (meine == sitzung) {
                        _zustand.update {
                            it.copy(eintraege = it.eintraege + NutzerEintrag(schluessel, antwort.text))
                        }
                        antwort.chat?.let { verarbeiteAntwort(it) }
                    }
                    gespraecheLaden()
                }
            } catch (abbruch: CancellationException) {
                // Wie bei [senden]: der Chat verschwindet mitten in der Anfrage.
                withContext(NonCancellable) {
                    val text = erkannt
                    if (text != null) {
                        textEinreihen(schluessel, text, ziel, meine)
                    } else {
                        spracheEinreihen(datei, ziel, antworten = !pruefen, meine = meine, schluessel = schluessel)
                    }
                }
                throw abbruch
            } catch (fehler: ApiFehler) {
                val text = erkannt
                val einreihen = fehler is ApiFehler.KeineVerbindung || fehler is ApiFehler.NichtAngemeldet
                when {
                    // Erkannt, aber der Chat kam nicht durch: ab hier ist es Text.
                    text != null && einreihen -> textEinreihen(schluessel, text, ziel, meine)
                    text != null -> zurueckInsFeld(schluessel, text, meine)
                    // Der Gedanke soll nicht verlorengehen, nur weil im Keller kein
                    // Netz ist oder der Server kurz weg. Die Aufnahme wandert
                    // verschluesselt in den Korb - im Pruefmodus nur zum Abschreiben.
                    fehler.istVoruebergehend ->
                        spracheEinreihen(datei, ziel, antworten = !pruefen, meine = meine, schluessel = schluessel)
                }
                // Gemeldet wird trotzdem, ausser beim reinen Funkloch - das sagt
                // schon die Zeile im Chat.
                if (fehler !is ApiFehler.KeineVerbindung) behandleFehler(fehler)
            } finally {
                datei.delete()
                if (meine == sitzung) _zustand.update { it.copy(laeuft = false) }
            }
        }
    }

    /**
     * Aufnahme in den [Ausgangskorb]. Mit [antworten] geht sie spaeter an den
     * Assistenten und steht bis dahin als "wartet" im Chat; ohne wird sie nur
     * abgeschrieben und kommt als Diktat zum Pruefen zurueck.
     */
    private suspend fun spracheEinreihen(datei: File, ziel: Ziel, antworten: Boolean, meine: Int, schluessel: String) {
        val name = withContext(Dispatchers.IO) {
            ausgangskorb.ablegen(
                datei,
                AusgangAuftrag(
                    aufgenommen = System.currentTimeMillis(),
                    gespraech_id = ziel.gespraechId.takeIf { antworten },
                    faden = ziel.faden.takeIf { antworten },
                    antworten = antworten,
                    vorspann = ziel.vorspann.takeIf { antworten },
                    anfrage_id = ziel.anfrageId.takeIf { antworten },
                ),
            )
        }
        if (name == null) {
            fehlerZeigen(R.string.ausgang_aufnahme_nicht_gespeichert)
            return
        }
        VersandArbeiter.anstossen(anwendung)
        eingereiht(meine, if (antworten) ziel else ziel.copy(faden = null, vorspann = null))
        _zustand.update { z ->
            z.copy(
                eintraege = when {
                    meine != sitzung -> z.eintraege
                    antworten -> z.eintraege + NutzerEintrag(
                        schluessel = schluessel,
                        text = anwendung.getString(R.string.ausgang_sprachnachricht),
                        wartet = name,
                    )
                    else -> z.eintraege + HinweisEintrag(
                        schluessel = naechsterSchluessel("hinweis"),
                        text = anwendung.getString(R.string.ausgang_diktat_wartet),
                    )
                },
            )
        }
    }

    /**
     * Liest eine Antwort vor, oder stoppt, wenn dieselbe gerade laeuft.
     *
     * Je nach Einstellung spricht der Server (`POST /v1/vorlesen`, Stimme und
     * Modell dort konfiguriert) oder das Geraet. Scheitert der Server, wird nicht
     * stumm abgebrochen, sondern auf die Geraetestimme zurueckgefallen - lieber
     * eine schlichtere Stimme als gar keine.
     */
    fun vorlesenUmschalten(eintrag: AssistentEintrag) {
        if (_zustand.value.liestVor == eintrag.schluessel) {
            sprechenBeenden()
            return
        }

        sprechenBeenden()
        val text = markdownEntfernen(eintrag.text)
        if (text.isBlank()) return

        _zustand.update { it.copy(liestVor = eintrag.schluessel) }

        if (_zustand.value.serverStimme) {
            vorlesenJob = viewModelScope.launch {
                try {
                    val mp3 = repository.vorlesen(text, _zustand.value.stimme)
                    // Inzwischen gestoppt oder eine Aufnahme gestartet: nicht mehr abspielen.
                    if (_zustand.value.liestVor != eintrag.schluessel) {
                        mp3.delete()
                        return@launch
                    }
                    tonwiedergabe.abspielen(mp3) { vorlesenBeendet(eintrag.schluessel) }
                } catch (fehler: ApiFehler.NichtAngemeldet) {
                    vorlesenBeendet(eintrag.schluessel)
                    behandleFehler(fehler)
                } catch (fehler: ApiFehler) {
                    // 422 heisst hier: der Server kennt diese Stimme nicht. Das ist
                    // ein Einstellungsfehler und darf nicht stumm verschluckt werden -
                    // gesprochen wird trotzdem, nur eben vom Geraet.
                    if (fehler is ApiFehler.Ungueltig) {
                        _zustand.update { it.copy(fehler = fehler.anzeigetext) }
                    }
                    geraetestimme(eintrag.schluessel, text)
                }
            }
        } else {
            geraetestimme(eintrag.schluessel, text)
        }
    }

    private fun geraetestimme(schluessel: String, text: String) {
        if (!sprachausgabe.verfuegbar) {
            vorlesenBeendet(schluessel)
            _zustand.update {
                it.copy(fehler = "Auf diesem Gerät ist keine deutsche Sprachausgabe eingerichtet.")
            }
            return
        }
        sprachausgabe.vorlesen(text) { vorlesenBeendet(schluessel) }
    }

    private fun vorlesenBeendet(schluessel: String) {
        _zustand.update { alt ->
            if (alt.liestVor == schluessel) alt.copy(liestVor = null) else alt
        }
    }

    private fun sprechenBeenden() {
        vorlesenJob?.cancel()
        vorlesenJob = null
        sprachausgabe.stoppen()
        tonwiedergabe.stoppen()
        _zustand.update { it.copy(liestVor = null) }
    }

    /**
     * Waehlt die Stimme fuers Vorlesen. Bewusst eine explizite Wahl statt eines
     * Umschaltens: der Nutzer soll beide Moeglichkeiten sehen, nicht nur die
     * gerade aktive.
     */
    /**
     * Waehlt die OpenAI-Stimme. `null` ueberlaesst sie der Servervorgabe aus
     * `STIMME` in `assistent.env`.
     */
    fun openAiStimmeWaehlen(name: String?) {
        if (_zustand.value.stimme == name) return
        sprechenBeenden()
        repository.einstellungen.stimme = name
        _zustand.update { it.copy(stimme = name) }
    }

    fun diktatPruefenSetzen(pruefen: Boolean) {
        if (_zustand.value.diktatPruefen == pruefen) return
        repository.einstellungen.diktatPruefen = pruefen
        _zustand.update { it.copy(diktatPruefen = pruefen) }
    }

    fun stimmeWaehlen(server: Boolean) {
        if (_zustand.value.serverStimme == server) return
        sprechenBeenden()
        repository.einstellungen.serverStimme = server
        _zustand.update { it.copy(serverStimme = server) }
    }

    // --- Gespraeche ----------------------------------------------------------

    fun gespraecheLaden() {
        viewModelScope.launch {
            _zustand.update { it.copy(gespraecheLaden = true) }
            try {
                val liste = repository.gespraeche()
                _zustand.update { it.copy(gespraeche = liste) }
            } catch (fehler: ApiFehler) {
                behandleFehler(fehler)
            } finally {
                _zustand.update { it.copy(gespraecheLaden = false) }
            }
        }
    }

    /**
     * Laedt ein frueheres Gespraech in den Chat.
     *
     * Die Bestaetigungen im Verlauf sind meist laengst beantwortet. Knoepfe
     * bekommt nur, was laut `GET /v1/bestaetigungen` noch offen ist - alles
     * andere wird als Systemzeile gezeigt, damit niemand auf einen toten Knopf tippt.
     */
    fun gespraechOeffnen(id: String) {
        if (_zustand.value.nimmtAuf) {
            fehlerZeigen(R.string.chat_erst_aufnahme_beenden)
            return
        }
        sprechenBeenden()
        val meine = ++sitzung
        _zustand.update { it.copy(laeuft = true, fehler = null) }

        viewModelScope.launch {
            try {
                // Beides parallel: der Verlauf allein sagt nicht, welche Rueckfrage
                // noch offen ist. In einem eigenen Bereich, damit ein Fehler des
                // einen als ApiFehler hier ankommt und nicht den ganzen
                // viewModelScope-Job abbricht (das brachte die App zum Absturz).
                val (nachrichten, offene) = coroutineScope {
                    val verlaufLaeuft = async { repository.verlauf(id) }
                    val offeneLaeuft = async { repository.offeneBestaetigungen() }
                    verlaufLaeuft.await() to offeneLaeuft.await()
                }
                val offeneIds = offene.map { it.id }.toSet()
                // Was in diesem Gespraech noch auf Netz wartet, kennt der Server
                // nicht - es haengt sich als "wartet" hinten an.
                val wartend = withContext(Dispatchers.IO) {
                    ausgangskorb.liste().filter { it.auftrag.gespraech_id == id && it.auftrag.antworten }
                }

                val eintraege = nachrichten.flatMap { nachricht ->
                    buildList {
                        when (nachricht.rolle) {
                            Nachricht.ROLLE_NUTZER -> add(
                                NutzerEintrag(naechsterSchluessel("nutzer"), nachricht.text, nachricht.dateien)
                            )

                            Nachricht.ROLLE_HINWEIS -> add(
                                HinweisEintrag(
                                    schluessel = naechsterSchluessel("hinweis"),
                                    text = nachricht.text,
                                    dateien = nachricht.dateien,
                                )
                            )

                            else -> add(
                                AssistentEintrag(
                                    schluessel = naechsterSchluessel("assistent"),
                                    text = nachricht.text,
                                    dateien = nachricht.dateien,
                                    links = nachricht.links,
                                    quellen = nachricht.quellen,
                                )
                            )
                        }

                        nachricht.bestaetigungen.forEach { frage ->
                            if (frage.id in offeneIds) {
                                add(
                                    FrageEintrag(
                                        schluessel = naechsterSchluessel("frage"),
                                        id = frage.id,
                                        beschreibung = frage.beschreibung,
                                        werkzeug = frage.werkzeug,
                                    )
                                )
                            } else {
                                add(
                                    HinweisEintrag(
                                        schluessel = naechsterSchluessel("hinweis"),
                                        text = "Rückfrage: ${frage.beschreibung}",
                                    )
                                )
                            }
                        }
                    }
                }

                val wartendeEintraege = wartend.map {
                    NutzerEintrag(
                        schluessel = naechsterSchluessel("nutzer"),
                        text = it.auftrag.text ?: anwendung.getString(R.string.ausgang_sprachnachricht),
                        wartet = it.name,
                    )
                }

                if (meine != sitzung) return@launch
                _zustand.update { it.copy(gespraechId = id, faden = null, eintraege = eintraege + wartendeEintraege) }
            } catch (fehler: ApiFehler) {
                if (meine == sitzung) behandleFehler(fehler)
            } finally {
                if (meine == sitzung) _zustand.update { it.copy(laeuft = false) }
            }
        }
    }

    fun gespraechLoeschen(id: String) {
        viewModelScope.launch {
            try {
                repository.gespraechLoeschen(id)
                _zustand.update { alt ->
                    val neu = alt.copy(gespraeche = alt.gespraeche.filterNot { it.id == id })
                    // Das offene Gespraech wurde geloescht - Chat leeren.
                    if (alt.gespraechId == id) neu.copy(gespraechId = null, eintraege = emptyList()) else neu
                }
            } catch (fehler: ApiFehler) {
                behandleFehler(fehler)
            }
        }
    }

    // --- Dateien -------------------------------------------------------------

    /** Laedt eine Datei aus `dateien` und meldet sie zum Oeffnen zurueck. */
    fun dateiOeffnen(pfad: String, beiErfolg: (File) -> Unit) {
        viewModelScope.launch {
            try {
                beiErfolg(repository.dateiLaden(pfad))
            } catch (fehler: ApiFehler) {
                behandleFehler(fehler)
            }
        }
    }

    /**
     * Bild fuer ein Vorschaubild. `null`, wenn es nicht zu laden war - dann
     * zeigt die Oberflaeche stattdessen den Datei-Chip. Bewusst ohne
     * Fehlermeldung: ein fehlendes Vorschaubild ist kein Grund fuer ein Popup.
     */
    suspend fun vorschauLaden(pfad: String): File? = try {
        repository.vorschauLaden(pfad)
    } catch (fehler: ApiFehler.NichtAngemeldet) {
        behandleFehler(fehler)
        null
    } catch (_: ApiFehler) {
        null
    }

    // --- Gemeinsames ---------------------------------------------------------

    private fun verarbeiteAntwort(antwort: ChatAntwort) {
        // Ein Auftrag startet im Chat, und die Antwort sagt nur "laeuft ...".
        // Einmal nachsehen, damit Zaehler und Abfrage ihn sofort kennen.
        if (auftragsAbfrage != null) viewModelScope.launch { auftraegeAbfragen() }
        viewModelScope.launch { aufgabenZaehlen() }
        // Die Antwort kam an, also auch die Nennung der Dokumente. Ab jetzt gibt es
        // eine gespraech_id, und weitere Dokumente meldet der Server selbst.
        _zustand.update { it.copy(ungemeldeteDokumente = emptyList()) }

        _zustand.update { alt ->
            val ausgefuehrteIds = antwort.ausgefuehrt.associateBy { it.id }

            // Hat der Nutzer im Chat zugestimmt ("ja, mach"), fuehrt der Server die
            // Aktion selbst aus. Die Knoepfe zu dieser id muessen dann verschwinden.
            val bestehende = alt.eintraege.map { eintrag ->
                val treffer = (eintrag as? FrageEintrag)?.let { ausgefuehrteIds[it.id] }
                if (eintrag is FrageEintrag && treffer != null) {
                    eintrag.copy(
                        zustand = FrageZustand.Erledigt(
                            text = statusText(treffer.status),
                            erfolgreich = treffer.status == EntscheidungAntwort.STATUS_AUSGEFUEHRT,
                        )
                    )
                } else {
                    eintrag
                }
            }

            val schonBekannt = alt.eintraege.filterIsInstance<FrageEintrag>().map { it.id }.toSet()
            val neu = buildList {
                add(
                    AssistentEintrag(
                        schluessel = naechsterSchluessel("assistent"),
                        text = antwort.antwort,
                        dateien = antwort.dateien,
                        links = antwort.links,
                        quellen = antwort.quellen,
                    )
                )
                // Ausgefuehrtes ohne zugehoerige Frage in der Liste (z.B. aus einer
                // frueheren Sitzung) wird als Systemzeile sichtbar gemacht.
                antwort.ausgefuehrt.filterNot { it.id in schonBekannt }.forEach {
                    add(
                        HinweisEintrag(
                            schluessel = naechsterSchluessel("hinweis"),
                            text = "${statusText(it.status)}: ${it.beschreibung}",
                        )
                    )
                }
                antwort.bestaetigungen.forEach {
                    add(
                        FrageEintrag(
                            schluessel = naechsterSchluessel("frage"),
                            id = it.id,
                            beschreibung = it.beschreibung,
                            werkzeug = it.werkzeug,
                        )
                    )
                }
            }

            alt.copy(
                gespraechId = antwort.gespraech_id,
                eintraege = bestehende + neu,
            )
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

    private fun statusText(status: String): String = when (status) {
        EntscheidungAntwort.STATUS_AUSGEFUEHRT -> "Ausgeführt"
        EntscheidungAntwort.STATUS_ABGELEHNT -> "Abgelehnt"
        EntscheidungAntwort.STATUS_FEHLGESCHLAGEN -> "Fehlgeschlagen"
        else -> status
    }

    companion object {
        private const val S_ENTWURF = "entwurf"
        private const val S_GESPRAECH = "gespraech"
        private const val S_FADEN = "faden"
        private const val S_MEDIUM = "medium"
        private const val S_MEDIUM_VIDEO = "medium_video"
        private const val S_MEDIUM_DOKUMENT = "medium_dokument"
        private const val S_MEDIUM_FRAGEN = "medium_fragen"
        private const val S_MEDIUM_TEXT = "medium_text"
        private const val S_MEDIUM_NOTIZ = "medium_notiz"
        private const val S_MEDIUM_ABSCHREIBEN = "medium_abschreiben"

        /** Grenze aus dem Vertrag: hoechstens 30 Minuten. */
        private const val MAX_VIDEO_MS = 30L * 60 * 1000

        /** `API.md`: 15-30 s. Im Chat das obere Ende, die Auftragsansicht fragt oefter. */
        private const val AUFTRAG_ABFRAGE_MS = 30_000L

        fun factory(repository: AssistentRepository, context: Context) = viewModelFactory {
            initializer {
                ChatViewModel(
                    repository = repository,
                    aufnahme = AudioAufnahme(context.applicationContext),
                    sprachausgabe = Sprachausgabe(context.applicationContext),
                    tonwiedergabe = Tonwiedergabe(),
                    // Dieselben Koerbe wie der VersandArbeiter - die Sperren gelten pro Objekt.
                    ausgangskorb = (context.applicationContext as ChefAssistentApp).ausgangskorb,
                    medienkorb = (context.applicationContext as ChefAssistentApp).medienkorb,
                    anwendung = context.applicationContext,
                    gespeichert = createSavedStateHandle(),
                )
            }
        }
    }
}
