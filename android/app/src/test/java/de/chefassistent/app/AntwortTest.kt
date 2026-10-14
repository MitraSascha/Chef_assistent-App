package de.chefassistent.app

import de.chefassistent.app.ui.antwort.Abschnitt
import de.chefassistent.app.ui.antwort.Ausrichtung
import de.chefassistent.app.ui.antwort.abschnitte
import de.chefassistent.app.ui.antwort.diagrammLesen
import de.chefassistent.app.ui.antwort.istZahl
import de.chefassistent.app.ui.antwort.skala
import de.chefassistent.app.ui.antwort.textZumKopieren
import de.chefassistent.app.ui.antwort.wertebereich
import de.chefassistent.app.ui.antwort.zahlKurz
import de.chefassistent.app.ui.antwort.zahlLang
import de.chefassistent.app.ui.antwort.zellen
import de.chefassistent.app.ui.markdownEntfernen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Zerlegen der Antwort und Diagramme nach `API.md` 1a (v1.14.0). */
class AntwortTest {

    /** Beispiel aus `API.md` 1a, mit vollstaendiger Tabelle. */
    private val beispiel = """
        Boese ist mit 1.820 € der größte offene Posten.

        ```diagramm
        {"art": "balken", "titel": "Offene Rechnungen je Kunde", "einheit": "€", "x": ["Boese", "Dreißig", "Valovo"], "reihen": [{"name": "Offen", "werte": [1820.25, 830, 455]}]}
        ```

        |  | Offen |
        |---|---|
        | Boese | 1.820,25 € |
        | Dreißig | 830,00 € |
        | Valovo | 455,00 € |

        Soll ich Mahnungen vorbereiten?
    """.trimIndent()

    @Test
    fun `zerlegt das Beispiel aus dem Vertrag`() {
        val teile = abschnitte(beispiel)
        assertEquals(4, teile.size)
        assertTrue(teile[0] is Abschnitt.Text)
        val diagramm = (teile[1] as Abschnitt.Diagramm).daten
        assertEquals("balken", diagramm.art)
        assertEquals(listOf(1820.25, 830.0, 455.0), diagramm.reihen.single().werte)
        val tabelle = teile[2] as Abschnitt.Tabelle
        assertEquals(listOf("", "Offen"), tabelle.kopf)
        assertEquals(3, tabelle.zeilen.size)
        assertEquals("Soll ich Mahnungen vorbereiten?", (teile[3] as Abschnitt.Text).markdown)
    }

    @Test
    fun `Satz mit Strich vor einer Linie ist keine Tabelle`() {
        val teile = abschnitte("Entweder A | oder B\n---\nWeiter im Text.")
        assertTrue(teile.none { it is Abschnitt.Tabelle })
        // Unterschiedliche Spaltenzahl in Kopf und Trennzeile ebenfalls nicht.
        assertTrue(abschnitte("| a | b | c |\n|---|---|\n| 1 | 2 | 3 |").none { it is Abschnitt.Tabelle })
        // Eine echte Tabelle bleibt eine.
        assertTrue(abschnitte("| a | b |\n|---|--:|\n| 1 | 2 |").any { it is Abschnitt.Tabelle })
    }

    @Test
    fun `ungueltiges Diagramm faellt weg, die Tabelle bleibt`() {
        val teile = abschnitte("```diagramm\n{kaputt\n```\n\n| a | b |\n|---|---|\n| 1 | 2 |")
        assertEquals(1, teile.size)
        assertTrue(teile[0] is Abschnitt.Tabelle)
    }

    @Test
    fun `unbekannte Felder im Diagramm schaden nicht`() {
        val d = diagrammLesen("""{"art":"linie","titel":"t","x":["a","b"],"reihen":[{"name":"r","werte":[1,null],"farbe":"rot"}],"neu":1}""")
        assertNotNull(d)
        assertNull(d!!.reihen[0].werte[1])
    }

