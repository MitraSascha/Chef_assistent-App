package de.chefassistent.app

import de.chefassistent.app.data.ApiFehler
import de.chefassistent.app.data.AusgangAuftrag
import de.chefassistent.app.data.MedienAuftrag
import de.chefassistent.app.data.Rueckmeldung
import de.chefassistent.app.data.Tresor
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Sichert die Regel ab, an der haengt, ob im Funkloch etwas verlorengeht:
 * Was voruebergehend ist, bleibt im Korb. Verworfen wird nur Endgueltiges.
 */
class AusgangTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `voruebergehende Fehler werden wiederholt, nie verworfen`() {
        val voruebergehend = listOf(
            ApiFehler.KeineVerbindung(IOException("weg")),
            ApiFehler.NichtAngemeldet("401"),
            ApiFehler.KiNichtErreichbar("502"),
            ApiFehler.Beschaeftigt("409"),
            ApiFehler.ZuVieleVersuche("429"),
            ApiFehler.Server(500, "500"),
            ApiFehler.Server(503, "503"),
            ApiFehler.Server(504, "504"),
            ApiFehler.Server(408, "408"),
        )
        voruebergehend.forEach { assertTrue(it::class.simpleName + " " + it.message, it.istVoruebergehend) }
    }

    @Test
    fun `endgueltige Fehler werden nicht endlos wiederholt`() {
        val endgueltig = listOf(
            ApiFehler.Ungueltig("422"),
            ApiFehler.ZuGross("413"),
            ApiFehler.FalscherTyp("415"),
            ApiFehler.Verboten("403"),
            ApiFehler.NichtGefunden("404"),
            ApiFehler.Server(400, "400"),
            ApiFehler.UnlesbareAntwort(IOException("kaputt")),
            ApiFehler.AdresseUngueltig("assistent mitra"),
        )
        endgueltig.forEach { assertFalse(it::class.simpleName + " " + it.message, it.istVoruebergehend) }
    }

    @Test
    fun `Vorspann steht vor dem Text, ohne Vorspann nur der Text`() {
        val mit = AusgangAuftrag(
            art = AusgangAuftrag.ART_TEXT,
            aufgenommen = 1,
            vorspann = "Bezieht sich auf Projekt HEZ-2003 Schulz.",
            text = "Was fehlt noch?",
        )
        assertEquals("Bezieht sich auf Projekt HEZ-2003 Schulz.\n\nWas fehlt noch?", mit.chatText)

        val ohne = mit.copy(vorspann = null)
        assertEquals("Was fehlt noch?", ohne.chatText)
        assertEquals("Was fehlt noch?", mit.copy(vorspann = "  ").chatText)
    }

    @Test
    fun `Begleitdaten einer aelteren App-Version bleiben lesbar`() {
        // Nur die Pflichtfelder - alles andere hat Vorgaben.
        val auftrag = json.decodeFromString(AusgangAuftrag.serializer(), """{"aufgenommen": 42}""")
        assertEquals(AusgangAuftrag.ART_SPRACHE, auftrag.art)
        assertTrue(auftrag.antworten)
        assertNull(auftrag.gespraech_id)

        // Unbekannte Felder einer neueren Version stoeren nicht.
        val neuer = json.decodeFromString(
            MedienAuftrag.serializer(),
            """{"aufgenommen": 1, "art": "video", "kuenftig": true}""",
        )
        assertEquals(MedienAuftrag.ART_VIDEO, neuer.art)

        val rueck = json.decodeFromString(
            Rueckmeldung.serializer(),
            """{"id": "a", "art": "diktat", "zeit": 5, "text": "Checkliste Schulz"}""",
        )
        assertNull(rueck.detail)
    }

    @Test
    fun `Fassungen der Begleitdaten werden erkannt, alte ohne Nummer als Fassung 0`() {
        assertEquals("1727000000000" to 3, Tresor.zerlegen("1727000000000.3.json.enc"))
        assertEquals("1727000000000" to 0, Tresor.zerlegen("1727000000000.json.enc"))
        assertEquals("rueckmeldungen" to 12, Tresor.zerlegen("rueckmeldungen.12.json.enc"))
        // Die grossen Dateien gehoeren nicht dazu.
        assertNull(Tresor.zerlegen("1727000000000.m4a.enc"))
        assertNull(Tresor.zerlegen("1727000000000.medium.enc"))
        assertNull(Tresor.zerlegen("1727000000000.3.json.enc.tmp"))
    }
}
