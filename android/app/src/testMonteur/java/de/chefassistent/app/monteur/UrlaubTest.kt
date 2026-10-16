package de.chefassistent.app.monteur

import de.chefassistent.app.R
import de.chefassistent.app.monteur.data.AbwVorschlag
import de.chefassistent.app.monteur.data.KRANK_ARTEN
import de.chefassistent.app.monteur.data.URLAUB_ARTEN
import de.chefassistent.app.monteur.ui.urlaub.AntragFormular
import de.chefassistent.app.monteur.ui.urlaub.artName
import de.chefassistent.app.monteur.ui.urlaub.datumZuMillis
import de.chefassistent.app.monteur.ui.urlaub.istZurueckziehbar
import de.chefassistent.app.monteur.ui.urlaub.jahrZumZurueckziehen
import de.chefassistent.app.monteur.ui.urlaub.millisZuDatum
import de.chefassistent.app.monteur.ui.urlaub.statusName
import de.chefassistent.app.monteur.ui.urlaub.tageSchaetzen
import de.chefassistent.app.monteur.ui.urlaub.tageText
import de.chefassistent.app.monteur.ui.urlaub.vorschlagZuAntrag
import de.chefassistent.app.monteur.ui.urlaub.zeitraumText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Reiter „Urlaub“ (Auftrag 1.22, Abschnitt 5): reine Logik ohne Oberflaeche. */
class UrlaubTest {

    @Test
    fun `jede Art aus dem Vertrag hat einen deutschen Namen`() {
        (URLAUB_ARTEN + KRANK_ARTEN).forEach { assertNotNull("Art $it", artName(it)) }
        assertEquals(R.string.u_art_ueberstunden, artName("ueberstunden"))
        assertEquals(R.string.u_art_kind_krank, artName("kind_krank"))
    }

    @Test
    fun `unbekannte Art und unbekannter Status bleiben roh`() {
        assertNull(artName("elternzeit"))
        assertNull(statusName("storniert"))
        assertEquals(R.string.u_status_beantragt, statusName("beantragt"))
    }

    @Test
    fun `Tage mit Komma und ohne unnoetige Nullen`() {
        assertEquals("2,5", tageText(2.5))
        assertEquals("3", tageText(3.0))
        assertEquals("0,5", tageText(0.5))
        assertEquals("30", tageText(30.0))
        assertEquals("–", tageText(null))
    }

    @Test
    fun `Zeitraum mit Wochentag und halben Tagen`() {
        assertEquals("Do 22.10. – Fr 23.10.", zeitraumText("2026-10-22", "2026-10-23"))
        assertEquals("Do 22.10.", zeitraumText("2026-10-22", "2026-10-22"))
        assertEquals("Do 22.10. ½", zeitraumText("2026-10-22", "2026-10-22", halbAnfang = true))
        assertEquals("Do 22.10. ½ – Fr 23.10. ½", zeitraumText("2026-10-22", "2026-10-23", true, true))
        assertEquals("Do 22.10. – Fr 23.10. ½", zeitraumText("2026-10-22", "2026-10-23", halbEnde = true))
    }

    @Test
    fun `Zeitraum ueber den Jahreswechsel nennt das Jahr`() {
        assertEquals("Di 29.12.2026 – Fr 01.01.2027", zeitraumText("2026-12-29", "2027-01-01"))
    }

    @Test
    fun `unlesbares Datum kommt roh zurueck`() {
        assertEquals("bald – später", zeitraumText("bald", "später"))
    }

    @Test
    fun `Zurueckziehen nimmt das Jahr des Beginns`() {
        assertEquals(2026, jahrZumZurueckziehen("2026-12-28", 2027))
        assertEquals(2027, jahrZumZurueckziehen("2027-01-04", 2026))
        assertEquals(2026, jahrZumZurueckziehen("", 2026))
    }

