package de.chefassistent.app.monteur.push

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import de.chefassistent.app.data.ApiFehler
import de.chefassistent.app.monteur.MonteurApp
import de.chefassistent.app.monteur.data.Nachrichten
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private const val LOG = "MonteurPush"

/** `Task.await()` ohne die Zusatzbibliothek kotlinx-coroutines-play-services. */
private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    kotlinx.coroutines.suspendCancellableCoroutine { fortsetzung ->
        addOnCompleteListener { aufgabe ->
            val fehler = aufgabe.exception
            if (fehler != null) fortsetzung.resumeWith(Result.failure(fehler))
            else @Suppress("UNCHECKED_CAST") fortsetzung.resumeWith(Result.success(aufgabe.result as T))
        }
    }

/**
 * Alles rund um Nachrichten (Auftrag 1.24): Token beim Server melden, abholen,
 * lokale Benachrichtigungen zeigen, wenn der Server nicht pusht. Ohne
 * `google-services.json` gibt es kein Firebase - dann nur Abholen.
 */
object Nachrichtendienst {

    private val hintergrund = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Firebase ist nur mit `google-services.json` eingerichtet. */
    fun firebaseDa(kontext: Context): Boolean = try {
        FirebaseApp.getApps(kontext).isNotEmpty() || FirebaseApp.initializeApp(kontext) != null
    } catch (_: Exception) {
        false
    }

    /** Nach jedem App-Start (angemeldet): Token holen und beim Server melden. Fehler still. */
    fun tokenMelden(app: MonteurApp) {
        if (!app.einstellungen.istAngemeldet || !firebaseDa(app)) return
        hintergrund.launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                tokenAnServer(app, token)
            } catch (e: Exception) {
                Log.w(LOG, "Token nicht verfuegbar: ${e.message}")
            }
        }
    }

    suspend fun tokenAnServer(app: MonteurApp, token: String) {
        if (!app.einstellungen.istAngemeldet) return
        try {
            app.repository.pushAnmelden(token)
            app.nachrichtenSpeicher.token = token
        } catch (e: ApiFehler) {
            // 503 = Server ohne Benachrichtigungen, 401 merkt die Oberflaeche selbst.
            Log.w(LOG, "Token nicht angemeldet: ${e.anzeigetext}")
        }
    }

    /** Beim Abmelden zuletzt: Token loeschen, damit das Handy nichts mehr bekommt. */
    suspend fun tokenLoeschen(kontext: Context) {
        if (!firebaseDa(kontext)) return
        try {
            FirebaseMessaging.getInstance().deleteToken().await()
        } catch (_: Exception) {
        }
    }

    /**
     * Abholen und - wenn der Server nicht pusht - neue Nachrichten lokal zeigen.
     * Beim allerersten Abruf werden alte Nachrichten nur gemerkt, nicht gemeldet.
     * Liefert die Antwort fuer die Leiste auf „Heute“.
     */
    suspend fun abholen(app: MonteurApp, benachrichtigen: Boolean): Nachrichten {
        val speicher = app.nachrichtenSpeicher
        // Ausstehendes Gelesen zuerst nachholen.
        speicher.gelesenAusstehend.takeIf { it > 0 }?.let { bis ->
            try {
                app.repository.gelesen(bis)
                speicher.gelesenAusstehend = 0
            } catch (_: ApiFehler) {
            }
        }
        val antwort = app.repository.nachrichten(0)
        speicher.pushAktiv = antwort.push_aktiv
        val gezeigt = speicher.gezeigt.toMutableSet()
        if (!speicher.ersterAbrufErledigt) {
            gezeigt += antwort.nachrichten.map { it.id }
            speicher.ersterAbrufErledigt = true
        } else if (benachrichtigen && !antwort.push_aktiv) {
            antwort.nachrichten
                .filter { !it.gelesen && it.id !in gezeigt && jetztZeigen(it.art, it.datum) }
                .sortedBy { it.id }
                .forEach {
                    Benachrichtigungen.zeigen(app, it)
                    gezeigt += it.id
                }
        }
        speicher.gezeigt = gezeigt
        abholenPlanen(app, !antwort.push_aktiv)
        return antwort
    }

    /** Ohne Push: alle 15 Minuten selbst abholen; mit Push abbestellen. */
    fun abholenPlanen(kontext: Context, aktiv: Boolean) {
        val manager = WorkManager.getInstance(kontext)
        if (!aktiv) {
            manager.cancelUniqueWork(ABHOLEN)
            return
        }
        val auftrag = PeriodicWorkRequestBuilder<NachrichtenAbholer>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        manager.enqueueUniquePeriodicWork(ABHOLEN, ExistingPeriodicWorkPolicy.KEEP, auftrag)
    }

    fun abholenAbbestellen(kontext: Context) = WorkManager.getInstance(kontext).cancelUniqueWork(ABHOLEN)

    private const val ABHOLEN = "monteur_nachrichten"
}

/** Alle 15 Minuten, nur solange der Server nicht pusht. */
class NachrichtenAbholer(kontext: Context, parameter: WorkerParameters) : CoroutineWorker(kontext, parameter) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? MonteurApp ?: return Result.failure()
        if (!app.einstellungen.istAngemeldet) return Result.success()
        return try {
            Nachrichtendienst.abholen(app, benachrichtigen = true)
            app.nachrichtenGeaendert()
            Result.success()
        } catch (e: ApiFehler) {
            if (e.istVoruebergehend && e !is ApiFehler.NichtAngemeldet) Result.retry() else Result.success()
        }
    }
}

/**
 * Push vom Server (FCM). Im Hintergrund zeigt Android die Benachrichtigung selbst;
 * hier landet nur, was im Vordergrund ankommt - dann zeigt die App sie und
 * aktualisiert die Leiste auf „Heute“.
 */
class MonteurPushDienst : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val app = application as? MonteurApp ?: return
        CoroutineScope(Dispatchers.IO).launch { Nachrichtendienst.tokenAnServer(app, token) }
    }

    override fun onMessageReceived(nachricht: RemoteMessage) {
        val app = application as? MonteurApp ?: return
        val daten = nachricht.data
        val id = daten["nachricht_id"]?.toIntOrNull() ?: return
        val art = daten["art"]
        Benachrichtigungen.zeigen(
            app,
            id = id,
            art = art,
            titel = nachricht.notification?.title ?: daten["titel"].orEmpty(),
            text = nachricht.notification?.body ?: daten["text"].orEmpty(),
            terminId = daten["termin_id"]?.takeIf { it.isNotBlank() },
            datum = daten["datum"]?.takeIf { it.isNotBlank() },
        )
        app.nachrichtenSpeicher.gezeigt = app.nachrichtenSpeicher.gezeigt + id
        app.nachrichtenGeaendert()
    }
}
