package de.chefassistent.app

import de.chefassistent.app.data.EINSATZ_STATUS
import de.chefassistent.app.data.EinsatzAenderung
import de.chefassistent.app.data.GU_WARNUNG_ARTEN
import de.chefassistent.app.data.GuPlan
import de.chefassistent.app.data.GuProjekte
import de.chefassistent.app.data.Netzwerk
import de.chefassistent.app.data.Notizen
import de.chefassistent.app.ui.plantafel.zeitraum
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

/** GU-Plantafel (`/v1/bauablauf`, seit v1.9.0). */
class PlantafelTest {

    private val json = Netzwerk.json

    @Test
    fun `Einsatzstatus stimmt mit dem Vertrag ueberein`() {
        assertEquals(enumAusVertrag("Einsatz", "status"), EINSATZ_STATUS.toSet())
    }

    @Test
    fun `Warnarten stimmen mit dem Vertrag ueberein`() {
        assertEquals(enumAusVertrag("GuWarnung", "art"), GU_WARNUNG_ARTEN.toSet())
    }

    @Test
    fun `liest die Plantafel aus API md`() {
        // Beispiel aus API.md, Abschnitt 4d, dazu ein unbekanntes Feld.
        val plan = json.decodeFromString<GuPlan>(
            """
            {"projekt": {"id": 3, "name": "Grünberger Str. 10", "ordner": "GU-Projekte/Grünberger Str. 10",
                         "von": "2026-10-09", "bis": "2026-10-20", "einsaetze": 2},
             "gewerke": [{"id": 7, "name": "Trockenbau", "firma": "Bau Nowak", "kontakt": "Herr Nowak",
                          "telefon": "0170 1234567", "farbe": "#718096"}],
             "einsaetze": [{"id": 12, "gewerk": "Trockenbau", "gewerk_id": 7, "farbe": "#718096", "firma": "Bau Nowak",
                            "von": "2026-10-09", "bis": "2026-10-13", "was": "Vorwände", "bereich": "WE 1 Bad",
                            "status": "bestaetigt", "nach": null, "neu_vom_server": 1}],
             "warnungen": [{"art": "reihenfolge", "einsaetze": [13, 12],
                            "text": "Fliesen beginnt Mo 12.10., aber Trockenbau ist erst Di 13.10. fertig."}]}
            """.trimIndent()
        )
        assertEquals("Grünberger Str. 10", plan.projekt.name)
        assertEquals(7, plan.einsaetze.single().gewerk_id)
        assertNull(plan.einsaetze.single().nach)
        assertEquals(listOf(13, 12), plan.warnungen.single().einsaetze)
    }

    @Test
    fun `Tippfehler im Jahr macht die Tafel nicht unendlich breit`() {
        // 2036 statt 2026: frueher rund 3700 Tage breit - mehr, als Compose zeichnen kann.
        val plan = json.decodeFromString<GuPlan>(
            """
            {"projekt": {"id": 3, "name": "X", "ordner": "GU-Projekte/X", "von": "2026-10-09", "bis": "2036-10-20", "einsaetze": 0},
             "gewerke": [], "einsaetze": [], "warnungen": []}
            """.trimIndent()
        )
        val heute = java.time.LocalDate.of(2026, 10, 13)
        val (anfang, ende) = zeitraum(plan, heute)
        assertTrue(java.time.temporal.ChronoUnit.DAYS.between(anfang, ende) <= 2 * 366)
        assertTrue(!anfang.isAfter(ende))
    }

    @Test
    fun `Projekt ohne Planung hat kein von und bis`() {
        val liste = json.decodeFromString<GuProjekte>(
            """{"projekte": [{"id": 1, "name": "X", "ordner": "GU-Projekte/X", "von": null, "bis": null, "einsaetze": 0}]}"""
        )
        assertNull(liste.projekte.single().von)
    }

    @Test
    fun `Einsatzaenderung schickt nur gesetzte Felder`() {
        val text = json.encodeToString(EinsatzAenderung.serializer(), EinsatzAenderung(von = "2026-10-12", folgende_mitverschieben = true))
        val obj = Json.parseToJsonElement(text).jsonObject
        assertEquals(setOf("von", "folgende_mitverschieben"), obj.keys)
        assertEquals("{}", json.encodeToString(EinsatzAenderung.serializer(), EinsatzAenderung()))
    }

    @Test
    fun `GU-Projektordner werden in den Notizen erkannt`() {
        val notizen = json.decodeFromString<Notizen>(
            """{"wurzel": "Notizen/Sascha", "ordner": [
                {"name": "Grünberger Str. 10", "pfad": "GU-Projekte/Grünberger Str. 10", "notizen": 2, "geaendert": "x"},
                {"name": "Begehung", "pfad": "Notizen/Sascha/Begehung", "notizen": 1, "geaendert": "x"}],
               "notizen": []}"""
        )
        assertTrue(notizen.ordner[0].istGuProjekt)
        assertFalse(notizen.ordner[1].istGuProjekt)
    }

    private fun enumAusVertrag(schema: String, feld: String): Set<String> {
        val wurzel = Json.parseToJsonElement(vertragsDatei().readText()).jsonObject
        val eigenschaft = wurzel["components"]!!.jsonObject["schemas"]!!.jsonObject[schema]!!
            .jsonObject["properties"]!!.jsonObject[feld]!!.jsonObject
        return eigenschaft["enum"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
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
