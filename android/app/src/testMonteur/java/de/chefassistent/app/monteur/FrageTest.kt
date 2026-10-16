package de.chefassistent.app.monteur

import de.chefassistent.app.monteur.data.AnleitungTreffer
import de.chefassistent.app.monteur.data.Artikel
import de.chefassistent.app.monteur.data.Quelle
import de.chefassistent.app.monteur.ui.karte.anleitungTitel
import de.chefassistent.app.monteur.ui.karte.artikelText
import de.chefassistent.app.monteur.ui.karte.frageKurzfassung
import de.chefassistent.app.monteur.ui.karte.istYoutube
import de.chefassistent.app.monteur.ui.karte.quellenTitel
import de.chefassistent.app.ui.ansehen.startIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Antwortkarte „frage“ (Auftrag 1.23, 2.): reine Logik ohne Oberflaeche. */
class FrageTest {

    @Test
    fun `YouTube wird am Host erkannt`() {
        assertTrue(istYoutube("https://www.youtube.com/watch?v=abc"))
        assertTrue(istYoutube("https://youtube.com/watch?v=abc"))
        assertTrue(istYoutube("https://m.youtube.com/watch?v=abc"))
        assertTrue(istYoutube("https://youtu.be/abc"))
        assertTrue(istYoutube("HTTPS://WWW.YOUTUBE.COM/shorts/x"))
    }

    @Test
    fun `aehnliche Adressen sind kein YouTube`() {
        assertFalse(istYoutube("https://youtube-fake.de/watch"))
        assertFalse(istYoutube("https://notyoutube.com/x"))
        assertFalse(istYoutube("https://example.com/?u=youtube.com"))
        assertFalse(istYoutube("kein link"))
        assertFalse(istYoutube(""))
    }

    @Test
    fun `Quellentitel faellt auf den Host zurueck`() {
        assertEquals("Vaillant Handbuch", quellenTitel(Quelle("Vaillant Handbuch", "https://vaillant.de/a")))
        assertEquals("vaillant.de", quellenTitel(Quelle("", "https://www.vaillant.de/a/b")))
        assertEquals("vaillant.de", quellenTitel(Quelle("  ", "https://vaillant.de")))
        assertEquals("kaputt", quellenTitel(Quelle("", "kaputt")))
    }

    @Test
    fun `Anleitung ohne Titel zeigt den Dateinamen`() {
        assertEquals("ecoTEC", anleitungTitel(AnleitungTreffer("Anleitungen/Vaillant/ecoTEC.pdf", 12, "")))
        assertEquals("Wartung", anleitungTitel(AnleitungTreffer("x/y.pdf", 1, "Wartung")))
    }

    @Test
    fun `Artikelchip ohne Preis`() {
        assertEquals("Eckventil 1/2 · 4711", artikelText(Artikel("4711", "Eckventil 1/2", "Stk")))
        assertEquals("4711", artikelText(Artikel("4711", "")))
    }

    @Test
    fun `Kurzfassung ohne Markdown und gekuerzt`() {
        assertEquals("Erst fett dann weiter", frageKurzfassung("Erst **fett**\n\ndann weiter"))
        val lang = frageKurzfassung("a".repeat(500), max = 10)
        assertEquals(11, lang.length)
        assertTrue(lang.endsWith("…"))
    }

    @Test
    fun `Startseite wird auf die Seitenzahl begrenzt`() {
        assertEquals(0, startIndex(0, 10))
        assertEquals(4, startIndex(4, 10))
        assertEquals(9, startIndex(25, 10))
        assertEquals(0, startIndex(-3, 10))
        assertEquals(0, startIndex(5, 0))
    }
}
