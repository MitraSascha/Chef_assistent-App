package de.chefassistent.app.monteur

import de.chefassistent.app.monteur.data.AbwVorschlag
import de.chefassistent.app.monteur.data.ZEIT_ARTEN_ANLEGEN
import de.chefassistent.app.monteur.data.ZeitAbschnitt
import de.chefassistent.app.monteur.ui.karte.KartenEingabe
import de.chefassistent.app.monteur.ui.karte.abwesenheitNormal
import de.chefassistent.app.monteur.ui.zeiten.abschnittEntfernen
import de.chefassistent.app.monteur.ui.zeiten.abschnittErsetzen
import de.chefassistent.app.monteur.ui.zeiten.abschnittMinuten
import de.chefassistent.app.monteur.ui.zeiten.arbeitMinuten
import de.chefassistent.app.monteur.ui.zeiten.artFuerBuchung
import de.chefassistent.app.monteur.ui.zeiten.artenZurWahl
import de.chefassistent.app.monteur.ui.zeiten.mitProjekt
import de.chefassistent.app.monteur.ui.zeiten.projektFuerArt
import de.chefassistent.app.monteur.ui.zeiten.vorschlagZuZeit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Karten „Zeiten“ und „Urlaub“ sowie die Art `lager` (Auftrag 1.23, 2. und 6.). */
class KarteZeitenTest {

    private val tag = listOf(
        ZeitAbschnitt("07:00", "07:45", "lager"),
        ZeitAbschnitt("07:45", "08:15", "fahrt"),
        ZeitAbschnitt("08:15", "12:00", "arbeit"),
        ZeitAbschnitt("12:00", "12:30", "pause"),
        ZeitAbschnitt("12:30", "13:00", "material"),
    )

    @Test
    fun `Arbeitszeit zaehlt alles ausser Pause`() {
        // 45 + 30 + 225 + 30 = 330 Minuten, die Pause nicht
        assertEquals(330, arbeitMinuten(tag))
        assertEquals(0, arbeitMinuten(emptyList()))
    }

    @Test
    fun `Abschnitt ueber Mitternacht und Unlesbares`() {
        assertEquals(90, abschnittMinuten(ZeitAbschnitt("23:30", "01:00", "arbeit")))
        assertEquals(0, abschnittMinuten(ZeitAbschnitt("8 Uhr", "12:00", "arbeit")))
        assertEquals(0, abschnittMinuten(ZeitAbschnitt("08:00", "08:00", "arbeit")))
    }

    @Test
    fun `Aendern liefert die vollstaendige Liste`() {
        val neu = ZeitAbschnitt("07:00", "07:30", "lager")
        val liste = abschnittErsetzen(tag, 0, neu)
        assertEquals(tag.size, liste.size)
        assertEquals(neu, liste[0])
        assertEquals(tag.drop(1), liste.drop(1))
        // Ausserhalb der Liste passiert nichts
        assertEquals(tag, abschnittErsetzen(tag, 9, neu))
    }

    @Test
    fun `Entfernen nimmt genau einen Abschnitt`() {
        val liste = abschnittEntfernen(tag, 3)
        assertEquals(4, liste.size)
        assertFalse(liste.any { it.art == "pause" })
    }

    @Test
    fun `lager wird nicht zu arbeit`() {
        assertEquals("lager", artFuerBuchung("lager"))
        assertEquals("arbeit", artFuerBuchung("irgendwas"))
        val zeit = vorschlagZuZeit(ZeitAbschnitt("07:00", "07:45", "lager"), "2026-10-20", "a1")
        assertEquals("lager", zeit?.art)
        assertNull(zeit?.projekt_nr)
    }

    @Test
    fun `Zur Wahl nur die fuenf Arten, fremde nur als aktuelle`() {
        assertEquals(ZEIT_ARTEN_ANLEGEN, artenZurWahl(null))
        assertEquals(ZEIT_ARTEN_ANLEGEN, artenZurWahl("lager"))
        assertEquals(ZEIT_ARTEN_ANLEGEN + "buero", artenZurWahl("buero"))
        assertFalse("vor_ort" in artenZurWahl(null))
        assertFalse("sonstige" in artenZurWahl("arbeit"))
    }

    @Test
    fun `Lager, Fahrt, Pause ohne Projekt`() {
        assertFalse(mitProjekt("lager"))
        assertFalse(mitProjekt("fahrt"))
        assertFalse(mitProjekt("pause"))
        assertTrue(mitProjekt("arbeit"))
        assertTrue(mitProjekt("material"))
        assertEquals("", projektFuerArt("lager", "SAN-660"))
        assertEquals("", projektFuerArt("fahrt", "SAN-660"))
        assertEquals("SAN-660", projektFuerArt("arbeit", " SAN-660 "))
        assertEquals("", projektFuerArt("arbeit", null))
    }

    @Test
    fun `Urlaub geht mit reinem Datum raus`() {
        val v = AbwVorschlag("urlaub", "2026-10-22T00:00", "2026-10-23", halber_tag_ende = true, kommentar = "Umzug")
        val n = abwesenheitNormal(v)
        assertEquals("2026-10-22", n.von)
        assertEquals("2026-10-23", n.bis)
        assertTrue(n.halber_tag_ende)
        assertEquals("Umzug", n.kommentar)
    }

    @Test
    fun `KartenEingabe schickt die Aenderungen mit`() {
        val eingabe = KartenEingabe()
        assertNull(eingabe.passt().zeiten)
        eingabe.zeiten = abschnittErsetzen(tag, 1, ZeitAbschnitt("07:45", "08:30", "fahrt"))
        eingabe.abwesenheit = AbwVorschlag("urlaub", "2026-10-22", "2026-10-23")
        val passt = eingabe.passt()
        assertEquals(5, passt.zeiten?.size)
        assertEquals("08:30", passt.zeiten?.get(1)?.ende)
        assertEquals("2026-10-22", passt.abwesenheit?.von)
    }
}
