package de.chefassistent.app.monteur

import de.chefassistent.app.monteur.data.MonteurTag
import de.chefassistent.app.monteur.data.TagTermin
import de.chefassistent.app.monteur.data.ZeitAbschnitt
import de.chefassistent.app.monteur.data.Zeiteintrag
import de.chefassistent.app.monteur.ui.zeiten.artFuerBuchung
import de.chefassistent.app.monteur.ui.zeiten.dauerText
import de.chefassistent.app.monteur.ui.zeiten.kontoZeitraum
import de.chefassistent.app.monteur.ui.zeiten.letzterTerminEnde
import de.chefassistent.app.monteur.ui.zeiten.minutenText
import de.chefassistent.app.monteur.ui.zeiten.sichtbareEintraege
import de.chefassistent.app.monteur.ui.zeiten.spanneText
import de.chefassistent.app.monteur.ui.zeiten.tagBestaetigenZeigen
import de.chefassistent.app.monteur.ui.zeiten.termineAm
import de.chefassistent.app.monteur.ui.zeiten.uhrzeitLesen
import de.chefassistent.app.monteur.ui.zeiten.vorschlagZuZeit
import de.chefassistent.app.monteur.ui.zeiten.zeitLesen
import de.chefassistent.app.monteur.ui.zeiten.zeitraum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Reine Logik des Reiters „Zeiten“ (Auftrag 1.22, Abschnitt 4). */
class ZeitenTest {

    private val berlin = ZoneId.of("Europe/Berlin")
    private val tag = LocalDate.of(2026, 10, 20)

    @Test
    fun `Minuten als Stunden`() {
        assertEquals("7:30 h", minutenText(450))
        assertEquals("0:05 h", minutenText(5))
        assertEquals("0:00 h", minutenText(0))
        assertEquals("12:00 h", minutenText(720))
    }

    @Test
    fun `Saldo mit Vorzeichen, auch negativ`() {
        assertEquals("+1:20 h", minutenText(80, vorzeichen = true))
        assertEquals("−0:45 h", minutenText(-45, vorzeichen = true))
        assertEquals("−10:01 h", minutenText(-601, vorzeichen = true))
        assertEquals("+0:00 h", minutenText(0, vorzeichen = true))
        // Ohne Vorzeichenwunsch trotzdem kein falsches Plus bei Minus.
        assertEquals("−0:45 h", minutenText(-45))
    }

    @Test
    fun `Laufende Dauer der Stempeluhr`() {
        val beginn = LocalDateTime.of(2026, 10, 20, 7, 0, 0)
        assertEquals("1:05:09", dauerText(beginn, beginn.plusSeconds(3909)))
        assertEquals("0:00:00", dauerText(beginn, beginn.minusMinutes(1)))
    }

    @Test
    fun `Ortszeit ohne Zone bleibt, wie sie ist`() {
        assertEquals(LocalDateTime.of(2026, 10, 20, 7, 30), zeitLesen("2026-10-20T07:30", berlin))
        assertEquals(LocalDateTime.of(2026, 10, 20, 7, 30, 15), zeitLesen("2026-10-20T07:30:15", berlin))
        assertEquals(LocalDateTime.of(2026, 10, 20, 7, 30), zeitLesen("2026-10-20 07:30", berlin))
    }

    @Test
    fun `Zeit mit Offset wird in die Geraetezone umgerechnet`() {
        assertEquals(LocalDateTime.of(2026, 10, 20, 7, 30), zeitLesen("2026-10-20T07:30:00+02:00", berlin))
        assertEquals(LocalDateTime.of(2026, 10, 20, 7, 30), zeitLesen("2026-10-20T05:30:00Z", berlin))
        // Winterzeit: +01:00
        assertEquals(LocalDateTime.of(2026, 12, 1, 8, 0), zeitLesen("2026-12-01T07:00:00Z", berlin))
    }

    @Test
    fun `Unlesbare Zeiten ergeben null`() {
        assertNull(zeitLesen(null))
        assertNull(zeitLesen(""))
        assertNull(zeitLesen("gestern"))
        assertNull(zeitLesen("2026-10-20"))
    }

    @Test
    fun `Uhrzeit lesen`() {
        assertEquals(LocalTime.of(7, 30), uhrzeitLesen("07:30"))
        assertEquals(LocalTime.of(7, 5), uhrzeitLesen("7:05"))
        assertNull(uhrzeitLesen("24:00"))
        assertNull(uhrzeitLesen("7.30"))
        assertNull(uhrzeitLesen(""))
    }

    @Test
    fun `Ortszeit aus Datum und HH MM`() {
        assertEquals("2026-10-20T07:30" to "2026-10-20T16:00", zeitraum("2026-10-20", "07:30", "16:00"))
        assertEquals("2026-10-20T07:05" to "2026-10-20T09:00", zeitraum("2026-10-20", "7:05", "9:00"))
        assertEquals("2026-10-20T07:30" to "2026-10-20T16:00", zeitraum(tag, LocalTime.of(7, 30), LocalTime.of(16, 0)))
    }

