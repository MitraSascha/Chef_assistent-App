package de.chefassistent.app.monteur

import de.chefassistent.app.data.Netzwerk
import de.chefassistent.app.monteur.data.Nachrichten
import de.chefassistent.app.monteur.push.Benachrichtigungen
import de.chefassistent.app.monteur.push.beginnOrtszeit
import de.chefassistent.app.monteur.push.jetztZeigen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class NachrichtenTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private val nacht = LocalDateTime.of(2026, 10, 16, 22, 0)

    @Test
    fun tagsueberImmer() {
        assertTrue(jetztZeigen("neu", "2026-10-20T08:00:00+02:00", LocalDateTime.of(2026, 10, 16, 6, 0), berlin))
        assertTrue(jetztZeigen("entfallen", null, LocalDateTime.of(2026, 10, 16, 20, 59), berlin))
    }

    @Test
    fun nachtsNurNotdienstUndBald() {
        assertTrue(jetztZeigen("notdienst", null, nacht, berlin))
        // Beginnt in 2 h -> zeigen
        assertTrue(jetztZeigen("neu", "2026-10-17T00:00:00+02:00", nacht, berlin))
        // Beginnt morgen frueh -> erst ab 6 Uhr
        assertFalse(jetztZeigen("verschoben", "2026-10-17T07:30:00+02:00", nacht, berlin))
        // Ohne Datum -> warten
        assertFalse(jetztZeigen("geaendert", null, LocalDateTime.of(2026, 10, 17, 5, 59), berlin))
        // Schon vorbei -> warten
        assertFalse(jetztZeigen("neu", "2026-10-16T21:00:00+02:00", nacht, berlin))
    }

    @Test
    fun beginnInOrtszeit() {
        assertEquals(LocalDateTime.of(2026, 10, 16, 9, 30), beginnOrtszeit("2026-10-16T07:30:00Z", berlin))
        assertEquals(LocalDateTime.of(2026, 10, 16, 9, 30), beginnOrtszeit("2026-10-16T09:30", berlin))
        assertEquals(LocalDateTime.of(2026, 10, 16, 0, 0), beginnOrtszeit("2026-10-16", berlin))
        assertNull(beginnOrtszeit("", berlin))
    }

    @Test
    fun kanalNachArt() {
        assertEquals("notdienst", Benachrichtigungen.kanal("notdienst"))
        assertEquals("termine", Benachrichtigungen.kanal("neu"))
        assertEquals("termine", Benachrichtigungen.kanal("unbekannt"))
    }

    @Test
    fun antwortTolerant() {
        val n = Netzwerk.json.decodeFromString(
            Nachrichten.serializer(),
            """{"nachrichten":[{"id":7,"art":"neu","titel":"Neuer Termin","text":"Mo 08:00","termin_id":"123",
               "datum":"2026-10-19T08:00:00+02:00","erstellt":"2026-10-16T10:00:00+02:00","gelesen":false,"neu_feld":1}],
               "ungelesen":1,"push_aktiv":true}""",
        )
        assertEquals(7, n.nachrichten.single().id)
        assertTrue(n.push_aktiv)
    }
}
