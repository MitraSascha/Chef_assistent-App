// EncryptedSharedPreferences ist in security-crypto 1.1.0 als veraltet markiert,
// Google hat aber keinen Nachfolger benannt. Bis es einen gibt, bleibt das hier
// die beste verfuegbare Ablage - Klartext waere die schlechtere Antwort.
@file:Suppress("DEPRECATION")

package de.chefassistent.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Server-Adresse und Zugangs-Token.
 *
 * Das Token ist ein dauerhafter Vollzugang zum Assistenten des Nutzers und liegt
 * deshalb in [EncryptedSharedPreferences], also verschluesselt mit einem Schluessel
 * aus dem Android Keystore - nie im Klartext.
 */
class Einstellungen(context: Context) {

    /**
     * Laesst sich der Speicher nicht oeffnen (Schluessel im Keystore fehlt oder ist
     * beschaedigt, etwa nach einem Geraeteumzug), stuerzte die App sonst bei jedem
     * Start ab, bis jemand die App-Daten loescht. Stattdessen wird er neu angelegt:
     * die Anmeldung ist weg, die App laeuft.
     */
    private val speicher: SharedPreferences = try {
        oeffnen(context)
    } catch (_: Exception) {
        context.deleteSharedPreferences(DATEI)
        oeffnen(context)
    }

