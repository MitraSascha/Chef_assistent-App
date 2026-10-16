package de.chefassistent.app.monteur

import de.chefassistent.app.monteur.data.ArtikelWahl
import de.chefassistent.app.monteur.data.Artikel
import de.chefassistent.app.monteur.data.Material
import de.chefassistent.app.monteur.data.ZeitAbschnitt
import de.chefassistent.app.monteur.data.materialJson
import de.chefassistent.app.monteur.ui.bericht.UNTERSCHRIFT_MAX_BYTES
import de.chefassistent.app.monteur.ui.bericht.datumText
import de.chefassistent.app.monteur.ui.bericht.kundeAusTitel
import de.chefassistent.app.monteur.ui.bericht.materialAenderungen
import de.chefassistent.app.monteur.ui.bericht.materialName
import de.chefassistent.app.monteur.ui.bericht.mengeSchritt
import de.chefassistent.app.monteur.ui.bericht.stundenSchritt
import de.chefassistent.app.monteur.ui.bericht.unterschriftSkalierung
import de.chefassistent.app.monteur.ui.bericht.zahlText
import de.chefassistent.app.monteur.ui.bericht.zeitZeile
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reine Logik des Bericht-Bildschirms (Auftrag 1.22, 3.). */
class BerichtTest {

    @Test
    fun `Stunden laufen in Viertelstunden`() {
        assertEquals(2.25, stundenSchritt(2.0, 1), 1e-9)
        assertEquals(1.75, stundenSchritt(2.0, -1), 1e-9)
    }

    @Test
    fun `krumme Stunden springen auf die naechste Viertelstunde in Druckrichtung`() {
        assertEquals(1.5, stundenSchritt(1.3, 1), 1e-9)
        assertEquals(1.25, stundenSchritt(1.3, -1), 1e-9)
        // Rundungsrauschen aus JSON darf keinen Viertelschritt verschlucken.
        assertEquals(1.0, stundenSchritt(1.2500000001, -1), 1e-9)
    }

    @Test
    fun `Stunden bleiben zwischen 0 und 24`() {
        assertEquals(0.0, stundenSchritt(0.0, -1), 1e-9)
        assertEquals(0.0, stundenSchritt(0.1, -1), 1e-9)
        assertEquals(24.0, stundenSchritt(24.0, 1), 1e-9)
    }

    @Test
    fun `Menge in ganzen Stueck, nie unter 1`() {
        assertEquals(3.0, mengeSchritt(2.0, 1), 1e-9)
        assertEquals(3.0, mengeSchritt(2.5, 1), 1e-9)
        assertEquals(2.0, mengeSchritt(2.5, -1), 1e-9)
        assertEquals(1.0, mengeSchritt(1.0, -1), 1e-9)
        assertEquals(0.5, mengeSchritt(0.5, -1), 1e-9)
        assertEquals(1.0, mengeSchritt(0.5, 1), 1e-9)
    }

    @Test
    fun `Zahlen auf Deutsch ohne ueberfluessige Nullen`() {
        assertEquals("2", zahlText(2.0))
        assertEquals("2,5", zahlText(2.5))
        assertEquals("0,75", zahlText(0.75))
        assertEquals("10", zahlText(10.0))
        assertEquals("0", zahlText(0.0))
    }

    private val liste = listOf(
        Material(text = "Eckventil", menge = 2.0, vorschlaege = listOf(Artikel("111", "Eckventil 1/2"), Artikel("222", "Eckventil 3/8")), rueckfrage = "1/2 oder 3/8?"),
        Material(text = "WC", menge = 1.0, artikel = Artikel("333", "Wand-WC")),
        Material(text = "Silikon", menge = 1.0),
    )

    @Test
    fun `Materialliste ist vollstaendig und behaelt sonst die Wahl`() {
        val aenderung = materialAenderungen(liste, mengen = mapOf(2 to 3.0), aenderungIndex = 0, wahl = ArtikelWahl.Waehlen("222"))
        assertEquals(listOf(0, 1, 2), aenderung.map { it.index })
        assertEquals(listOf(2.0, 1.0, 3.0), aenderung.map { it.menge })
        assertEquals(ArtikelWahl.Waehlen("222"), aenderung[0].artikel)
        assertEquals(ArtikelWahl.Behalten, aenderung[1].artikel)

        val json = materialJson(aenderung)
        assertEquals("222", json[0].jsonObject["artikel_nr"]!!.jsonPrimitive.content)
        // Behalten heisst: Feld fehlt ganz.
        assertFalse(json[1].jsonObject.containsKey("artikel_nr"))
    }

    @Test
    fun `Frei lassen schickt ausdruecklich null`() {
        val json = materialJson(materialAenderungen(liste, aenderungIndex = 0, wahl = ArtikelWahl.Frei))
        assertEquals(JsonNull, json[0].jsonObject["artikel_nr"])
    }

    @Test
    fun `Entfernen laesst genau die Position weg`() {
        val aenderung = materialAenderungen(liste, aenderungIndex = 1, entfernen = true)
        assertEquals(listOf(0, 2), aenderung.map { it.index })
    }

    @Test
    fun `Materialname ist der gewaehlte Artikel, sonst das Diktat`() {
        assertEquals("Wand-WC", materialName(liste[1]))
        assertEquals("Silikon", materialName(liste[2]))
    }

    @Test
    fun `Zeitzeile`() {
        assertEquals("07:30–10:15 · Arbeit", zeitZeile(ZeitAbschnitt("07:30", "10:15", "arbeit")) { if (it == "arbeit") "Arbeit" else it })
    }

    @Test
    fun `Datum und Kunde`() {
        assertEquals("16.10.2026", datumText("2026-10-16"))
        assertEquals("16.10.2026", datumText("2026-10-16T08:00:00+02:00"))
        assertEquals("gestern", datumText("gestern"))
        assertEquals("Böse", kundeAusTitel("SAN-1243 · Böse"))
        assertEquals("", kundeAusTitel("SAN-1243"))
        assertEquals("", kundeAusTitel(null))
    }

    @Test
    fun `Unterschrift wird nur ueber 2 MB verkleinert`() {
        assertNull(unterschriftSkalierung(UNTERSCHRIFT_MAX_BYTES))
        val f = unterschriftSkalierung(UNTERSCHRIFT_MAX_BYTES * 4)
        assertNotNull(f)
        // Flaeche ~ Bytes: ein Viertel der Bytes heisst knapp die halbe Kantenlaenge.
        assertTrue(f!! < 0.5f && f > 0.4f)
        // Auch bei kleinem Ueberschuss sicher kleiner, sonst liefe die Schleife ewig.
        assertTrue(unterschriftSkalierung(UNTERSCHRIFT_MAX_BYTES + 1)!! <= 0.9f)
    }

    @Test
    fun `neue Position aus dem Artikelstamm und als freie Position`() {
        val katalog = de.chefassistent.app.monteur.data.materialJson(
            listOf(de.chefassistent.app.monteur.ui.bericht.neuePosition(2.0, "EV"))
        ).toString()
        org.junit.Assert.assertEquals("""[{"menge":2.0,"artikel_nr":"EV"}]""", katalog)
        val frei = de.chefassistent.app.monteur.data.materialJson(
            listOf(de.chefassistent.app.monteur.ui.bericht.neuePosition(1.0, null, text = " Silikon ", einheit = "Kart"))
        ).toString()
        org.junit.Assert.assertEquals("""[{"menge":1.0,"text":"Silikon","einheit":"Kart","artikel_nr":null}]""", frei)
    }
}
