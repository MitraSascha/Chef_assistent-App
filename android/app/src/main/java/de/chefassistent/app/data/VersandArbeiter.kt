package de.chefassistent.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import de.chefassistent.app.R
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Schickt wartende Nachrichten (Sprache und Text), Fotos, Videos und Dokumente
 * ab, sobald Netz da ist.
 *
 * Videos laufen **immer** hier durch, nicht nur im Funkloch: der Upload von
 * 50-100 MB soll nicht davon abhaengen, dass jemand die App offen haelt.
 *
 * Laeuft unter WorkManager, damit der Versand auch dann noch passiert, wenn die
 * App laengst geschlossen ist - genau der Fall, wenn man im Keller aufnimmt und
 * erst auf dem Parkplatz wieder Empfang hat.
 *
 * **Grundsatz: nichts geht verloren, nur weil gerade kein Netz war.**
 * - Voruebergehende Fehler ([ApiFehler.istVoruebergehend]) fuehren zu
 *   [Result.retry], der Eintrag bleibt liegen.
 * - Endgueltige Fehler verwerfen nur, was nachweislich nichts enthaelt (422 bei
 *   Sprache: keine Sprache erkannt) oder nie durchgehen kann (413). Alles andere
 *   wird beiseitegelegt bzw. als [Rueckmeldung] an die App zurueckgegeben -
 *   und in jedem Fall bekommt der Nutzer einen Hinweis.
 *
 * **Netz heisst nicht Tailscale.** Die Bedingung `CONNECTED` sagt nur, dass
 * irgendeine Verbindung besteht; ob der Server erreichbar ist, weiss erst der Aufruf.
 *
 * **Doppelt statt verloren:** Laeuft eine Anfrage in den Lese-Timeout, nachdem der
 * Server sie schon hatte, geht sie spaeter noch einmal raus. Ein doppelter Eintrag
 * ist der bessere Tausch als ein verlorener.
 */
