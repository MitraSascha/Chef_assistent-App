package de.chefassistent.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

/**
 * Fehler des Assistenten, nach Bedeutung getrennt - damit die Oberflaeche
 * entscheiden kann, statt Statuscodes zu vergleichen.
 *
 * Erbt von [IOException], damit OkHttp und Retrofit die Ausnahme unveraendert
 * durchreichen, statt sie in etwas Eigenes zu verpacken.
 *
 * [anzeigetext] ist der Text aus `detail` des Servers. Der ist laut Vertrag
 * bereits deutscher Klartext und wird direkt angezeigt - hier werden keine
 * eigenen Formulierungen darueber gelegt.
 */
sealed class ApiFehler(val anzeigetext: String, ursache: Throwable? = null) :
    IOException(anzeigetext, ursache) {

    /** 401 - Token fehlt, ist falsch oder gesperrt. Fuehrt zurueck zur Anmeldung. */
    class NichtAngemeldet(text: String) : ApiFehler(text)

    /** 403 - Pfad ausserhalb des freigegebenen Bereichs, oder bei Fotos eine fremde Notiz. */
    class Verboten(text: String) : ApiFehler(text)

    /** 404 - Gespraech, Bestaetigung oder Datei gibt es nicht. */
    class NichtGefunden(text: String) : ApiFehler(text)

    /**
     * 409 - entweder laeuft im Gespraech noch eine Anfrage, oder die Bestaetigung
     * wurde bereits beantwortet bzw. ist nach 24 h abgelaufen.
     * Kein Fehler-Popup wert: Zustand neu laden reicht.
     */
    class Beschaeftigt(text: String) : ApiFehler(text)

    /** 413 - Aufnahme oder Foto groesser als 25 MB. Sollte die App vor dem Senden abfangen. */
    class ZuGross(text: String) : ApiFehler(text)

    /** 415 - das Foto ist kein lesbares Bild (z.B. HEIC). */
    class FalscherTyp(text: String) : ApiFehler(text)

    /** 422 - Eingabe ungueltig oder keine Sprache erkannt. */
    class Ungueltig(text: String) : ApiFehler(text)

    /**
     * 429 - zu viele Fehlversuche bei der Anmeldung, der Server sperrt fuer eine
     * Weile (laut `API.md` 15 Minuten). `detail` sagt das bereits, wird direkt angezeigt.
     */
    class ZuVieleVersuche(text: String) : ApiFehler(text)

    /** 502 - die KI hinter dem Assistenten antwortet nicht. Erneut versuchen anbieten. */
    class KiNichtErreichbar(text: String) : ApiFehler(text)

    /** Alles andere mit Statuscode. */
    class Server(val code: Int, text: String) : ApiFehler(text)

    /**
     * Die Verbindung kam gar nicht zustande. Seit v1.10.0 ist der Server auch
     * ohne VPN erreichbar; bei der Tailscale-Adresse bleibt ein inaktives VPN
     * aber die haeufigste Ursache, nicht ein Fehler im Backend.
     */
    class KeineVerbindung(ursache: Throwable) :
        ApiFehler("Keine Verbindung zum Assistenten. Ist das Handy online – und bei der Tailscale-Adresse Tailscale aktiv?", ursache)

    /**
     * Die Anfrage kam an, ist auf dem Server aber gescheitert (`status: "fehler"` bei
     * `GET /v1/chat/{anfrage_id}`, etwa nach einem Neustart). Endgueltig - der Nutzer
     * schickt sie selbst noch einmal ("Nochmal versuchen").
     */
    class AnfrageFehlgeschlagen(text: String?) :
        ApiFehler(text?.takeIf { it.isNotBlank() } ?: "Die Anfrage ist auf dem Server gescheitert. Bitte nochmal senden.")

    /** Die eingestellte Server-Adresse ist keine gueltige URL. */
    class AdresseUngueltig(adresse: String) :
        ApiFehler("„${adresse.trim().trimEnd('/')}“ ist keine gültige Server-Adresse.")

    /** Antwort kam an, liess sich aber nicht lesen. */
    class UnlesbareAntwort(ursache: Throwable) :
        ApiFehler("Der Assistent hat unerwartet geantwortet.", ursache)

    /**
     * Geht von selbst vorbei: kein Netz, Server oder KI kurz weg, Gespraech gerade
     * beschaeftigt, abgelaufene Anmeldung. Was im Korb wartet, wird dann spaeter
     * erneut versucht und **nie** verworfen.
     */
    val istVoruebergehend: Boolean
        get() = when (this) {
            is KeineVerbindung, is NichtAngemeldet, is KiNichtErreichbar, is Beschaeftigt, is ZuVieleVersuche -> true
            is Server -> code >= 500 || code == 408
            else -> false
        }

    /**
     * Bei Chat und Sprache unklar, ob der Server die Nachricht hat: Verbindung
     * abgerissen oder ein Proxy meldet 502-504. Laut API.md 1b heisst das **nicht**
     * "verloren" - das Ergebnis wird abgeholt.
     */
    val istUnklar: Boolean
        get() = this is KeineVerbindung || this is KiNichtErreichbar || (this is Server && code in 503..504)

    companion object {
        fun ausStatus(code: Int, detail: String): ApiFehler = when (code) {
            401 -> NichtAngemeldet(detail)
            403 -> Verboten(detail)
            404 -> NichtGefunden(detail)
            409 -> Beschaeftigt(detail)
            413 -> ZuGross(detail)
            415 -> FalscherTyp(detail)
            422 -> Ungueltig(detail)
            429 -> ZuVieleVersuche(detail)
            502 -> KiNichtErreichbar(detail)
            else -> Server(code, detail)
        }

        /**
         * Holt `detail` aus dem Fehlerkoerper.
         *
         * Bei 422 liefert FastAPI unter `detail` eine Liste von Pruefmeldungen
         * statt eines Textes - deshalb wird nur ein echter String uebernommen
         * und sonst auf einen Ersatztext zurueckgefallen.
         */
        fun detailAusKoerper(json: Json, koerper: String?, code: Int): String {
            val text = koerper?.takeIf { it.isNotBlank() }?.let { roh ->
                runCatching {
                    json.parseToJsonElement(roh).jsonObject["detail"]?.jsonPrimitive?.content
                }.getOrNull()
            }
            return text ?: ersatztext(code)
        }

        private fun ersatztext(code: Int): String = when (code) {
            401 -> "Nicht angemeldet."
            403 -> "Zugriff ist nicht erlaubt."
            404 -> "Nicht gefunden."
            409 -> "Der Assistent bearbeitet gerade noch die vorige Anfrage."
            413 -> "Die Datei ist zu gross."
            415 -> "Das Foto konnte nicht gelesen werden."
            422 -> "Die Eingabe konnte nicht verarbeitet werden."
            429 -> "Zu viele Fehlversuche. Bitte später erneut versuchen."
            502 -> "Der Assistent ist gerade nicht erreichbar."
            else -> "Unerwarteter Fehler vom Assistenten (HTTP $code)."
        }
    }
}
