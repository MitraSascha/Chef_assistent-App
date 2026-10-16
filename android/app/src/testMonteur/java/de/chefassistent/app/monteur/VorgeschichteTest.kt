package de.chefassistent.app.monteur

import de.chefassistent.app.data.Netzwerk
import de.chefassistent.app.monteur.data.Anlage
import de.chefassistent.app.monteur.data.Projektmappe
import de.chefassistent.app.monteur.data.TagTermin
import de.chefassistent.app.monteur.data.Vorgeschichte
import de.chefassistent.app.monteur.data.VorgeschichteQuellen
import de.chefassistent.app.monteur.ui.projekt.geraetZeile
import de.chefassistent.app.monteur.ui.projekt.hatInhalt
import de.chefassistent.app.monteur.ui.projekt.quellenText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VorgeschichteTest {
    private val json = Netzwerk.json

    @Test
    fun projektmappeMitVorgeschichte() {
        val m = json.decodeFromString(
            Projektmappe.serializer(),
            """{"id":5,"vorgeschichte":{"zusammenfassung":"Gas-Brennwerttherme im Keller.",
               "anlage":{"geraet":"Gas-Brennwerttherme","hersteller":"Vaillant","typ":"ecoTEC plus VC 206/5-5","baujahr":"2016"},
               "letzte_wartung":"14.10.2025",
               "letzte_arbeiten":[{"datum":"14.10.2025","text":"Wartung Therme"}],
               "offen":["Ausdehnungsgefäß tauschen"],"hinweise":["Schlüssel bei der Nachbarin"],
               "quellen":{"berichte":3,"protokolle":1,"kommentare":2,"projekte":3},
               "stand":"2026-10-16T05:30:12+00:00","neu":{"x":1}}}""",
        )
        val v = m.vorgeschichte!!
        assertEquals("Gas-Brennwerttherme · Vaillant ecoTEC plus VC 206/5-5 · Baujahr 2016", geraetZeile(v.anlage))
        assertEquals("aus 3 Berichten, 1 Protokoll, 2 Kommentaren", quellenText(v.quellen))
        assertTrue(hatInhalt(v))
    }

    @Test
    fun nullUndLeerFallenWeg() {
        assertNull(json.decodeFromString(Projektmappe.serializer(), """{"id":5,"vorgeschichte":null}""").vorgeschichte)
        assertNull(json.decodeFromString(TagTermin.serializer(), """{"id":"hero:1"}""").vorgeschichte)
        assertEquals("kurz", json.decodeFromString(TagTermin.serializer(), """{"id":"hero:1","vorgeschichte":"kurz"}""").vorgeschichte)
        assertNull(geraetZeile(null))
        assertNull(geraetZeile(Anlage()))
        assertEquals("Vaillant", geraetZeile(Anlage(hersteller = "Vaillant", typ = " ")))
        assertEquals("Therme · Baujahr 2016", geraetZeile(Anlage(geraet = "Therme", baujahr = "2016")))
        assertNull(quellenText(VorgeschichteQuellen(projekte = 2)))
        assertFalse(hatInhalt(null))
        assertFalse(hatInhalt(Vorgeschichte(anlage = Anlage(), offen = listOf(" "))))
    }
}