    private fun oeffnen(context: Context): SharedPreferences {
        val schluessel = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            DATEI,
            schluessel,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var basisUrl: String
        get() = speicher.getString(SCHLUESSEL_URL, null) ?: STANDARD_BASIS_URL
        set(wert) = speicher.edit().putString(SCHLUESSEL_URL, Netzwerk.normalisiereUrl(wert)).apply()

    var token: String?
        get() = speicher.getString(SCHLUESSEL_TOKEN, null)
        set(wert) {
            speicher.edit().apply {
                if (wert.isNullOrBlank()) remove(SCHLUESSEL_TOKEN) else putString(SCHLUESSEL_TOKEN, wert.trim())
            }.apply()
        }

    /**
     * Anmeldename der letzten erfolgreichen Anmeldung, zum Vorbelegen des Feldes.
     * Bleibt beim Abmelden und nach einem 401 erhalten - nur das Token faellt weg.
     * Das Passwort wird nirgends gespeichert.
     */
    var name: String?
        get() = speicher.getString(SCHLUESSEL_NAME, null)
        set(wert) {
            speicher.edit().apply {
                if (wert.isNullOrBlank()) remove(SCHLUESSEL_NAME) else putString(SCHLUESSEL_NAME, wert.trim())
            }.apply()
        }

    /**
     * `true` = Antworten ueber `POST /v1/vorlesen` vom Server sprechen lassen
     * (natuerlichere Stimme, kostet pro Aufruf, braucht Netz).
     * `false` = Android-Sprachausgabe auf dem Geraet (kostenlos, offline).
     */
    var serverStimme: Boolean
        get() = speicher.getBoolean(SCHLUESSEL_SERVER_STIMME, true)
        set(wert) = speicher.edit().putBoolean(SCHLUESSEL_SERVER_STIMME, wert).apply()

    /**
     * Gewaehlte OpenAI-Stimme, oder `null` fuer die Servervorgabe.
     * Der Server speichert dazu nichts pro Nutzer - die Wahl lebt hier.
     */
    var stimme: String?
        get() = speicher.getString(SCHLUESSEL_STIMME, null)
        set(wert) {
            speicher.edit().apply {
                if (wert.isNullOrBlank()) remove(SCHLUESSEL_STIMME) else putString(SCHLUESSEL_STIMME, wert)
            }.apply()
        }

    /**
     * `true` = erkannten Text erst im Eingabefeld zeigen, damit er vor dem
     * Senden korrigiert werden kann. `false` = Aufnahme geht direkt als Frage raus.
     *
     * Vorgabe `true`: auf der Baustelle verhoert sich die Erkennung bei
     * Eigennamen und Projektnummern, und eine falsch verstandene Anweisung
     * kann eine falsche Ablageaktion ausloesen.
     */
    var diktatPruefen: Boolean
        get() = speicher.getBoolean(SCHLUESSEL_DIKTAT_PRUEFEN, true)
        set(wert) = speicher.edit().putBoolean(SCHLUESSEL_DIKTAT_PRUEFEN, wert).apply()

    /**
     * Zuletzt benutzte Projektkontexte, neuester zuerst, hoechstens [MAX_KONTEXTE].
     *
     * Nur die **Liste** wird gespeichert, nicht der aktuell gesetzte Kontext.
     * Der lebt bewusst nur solange die App laeuft: ein Kontext von gestern, der
     * heute unbemerkt an jeder Frage haengt, richtet mehr Schaden an als das
     * erneute Auswaehlen Muehe kostet.
     */
    var letzteKontexte: List<String>
        get() = speicher.getString(SCHLUESSEL_KONTEXTE, null)
            ?.split(TRENNER)
            ?.filter { it.isNotBlank() }
            .orEmpty()
        set(wert) {
            val sauber = wert.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(MAX_KONTEXTE)
            speicher.edit().putString(SCHLUESSEL_KONTEXTE, sauber.joinToString(TRENNER)).apply()
        }

    /** Merkt sich einen Kontext als zuletzt benutzt. */
    fun kontextGemerkt(kontext: String) {
        letzteKontexte = listOf(kontext) + letzteKontexte
    }

    /**
     * Fertige Auftraege, die der Nutzer schon in der Auftragsansicht gesehen hat.
     * Alles Fertige, was hier fehlt, wird hervorgehoben und im Chat mitgezaehlt.
     */
    var geseheneAuftraege: Set<String>
        get() = speicher.getString(SCHLUESSEL_GESEHENE_AUFTRAEGE, null)
            ?.split(TRENNER)
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()
        set(wert) = speicher.edit()
            .putString(SCHLUESSEL_GESEHENE_AUFTRAEGE, wert.toList().takeLast(MAX_GESEHENE_AUFTRAEGE).joinToString(TRENNER))
            .apply()

    /** Gewaehlte Personen im Kalender (`ich`, `hero:<id>` oder `alle`), Vorgabe nur `ich`. */
    var kalenderPersonen: List<String>
        get() = speicher.getString(SCHLUESSEL_KALENDER_PERSONEN, null)
            ?.split(TRENNER)
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(PERSON_ICH)
        set(wert) = speicher.edit().putString(SCHLUESSEL_KALENDER_PERSONEN, wert.joinToString(TRENNER)).apply()

    /**
     * Gruppen der Gespraechsliste, die vom Standard abweichen (seit v1.21.0):
     * Monate starten eingeklappt, alle anderen offen. Wer hier steht, ist umgeklappt.
     */
    var umgeklappteGruppen: Set<String>
        get() = speicher.getString(SCHLUESSEL_UMGEKLAPPT, null)
            ?.split(TRENNER)
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()
        set(wert) = speicher.edit().putString(SCHLUESSEL_UMGEKLAPPT, wert.joinToString(TRENNER)).apply()

    val istAngemeldet: Boolean get() = !token.isNullOrBlank()

    fun abmelden() {
        token = null
    }

    companion object {
        private const val DATEI = "chef_assistent_zugang"
        private const val SCHLUESSEL_URL = "basis_url"
        private const val SCHLUESSEL_TOKEN = "token"
        private const val SCHLUESSEL_NAME = "anmeldename"
        private const val SCHLUESSEL_SERVER_STIMME = "server_stimme"
        private const val SCHLUESSEL_STIMME = "stimme"
        private const val SCHLUESSEL_DIKTAT_PRUEFEN = "diktat_pruefen"
        private const val SCHLUESSEL_KONTEXTE = "letzte_kontexte"
        private const val SCHLUESSEL_GESEHENE_AUFTRAEGE = "gesehene_auftraege"
        private const val SCHLUESSEL_KALENDER_PERSONEN = "kalender_personen"
        private const val SCHLUESSEL_UMGEKLAPPT = "umgeklappte_gruppen"

        /** Zeilenumbruch als Trenner: in einem Projektnamen kommt keiner vor. */
        private const val TRENNER = "\n"
        private const val MAX_KONTEXTE = 5

        /** Genug fuer Monate, ohne dass die Liste unbegrenzt waechst. */
        private const val MAX_GESEHENE_AUFTRAEGE = 200

        /**
         * Vorbelegung aus `API.md` (seit v1.10.0: Internet, ohne VPN). Steht hier nur
         * als Startwert im Anmeldefeld - die tatsaechlich benutzte Adresse kommt immer
         * aus dem Speicher, damit ein Serverumzug keine neue App-Version braucht.
         * Bestehende Installationen behalten ihre gespeicherte Adresse.
         */
        const val STANDARD_BASIS_URL = "https://assistent.mitra-sanitaer.de/"

        /**
         * Weiterhin moeglich, aber nur mit aktivem Tailscale. Einzige Adresse, ueber
         * die alte `ca_`-Zugangsschluessel noch gelten.
         */
        const val TAILSCALE_BASIS_URL = "https://mitraserver-aspire-xc-605.tail8bd028.ts.net/"
    }
}
