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
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import de.chefassistent.app.ChefAssistentApp
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
        val anwendung = applicationContext as? ChefAssistentApp ?: return Result.failure()
        val repository = anwendung.repository

        if (!repository.einstellungen.istAngemeldet) {
            // Ohne Token waere jeder Versuch ein 401. Alles bleibt liegen; der Chat
            // stoesst den Versand nach der naechsten Anmeldung wieder an.
            return Result.failure()
        }

        kopienAufraeumen()
        nachrichtenSenden(repository, anwendung.ausgangskorb)?.let { return it }
        return medienSenden(repository, anwendung.medienkorb, anwendung.ausgangskorb)
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
    private suspend fun nachrichtenSenden(repository: AssistentRepository, korb: Ausgangskorb): Result? {
        val erledigt = mutableSetOf<String>()

        while (true) {
            var notiz = korb.liste().firstOrNull { it.name !in erledigt } ?: return null

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
                        repository.chat(auftrag.chatText, gid, auftrag.bilder, anfrageId)
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
                            repository.sprache(datei, gid, antworten = true, anfrageId = anfrageId)
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

    /**
     * Fotos, Videos und Dokumente der Reihe nach, immer das aelteste zuerst. Die Liste wird
     * jedes Mal neu gelesen, weil ein angekommenes Medium die Notiz der folgenden
     * festlegt.
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
            val datei = when (val ausgepackt = korb.auspacken(medium)) {
                is Auspacken.Fertig -> ausgepackt.datei
                Auspacken.KeinPlatz -> return Result.retry()
                is Auspacken.Defekt -> {
                    korb.beiseitelegen(medium.name)
                    ausgang.rueckmelden(
                        Rueckmeldung(
                            art = Rueckmeldung.ART_HINWEIS,
                            zeit = medium.auftrag.aufgenommen,
                            text = "${artName(medium.auftrag)} von ${uhrzeit(medium.auftrag.aufgenommen)} ließ sich " +
                                "nicht mehr entschlüsseln und wurde nicht gesendet. Es bleibt auf dem Gerät.",
                        )
                    )
                    continue
                }
            }

            var auftrag = medium.auftrag
            if (auftrag.art != MedienAuftrag.ART_FOTO) {
                imVordergrund(if (auftrag.art == MedienAuftrag.ART_VIDEO) "Video wird hochgeladen …" else "Dokument wird hochgeladen …")
            }
            val melden: (Int) -> Unit = { prozent ->
                setProgressAsync(workDataOf(FORTSCHRITT_PROZENT to prozent))
            }
            try {
                while (true) {
                    try {
                        val notiz = when (auftrag.art) {
                            MedienAuftrag.ART_VIDEO -> {
                                val antwort = repository.video(
                                    datei = datei,
                                    notiz = auftrag.notiz,
                                    text = auftrag.text,
                                    abschreiben = auftrag.abschreiben,
                                    gespraechId = auftrag.gespraech_id,
                                    fortschritt = melden,
                                )
                                videos++
                                if (antwort.schreibtAb) abschriften++
                                antwort.notiz
                            }
                            MedienAuftrag.ART_DOKUMENT -> {
                                val antwort = repository.dokument(
                                    datei = datei,
                                    dateiname = auftrag.dateiname ?: datei.name,
                                    notiz = auftrag.notiz,
                                    text = auftrag.text,
                                    gespraechId = auftrag.gespraech_id,
                                    fortschritt = melden,
                                )
                                dokumente++
                                if (!antwort.lesbar) unlesbar = antwort.hinweis
                                antwort.notiz
                            }
                            else -> {
                                val notiz = repository.foto(
                                    bild = datei,
                                    notiz = auftrag.notiz,
                                    text = auftrag.text,
                                    gespraechId = auftrag.gespraech_id,
                                    aufbereiten = false,
                                ).notiz
                                fotos++
                                notiz
                            }
                        }
                        korb.entfernen(medium.name)
                        auftrag.serie?.let { korb.serieFestlegen(it, notiz) }
                        letzteNotiz = notiz
                        break
                    } catch (fehler: ApiFehler) {
                        if (fehler !is ApiFehler.NichtGefunden && fehler !is ApiFehler.Verboten) throw fehler
                        // Gespraech inzwischen geloescht oder Notiz weg/fremd. Lieber
                        // ohne diese Angabe ablegen als die Aufnahme wegwerfen - erst
                        // das Gespraech weglassen, das schadet am wenigsten.
                        auftrag = when {
                            auftrag.gespraech_id != null -> auftrag.copy(gespraech_id = null)
                            auftrag.notiz != null -> auftrag.copy(notiz = null)
                            else -> throw fehler
                        }
                        korb.auftragAendern(medium.name, auftrag)
                    }
                }
            } catch (fehler: ApiFehler) {
                if (fehler.istVoruebergehend) return Result.retry()
                // 413/415/422 wiederholen sich bei jedem Versuch und wuerden den Korb
                // blockieren. Geloescht wird trotzdem nicht: auf dem Handy gibt es
                // oft keine andere Kopie mehr.
                korb.beiseitelegen(medium.name)
                ausgang.rueckmelden(
                    Rueckmeldung(
                        art = Rueckmeldung.ART_HINWEIS,
                        zeit = auftrag.aufgenommen,
                        text = "${artName(auftrag)} von ${uhrzeit(auftrag.aufgenommen)} nicht abgelegt: " +
                            "${fehler.anzeigetext} Es bleibt auf dem Gerät.",
                    )
                )
            } finally {
                datei.delete()
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
                .setBackoffCriteria(BackoffPolicy.LINEAR, 60, TimeUnit.SECONDS)
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
