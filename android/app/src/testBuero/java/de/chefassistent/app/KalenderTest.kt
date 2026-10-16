package de.chefassistent.app

import de.chefassistent.app.data.KalenderAntwort
import de.chefassistent.app.data.Netzwerk
import de.chefassistent.app.data.PERSON_ALLE
import de.chefassistent.app.data.PERSON_ICH
import de.chefassistent.app.data.Personen
import de.chefassistent.app.ui.kalender.auswahlUmschalten
import de.chefassistent.app.ui.kalender.farbIndex
import de.chefassistent.app.ui.kalender.farbReihenfolge
import de.chefassistent.app.ui.kalender.nachTagen
import de.chefassistent.app.ui.kalender.tage
import de.chefassistent.app.ui.kalender.vorname
import de.chefassistent.app.ui.kalender.zeitText
import de.chefassistent.app.ui.kalender.zeitraum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Kalender mit HERO-Terminen (`/v1/kalender`, `/v1/personen`, seit v1.16.0). */
class KalenderTest {

    private val json = Netzwerk.json
    private val berlin = ZoneId.of("Europe/Berlin")

    /** Beispiel aus API.md 5a, "…" ersetzt, dazu ein mehrtaegiger Termin. */
    private val kalender = """
        {"von": "2026-10-12", "bis": "2026-10-18", "personen": ["ich", "hero:106018"], "spiegel": true, "neu": 1,
         "termine": [{"id": "hero:616233015", "quelle": "hero", "titel": "Badsanierung Herr Böse",
           "beginn": "2026-10-12T14:30+02:00", "ende": "2026-10-12T15:30+02:00", "ganztag": false,
           "kategorie": "Umsetzung", "farbe": "#0B8043",
           "projekt": {"nr": "SAN-1166", "kunde": "Herr Dipl.-Kfm. Mark Boese", "titel": "Badsanierung 2. OG & 4. OG",
                       "adresse": "Grimmstraße 7, 10967 Berlin", "pfad": "HERO-Archiv/SAN-1166 Herr Dipl.-Kfm. Mark Boese"},
           "personen": [{"hero_id": 94314, "name": "Patrick van Dalen", "app_benutzer": null}],
           "ort": null, "notiz": null, "nur_lesen": true},
          {"id": "eigen:12", "quelle": "eigen", "titel": "Zahnarzt", "beginn": "2026-10-13T16:00:00+02:00",
           "ende": "2026-10-13T17:00:00+02:00", "ganztag": false, "kategorie": "Eigener Termin", "farbe": null, "projekt": null,
           "personen": [{"hero_id": null, "name": "Patrick", "app_benutzer": "patrick"}], "nur_lesen": false},
          {"id": "hero:1", "quelle": "hero", "titel": "Rufbereitschaft", "beginn": "2026-10-05T02:01+02:00",
           "ende": "2026-10-14T01:59+02:00", "ganztag": false, "kategorie": "Bereitschaft",
           "personen": [{"hero_id": 106018, "name": "Julian Hoffmann", "app_benutzer": null}], "nur_lesen": true}]}
    """.trimIndent()

    private val personen = """
        {"ich": "hero:94314",
         "personen": [{"schluessel": "hero:94314", "name": "Patrick van Dalen", "hero_id": 94314, "app_benutzer": "patrick", "ich": true, "termine": 473},
                      {"schluessel": "hero:130793", "name": "Sascha Richter", "hero_id": 130793, "app_benutzer": "sascha", "ich": false, "termine": 113},
                      {"schluessel": "hero:106018", "name": "Julian Hoffmann", "hero_id": 106018, "app_benutzer": null, "ich": false, "termine": 519}]}
    """.trimIndent()

    @Test
    fun `liest Kalender und Personen aus API md`() {
        val antwort = json.decodeFromString<KalenderAntwort>(kalender)
        assertTrue(antwort.spiegel)
        assertEquals(3, antwort.termine.size)
        val hero = antwort.termine.first()
        assertTrue(hero.nur_lesen)
        assertEquals("HERO-Archiv/SAN-1166 Herr Dipl.-Kfm. Mark Boese", hero.projekt?.pfad)
        assertNull(antwort.termine[1].projekt)

        val p = json.decodeFromString<Personen>(personen)
        assertEquals("hero:94314", p.ich)
        assertNull(p.personen[2].app_benutzer)
        assertNull(json.decodeFromString<Personen>("""{"ich": null, "personen": []}""").ich)
    }