    @Test
    fun `Grenzen aus dem Vertrag werden geprueft`() {
        // werte nicht so lang wie x
        assertNull(diagrammLesen("""{"art":"balken","titel":"t","x":["a","b"],"reihen":[{"name":"r","werte":[1]}]}"""))
        // Linie braucht mindestens zwei Punkte
        assertNull(diagrammLesen("""{"art":"linie","titel":"t","x":["a"],"reihen":[{"name":"r","werte":[1]}]}"""))
        // Torte: genau eine Reihe, alle Werte > 0
        assertNull(diagrammLesen("""{"art":"torte","titel":"t","x":["a","b"],"reihen":[{"name":"r","werte":[1,0]}]}"""))
        assertNotNull(diagrammLesen("""{"art":"torte","titel":"t","x":["a","b"],"reihen":[{"name":"r","werte":[1,3]}]}"""))
        // Unbekannte Art
        assertNull(diagrammLesen("""{"art":"radar","titel":"t","x":["a","b"],"reihen":[{"name":"r","werte":[1,3]}]}"""))
        // Gestapelt ohne negative Werte
        assertNull(diagrammLesen("""{"art":"balken","titel":"t","gestapelt":true,"x":["a"],"reihen":[{"name":"r","werte":[-1]},{"name":"s","werte":[2]}]}"""))
    }

    @Test
    fun `gestapelte Balken skalieren nach der Summe`() {
        val d = diagrammLesen("""{"art":"balken","titel":"t","gestapelt":true,"x":["a","b"],"reihen":[{"name":"r","werte":[3,1]},{"name":"s","werte":[4,null]}]}""")!!
        assertEquals(0.0 to 7.0, d.wertebereich())
    }

    @Test
    fun `andere Codebloecke bleiben Code`() {
        val teile = abschnitte("Pfad:\n```\nM:\\Projekte\\HEZ\n```")
        assertEquals("M:\\Projekte\\HEZ", (teile[1] as Abschnitt.Code).inhalt)
    }

    @Test
    fun `Tabellen-Ausrichtung und Zellen`() {
        val teile = abschnitte("| A | B | C |\n|:--|:-:|--:|\n| x \\| y | 2 |")
        val t = teile.single() as Abschnitt.Tabelle
        assertEquals(listOf(Ausrichtung.START, Ausrichtung.MITTE, Ausrichtung.ENDE), t.ausrichtung)
        // Fehlende Zelle aufgefuellt, \| bleibt Strich
        assertEquals(listOf("x | y", "2", ""), t.zeilen.single())
        assertEquals(listOf("a", "b"), zellen("| a | b |"))
    }

    @Test
    fun `Zahlenzellen werden erkannt`() {
        assertTrue(istZahl("1.820,25 €"))
        assertTrue(istZahl("**455,00 €**"))
        assertTrue(istZahl("-12 %"))
        assertTrue(istZahl("3,5 h"))
        assertFalse(istZahl("Boese"))
        assertFalse(istZahl("SAN-1166"))
    }

    @Test
    fun `y-Achse beginnt bei 0 und hat runde Schritte`() {
        val s = skala(0.0, 1820.25)
        assertEquals(0.0, s.unten, 0.0)
        assertEquals(500.0, s.schritt, 0.0)
        assertEquals(2000.0, s.oben, 0.0)

        val negativ = skala(-30.0, 80.0)
        assertTrue(negativ.unten <= -30.0)
        assertTrue(negativ.striche.contains(0.0))
    }

    @Test
    fun `Zahlen deutsch und gekuerzt`() {
        assertEquals("1.820,25", zahlLang(1820.25))
        assertEquals("830", zahlLang(830.0))
        assertEquals("25 Tsd.", zahlKurz(25_000.0))
        assertEquals("1,2 Mio.", zahlKurz(1_200_000.0))
        assertEquals("2.000", zahlKurz(2000.0))
    }

    @Test
    fun `Kopieren und Vorlesen lassen den Diagrammblock weg`() {
        val kopie = textZumKopieren(beispiel)
        assertFalse(kopie.contains("diagramm"))
        assertFalse(kopie.contains("\"art\""))
        assertTrue(kopie.contains("| Boese | 1.820,25 € |"))

        val gesprochen = markdownEntfernen(beispiel)
        assertFalse(gesprochen.contains("\"art\""))
    }
}
