package de.chefassistent.app

import de.chefassistent.app.ui.ansehen.kannAnsehen
import de.chefassistent.app.ui.kalender.zeitraumMitKw
import de.chefassistent.app.ui.komponenten.DateiArt
import de.chefassistent.app.ui.komponenten.dateiArt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Neugestaltung 1.21 (Auftrag 1.21, Teil B): Kalenderkopf, Typmarken, Ansehen in der App. */
class Gestaltung121Test {

    @Test
    fun `Kalenderkopf mit KW`() {
        assertEquals("12.–18. Oktober 2026 · KW 42", zeitraumMitKw(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18)))
        assertEquals(
            "28. September – 4. Oktober 2026 · KW 40",
            zeitraumMitKw(LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 4)),
        )
        assertEquals("Fr. 16. Oktober 2026 · KW 42", zeitraumMitKw(LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 16)))
        // KW 53 bzw. 1 ueber den Jahreswechsel
        assertTrue(zeitraumMitKw(LocalDate.of(2026, 12, 28), LocalDate.of(2027, 1, 3)).endsWith("KW 53"))
    }

    @Test
    fun `Typmarke nach Endung`() {
        assertEquals(DateiArt.PDF, dateiArt("Rechnung.PDF"))
        assertEquals(DateiArt.BILD, dateiArt("foto.jpeg"))
        assertEquals(DateiArt.TABELLE, dateiArt("liste.xlsx"))
        assertEquals(DateiArt.ORDNER, dateiArt("Kunden", istOrdner = true))
        assertEquals(DateiArt.SONST, dateiArt("ohne_endung"))
    }

    @Test
    fun `nur PDF, Bilder, Videos und Text in der App`() {
        assertTrue(kannAnsehen("a/b/Rechnung.pdf"))
        assertTrue(kannAnsehen("Notizen/x.md"))
        assertTrue(kannAnsehen("liste.csv"))
        assertTrue(kannAnsehen("video.mp4"))
        assertFalse(kannAnsehen("Angebot.docx"))
        assertFalse(kannAnsehen("Liste.xlsx"))
    }
}
