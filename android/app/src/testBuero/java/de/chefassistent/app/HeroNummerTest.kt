package de.chefassistent.app

import de.chefassistent.app.ui.chat.heroNummerAus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Auftrag 1.32 A: angehefteter Name mit HERO-Nummer -> Mappe nachholen. */
class HeroNummerTest {

    @Test
    fun `Nummer am Anfang wird erkannt`() {
        assertEquals("SAN-660", heroNummerAus("SAN-660 Firma WEG Trelleborger Straße 14"))
        assertEquals("SAN-660", heroNummerAus("san 660"))
        assertEquals("UNB-795", heroNummerAus("  UNB-795"))
        assertEquals("HEZ-2003", heroNummerAus("HEZ2003 Schulz"))
    }

    @Test
    fun `ohne Nummer am Anfang nichts`() {
        assertNull(heroNummerAus("Begehung Simon-Dach-Straße 46"))
        assertNull(heroNummerAus("Projekt SAN-660"))
        assertNull(heroNummerAus("Hotel"))
    }
}
