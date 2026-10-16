package de.chefassistent.app.monteur

import de.chefassistent.app.R
import de.chefassistent.app.monteur.data.Artikel
import de.chefassistent.app.monteur.data.ArtikelWahl
import de.chefassistent.app.monteur.data.KarteProjekt
import de.chefassistent.app.monteur.data.Material
import de.chefassistent.app.monteur.data.materialJson
import de.chefassistent.app.monteur.ui.bericht.neuePosition
import de.chefassistent.app.monteur.ui.karte.BestellZeile
import de.chefassistent.app.monteur.ui.karte.MELDUNG_MAX_ZEICHEN
import de.chefassistent.app.monteur.ui.karte.bestellAenderungen
import de.chefassistent.app.monteur.ui.karte.bestellAnzeige
import de.chefassistent.app.monteur.ui.karte.bestellStart
import de.chefassistent.app.monteur.ui.karte.meldungArtText
import de.chefassistent.app.monteur.ui.karte.meldungText
import de.chefassistent.app.monteur.ui.karte.mengeSetzen
import de.chefassistent.app.monteur.ui.karte.projektText
import de.chefassistent.app.monteur.ui.karte.wahlSetzen
import de.chefassistent.app.monteur.ui.karte.zeileAnhaengen
import de.chefassistent.app.monteur.ui.karte.zeileEntfernen
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Karten „Material bestellen“ und „Info ans Büro“ (Auftrag 1.23, 2.): lokaler Stand und was bei „Passt“ mitgeht. */
class BestellungTest {

    private val halb = Artikel(nr = "100", name = "Eckventil 1/2\" x 3/8\"", einheit = "Stk")
    private val dreiAchtel = Artikel(nr = "101", name = "Eckventil 3/8\" x 3/8\"", einheit = "Stk")

    private val material = listOf(
        Material(
            text = "Eckventil", menge = 3.0, einheit = "Stk",
            vorschlaege = listOf(halb, dreiAchtel), rueckfrage = "Welche Dimension?",
        ),
        Material(text = "Flexschlauch", menge = 2.0, einheit = "Stk", artikel = Artikel("200", "Flexschlauch 30 cm", "Stk")),
    )

    @Test
    fun `unveraendert geht nichts mit`() {
        assertNull(bestellAenderungen(material, bestellStart(material)))
    }

    @Test
    fun `Menge hin und zurueck zaehlt nicht als Aenderung`() {
        val z = mengeSetzen(mengeSetzen(bestellStart(material), 1, 3.0), 1, 2.0)
        assertNull(bestellAenderungen(material, z))
    }

    @Test
    fun `Menge geaendert schickt die vollstaendige Liste`() {
        val liste = bestellAenderungen(material, mengeSetzen(bestellStart(material), 1, 5.0))!!
        assertEquals(listOf(0, 1), liste.map { it.index })
        assertEquals(listOf(3.0, 5.0), liste.map { it.menge })
        assertTrue(liste.all { it.artikel == ArtikelWahl.Behalten })
        assertTrue(liste.all { it.text == null && it.einheit == null })
    }

    @Test
    fun `Vorschlag waehlen loest die Rueckfrage und sendet die Nummer`() {
        val z = wahlSetzen(bestellStart(material), 0, halb)
        val liste = bestellAenderungen(material, z)!!
        assertEquals(ArtikelWahl.Waehlen("100"), liste[0].artikel)
        assertEquals(ArtikelWahl.Behalten, liste[1].artikel)

        val a = bestellAnzeige(material, z)[0]
        assertNull(a.rueckfrage)
        assertEquals(halb.name, a.name)
        assertEquals("100", a.artikelNr)
        assertEquals("Eckventil", a.diktat)
        assertTrue(a.geaendert)
    }

    @Test
    fun `Frei lassen loest die Rueckfrage und sendet artikel_nr null`() {
        val z = wahlSetzen(bestellStart(material), 0, null)
        val liste = bestellAenderungen(material, z)!!
        assertEquals(ArtikelWahl.Frei, liste[0].artikel)
        assertEquals(JsonNull, materialJson(liste)[0].jsonObject["artikel_nr"])

        val a = bestellAnzeige(material, z)[0]
        assertNull(a.rueckfrage)
        assertNull(a.artikelNr)
        assertEquals("Eckventil", a.name)
    }

