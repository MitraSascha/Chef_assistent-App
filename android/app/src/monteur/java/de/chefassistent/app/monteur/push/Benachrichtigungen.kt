package de.chefassistent.app.monteur.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.chefassistent.app.R
import de.chefassistent.app.monteur.MonteurActivity
import de.chefassistent.app.monteur.data.MonteurNachricht
import de.chefassistent.app.monteur.data.NACHRICHT_NOTDIENST
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Benachrichtigungen der Monteur-App (Auftrag 1.24). Zwei Kanaele mit genau den IDs,
 * die der Server als `channel_id` schickt: `termine` und `notdienst`. Die
 * Benachrichtigungs-ID ist die `nachricht_id` - Push und eigenes Abholen zeigen
 * dieselbe Nachricht also nie doppelt.
 */
object Benachrichtigungen {
    const val KANAL_TERMINE = "termine"
    const val KANAL_NOTDIENST = "notdienst"

    /** Extras im Start-Intent: so oeffnet das Antippen den Termin. */
    const val EXTRA_NACHRICHT = "nachricht_id"
    const val EXTRA_TERMIN = "termin_id"
    const val EXTRA_DATUM = "datum"

    /** Beim Start anlegen; mehrfaches Anlegen schadet nicht. */
    fun kanaeleAnlegen(kontext: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = kontext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(KANAL_TERMINE, "Termine", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Neue, verschobene und abgesagte Termine"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(KANAL_NOTDIENST, "Notdienst", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Notdienst-Einsätze – mit Ton, auch nachts"
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                // „Nicht stoeren“ uebergehen darf nur der Monteur selbst in den Kanal-Einstellungen.
            }
        )
    }

    fun kanal(art: String?): String = if (art == NACHRICHT_NOTDIENST) KANAL_NOTDIENST else KANAL_TERMINE

    /** Ohne Erlaubnis (Android 13+) wird still nichts gezeigt - die Leiste auf „Heute“ bleibt. */
    fun zeigen(kontext: Context, id: Int, art: String?, titel: String, text: String, terminId: String?, datum: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(kontext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val absicht = Intent(kontext, MonteurActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NACHRICHT, id.toString())
            terminId?.let { putExtra(EXTRA_TERMIN, it) }
            datum?.let { putExtra(EXTRA_DATUM, it) }
        }
        val tippen = PendingIntent.getActivity(
            kontext, id, absicht, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notdienst = art == NACHRICHT_NOTDIENST
        val b = NotificationCompat.Builder(kontext, kanal(art))
            .setSmallIcon(R.drawable.ic_hochladen)
            .setContentTitle(titel)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(tippen)
            .setPriority(if (notdienst) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (notdienst) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        try {
            NotificationManagerCompat.from(kontext).notify(id, b.build())
        } catch (_: SecurityException) {
        }
    }

    fun zeigen(kontext: Context, n: MonteurNachricht) =
        zeigen(kontext, n.id, n.art, n.titel, n.text, n.termin_id, n.datum)

    /** Nach dem Antippen bzw. Lesen in der App: Benachrichtigung entfernen. */
    fun entfernen(kontext: Context, id: Int) = NotificationManagerCompat.from(kontext).cancel(id)
}

/**
 * Ruhezeit 21-6 Uhr wie auf dem Server: nachts nur Notdienst und Termine, die in
 * weniger als 3 Stunden beginnen. Der Rest wird ab 6 Uhr gezeigt.
 */
fun jetztZeigen(art: String, datum: String?, jetzt: LocalDateTime = LocalDateTime.now(), zone: ZoneId = ZoneId.systemDefault()): Boolean {
    val stunde = jetzt.hour
    if (stunde in 6..20) return true
    if (art == NACHRICHT_NOTDIENST) return true
    val beginn = beginnOrtszeit(datum, zone) ?: return false
    val bis = Duration.between(jetzt, beginn)
    return !bis.isNegative && bis < Duration.ofHours(3)
}

/** Beginn als Ortszeit, mit oder ohne Offset; nur ein Datum gilt als 0 Uhr. */
fun beginnOrtszeit(datum: String?, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime? {
    if (datum.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(datum).atZoneSameInstant(zone).toLocalDateTime() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(datum) }.getOrNull()
        ?: runCatching { java.time.LocalDate.parse(datum.take(10)).atStartOfDay() }.getOrNull()
}
