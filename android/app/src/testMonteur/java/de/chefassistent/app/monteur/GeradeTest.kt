package de.chefassistent.app.monteur

import de.chefassistent.app.data.Netzwerk
import de.chefassistent.app.monteur.data.EingangAuftrag
import de.chefassistent.app.monteur.data.FotoAuswahl
import de.chefassistent.app.monteur.data.GeradeFoto
import de.chefassistent.app.monteur.data.Hochgeladen
import de.chefassistent.app.monteur.data.MonteurTag
import de.chefassistent.app.monteur.data.TagProjekt
import de.chefassistent.app.monteur.data.TagTermin
import de.chefassistent.app.monteur.ui.heute.laufenderTermin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class GeradeTest {
    private fun foto(id: String, verwendet: Boolean = false) = GeradeFoto(id, 1, 0L, "/x/$id.jpg", verwendet = verwendet)
    private val liste = listOf(foto("a"), foto("b"), foto("c"))

    @Test
    fun automatischDasLetzte() {
        assertEquals(listOf("c"), FotoAuswahl().gemeint(liste).map { it.id })
        // Schon verwendet → nichts automatisch.
        assertTrue(FotoAuswahl().gemeint(liste.dropLast(1) + foto("c", verwendet = true)).isEmpty())
        assertTrue(FotoAuswahl().gemeint(emptyList()).isEmpty())
    }

    @Test
    fun antippenMarkiertUndSchaltetAb() {
        var a = FotoAuswahl()
        // Ein anderes antippen: das automatische bleibt dabei.
        a = a.umschalten(liste[0], liste)
        assertEquals(listOf("a", "c"), a.gemeint(liste).map { it.id })
        a = a.umschalten(liste[0], liste)
        assertEquals(listOf("c"), a.gemeint(liste).map { it.id })
        // Das automatisch gemeinte antippen: abgeschaltet.
        a = FotoAuswahl().umschalten(liste[2], liste)
        assertTrue(a.gemeint(liste).isEmpty())
    }

    @Test
    fun laufenderTerminMitSpielraum() {
        val p = TagProjekt(id = 7, nr = "UNB-795")
        val tage = listOf(
            MonteurTag(
                "2026-10-16",
                termine = listOf(
                    TagTermin("hero:1", beginn = "2026-10-16T09:00", ende = "2026-10-16T12:00", projekt = p),
                    TagTermin("hero:2", beginn = "2026-10-16T13:00", ende = "2026-10-16T15:00", projekt = TagProjekt(id = 8)),
                    TagTermin("hero:3", beginn = "2026-10-16T10:00", ende = "2026-10-16T11:00"),
                ),
            ),
        )
        assertEquals("hero:1", laufenderTermin(tage, LocalDateTime.of(2026, 10, 16, 8, 45))?.id)
        assertNull(laufenderTermin(tage, LocalDateTime.of(2026, 10, 16, 8, 0)))
        // Ueberlappen (12:45): der spaetere Beginn gewinnt.
        assertEquals("hero:2", laufenderTermin(tage, LocalDateTime.of(2026, 10, 16, 12, 45))?.id)
        assertEquals("hero:2", laufenderTermin(tage, LocalDateTime.of(2026, 10, 16, 16, 59))?.id)
        assertNull(laufenderTermin(tage, LocalDateTime.of(2026, 10, 16, 17, 30)))
        assertNull(laufenderTermin(tage, LocalDateTime.of(2026, 10, 17, 10, 0)))
    }

    @Test
    fun vertragFotoId() {
        val h = Netzwerk.json.decodeFromString(Hochgeladen.serializer(), """{"art":"foto","name":"x.jpg","ziel":"hero","foto_id":"m123"}""")
        assertEquals("m123", h.foto_id)
        assertNull(Netzwerk.json.decodeFromString(Hochgeladen.serializer(), """{"art":"video"}""").foto_id)
        assertTrue(Netzwerk.json.decodeFromString(EingangAuftrag.serializer(), """{"anfrage_id":"a"}""").gerade_ids.isEmpty())
    }
}
