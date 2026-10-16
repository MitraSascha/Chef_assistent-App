package de.chefassistent.app

import de.chefassistent.app.data.AnmeldeAntwort
import de.chefassistent.app.data.Anmeldung
import de.chefassistent.app.data.AssistentRepository
import de.chefassistent.app.data.Geraet
import de.chefassistent.app.ui.geraete.ortszeit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * Anmeldung seit v1.10.0 (`API.md` Abschnitt 0). Der Vertrag im Repo ist noch
 * 1.9.0 - die Beispiele hier stammen aus `API.md`.
 */
class AnmeldungTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun `Anmeldung geht mit genau den vier Vertragsfeldern raus`() {
        val roh = json.encodeToString(Anmeldung("sascha", "geheim", "123456", "Galaxy S25 – App"))
        val objekt = json.parseToJsonElement(roh).jsonObject

        assertEquals(setOf("name", "passwort", "code", "geraet"), objekt.keys)
        assertEquals("123456", objekt["code"]!!.jsonPrimitive.content)
        assertEquals("Galaxy S25 – App", objekt["geraet"]!!.jsonPrimitive.content)
    }

    @Test
    fun `liest die AnmeldeAntwort aus API md`() {
        val antwort = json.decodeFromString<AnmeldeAntwort>(
            """{"token": "cs_abc", "geraet_id": 7, "name": "sascha", "anzeigename": "Sascha", "gueltig_tage": 60}"""
        )
        assertEquals("cs_abc", antwort.token)
        assertEquals(7, antwort.geraet_id)
        assertEquals("Sascha", antwort.anzeigename)
        assertEquals(60, antwort.gueltig_tage)
    }

    @Test
    fun `AnmeldeAntwort haelt unbekannte und fehlende Felder aus`() {
        val antwort = json.decodeFromString<AnmeldeAntwort>(
            """{"token": "cs_abc", "zwei_faktor": {"art": "totp"}, "neu": [1, 2]}"""
        )
        assertEquals("cs_abc", antwort.token)
        assertNull(antwort.geraet_id)
        assertNull(antwort.gueltig_tage)
    }

    @Test
    fun `liest die Geraeteliste`() {
        val liste = json.decodeFromString<List<Geraet>>(
            """
            [
              {"id": 7, "bezeichnung": "Galaxy S25 – App", "ip": "100.64.0.3",
               "erstellt": "2026-09-30T08:00:00+00:00", "zuletzt": "2026-09-30T09:15:00+00:00",
               "dieses": true, "browser": "egal"},
              {"id": 8, "bezeichnung": "Firefox – Weboberfläche", "ip": null,
               "erstellt": "2026-09-29T17:00:00+00:00", "zuletzt": "2026-09-29T17:05:00+00:00", "dieses": false},
              {"id": 9}
            ]
            """.trimIndent()
        )
        assertEquals(3, liste.size)
        assertTrue(liste[0].dieses)
        assertNull(liste[1].ip)
        assertFalse(liste[2].dieses)
        assertNull(liste[2].bezeichnung)
    }

    @Test
    fun `Geraetezeiten landen in der Geraetezone`() {
        // UTC 07:30 ist im Sommer 09:30 in Berlin - der Fehler von damals.
        assertEquals("30.09.2026 09:30", ortszeit("2026-09-30T07:30:00+00:00", ZoneId.of("Europe/Berlin")))
        assertEquals("30.09.2026 09:30", ortszeit("2026-09-30T07:30:00Z", ZoneId.of("Europe/Berlin")))
        // Ohne Offset unveraendert, Unlesbares roh.
        assertEquals("30.09.2026 07:30", ortszeit("2026-09-30 07:30:00", ZoneId.of("Europe/Berlin")))
        assertEquals("gestern", ortszeit("gestern", ZoneId.of("Europe/Berlin")))
    }

    @Test
    fun `Geraetebezeichnung bevorzugt den Verkaufsnamen`() {
        assertEquals("Galaxy S25 – App", AssistentRepository.geraetBezeichnung("Galaxy S25", "SM-S931B"))
        assertEquals("SM-S931B – App", AssistentRepository.geraetBezeichnung("  ", "SM-S931B"))
        assertEquals("SM-S931B – App", AssistentRepository.geraetBezeichnung(null, "SM-S931B"))
        assertEquals("Android – App", AssistentRepository.geraetBezeichnung(null, null))
    }

    @Test
    fun `Geraetebezeichnung bleibt unter der Vertragsgrenze`() {
        val lang = AssistentRepository.geraetBezeichnung("x".repeat(200), "SM-S931B")
        assertEquals(Anmeldung.MAX_GERAET, lang.length)
        assertTrue(lang.endsWith(" – App"))
    }
}
