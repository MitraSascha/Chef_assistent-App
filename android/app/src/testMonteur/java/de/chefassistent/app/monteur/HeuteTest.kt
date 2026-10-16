package de.chefassistent.app.monteur

import de.chefassistent.app.data.Netzwerk
import de.chefassistent.app.monteur.data.TagTermin
import de.chefassistent.app.monteur.data.Unterlage
import de.chefassistent.app.monteur.ui.heute.HeuteViewModel
import de.chefassistent.app.monteur.ui.heute.tagText
import de.chefassistent.app.monteur.ui.heute.uhrzeit
import de.chefassistent.app.monteur.ui.heute.zeitText
import de.chefassistent.app.monteur.ui.projekt.ProjektViewModel
import de.chefassistent.app.monteur.ui.projekt.datumText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Heute und Projektmappe: Zeitraum, Uhrzeiten, Gruppen. */
class HeuteTest {

    @Test
    fun `Woche ist Montag bis Sonntag`() {
        val (von, bis) = HeuteViewModel.zeitraum(LocalDate.of(2026, 10, 16), woche = true)
        assertEquals(LocalDate.of(2026, 10, 12), von)
        assertEquals(LocalDate.of(2026, 10, 18), bis)
        assertEquals(LocalDate.of(2026, 10, 16) to LocalDate.of(2026, 10, 16), HeuteViewModel.zeitraum(LocalDate.of(2026, 10, 16), false))
    }

    @Test
    fun `Uhrzeit mit und ohne Offset`() {
        assertEquals("08:00", uhrzeit("2026-10-16T08:00"))
        assertEquals("08:00", uhrzeit("2026-10-16T08:00:00"))
        assertNull(uhrzeit("2026-10-16"))
    }

    @Test
    fun `ganztaegig hat keine Uhrzeit`() {
        val t = TagTermin(id = "hero:1", beginn = "2026-10-16", ende = "2026-10-16", ganztag = true)
        assertNull(zeitText(t))
        val z = TagTermin(id = "hero:2", beginn = "2026-10-16T08:00", ende = "2026-10-16T10:30")
        assertEquals("08:00 – 10:30", zeitText(z))
    }

    @Test
    fun `Tag und Datum`() {
        assertEquals("Fr 16.10.", tagText("2026-10-16"))
        assertEquals("16.10.2026", datumText("2026-10-16T09:00:00+02:00"))
        assertNull(datumText(null))
        assertEquals("kaputt", datumText("kaputt"))
    }

    @Test
    fun `Unterlagen nach Art in Serverreihenfolge`() {
        val liste = listOf(
            Unterlage(art = "Angebot", name = "a.pdf"),
            Unterlage(art = "Aufmaß", name = "b.pdf"),
            Unterlage(art = "Angebot", name = "c.pdf"),
        )
        val gruppen = ProjektViewModel.nachArt(liste)
        assertEquals(listOf("Angebot", "Aufmaß"), gruppen.map { it.first })
        assertEquals(listOf("a.pdf", "c.pdf"), gruppen[0].second.map { it.name })
    }

    @Test
    fun `Termin ohne Projekt ist lesbar`() {
        val t = Netzwerk.json.decodeFromString<TagTermin>(
            """{"id":"hero:9","titel":"Büro","beginn":"2026-10-16T13:00","ende":"2026-10-16T14:00","ganztag":false,
            "kategorie":"","kollegen":[],"projekt":null,"berichte":[]}"""
        )
        assertNull(t.projekt)
    }
}
