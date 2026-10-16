package de.chefassistent.app.monteur.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.chefassistent.app.monteur.MonteurApp
import java.util.concurrent.TimeUnit

/**
 * Schickt den [MonteurKorb] ab, sobald Netz da ist - auch wenn die App laengst
 * zu ist. `CONNECTED` heisst nur „irgendeine Verbindung“; ob der Server erreichbar
 * ist, weiss erst der Aufruf. Darum fuehrt jeder voruebergehende Fehler zu [Result.retry].
 */
class MonteurVersand(kontext: Context, parameter: WorkerParameters) : CoroutineWorker(kontext, parameter) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? MonteurApp ?: return Result.failure()
        // Ohne Token waere alles 401. Es bleibt liegen bis zur naechsten Einrichtung.
        if (!app.einstellungen.istAngemeldet) return Result.failure()
        return if (app.korb.senden(app.repository)) Result.success() else Result.retry()
    }

    companion object {
        private const val NAME = "monteur_versand"

        /** Ersetzt einen nur wartenden Auftrag, damit „Jetzt senden“ nicht hinter Backoff haengt. */
        fun anstossen(kontext: Context) {
            val auftrag = OneTimeWorkRequestBuilder<MonteurVersand>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(kontext).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, auftrag)
        }
    }
}