    @Test
    fun `Ueber Mitternacht endet am Folgetag, gleiche Zeit gar nicht`() {
        assertEquals("2026-10-20T22:00" to "2026-10-21T01:30", zeitraum("2026-10-20", "22:00", "01:30"))
        assertEquals("2026-10-31T23:00" to "2026-11-01T00:30", zeitraum("2026-10-31", "23:00", "00:30"))
        assertNull(zeitraum("2026-10-20", "08:00", "08:00"))
        assertNull(zeitraum("kaputt", "08:00", "09:00"))
        assertNull(zeitraum("2026-10-20", "8 Uhr", "09:00"))
    }

    @Test
    fun `Vorschlag wird zu ZeitNeu mit eigener anfrage_id`() {
        val zeit = vorschlagZuZeit(ZeitAbschnitt("07:00", "12:00", "fahrt"), "2026-10-20", "abc12345")!!
        assertEquals("2026-10-20T07:00", zeit.beginn)
        assertEquals("2026-10-20T12:00", zeit.ende)
        assertEquals("fahrt", zeit.art)
        assertEquals("abc12345", zeit.anfrage_id)
        // Unbekannte Art: der Vertrag nimmt beim Anlegen nur die bekannten.
        assertEquals("arbeit", artFuerBuchung("sonstige"))
        assertNull(vorschlagZuZeit(ZeitAbschnitt("", "12:00", "arbeit"), "2026-10-20", "abc12345"))
    }

    @Test
    fun `Banner heute erst ab 15 Uhr`() {
        assertFalse(tagBestaetigenZeigen(tag, tag.atTime(14, 59), false, 2, null))
        assertTrue(tagBestaetigenZeigen(tag, tag.atTime(15, 0), false, 2, null))
        assertTrue(tagBestaetigenZeigen(tag, tag.atTime(18, 0), false, 0, null))
    }

    @Test
    fun `Banner heute, sobald der letzte Termin vorbei ist`() {
        val ende = tag.atTime(12, 30)
        assertFalse(tagBestaetigenZeigen(tag, tag.atTime(12, 29), false, 1, ende))
        assertTrue(tagBestaetigenZeigen(tag, tag.atTime(12, 30), false, 1, ende))
    }

    @Test
    fun `Kein Banner, wenn eingereicht oder kuenftig`() {
        assertFalse(tagBestaetigenZeigen(tag, tag.atTime(18, 0), true, 3, null))
        assertFalse(tagBestaetigenZeigen(tag.plusDays(1), tag.atTime(18, 0), false, 3, null))
    }

    @Test
    fun `Vergangener Tag nur mit Eintraegen`() {
        val gestern = tag.minusDays(1)
        assertTrue(tagBestaetigenZeigen(gestern, tag.atTime(8, 0), false, 1, null))
        assertFalse(tagBestaetigenZeigen(gestern, tag.atTime(8, 0), false, 0, null))
    }

    @Test
    fun `Letzter Termin des Tages, ganztaegige zaehlen nicht`() {
        val termine = listOf(
            TagTermin(id = "hero:1", beginn = "2026-10-20T08:00", ende = "2026-10-20T11:00"),
            TagTermin(id = "hero:2", beginn = "2026-10-20T12:00", ende = "2026-10-20T14:30:00+02:00"),
            TagTermin(id = "hero:3", beginn = "2026-10-20", ende = "2026-10-20", ganztag = true),
            TagTermin(id = "hero:4", beginn = "2026-10-21T08:00", ende = "2026-10-21T17:00"),
        )
        assertEquals(tag.atTime(14, 30), letzterTerminEnde(termine, tag, berlin))
        assertNull(letzterTerminEnde(emptyList(), tag, berlin))
    }

    @Test
    fun `Termine nur vom angezeigten Tag`() {
        val tage = listOf(
            MonteurTag(datum = "2026-10-19", termine = listOf(TagTermin(id = "hero:1"))),
            MonteurTag(datum = "2026-10-20", termine = listOf(TagTermin(id = "hero:2"))),
        )
        assertEquals(listOf("hero:2"), termineAm(tage, tag).map { it.id })
    }

    @Test
    fun `Geloeschte Eintraege weg, nach Beginn sortiert`() {
        val liste = listOf(
            Zeiteintrag(id = 2, beginn = "2026-10-20T12:00", ende = "2026-10-20T13:00", art = "arbeit", status = "neu"),
            Zeiteintrag(id = 1, beginn = "2026-10-20T07:00", ende = "2026-10-20T12:00", art = "arbeit", status = "eingereicht"),
            Zeiteintrag(id = 3, beginn = "2026-10-20T13:00", ende = "2026-10-20T14:00", art = "pause", status = "geloescht"),
        )
        assertEquals(listOf(1, 2), sichtbareEintraege(liste).map { it.id })
        assertFalse(liste[1].aenderbar)
        assertTrue(liste[0].aenderbar)
    }

    @Test
    fun `Spanne als Uhrzeiten`() {
        assertEquals("07:30–16:00", spanneText("2026-10-20T07:30", "2026-10-20T16:00:00"))
        assertEquals("22:00–01:30", spanneText("2026-10-20T22:00", "2026-10-21T01:30"))
    }

    @Test
    fun `Stundenkonto bis heute, vergangene Monate ganz`() {
        assertEquals(LocalDate.of(2026, 10, 1) to tag, kontoZeitraum(tag, tag))
        assertEquals(
            LocalDate.of(2026, 9, 1) to LocalDate.of(2026, 9, 30),
            kontoZeitraum(LocalDate.of(2026, 9, 12), tag),
        )
    }
}
