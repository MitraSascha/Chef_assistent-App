package de.chefassistent.app

import de.chefassistent.app.data.ApiFehler
import de.chefassistent.app.data.Einstellungen
import de.chefassistent.app.data.Netzwerk
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetzwerkTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `ergaenzt fehlendes Schema und Schraegstrich`() {
        assertEquals(
            "https://assistent.mitra-sanitaer.de/",
            Netzwerk.normalisiereUrl("assistent.mitra-sanitaer.de"),
        )
        assertEquals(
            "https://mitraserver-aspire-xc-605.tail8bd028.ts.net/",
            Netzwerk.normalisiereUrl("mitraserver-aspire-xc-605.tail8bd028.ts.net"),
        )
    }

    @Test
    fun `beide bekannten Adressen sind schon normalisiert`() {
        // Sonst erkennt der Anmeldebildschirm die Chips nicht als gewaehlt.
        assertEquals(Einstellungen.STANDARD_BASIS_URL, Netzwerk.normalisiereUrl(Einstellungen.STANDARD_BASIS_URL))
        assertEquals(Einstellungen.TAILSCALE_BASIS_URL, Netzwerk.normalisiereUrl(Einstellungen.TAILSCALE_BASIS_URL))
        assertEquals("https://assistent.mitra-sanitaer.de/", Einstellungen.STANDARD_BASIS_URL)
    }

    @Test
    fun `nur die Anmeldung geht ohne Bearer raus`() {
        assertTrue(Netzwerk.istAnmeldung("/v1/anmelden"))
        assertTrue(Netzwerk.istAnmeldung("/v1/anmelden/"))
        assertFalse(Netzwerk.istAnmeldung("/v1/abmelden"))
        assertFalse(Netzwerk.istAnmeldung("/v1/ich"))
        assertFalse(Netzwerk.istAnmeldung("/v1/geraete"))
    }

    @Test
    fun `429 zeigt detail des Servers`() {
        val detail = ApiFehler.detailAusKoerper(
            json,
            """{"detail": "Zu viele Fehlversuche. Bitte in 15 Minuten erneut versuchen."}""",
            429,
        )
        val fehler = ApiFehler.ausStatus(429, detail)
        assertTrue(fehler is ApiFehler.ZuVieleVersuche)
        assertEquals("Zu viele Fehlversuche. Bitte in 15 Minuten erneut versuchen.", fehler.anzeigetext)
    }

    @Test
    fun `429 ohne detail hat einen deutschen Ersatztext`() {
        assertEquals(
            "Zu viele Fehlversuche. Bitte später erneut versuchen.",
            ApiFehler.detailAusKoerper(json, "", 429),
        )
    }

    @Test
    fun `laesst http stehen, wenn es ausdruecklich angegeben ist`() {
        assertEquals("http://server.lan/", Netzwerk.normalisiereUrl("http://server.lan"))
    }

    @Test
    fun `entfernt Leerzeichen aus getippten Adressen`() {
        assertEquals("https://server.lan/", Netzwerk.normalisiereUrl("  server.lan  "))
    }

    @Test
    fun `leere Eingabe bleibt leer`() {
        assertEquals("", Netzwerk.normalisiereUrl("   "))
    }

    @Test
    fun `nimmt detail aus dem Fehlerkoerper`() {
        val text = ApiFehler.detailAusKoerper(
            json,
            """{"detail": "Das Gespräch ist noch beschäftigt."}""",
            409,
        )
        assertEquals("Das Gespräch ist noch beschäftigt.", text)
    }

    @Test
    fun `faellt bei FastAPI-Pruefmeldungen auf einen Ersatztext zurueck`() {
        // Bei 422 ist `detail` eine Liste, kein Text - das darf nicht zu
        // "[{loc=...}]" auf dem Bildschirm fuehren.
        val koerper = """{"detail":[{"loc":["body","text"],"msg":"field required"}]}"""
        val text = ApiFehler.detailAusKoerper(json, koerper, 422)

        assertEquals("Die Eingabe konnte nicht verarbeitet werden.", text)
    }

    @Test
    fun `haelt kaputtes JSON aus`() {
        val text = ApiFehler.detailAusKoerper(json, "<html>502 Bad Gateway</html>", 502)
        assertEquals("Der Assistent ist gerade nicht erreichbar.", text)
    }

    @Test
    fun `leerer Koerper ergibt Ersatztext`() {
        assertEquals("Nicht angemeldet.", ApiFehler.detailAusKoerper(json, null, 401))
    }

    @Test
    fun `ordnet Statuscodes den richtigen Fehlern zu`() {
        assertTrue(ApiFehler.ausStatus(401, "x") is ApiFehler.NichtAngemeldet)
        assertTrue(ApiFehler.ausStatus(403, "x") is ApiFehler.Verboten)
        assertTrue(ApiFehler.ausStatus(404, "x") is ApiFehler.NichtGefunden)
        assertTrue(ApiFehler.ausStatus(409, "x") is ApiFehler.Beschaeftigt)
        assertTrue(ApiFehler.ausStatus(413, "x") is ApiFehler.ZuGross)
        assertTrue(ApiFehler.ausStatus(415, "x") is ApiFehler.FalscherTyp)
        assertTrue(ApiFehler.ausStatus(422, "x") is ApiFehler.Ungueltig)
        assertTrue(ApiFehler.ausStatus(429, "x") is ApiFehler.ZuVieleVersuche)
        assertTrue(ApiFehler.ausStatus(502, "x") is ApiFehler.KiNichtErreichbar)

        val sonstiger = ApiFehler.ausStatus(500, "x")
        assertTrue(sonstiger is ApiFehler.Server)
        assertEquals(500, (sonstiger as ApiFehler.Server).code)
    }
}