    @Test
    fun `nur unentschiedene Antraege sind wischbar`() {
        assertTrue(istZurueckziehbar("beantragt"))
        assertTrue(istZurueckziehbar("entwurf"))
        assertFalse(istZurueckziehbar("genehmigt"))
        assertFalse(istZurueckziehbar("abgelehnt"))
    }

    @Test
    fun `Vorschlag wird zum Antrag mit eigener anfrage_id`() {
        val v = AbwVorschlag(
            art = "urlaub",
            von = "2026-10-22",
            bis = "2026-10-23",
            halber_tag_ende = true,
            kommentar = "Umzug",
        )
        val a = vorschlagZuAntrag(v, "abcdef123456")
        assertEquals("urlaub", a.art)
        assertEquals("2026-10-22", a.von)
        assertEquals("2026-10-23", a.bis)
        assertFalse(a.halber_tag_anfang)
        assertTrue(a.halber_tag_ende)
        assertEquals("Umzug", a.kommentar)
        assertEquals("abcdef123456", a.anfrage_id)
    }

    @Test
    fun `Vorschlag mit Uhrzeit wird auf das Datum gekuerzt`() {
        val a = vorschlagZuAntrag(AbwVorschlag("urlaub", "2026-10-22T00:00", "2026-10-23T00:00"), "abcdef123456")
        assertEquals("2026-10-22", a.von)
        assertEquals("2026-10-23", a.bis)
    }

    @Test
    fun `geschaetzte Tage zaehlen nur Mo bis Fr`() {
        assertEquals(2.0, tageSchaetzen("2026-10-22", "2026-10-23", false, false)!!, 0.0)
        // Do bis Mo: Wochenende zaehlt nicht.
        assertEquals(3.0, tageSchaetzen("2026-10-22", "2026-10-26", false, false)!!, 0.0)
        assertEquals(1.5, tageSchaetzen("2026-10-22", "2026-10-23", true, false)!!, 0.0)
        assertEquals(0.5, tageSchaetzen("2026-10-22", "2026-10-22", false, true)!!, 0.0)
        assertNull(tageSchaetzen("2026-10-23", "2026-10-22", false, false))
    }

    @Test
    fun `Datumsauswahl rechnet ohne Zeitzonenversatz`() {
        val millis = datumZuMillis("2026-10-22")!!
        assertEquals("2026-10-22", millisZuDatum(millis))
        assertNull(datumZuMillis(""))
    }

    @Test
    fun `Krank melden ist heute bis heute vorbelegt`() {
        val heute = LocalDate.of(2026, 10, 16)
        val f = AntragFormular.krank("abcdef123456", heute)
        assertTrue(f.krank)
        assertEquals("krank", f.art)
        assertEquals(KRANK_ARTEN, f.arten)
        assertEquals("2026-10-16", f.von)
        assertEquals("2026-10-16", f.bis)
        assertTrue(f.gueltig)
    }

    @Test
    fun `Urlaubsformular braucht erst einen Zeitraum`() {
        val f = AntragFormular.urlaub("abcdef123456")
        assertFalse(f.krank)
        assertEquals(URLAUB_ARTEN, f.arten)
        assertFalse(f.gueltig)
        val gefuellt = f.copy(von = "2026-10-22", bis = "2026-10-23", kommentar = "  Hochzeit ")
        assertTrue(gefuellt.gueltig)
        val antrag = gefuellt.zuAntrag()
        assertEquals("Hochzeit", antrag.kommentar)
        assertEquals("abcdef123456", antrag.anfrage_id)
        assertFalse(f.copy(von = "2026-10-23", bis = "2026-10-22").gueltig)
    }

    @Test
    fun `Aendern eines Krank-Vorschlags oeffnet die Krank-Variante`() {
        val f = AntragFormular.ausVorschlag(AbwVorschlag("kind_krank", "2026-10-16", "2026-10-16"), "abcdef123456")
        assertTrue(f.krank)
        assertEquals("kind_krank", f.art)
    }
}