    @Test
    fun `Woche geht von Montag bis Sonntag`() {
        assertEquals(LocalDate.of(2026, 10, 12) to LocalDate.of(2026, 10, 18), zeitraum(LocalDate.of(2026, 10, 13), true))
        assertEquals(LocalDate.of(2026, 10, 13) to LocalDate.of(2026, 10, 13), zeitraum(LocalDate.of(2026, 10, 13), false))
    }

    @Test
    fun `mehrtaegige Termine stehen an jedem Tag im Zeitraum`() {
        val antwort = json.decodeFromString<KalenderAntwort>(kalender)
        val tage = nachTagen(antwort.termine, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18), berlin)
        assertEquals(7, tage.size)
        // Rufbereitschaft bis 14.10. 01:59 beruehrt 12., 13. und 14.
        val ruf = tage.filterValues { l -> l.any { it.termin.id == "hero:1" } }.keys
        assertEquals(listOf(12, 13, 14), ruf.map { it.dayOfMonth })
        assertTrue(tage.getValue(LocalDate.of(2026, 10, 18)).isEmpty())
        assertEquals("5.10. 02:01", zeitText("2026-10-05T02:01+02:00", true, berlin))
        assertEquals("14:30", zeitText("2026-10-12T14:30+02:00", false, berlin))
    }

    @Test
    fun `doppelt gelieferte Termine erscheinen einmal je Tag`() {
        val antwort = json.decodeFromString<KalenderAntwort>(kalender)
        val doppelt = antwort.termine + antwort.termine.last()
        val tag = nachTagen(doppelt, LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 13), berlin).values.single()
        assertEquals(1, tag.count { it.termin.id == "hero:1" })
    }

    @Test
    fun `Termin ueber Monate steht auch in einer spaeten Woche`() {
        // Frueher ab dem Beginn nach 62 Tagen gekappt - im Oktober fehlte er dann.
        val baustelle = json.decodeFromString<KalenderAntwort>(kalender).termine.first()
            .copy(id = "hero:lang", beginn = "2026-07-01", ende = "2026-12-31", ganztag = true)
        val woche = nachTagen(listOf(baustelle), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18), berlin)
        assertTrue(woche.values.all { tag -> tag.any { it.termin.id == "hero:lang" } })
        assertTrue(tage(baustelle, berlin).size > 1)
    }

    @Test
    fun `Ende um Mitternacht zaehlt den Folgetag nicht`() {
        val t = json.decodeFromString<KalenderAntwort>(kalender).termine.first()
            .copy(beginn = "2026-10-12T20:00+02:00", ende = "2026-10-13T00:00+02:00")
        assertEquals(listOf(LocalDate.of(2026, 10, 12)), tage(t, berlin))
        val ganztag = t.copy(beginn = "2026-10-12", ende = "2026-10-12", ganztag = true)
        assertEquals(listOf(LocalDate.of(2026, 10, 12)), tage(ganztag, berlin))
    }

    @Test
    fun `Farben in fester Reihenfolge und je Person`() {
        val p = json.decodeFromString<Personen>(personen).personen
        val termine = json.decodeFromString<KalenderAntwort>(kalender).termine
        val reihenfolge = farbReihenfolge(listOf("hero:106018", PERSON_ICH), p)
        assertEquals(listOf(PERSON_ICH, "hero:106018"), reihenfolge)
        assertEquals(0, farbIndex(termine[0], reihenfolge, p)) // HERO-Termin von Patrick (ich)
        assertEquals(0, farbIndex(termine[1], reihenfolge, p)) // eigener Termin ueber app_benutzer
        assertEquals(1, farbIndex(termine[2], reihenfolge, p)) // Julian
        assertNull(farbIndex(termine[2], listOf(PERSON_ICH), p))
        assertEquals(3, farbReihenfolge(listOf(PERSON_ALLE), p).size)
    }

    @Test
    fun `Auswahl ist nie leer und Alle schliesst den Rest aus`() {
        assertEquals(listOf(PERSON_ICH), auswahlUmschalten(listOf(PERSON_ICH), PERSON_ICH))
        assertEquals(listOf(PERSON_ICH, "hero:1"), auswahlUmschalten(listOf(PERSON_ICH), "hero:1"))
        assertEquals(listOf(PERSON_ALLE), auswahlUmschalten(listOf(PERSON_ICH, "hero:1"), PERSON_ALLE))
        assertEquals(listOf("hero:1"), auswahlUmschalten(listOf(PERSON_ALLE), "hero:1"))
        assertEquals(listOf(PERSON_ICH), auswahlUmschalten(listOf(PERSON_ALLE), PERSON_ALLE))
        assertEquals("Patrick", vorname("Patrick van Dalen"))
    }
}
