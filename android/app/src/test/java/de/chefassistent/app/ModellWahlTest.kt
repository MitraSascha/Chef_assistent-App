package de.chefassistent.app

import de.chefassistent.app.data.ChatAnfrage
import de.chefassistent.app.data.GespraechKurz
import de.chefassistent.app.data.MODELL_AUTO
import de.chefassistent.app.data.ModellAuswahl
import de.chefassistent.app.data.ModellWahl
import de.chefassistent.app.data.Netzwerk
import de.chefassistent.app.ui.chat.denkaufwandName
import de.chefassistent.app.data.istModellFehler
import de.chefassistent.app.ui.chat.modellName
import de.chefassistent.app.ui.chat.modellZeile
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.20.0: Modell und Denkaufwand waehlen (API.md 1c, Auftrag 1.20 Teil A). */
class ModellWahlTest {

    private val json = Netzwerk.json

    /** Beispiel aus dem Auftrag, ergaenzt um Felder, die die App nicht kennt. */
    private val auswahl = json.decodeFromString<ModellAuswahl>(
        """
        {"modelle": [{"wert": "gpt-5.6-luna", "name": "gpt-5.6-luna", "teuer": false, "hinweis": null},
                     {"wert": "gpt-5.6-sol", "name": "gpt-5.6-sol", "teuer": false, "hinweis": null},
                     {"wert": "gpt-5.6-astra", "name": "gpt-5.6-astra", "teuer": true,
                      "hinweis": "Deutlich teurer pro Anfrage – nur für besonders schwierige Aufgaben",
                      "preis_faktor": 12}],
         "denkstufen": [{"wert": "low", "name": "niedrig"}, {"wert": "medium", "name": "mittel"},
                        {"wert": "high", "name": "hoch", "neu": true}],
         "automatisch": "Automatisch: Der Server wählt je Nachricht.",
         "standard_modell": "gpt-5.6-luna", "arbeits_modell": "gpt-5.6-sol",
         "standard_denkaufwand": {"wert": "low", "name": "niedrig"},
         "arbeits_denkaufwand": {"wert": "medium", "name": "mittel"},
         "kuenftiges_feld": {"x": 1}}
        """.trimIndent()
    )

    @Test
    fun `liest GET v1 modelle aus dem Auftrag samt unbekannter Felder`() {
        assertEquals(listOf("gpt-5.6-luna", "gpt-5.6-sol", "gpt-5.6-astra"), auswahl.modelle.map { it.wert })
        assertEquals(listOf("low", "medium", "high"), auswahl.denkstufen.map { it.wert })
        assertTrue(auswahl.istTeuer("gpt-5.6-astra"))
        assertFalse(auswahl.istTeuer("gpt-5.6-sol"))
        assertFalse(auswahl.istTeuer(MODELL_AUTO))
        assertEquals("gpt-5.6-sol", auswahl.arbeits_modell)
        assertEquals("mittel", auswahl.arbeits_denkaufwand?.name)
        assertTrue(auswahl.automatisch.startsWith("Automatisch"))
    }

    @Test
    fun `Zeile unter der Antwort`() {
        assertEquals("gpt-5.6-luna · niedrig", modellZeile("gpt-5.6-luna", "niedrig", false, auswahl))
        assertEquals("gpt-5.6-sol · mittel · gewechselt", modellZeile("gpt-5.6-sol", "mittel", true, auswahl))
        assertEquals("gpt-5.6-astra (teuer) · hoch", modellZeile("gpt-5.6-astra", "hoch", false, auswahl))
    }

    @Test
    fun `Zeile ohne Modell bleibt leer, ohne Auswahl ohne Teuer-Marke`() {
        assertNull(modellZeile(null, "mittel", true, auswahl))
        assertNull(modellZeile("  ", null, false, auswahl))
        assertEquals("gpt-5.6-astra", modellZeile("gpt-5.6-astra", null, false, null))
        // Unbekanntes Modell (z. B. aus der Auswahl genommen): Wert zeigen.
        assertEquals("gpt-9 · hoch", modellZeile("gpt-9", "hoch", false, auswahl))
    }

    @Test
    fun `Anzeigenamen, auto ist Automatisch`() {
        assertNull(modellName(MODELL_AUTO, auswahl))
        assertNull(denkaufwandName(MODELL_AUTO, auswahl))
        assertEquals("gpt-5.6-sol", modellName("gpt-5.6-sol", auswahl))
        assertEquals("hoch", denkaufwandName("high", auswahl))
        assertEquals("xhigh", denkaufwandName("xhigh", auswahl))
    }

    @Test
    fun `ChatAnfrage schickt modell und denkaufwand, laesst sie bei null weg`() {
        val mit = json.encodeToJsonElement(
            ChatAnfrage.serializer(),
            ChatAnfrage(text = "Hallo", gespraech_id = null, modell = "gpt-5.6-sol", denkaufwand = MODELL_AUTO),
        ).jsonObject
        assertEquals("\"gpt-5.6-sol\"", mit["modell"].toString())
        assertEquals("\"auto\"", mit["denkaufwand"].toString())

        val ohne = json.encodeToJsonElement(
            ChatAnfrage.serializer(),
            ChatAnfrage(text = "Hallo", gespraech_id = null),
        ).jsonObject
        assertFalse("modell" in ohne)
        assertFalse("denkaufwand" in ohne)
    }

    @Test
    fun `PATCH schickt nur das geaenderte Feld`() {
        val text = json.encodeToString(ModellWahl.serializer(), ModellWahl(denkaufwand = "high"))
        assertEquals("""{"denkaufwand":"high"}""", text)
    }

    @Test
    fun `GespraechKurz ohne die Felder ist auto`() {
        val alt = json.decodeFromString<GespraechKurz>(
            """{"id": "g1", "titel": "Test", "erstellt": "2026-10-15T09:00:00+02:00", "geaendert": "2026-10-15T09:00:00+02:00"}"""
        )
        assertEquals(MODELL_AUTO, alt.modell)
        assertEquals(MODELL_AUTO, alt.denkaufwand)

        val neu = json.decodeFromString<GespraechKurz>(
            """{"id": "g1", "titel": null, "erstellt": "x", "geaendert": "y", "laeuft": false,
                "modell": "gpt-5.6-sol", "denkaufwand": "high"}"""
        )
        assertEquals("gpt-5.6-sol", neu.modell)
        assertEquals("high", neu.denkaufwand)
    }

    @Test
    fun `422 setzt nur bei fester Wahl und passender Meldung zurueck`() {
        val meldung = "Unbekanntes Modell „gpt-4“. Erlaubt: auto, gpt-5.6-luna, gpt-5.6-sol."
        assertTrue(istModellFehler(meldung, ModellWahl("gpt-4", MODELL_AUTO)))
        assertTrue(istModellFehler("Unbekannter Denkaufwand „ultra“", ModellWahl(MODELL_AUTO, "ultra")))
        // "auto" ist immer erlaubt - dann lag es an etwas anderem.
        assertFalse(istModellFehler(meldung, ModellWahl(MODELL_AUTO, MODELL_AUTO)))
        assertFalse(istModellFehler(meldung, null))
        // Anderer 422 trotz fester Wahl.
        assertFalse(istModellFehler("Text ist leer.", ModellWahl("gpt-5.6-sol", "high")))
    }
}
