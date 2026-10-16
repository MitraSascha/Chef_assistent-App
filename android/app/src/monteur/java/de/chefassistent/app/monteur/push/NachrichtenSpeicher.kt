package de.chefassistent.app.monteur.push

import android.content.Context

/**
 * Kleiner Merkzettel fuer Benachrichtigungen. Nichts davon ist geheim (IDs, Schalter),
 * deshalb normale SharedPreferences statt des verschluesselten Token-Speichers.
 */
class NachrichtenSpeicher(kontext: Context) {
    private val prefs = kontext.getSharedPreferences("monteur_nachrichten", Context.MODE_PRIVATE)

    /** Erst nach dem ersten Abruf werden neue Nachrichten als Benachrichtigung gezeigt. */
    var ersterAbrufErledigt: Boolean
        get() = prefs.getBoolean(K_ERSTER, false)
        set(wert) = prefs.edit().putBoolean(K_ERSTER, wert).apply()

    /** POST_NOTIFICATIONS nur einmal fragen. */
    var erlaubnisGefragt: Boolean
        get() = prefs.getBoolean(K_ERLAUBNIS, false)
        set(wert) = prefs.edit().putBoolean(K_ERLAUBNIS, wert).apply()

    var pushAktiv: Boolean
        get() = prefs.getBoolean(K_PUSH, false)
        set(wert) = prefs.edit().putBoolean(K_PUSH, wert).apply()

    /** Zuletzt beim Server angemeldetes FCM-Token - zum Abmelden. */
    var token: String?
        get() = prefs.getString(K_TOKEN, null)
        set(wert) = prefs.edit().putString(K_TOKEN, wert).apply()

    /** Gelesen-Markierung, die noch nicht beim Server ist (wird beim naechsten Abruf wiederholt). */
    var gelesenAusstehend: Int
        get() = prefs.getInt(K_GELESEN, 0)
        set(wert) = prefs.edit().putInt(K_GELESEN, wert).apply()

    /** Schon als Benachrichtigung gezeigte Nachrichten (hoechstens 200). */
    var gezeigt: Set<Int>
        get() = prefs.getString(K_GEZEIGT, "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }.toSet()
        set(wert) = prefs.edit().putString(K_GEZEIGT, wert.sortedDescending().take(200).joinToString(",")).apply()

    /** Beim Abmelden: neue Einrichtung, neuer Anfang. */
    fun leeren() {
        val gefragt = erlaubnisGefragt
        prefs.edit().clear().apply()
        erlaubnisGefragt = gefragt
    }

    private companion object {
        const val K_ERSTER = "erster_abruf"
        const val K_ERLAUBNIS = "erlaubnis_gefragt"
        const val K_PUSH = "push_aktiv"
        const val K_TOKEN = "fcm_token"
        const val K_GELESEN = "gelesen_ausstehend"
        const val K_GEZEIGT = "gezeigt"
    }
}
