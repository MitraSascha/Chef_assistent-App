package de.chefassistent.app

import de.chefassistent.app.data.BewertungAntwort
import de.chefassistent.app.data.ChatAntwort
import de.chefassistent.app.data.MedienAuftrag
import de.chefassistent.app.data.Nachricht
import de.chefassistent.app.data.Netzwerk
import de.chefassistent.app.data.VORLIEBE_FIRMA
import de.chefassistent.app.data.VORLIEBE_MAX_ZEICHEN
import de.chefassistent.app.data.VORLIEBE_MICH
import de.chefassistent.app.data.VorliebeAntwort
import de.chefassistent.app.data.VorliebeNeu
import de.chefassistent.app.data.VorliebenListe
import de.chefassistent.app.data.bewertungKoerper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** v1.32.0 Vorlieben (API.md 4f) und v1.34.0 👎 (API.md 4g). */
class VorliebenBewertungTest {

    private val json = Netzwerk.json

    private val vertrag by lazy {
        val datei = listOf(File("../../openapi.json"), File("../openapi.json"), File("openapi.json")).first { it.exists() }
        Json.parseToJsonElement(datei.readText()).jsonObject["components"]!!.jsonObject["schemas"]!!.jsonObject
    }

    @Test
    fun `liest die Vorlieben aus dem Beispiel in API_md`() {
        val liste = json.decodeFromString<VorliebenListe>(
            """{"firma": [{"id": 3, "text": "Material bei Kunde Grimm immer über Richter+Frenzel", "fuer": "firma",
                           "von": "patrick", "geaendert": "2026-11-05T09:12:00+00:00"}],
                "eigene": [{"id": 12, "text": "Zusammenfassungen immer als PDF", "fuer": "mich", "von": "sascha",
                            "geaendert": "2026-11-05T09:12:00+00:00", "neu_vom_server": 1}],
                "firma_aendern": false}"""
        )
        assertEquals(VORLIEBE_FIRMA, liste.firma.single().fuer)
        assertEquals(12, liste.eigene.single().id)
        assertFalse(liste.firma_aendern)
    }

    @Test
    fun `Antwort auf Merken mit und ohne Hinweis`() {
        val mit = json.decodeFromString<VorliebeAntwort>(
            """{"vorliebe": {"id": 1, "text": "x", "fuer": "mich", "von": "s", "geaendert": "2026-11-05T09:12:00+00:00"},
                "hinweis": "War schon gespeichert."}"""
        )
        assertEquals("War schon gespeichert.", mit.hinweis)
        val ohne = json.decodeFromString<VorliebeAntwort>(
            """{"vorliebe": {"id": 1, "text": "x", "fuer": "mich", "von": "s", "geaendert": "z"}, "hinweis": null}"""
        )
        assertNull(ohne.hinweis)
    }

    @Test
    fun `Merken schickt Text und Bereich`() {
        assertEquals("""{"text":"a","fuer":"firma"}""", json.encodeToString(VorliebeNeu("a", VORLIEBE_FIRMA)))
    }

    @Test
    fun `Bereiche und Laenge stimmen mit dem Vertrag`() {
        val neu = vertrag["VorliebeNeu"]!!.jsonObject["properties"]!!.jsonObject
        val werte = neu["fuer"]!!.jsonObject["enum"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
        assertEquals(setOf(VORLIEBE_MICH, VORLIEBE_FIRMA), werte)
        assertEquals(VORLIEBE_MAX_ZEICHEN, neu["text"]!!.jsonObject["maxLength"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `Bewertung setzen schickt wert und Grund`() {
        assertEquals("""{"wert":"schlecht","grund":"falsches Gerät"}""", bewertungKoerper(true, "  falsches Gerät ").toString())
        assertEquals("""{"wert":"schlecht"}""", bewertungKoerper(true, "   ").toString())
        assertEquals("""{"wert":"schlecht"}""", bewertungKoerper(true, null).toString())
    }

    @Test
    fun `Zuruecknehmen schickt ausdruecklich null`() {
        // Ueber denselben Weg wie Retrofit, also mit explicitNulls = false.
        val text = json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), bewertungKoerper(false, "egal"))
        assertEquals("""{"wert":null}""", text)
    }

    @Test
    fun `nachricht_id in der Chat-Antwort und bewertung im Verlauf`() {
        val antwort = json.decodeFromString<ChatAntwort>("""{"gespraech_id": "g", "antwort": "a", "nachricht_id": 4711}""")
        assertEquals(4711L, antwort.nachricht_id)
        val alt = json.decodeFromString<ChatAntwort>("""{"gespraech_id": "g", "antwort": "a"}""")
        assertNull(alt.nachricht_id)

        val nachricht = json.decodeFromString<Nachricht>(
            """{"id": 9, "rolle": "assistent", "text": "t", "zeit": "z", "bewertung": "schlecht", "bewertung_grund": "falsch"}"""
        )
        assertEquals("schlecht", nachricht.bewertung)
        val ohne = json.decodeFromString<Nachricht>("""{"id": 9, "rolle": "assistent", "text": "t", "zeit": "z"}""")
        assertNull(ohne.bewertung)
    }

    @Test
    fun `Antwort auf die Bewertung`() {
        val zurueck = json.decodeFromString<BewertungAntwort>("""{"nachricht_id": 9, "bewertung": null, "bewertung_grund": null}""")
        assertNull(zurueck.bewertung)
    }

    @Test
    fun `alter Medienauftrag ohne faden bleibt lesbar`() {
        val auftrag = json.decodeFromString<MedienAuftrag>("""{"art": "foto", "aufgenommen": 1, "gespraech_id": null}""")
        assertNull(auftrag.faden)
        assertTrue(json.encodeToString(MedienAuftrag.serializer(), auftrag.copy(faden = "f")).contains("\"faden\":\"f\""))
    }
}
