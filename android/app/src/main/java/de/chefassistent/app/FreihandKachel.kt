package de.chefassistent.app

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

/**
 * Kachel in den Schnelleinstellungen: einmal von oben wischen, antippen, reden.
 *
 * Gedacht fuer den Moment im Auto oder auf der Baustelle, in dem man die App
 * nicht erst suchen will. Der Nutzer muss die Kachel einmalig in den
 * Schnelleinstellungen hinzufuegen - Android zeigt sie sonst nicht an.
 */
class FreihandKachel : TileService() {

    override fun onClick() {
        super.onClick()

        val absicht = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.AKTION_FREIHAND
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Ab Android 14 nimmt startActivityAndCollapse nur noch einen
            // PendingIntent; die Variante mit Intent wirft dort eine Ausnahme.
            val warteAbsicht = PendingIntent.getActivity(
                this,
                0,
                absicht,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(warteAbsicht)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(absicht)
        }
    }
}
