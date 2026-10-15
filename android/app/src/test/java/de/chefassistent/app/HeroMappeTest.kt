package de.chefassistent.app

import de.chefassistent.app.data.HERO_DATEI_STATUS
import de.chefassistent.app.data.HeroMappe
import de.chefassistent.app.data.HeroMappeAnfrage
import de.chefassistent.app.data.Netzwerk
import de.chefassistent.app.data.Notizen
import de.chefassistent.app.data.istHeroProjektordner
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v1.19.0: HERO-Projekt anheften (API.md 4, Auftrag 1.19). */
class HeroMappeTest {

    private val json = Netzwerk.json

    @Test
    fun `liest die HERO-Mappe aus dem Beispiel in API_md`() {
        val mappe = json.decodeFromString<HeroMappe>(
            """
            {"name": "SAN-660 Firma WEG Trelleborger Straße 14 in 13189 Berlin",
             "pfad": "Notizen/Sascha/SAN-660 Firma WEG Trelleborger Straße 14 in 13189 Berlin",
             "projekt": "SAN-660", "hero_ordner": "HERO-Archiv/SAN-660 Firma WEG Trelleborger Straße 14 in 13189 Berlin",
             "dateien": [{"quelle": "Notizen/Sascha/SAN-660 …/2026-10-15 09-12-04.jpg", "ziel": "2026-10-15 09-12-04.jpg",
                          "status": "kopiert", "zeit": "2026-10-15T07:13:10+00:00", "neu": 1}],
             "kuenftig": true}
            """.trimIndent()
        )

        assertEquals("SAN-660", mappe.projekt)
        assertTrue(mappe.pfad.startsWith("Notizen/Sascha/SAN-660"))
        assertEquals("kopiert", mappe.dateien.single().status)
    }

    @Test
    fun `Datei ohne Ziel ist erlaubt`() {
        val mappe = json.decodeFromString<HeroMappe>(
            """{"name": "SAN-1", "pfad": "Notizen/Sascha/SAN-1", "projekt": "SAN-1", "hero_ordner": "HERO-Archiv/SAN-1",
                "dateien": [{"quelle": "Notizen/Sascha/SAN-1/a.mp4", "ziel": null, "status": "video", "zeit": "2026-10-15T07:13:10+00:00"}]}"""
        )
        assertNull(mappe.dateien.single().ziel)
    }

    @Test
    fun `Anfrage schickt nur das Projekt`() {
        assertEquals("""{"projekt":"HERO-Archiv/SAN-660 Firma"}""", json.encodeToString(HeroMappeAnfrage("HERO-Archiv/SAN-660 Firma")))
    }

    @Test
    fun `Notizordner mit und ohne hero_projekt`() {
        val notizen = json.decodeFromString<Notizen>(
            """
            {"wurzel": "Notizen/Sascha",
             "ordner": [
               {"name": "SAN-660 Firma WEG", "pfad": "Notizen/Sascha/SAN-660 Firma WEG", "notizen": 1,
                "hero_projekt": "SAN-660", "geaendert": "2026-10-15T09:00:00+02:00"},
               {"name": "Begehung", "pfad": "Notizen/Sascha/Begehung", "notizen": 2, "hero_projekt": null,
                "geaendert": "2026-10-15T09:00:00+02:00"},
               {"name": "Alt", "pfad": "Notizen/Sascha/Alt", "notizen": 0, "geaendert": "2026-10-15T09:00:00+02:00"}
             ],
             "notizen": []}
            """.trimIndent()
        )
        assertEquals(listOf("SAN-660", null, null), notizen.ordner.map { it.hero_projekt })
    }

    @Test
    fun `nur Projektordner direkt im HERO-Archiv zaehlen`() {
        assertTrue(istHeroProjektordner("HERO-Archiv/SAN-660 Firma WEG Trelleborger Straße 14"))
        assertFalse(istHeroProjektordner("HERO-Archiv/SAN-660 Firma WEG/Fotos"))
        assertFalse(istHeroProjektordner("HERO-Archiv/SAN-660/Rechnungen"))
        assertFalse(istHeroProjektordner("HERO-Archiv"))
        assertFalse(istHeroProjektordner("Notizen/Sascha"))
    }

    @Test
    fun `Dateistatus stimmen mit dem Vertrag ueberein`() {
        val schema = Json.parseToJsonElement(vertragsDatei().readText()).jsonObject
        val werte = schema["components"]!!.jsonObject["schemas"]!!.jsonObject["HeroMappeDatei"]!!
            .jsonObject["properties"]!!.jsonObject["status"]!!.jsonObject["enum"]!!.jsonArray
            .map { it.jsonPrimitive.content }
        assertEquals(werte.toSet(), HERO_DATEI_STATUS.toSet())
    }

    @Test
    fun `Vertrag kennt hero_projekt an Notizordnern und den Endpunkt`() {
        val schema = Json.parseToJsonElement(vertragsDatei().readText()).jsonObject
        val ordner = schema["components"]!!.jsonObject["schemas"]!!.jsonObject["NotizOrdner"]!!
            .jsonObject["properties"]!!.jsonObject
        assertTrue(ordner.containsKey("hero_projekt"))
        val pfad = schema["paths"]!!.jsonObject["/v1/hero-mappe"]!!.jsonObject
        assertTrue(pfad.containsKey("post") && pfad.containsKey("get"))
    }

    private fun vertragsDatei(): File {
        var ordner: File? = File("").absoluteFile
        while (ordner != null) {
            val treffer = File(ordner, "openapi.json")
            if (treffer.isFile) return treffer
            ordner = ordner.parentFile
        }
        throw AssertionError("openapi.json nicht gefunden - der Vertrag gehoert in den Projektstamm.")
    }
}
