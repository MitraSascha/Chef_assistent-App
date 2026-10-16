package de.chefassistent.app.monteur.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import de.chefassistent.app.R
import de.chefassistent.app.monteur.MonteurApp
import java.util.concurrent.TimeUnit

/**
 * Schickt den [MonteurKorb] ab, sobald Netz da ist - auch wenn die App laengst
 * zu ist. `CONNECTED` heisst nur „irgendeine Verbindung“; ob der Server erreichbar
 * ist, weiss erst der Aufruf. Darum fuehrt jeder voruebergehende Fehler zu [Result.retry].
 *
 * Beim Hochladen von Dateien (Videos bis 1 GB) meldet er den Fortschritt
 * ([FORTSCHRITT], [TITEL]) und laeuft als Vordergrunddienst - ohne beendet Android
 * einen Hintergrundauftrag nach etwa 10 Minuten.
 */
class MonteurVersand(kontext: Context, parameter: WorkerParameters) : CoroutineWorker(kontext, parameter) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? MonteurApp ?: return Result.failure()
        // Ohne Token waere alles 401. Es bleibt liegen bis zur naechsten Einrichtung.
        if (!app.einstellungen.istAngemeldet) return Result.failure()
        var vordergrund = false
        val fertig = app.korb.senden(app.repository) { titel, prozent ->
            setProgressAsync(workDataOf(TITEL to titel, FORTSCHRITT to prozent))
            if (!vordergrund) {
                vordergrund = true
                try {
                    setForegroundAsync(vordergrundInfo(titel))
                } catch (_: Exception) {
                    // Aus dem Hintergrund ab Android 12 ggf. nicht erlaubt - dann ohne.
                }
            }
        }
        return if (fertig) Result.success() else Result.retry()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = vordergrundInfo("")

    private fun vordergrundInfo(titel: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(KANAL, "Hochladen", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Fotos, Videos und Berichte, die nachgesendet werden"
                }
            )
        }
        val benachrichtigung = NotificationCompat.Builder(applicationContext, KANAL)
            .setSmallIcon(R.drawable.ic_hochladen)
            .setContentTitle(applicationContext.getString(R.string.app_name))
            .setContentText(if (titel.isBlank()) "Wird gesendet …" else "$titel wird hochgeladen …")
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

    companion object {
        const val NAME = "monteur_versand"
        const val FORTSCHRITT = "fortschritt"
        const val TITEL = "titel"
        private const val KANAL = "monteur_hochladen"
        private const val BENACHRICHTIGUNG = 4711

        /**
         * Laeuft gerade ein Versand, wird angehaengt statt abgebrochen (ein grosses Video
         * soll nicht neu beginnen, weil ein Foto dazukommt). Ein nur wartender Auftrag
         * wird ersetzt, sonst hinge „Jetzt senden“ hinter dem Backoff.
         */
        fun anstossen(kontext: Context) {
            val app = kontext.applicationContext
            // WorkInfo abzufragen blockiert kurz - nicht auf dem Hauptthread.
            Thread {
                val manager = WorkManager.getInstance(app)
                val laeuft = runCatching {
                    manager.getWorkInfosForUniqueWork(NAME).get().any { it.state == androidx.work.WorkInfo.State.RUNNING }
                }.getOrDefault(false)
                val auftrag = OneTimeWorkRequestBuilder<MonteurVersand>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    // Linear: nach vielen Fehlversuchen im Funkloch nicht stundenlang warten.
                    // Kommt das Netz zurueck, stoesst MonteurApp ohnehin sofort an.
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                    .build()
                manager.enqueueUniqueWork(
                    NAME,
                    if (laeuft) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE,
                    auftrag,
                )
            }.start()
        }
    }
}