class VersandArbeiter(
    kontext: Context,
    parameter: WorkerParameters,
) : CoroutineWorker(kontext, parameter) {

    override suspend fun doWork(): Result {
        val anwendung = applicationContext as? Koerbe ?: return Result.failure()
        val repository = anwendung.repository

        if (!repository.einstellungen.istAngemeldet) {
            // Ohne Token waere jeder Versuch ein 401. Alles bleibt liegen; der Chat
            // stoesst den Versand nach der naechsten Anmeldung wieder an.
            return Result.failure()
        }

        kopienAufraeumen()
        nachrichtenSenden(repository, anwendung.ausgangskorb, anwendung.medienkorb)?.let { return it }
        val medien = medienSenden(repository, anwendung.medienkorb, anwendung.ausgangskorb)
        // Fragen zu Fotos, die gerade angekommen sind (1.35.0): erst das Foto, dann die Frage.
        return nachrichtenSenden(repository, anwendung.ausgangskorb, anwendung.medienkorb) ?: medien
    }

    /**
     * Entschluesselte Kopien im Cache, die ein abgebrochener frueherer Lauf
     * liegen liess (Prozess beendet, bevor `finally` lief). Die verschluesselten
     * Originale liegen weiter im Korb - im Klartext soll nichts herumliegen.
     */
    private fun kopienAufraeumen() {
        val grenze = System.currentTimeMillis() - KOPIE_ALT_MS
        applicationContext.cacheDir.listFiles { datei ->
            (datei.name.startsWith("ausgang_") || datei.name.startsWith("medienausgang_")) &&
                datei.lastModified() < grenze
        }?.forEach { it.delete() }
    }

    /**
     * Grosse Uploads als Vordergrunddienst: sonst beendet Android den Auftrag nach
     * etwa 10 Minuten, und ein Video ueber Mobilfunk faenge immer wieder von vorn an.
     * Klappt das nicht (Android 12+ erlaubt es aus dem Hintergrund nicht immer),
     * geht es ohne weiter - schlimmstenfalls wie bisher.
     */
    private suspend fun imVordergrund(text: String) {
        try {
            setForeground(vordergrundInfo(text))
        } catch (_: Exception) {
            // ForegroundServiceStartNotAllowedException u. a.
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = vordergrundInfo("Wird gesendet …")

    private fun vordergrundInfo(text: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(KANAL, "Hochladen", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Fotos, Videos und Nachrichten, die nachgesendet werden"
                }
            )
        }
        val benachrichtigung = NotificationCompat.Builder(applicationContext, KANAL)
            .setSmallIcon(R.drawable.ic_hochladen)
            .setContentTitle("Chef-Assistent")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(0, 0, true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(BENACHRICHTIGUNG, benachrichtigung, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(BENACHRICHTIGUNG, benachrichtigung)
        }
    }

    /**
     * Sprachnotizen und Texte, aelteste zuerst. Die Liste wird jedes Mal neu
     * gelesen, weil eine angekommene Nachricht das Gespraech der folgenden
     * festlegt. `null` heisst: fertig, weiter mit den Medien.
     */
    private suspend fun nachrichtenSenden(repository: AssistentRepository, korb: Ausgangskorb, medien: Medienkorb): Result? {
        val erledigt = mutableSetOf<String>()
        // Gespraeche bzw. Faeden, in denen eine Frage auf ihr Foto wartet: was danach
        // kam, wartet mit, sonst stimmte die Reihenfolge im Gespraech nicht.
        val gesperrt = mutableSetOf<String>()

        while (true) {
            val offen = korb.liste().filter { it.name !in erledigt }
            var notiz = offen.firstOrNull() ?: return null
            val schluessel = notiz.auftrag.gespraech_id ?: notiz.auftrag.faden
            if (schluessel != null && schluessel in gesperrt) {
                erledigt += notiz.name
                continue
            }
            val fotoId = notiz.auftrag.wartet_auf_foto
            if (fotoId != null) {
                val pfad = korb.fotoZu(fotoId)
                if (pfad != null) {
                    // Schon angekommen, bevor die Frage im Korb lag.
                    korb.auftragAendern(
                        notiz.name,
                        notiz.auftrag.copy(wartet_auf_foto = null, bilder = (notiz.auftrag.bilder + pfad).distinct()),
                    )
                } else if (medien.liste().any { it.auftrag.upload_id == fotoId }) {
                    erledigt += notiz.name
                    schluessel?.let { gesperrt += it }
                } else {
                    // Das Foto ist weder unterwegs noch angekommen (verworfen, geloescht).
                    korb.fotoGescheitert(fotoId, "Das Foto zu dieser Frage wurde nicht abgelegt.")
                }
                continue
            }

            try {
                // Was an den Assistenten geht, bekommt vor dem ersten Versuch eine
                // feste Kennung (API.md 1b) - auch Eintraege aus aelteren Versionen.
                val geht = notiz.auftrag.art == AusgangAuftrag.ART_TEXT ||
                    (notiz.auftrag.antworten && notiz.auftrag.vorspann.isNullOrBlank())
                if (geht && notiz.auftrag.anfrage_id == null) {
                    val mitKennung = notiz.auftrag.copy(anfrage_id = AssistentRepository.neueAnfrageId())
                    korb.auftragAendern(notiz.name, mitKennung)
                    notiz = notiz.copy(auftrag = mitKennung)
                }
                val auftrag = notiz.auftrag
                val anfrageId = auftrag.anfrage_id

                // Erst fragen, ob der Server sie schon hat: ein frueherer Versuch
                // kann angekommen sein, ohne dass die Antwort zurueckkam.
                if (geht && anfrageId != null) {
                    val stand = repository.chatStatus(anfrageId)
                    when (stand?.status) {
                        null -> Unit // kam nie an - senden
                        ChatStatus.STATUS_FERTIG -> {
                            angekommen(korb, notiz, stand.gespraech_id ?: stand.antwort?.gespraech_id)
                            erledigt += notiz.name
                            continue
                        }
                        ChatStatus.STATUS_FEHLER -> {
                            serverFehler(korb, notiz, stand.fehler)
                            erledigt += notiz.name
                            continue
                        }
                        // Der Assistent arbeitet noch daran: spaeter wieder nachsehen.
                        else -> return Result.retry()
                    }
                }

                if (auftrag.art == AusgangAuftrag.ART_TEXT) {
                    val antwort = mitGespraech(korb, notiz) { gid ->
                        repository.chat(auftrag.chatText, gid, auftrag.bilder, anfrageId, auftrag.wahl)
                    }
                    angekommen(korb, notiz, antwort.gespraech_id)
                    erledigt += notiz.name
                    continue
                }

                val datei = when (val ausgepackt = korb.auspacken(notiz)) {
                    is Auspacken.Fertig -> ausgepackt.datei
                    Auspacken.KeinPlatz -> return Result.retry()
                    is Auspacken.Defekt -> {
                        korb.beiseitelegen(notiz.name)
                        korb.rueckmelden(
                            Rueckmeldung(
                                art = Rueckmeldung.ART_HINWEIS,
                                zeit = auftrag.aufgenommen,
                                text = "Eine Sprachnotiz von ${uhrzeit(auftrag.aufgenommen)} ließ sich nicht mehr " +
                                    "entschlüsseln und wurde nicht gesendet. Sie bleibt auf dem Gerät.",
                            )
                        )
                        erledigt += notiz.name
                        continue
                    }
                }

                try {
                    if (geht) {
                        val antwort = mitGespraech(korb, notiz) { gid ->
                            repository.sprache(datei, gid, antworten = true, anfrageId = anfrageId, wahl = auftrag.wahl)
                        }
                        angekommen(korb, notiz, antwort.chat?.gespraech_id)
                        erledigt += notiz.name
                    } else {
                        // Nur abschreiben: entweder will der Nutzer pruefen, oder der
                        // Vorspann muss vor den Text.
                        val erkannt = repository.sprache(datei, gespraechId = null, antworten = false).text
                        if (auftrag.antworten) {
                            // Ab jetzt ist es Text. Scheitert gleich der Chat, wird
                            // nicht noch einmal transkribiert.
                            korb.zuText(notiz.name, auftrag.copy(anfrage_id = auftrag.anfrage_id ?: AssistentRepository.neueAnfrageId()), erkannt)
                        } else {
                            korb.rueckmelden(
                                Rueckmeldung(art = Rueckmeldung.ART_DIKTAT, zeit = auftrag.aufgenommen, text = erkannt)
                            )
                            korb.entfernen(notiz.name)
                            erledigt += notiz.name
                        }
                    }
                } finally {
                    datei.delete()
                }
            } catch (fehler: ApiFehler) {
                if (fehler.istVoruebergehend) return Result.retry()
                // Das Modell von damals gibt es nicht mehr (422, seit 1.20.0). Ausgefuehrt
                // wurde nichts - ohne Wahl erneut, dann gilt die Einstellung des Gespraechs.
                // Sonst ginge eine Sprachnachricht als "keine Sprache erkannt" verloren.
                if (fehler is ApiFehler.Ungueltig && istModellFehler(fehler.anzeigetext, notiz.auftrag.wahl)) {
                    korb.auftragAendern(notiz.name, notiz.auftrag.copy(modell = null, denkaufwand = null))
                    return Result.retry()
                }
                endgueltigGescheitert(korb, notiz, fehler)
                erledigt += notiz.name
            }
        }
    }

    /**
     * Gibt es das Gespraech nicht mehr (404), geht die Nachricht in ein neues -
     * lieber dort als gar nicht.
     */
    private suspend fun <T> mitGespraech(korb: Ausgangskorb, notiz: WartendeNotiz, senden: suspend (String?) -> T): T {
        val gespraechId = notiz.auftrag.gespraech_id
        return try {
            senden(gespraechId)
        } catch (fehler: ApiFehler.NichtGefunden) {
            if (gespraechId == null) throw fehler
            korb.auftragAendern(notiz.name, notiz.auftrag.copy(gespraech_id = null))
            senden(null)
        }
    }

    private fun angekommen(korb: Ausgangskorb, notiz: WartendeNotiz, gespraechId: String?) {
        korb.entfernen(notiz.name)
        val faden = notiz.auftrag.faden
        if (faden != null && gespraechId != null) korb.fadenFestlegen(faden, gespraechId)
    }

    /**
     * Angekommen, aber auf dem Server gescheitert (`status: "fehler"`, etwa nach einem
     * Neustart). Laut API.md steht dann im Gespraech ein Hinweis, mit "mach weiter"
     * geht es weiter. Ein Text kommt trotzdem zurueck, falls er neu geschickt werden soll.
     */
    private fun serverFehler(korb: Ausgangskorb, notiz: WartendeNotiz, fehler: String?) {
        val auftrag = notiz.auftrag
        val detail = ApiFehler.AnfrageFehlgeschlagen(fehler).anzeigetext
        korb.rueckmelden(
            if (auftrag.art == AusgangAuftrag.ART_TEXT) {
                Rueckmeldung(
                    art = Rueckmeldung.ART_NICHT_GESENDET,
                    zeit = auftrag.aufgenommen,
                    text = auftrag.text.orEmpty(),
                    detail = detail,
                )
            } else {
                Rueckmeldung(
                    art = Rueckmeldung.ART_HINWEIS,
                    zeit = auftrag.aufgenommen,
                    text = "Sprachnachricht von ${uhrzeit(auftrag.aufgenommen)} kam an, wurde aber nicht fertig " +
                        "bearbeitet: $detail Im Gespräch „mach weiter“ schreiben.",
                )
            }
        )
        korb.entfernen(notiz.name)
    }

    /**
     * Der Server lehnt die Nachricht ab, und das wird sich nicht aendern. Was der
     * Nutzer geschrieben hat, geht als [Rueckmeldung] zurueck in die App.
     */
    private fun endgueltigGescheitert(korb: Ausgangskorb, notiz: WartendeNotiz, fehler: ApiFehler) {
        val auftrag = notiz.auftrag
        val zeit = uhrzeit(auftrag.aufgenommen)
        when {
            auftrag.art == AusgangAuftrag.ART_TEXT -> {
                korb.rueckmelden(
                    Rueckmeldung(
                        art = Rueckmeldung.ART_NICHT_GESENDET,
                        zeit = auftrag.aufgenommen,
                        text = auftrag.text.orEmpty(),
                        detail = fehler.anzeigetext,
                    )
                )
                korb.entfernen(notiz.name)
            }
            // Keine Sprache erkannt bzw. zu gross: wiederholt sich bei jedem Versuch.
            fehler is ApiFehler.Ungueltig || fehler is ApiFehler.ZuGross -> {
                korb.rueckmelden(
                    Rueckmeldung(
                        art = Rueckmeldung.ART_HINWEIS,
                        zeit = auftrag.aufgenommen,
                        text = "Sprachnotiz von $zeit nicht verarbeitet: ${fehler.anzeigetext}",
                    )
                )
                korb.entfernen(notiz.name)
            }
            // Der Server hat sie verarbeitet, nur die Antwort war nicht lesbar.
            // Sie steht im Verlauf.
            fehler is ApiFehler.UnlesbareAntwort -> korb.entfernen(notiz.name)
            else -> {
                korb.beiseitelegen(notiz.name)
                korb.rueckmelden(
                    Rueckmeldung(
                        art = Rueckmeldung.ART_HINWEIS,
                        zeit = auftrag.aufgenommen,
                        text = "Sprachnotiz von $zeit nicht gesendet: ${fehler.anzeigetext} Sie bleibt auf dem Gerät.",
                    )
                )
            }
        }
    }

    /** Was ein angekommenes Medium fuer die Hinweise im Chat bedeutet. */
    private data class Angekommen(
        val notiz: String?,
        val schreibtAb: Boolean = false,
        val unlesbar: String? = null,
        val foto: String? = null,
    )

    /**
     * Fotos, Videos und Dokumente der Reihe nach, immer das aelteste zuerst. Die Liste wird
     * jedes Mal neu gelesen, weil ein angekommenes Medium die Notiz der folgenden
     * festlegt.
     *
     * Seit 1.35.0 in Stuecken ueber `/v1/uploads` (API.md 4h): nach einem Abbruch geht es an
     * der letzten Stelle weiter, nie wieder bei 0 %. Ein alter Server ohne diese Route
     * bekommt die Datei wie bisher am Stueck.
     */
    private suspend fun medienSenden(
        repository: AssistentRepository,
        korb: Medienkorb,
        ausgang: Ausgangskorb,
    ): Result {
        var fotos = 0
        var videos = 0
        var abschriften = 0
        var dokumente = 0
        // Hinweis des Servers zum letzten Dokument ohne lesbaren Text (Scan, Passwort).
        var unlesbar: String? = null
        var letzteNotiz: String? = null
        val erledigt = mutableSetOf<String>()

        while (true) {
            val medium = korb.liste().firstOrNull { it.name !in erledigt } ?: break
            erledigt += medium.name

            var auftrag = medium.auftrag
            // Aus einem neuen Chat ohne Gespraech: hat dessen erste Nachricht inzwischen
            // ein Gespraech angelegt, gehoert das Medium dorthin (Auftrag 1.32 A). Sonst
            // geht es ohne raus, und der Server gibt es dem naechsten Chat-Zug mit.
            if (auftrag.gespraech_id == null && auftrag.faden != null) {
                ausgang.gespraechZuFaden(auftrag.faden!!)?.let { gid ->
                    auftrag = auftrag.copy(gespraech_id = gid, faden = null)
                    korb.auftragAendern(medium.name, auftrag)
                }
            }

            try {
                if (auftrag.aufbereiten) {
                    auftrag = videoAufbereiten(korb, ausgang, medium.name, auftrag) ?: continue
                }
                if (auftrag.upload_id == null || auftrag.groesse == null) {
                    auftrag = auftrag.copy(
                        upload_id = auftrag.upload_id ?: UploadStart.neueId(),
                        groesse = auftrag.groesse ?: korb.groesse(medium.name),
                    )
                    korb.auftragAendern(medium.name, auftrag)
                }
                if (auftrag.art != MedienAuftrag.ART_FOTO || (auftrag.groesse ?: 0) > GROSS_BYTES) {
                    imVordergrund(
                        if (auftrag.art == MedienAuftrag.ART_VIDEO) "Video wird hochgeladen …" else "Wird hochgeladen …"
                    )
                }

                val angekommen = hochladen(repository, korb, ausgang, medium.name, auftrag) ?: continue
                when (auftrag.art) {
                    MedienAuftrag.ART_VIDEO -> {
                        videos++
                        if (angekommen.schreibtAb) abschriften++
                    }
                    MedienAuftrag.ART_DOKUMENT -> {
                        dokumente++
                        angekommen.unlesbar?.let { unlesbar = it }
                    }
                    else -> fotos++
                }
                // Die upload_id kann sich unterwegs geaendert haben (neue ID), die Frage
                // haengt aber an der urspruenglichen.
                val fragenId = medium.auftrag.upload_id
                korb.entfernen(medium.name)
                if (angekommen.foto != null) {
                    listOfNotNull(fragenId, auftrag.upload_id).distinct().forEach { ausgang.fotoAngekommen(it, angekommen.foto) }
                }
                angekommen.notiz?.let { notiz ->
                    auftrag.serie?.let { korb.serieFestlegen(it, notiz) }
                    letzteNotiz = notiz
                }
            } catch (fehler: ApiFehler) {
                if (fehler.istVoruebergehend) return Result.retry()
                abgelehnt(korb, ausgang, medium.name, medium.auftrag, fehler.anzeigetext)
            }
        }

        return Result.success(
            workDataOf(
                ERGEBNIS_FOTOS to fotos,
                ERGEBNIS_VIDEOS to videos,
                ERGEBNIS_ABSCHRIFTEN to abschriften,
                ERGEBNIS_DOKUMENTE to dokumente,
                ERGEBNIS_UNLESBAR to unlesbar,
                ERGEBNIS_NOTIZ to letzteNotiz,
            )
        )
    }

    /**
     * Rechnet ein Video aus dem Ausgang auf 720p um (wie bisher im Dialog, seit 1.35.0
     * hier, damit die App sofort wieder bedienbar ist). Scheitert die Umrechnung, geht
     * das Original raus. `null` = beiseitegelegt bzw. weg, weiter mit dem naechsten.
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private suspend fun videoAufbereiten(
        korb: Medienkorb,
        ausgang: Ausgangskorb,
        name: String,
        auftrag: MedienAuftrag,
    ): MedienAuftrag? {
        val roh = korb.rohDatei(name) ?: run {
            abgelehnt(korb, ausgang, name, auftrag, "Das Original des Videos fehlt.")
            return null
        }
        imVordergrund("Video wird verkleinert …")
        var zuletzt = -1
        val klein = try {
            VideoAufbereitung.verkleinern(applicationContext, roh) { prozent ->
                if (prozent != zuletzt) {
                    zuletzt = prozent
                    setProgressAsync(
                        workDataOf(
                            FORTSCHRITT_PROZENT to prozent,
                            FORTSCHRITT_MEDIUM to name,
                            FORTSCHRITT_PHASE to PHASE_VERKLEINERN,
                        )
                    )
                }
            }
        } catch (abbruch: CancellationException) {
            throw abbruch
        } catch (_: Exception) {
            // Manche Geraete koennen bestimmte Formate nicht umrechnen.
            roh
        }
        if (!korb.existiert(name)) {
            // Waehrend des Umrechnens aus dem Ausgang geloescht.
            if (klein != roh) klein.delete()
            return null
        }
        if (klein.length() > AssistentRepository.MAX_VIDEO_BYTES) {
            if (klein != roh) klein.delete()
            abgelehnt(korb, ausgang, name, auftrag, "Das Video ist zu groß (maximal 1 GB).")
            return null
        }
        return try {
            korb.aufbereitet(name, klein, auftrag)
        } finally {
            if (klein != roh) klein.delete()
        }
    }

    /**
     * Ein Medium hochladen, in Stuecken oder (alter Server) am Stueck. `null` = nicht
     * angekommen und schon erledigt (abgelehnt, geloescht, defekt).
     */
    private suspend fun hochladen(
        repository: AssistentRepository,
        korb: Medienkorb,
        ausgang: Ausgangskorb,
        name: String,
        anfang: MedienAuftrag,
    ): Angekommen? {
        var auftrag = anfang
        var neueIds = 0
        var zuletzt = -1
        while (true) {
            val start = UploadStart(
                upload_id = auftrag.upload_id!!,
                ziel = when (auftrag.art) {
                    MedienAuftrag.ART_VIDEO -> UploadStart.ZIEL_VIDEO
                    MedienAuftrag.ART_DOKUMENT -> UploadStart.ZIEL_DOKUMENT
                    else -> UploadStart.ZIEL_FOTO
                },
                dateiname = auftrag.serverDateiname.take(200),
                groesse = auftrag.groesse!!,
                felder = buildJsonObject {
                    auftrag.notiz?.let { put("notiz", it) }
                    auftrag.text.trim().take(MAX_TEXT).takeIf { it.isNotEmpty() }?.let { put("text", it) }
                    auftrag.gespraech_id?.let { put("gespraech_id", it) }
                    if (auftrag.art == MedienAuftrag.ART_VIDEO) auftrag.abschreiben?.let { put("abschreiben", it) }
                },
            )
            val ergebnis = StueckUpload.hochladen(
                weg = repository.uploadWeg,
                start = start,
                oeffnen = { korb.lesen(name) },
                teilGroesse = { StueckUpload.teilGroesse(applicationContext, it) },
                fortschritt = { gesendet, gesamt ->
                    val prozent = (gesendet * 100 / gesamt.coerceAtLeast(1)).toInt()
                    if (prozent != zuletzt) {
                        zuletzt = prozent
                        setProgressAsync(
                            workDataOf(
                                FORTSCHRITT_PROZENT to prozent,
                                FORTSCHRITT_MEDIUM to name,
                                FORTSCHRITT_PHASE to PHASE_SENDEN,
                            )
                        )
                    }
                },
                nochGewollt = { korb.existiert(name) },
            )
            when (ergebnis) {
                is UploadAusgang.Fertig -> return auswerten(auftrag.art, ergebnis.ergebnis)
                UploadAusgang.AlterServer -> return direktSenden(repository, korb, name, auftrag)
                UploadAusgang.Abgebrochen -> {
                    listOfNotNull(anfang.upload_id, auftrag.upload_id).distinct()
                        .forEach { ausgang.fotoGescheitert(it, "Das Foto wurde aus dem Ausgang gelöscht.") }
                    return null
                }
                is UploadAusgang.Defekt -> {
                    abgelehnt(korb, ausgang, name, anfang, "Die Datei ließ sich nicht mehr lesen.")
                    return null
                }
                UploadAusgang.NeueIdNoetig -> {
                    if (++neueIds > 2) throw ApiFehler.Server(500, "Hochladen klappt gerade nicht.")
                    auftrag = auftrag.copy(upload_id = UploadStart.neueId())
                    korb.auftragAendern(name, auftrag)
                    zuletzt = -1
                }
                is UploadAusgang.Abgelehnt -> {
                    val fehler = ergebnis.fehler
                    // Gespraech inzwischen geloescht oder Notiz weg/fremd. Lieber ohne diese
                    // Angabe ablegen als die Aufnahme wegwerfen - erst das Gespraech weglassen,
                    // das schadet am wenigsten. Die Felder haengen an der upload_id: neue ID.
                    val ohne = if (fehler is ApiFehler.NichtGefunden || fehler is ApiFehler.Verboten) {
                        when {
                            auftrag.gespraech_id != null -> auftrag.copy(gespraech_id = null, faden = null)
                            auftrag.notiz != null -> auftrag.copy(notiz = null)
                            else -> null
                        }
                    } else {
                        null
                    }
                    if (ohne == null) throw fehler
                    auftrag = ohne.copy(upload_id = UploadStart.neueId())
                    korb.auftragAendern(name, auftrag)
                    zuletzt = -1
                }
            }
        }
    }

    /** `ergebnis` ist die Antwort des direkten Endpunkts. Unlesbar heisst nur: kein Hinweis. */
    private fun auswerten(art: String, ergebnis: JsonObject): Angekommen = try {
        when (art) {
            MedienAuftrag.ART_VIDEO -> Netzwerk.json.decodeFromJsonElement(VideoAntwort.serializer(), ergebnis)
                .let { Angekommen(it.notiz, schreibtAb = it.schreibtAb) }
            MedienAuftrag.ART_DOKUMENT -> Netzwerk.json.decodeFromJsonElement(DokumentAntwort.serializer(), ergebnis)
                .let { Angekommen(it.notiz, unlesbar = if (it.lesbar) null else it.hinweis) }
            else -> Netzwerk.json.decodeFromJsonElement(FotoAntwort.serializer(), ergebnis)
                .let { Angekommen(it.notiz, foto = it.foto) }
        }
    } catch (_: Exception) {
        Angekommen(notiz = null)
    }

    /** Wie vor 1.35.0: die ganze Datei auf einmal an den direkten Endpunkt. */
    private suspend fun direktSenden(
        repository: AssistentRepository,
        korb: Medienkorb,
        name: String,
        anfang: MedienAuftrag,
    ): Angekommen {
        val datei = when (val ausgepackt = korb.auspacken(WartendesMedium(name, anfang))) {
            is Auspacken.Fertig -> ausgepackt.datei
            // Voruebergehend: spaeter noch einmal.
            Auspacken.KeinPlatz -> throw ApiFehler.Server(507, "Kein Platz zum Auspacken.")
            is Auspacken.Defekt -> throw ApiFehler.Ungueltig("Die Datei ließ sich nicht mehr entschlüsseln.")
        }
        val melden: (Int) -> Unit = { prozent ->
            setProgressAsync(
                workDataOf(FORTSCHRITT_PROZENT to prozent, FORTSCHRITT_MEDIUM to name, FORTSCHRITT_PHASE to PHASE_SENDEN)
            )
        }
        var auftrag = anfang
        try {
            while (true) {
                try {
                    return when (auftrag.art) {
                        MedienAuftrag.ART_VIDEO -> repository.video(
                            datei = datei, notiz = auftrag.notiz, text = auftrag.text,
                            abschreiben = auftrag.abschreiben, gespraechId = auftrag.gespraech_id, fortschritt = melden,
                        ).let { Angekommen(it.notiz, schreibtAb = it.schreibtAb) }
                        MedienAuftrag.ART_DOKUMENT -> repository.dokument(
                            datei = datei, dateiname = auftrag.dateiname ?: datei.name, notiz = auftrag.notiz,
                            text = auftrag.text, gespraechId = auftrag.gespraech_id, fortschritt = melden,
                        ).let { Angekommen(it.notiz, unlesbar = if (it.lesbar) null else it.hinweis) }
                        else -> repository.foto(
                            bild = datei, notiz = auftrag.notiz, text = auftrag.text,
                            gespraechId = auftrag.gespraech_id, aufbereiten = false,
                        ).let { Angekommen(it.notiz, foto = it.foto) }
                    }
                } catch (fehler: ApiFehler) {
                    if (fehler !is ApiFehler.NichtGefunden && fehler !is ApiFehler.Verboten) throw fehler
                    auftrag = when {
                        auftrag.gespraech_id != null -> auftrag.copy(gespraech_id = null, faden = null)
                        auftrag.notiz != null -> auftrag.copy(notiz = null)
                        else -> throw fehler
                    }
                    korb.auftragAendern(name, auftrag)
                }
            }
        } finally {
            datei.delete()
        }
    }

    /**
     * 413/415/422 und Co. wiederholen sich bei jedem Versuch und wuerden den Korb
     * blockieren. Geloescht wird trotzdem nicht: auf dem Handy gibt es oft keine
     * andere Kopie mehr. Eine Frage, die auf dieses Foto wartet, kommt zurueck ins Feld.
     */
    private fun abgelehnt(korb: Medienkorb, ausgang: Ausgangskorb, name: String, auftrag: MedienAuftrag, grund: String) {
        korb.beiseitelegen(name)
        auftrag.upload_id?.let { ausgang.fotoGescheitert(it, grund) }
        ausgang.rueckmelden(
            Rueckmeldung(
                art = Rueckmeldung.ART_HINWEIS,
                zeit = auftrag.aufgenommen,
                text = "${artName(auftrag)} von ${uhrzeit(auftrag.aufgenommen)} nicht abgelegt: $grund Es bleibt auf dem Gerät.",
            )
        )
    }

    private fun artName(auftrag: MedienAuftrag) = when (auftrag.art) {
        MedienAuftrag.ART_VIDEO -> "Ein Video"
        MedienAuftrag.ART_DOKUMENT -> "Das Dokument „${auftrag.dateiname.orEmpty()}“"
        else -> "Ein Foto"
    }

    private fun uhrzeit(zeit: Long): String =
        SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY).format(Date(zeit))

    companion object {
        const val NAME = "ausgangskorb_versand"

        private const val KANAL = "versand"
        private const val BENACHRICHTIGUNG = 4711
        private const val KOPIE_ALT_MS = 60 * 60_000L

        const val FORTSCHRITT_PROZENT = "prozent"

        /** Name des Eintrags im [Medienkorb], zu dem [FORTSCHRITT_PROZENT] gehoert (1.35.0). */
        const val FORTSCHRITT_MEDIUM = "medium"

        /** [PHASE_VERKLEINERN] oder [PHASE_SENDEN]. */
        const val FORTSCHRITT_PHASE = "phase"
        const val PHASE_VERKLEINERN = "verkleinern"
        const val PHASE_SENDEN = "senden"

        /** Ab hier laeuft auch ein Foto als Vordergrunddienst mit Benachrichtigung. */
        private const val GROSS_BYTES = 5L * 1024 * 1024

        /** Wie bei den direkten Endpunkten. */
        private const val MAX_TEXT = 4000
        const val ERGEBNIS_FOTOS = "fotos"
        const val ERGEBNIS_VIDEOS = "videos"
        const val ERGEBNIS_ABSCHRIFTEN = "abschriften"
        const val ERGEBNIS_DOKUMENTE = "dokumente"
        const val ERGEBNIS_UNLESBAR = "unlesbar"
        const val ERGEBNIS_NOTIZ = "notiz"

        /**
         * Stoesst den Versand an, sobald eine Verbindung besteht.
         *
         * Laeuft gerade ein Versand, wird angehaengt: Ersetzen wuerde einen
         * Video-Upload mittendrin abbrechen. Wartet der vorige dagegen nur auf
         * seinen naechsten Versuch (nach vielen Fehlversuchen koennen das Stunden
         * sein), wird er ersetzt - sonst haenge der neue Auftrag hinter dieser
         * Wartezeit, und "Jetzt senden" bewirkte nichts.
         */
        fun anstossen(kontext: Context) {
            val auftrag = OneTimeWorkRequestBuilder<VersandArbeiter>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                // Linear statt exponentiell (Vorgabe des Nutzers: nie lange warten). Kommt das
                // Netz zurueck, stoesst ChefAssistentApp ohnehin sofort an.
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                // Auftrag 1.35: sofort los, sobald Netz da ist. Ist das Kontingent fuer
                // Eilauftraege aufgebraucht, laeuft er als normaler Auftrag.
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            val manager = WorkManager.getInstance(kontext)
            val stand = manager.getWorkInfosForUniqueWork(NAME)
            stand.addListener({
                val laeuft = runCatching { stand.get() }.getOrNull().orEmpty()
                    .any { it.state == WorkInfo.State.RUNNING }
                manager.enqueueUniqueWork(
                    NAME,
                    if (laeuft) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE,
                    auftrag,
                )
            }, Runnable::run)
        }
    }
}