    @Test
    fun `offene Rueckfrage bleibt markiert`() {
        val a = bestellAnzeige(material, bestellStart(material))
        assertEquals("Welche Dimension?", a[0].rueckfrage)
        assertFalse(a[0].geaendert)
        assertNull(a[1].rueckfrage)
        // Gewaehlter Katalogartikel: Name des Artikels, Diktat darunter.
        assertEquals("Flexschlauch 30 cm", a[1].name)
        assertEquals("200", a[1].artikelNr)
    }

    @Test
    fun `entfernte Position fehlt, die anderen behalten ihren Index`() {
        val liste = bestellAenderungen(material, zeileEntfernen(bestellStart(material), 0))!!
        assertEquals(1, liste.size)
        assertEquals(1, liste[0].index)
        assertEquals(1, bestellAnzeige(material, zeileEntfernen(bestellStart(material), 0)).size)
    }

    @Test
    fun `Suchtreffer wird neue Position mit Nummer und ohne Index`() {
        val treffer = Artikel("300", "Kugelhahn 1/2\"", "Stk")
        val z = zeileAnhaengen(bestellStart(material), neuePosition(2.0, treffer.nr), treffer)
        val liste = bestellAenderungen(material, z)!!
        assertEquals(3, liste.size)
        assertNull(liste[2].index)
        assertEquals(2.0, liste[2].menge, 0.0)
        assertEquals(ArtikelWahl.Waehlen("300"), liste[2].artikel)
        assertNull(liste[2].text)

        val a = bestellAnzeige(material, z)[2]
        assertEquals("Kugelhahn 1/2\"", a.name)
        assertEquals("Stk", a.einheit)
        assertTrue(a.neu)
        assertNull(a.rueckfrage)

        val json = materialJson(liste)[2].jsonObject
        assertFalse("index" in json)
        assertEquals("300", json["artikel_nr"]!!.jsonPrimitive.content)
    }

    @Test
    fun `freie neue Position schickt Text und Einheit`() {
        val z = zeileAnhaengen(bestellStart(material), neuePosition(4.0, null, text = " Silikon weiß ", einheit = "Kart"), null)
        val neu = bestellAenderungen(material, z)!!.last()
        assertNull(neu.index)
        assertEquals("Silikon weiß", neu.text)
        assertEquals("Kart", neu.einheit)
        assertEquals(ArtikelWahl.Frei, neu.artikel)
        val a = bestellAnzeige(material, z).last()
        assertEquals("Silikon weiß", a.name)
        assertEquals("Kart", a.einheit)
    }

    @Test
    fun `alle Positionen entfernt ist eine leere Liste, nicht unveraendert`() {
        val z = zeileEntfernen(zeileEntfernen(bestellStart(material), 0), 0)
        assertEquals(emptyList<Any>(), bestellAenderungen(material, z))
    }

    @Test
    fun `lokaler Stand ueberlebt den Weg durchs Bundle`() {
        val z = zeileAnhaengen(wahlSetzen(bestellStart(material), 0, halb), neuePosition(1.0, "300"), Artikel("300", "Kugelhahn", "Stk"))
        val json = Json { ignoreUnknownKeys = true }
        val zurueck = json.decodeFromString<List<BestellZeile>>(json.encodeToString(z))
        assertEquals(z, zurueck)
    }

    @Test
    fun `Meldungstext nur, wenn geaendert`() {
        assertNull(meldungText("Kunde nicht da.", "Kunde nicht da."))
        assertEquals("Kunde nicht da, 9:10 geklingelt.", meldungText("Kunde nicht da.", "Kunde nicht da, 9:10 geklingelt."))
        assertNull(meldungText("Kunde nicht da.", "   "))
        assertEquals(MELDUNG_MAX_ZEICHEN, meldungText("", "x".repeat(3000))!!.length)
    }

    @Test
    fun `Meldungsarten aus dem Vertrag haben einen Namen, Unbekanntes bleibt roh`() {
        assertEquals(R.string.bs_art_nicht_angetroffen, meldungArtText("nicht_angetroffen"))
        assertEquals(R.string.bs_art_verspaetung, meldungArtText("verspaetung"))
        assertEquals(R.string.bs_art_mangel, meldungArtText("mangel"))
        assertEquals(R.string.bs_art_nachtrag, meldungArtText("nachtrag"))
        assertEquals(R.string.bs_art_sonstiges, meldungArtText("sonstiges"))
        assertNull(meldungArtText("neu_erfunden"))
    }

    @Test
    fun `Projekt als Nummer und Kunde`() {
        assertEquals("UNB-795 · Schulz", projektText(KarteProjekt(1, "UNB-795", "Schulz")))
        assertEquals("UNB-795", projektText(KarteProjekt(1, "UNB-795", "")))
        assertEquals("", projektText(null))
    }
}
