package de.chefassistent.app.monteur

import de.chefassistent.app.data.Netzwerk
import de.chefassistent.app.monteur.data.EingangAuftrag
import de.chefassistent.app.monteur.data.Karte
import de.chefassistent.app.monteur.data.Passt
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FotoAblageTest {
    private val json = Netzwerk.json

    @Test
    fun karteFotoUndFrageMitFoto() {
        val k = json.decodeFromString(
            Karte.serializer(),
            """{"id":"e1","art":"foto","foto":{"titel":"Vorher Bad","abgelegt":[]},"fotos":2,"neu":1}""",
        )
        assertEquals(Karte.ART_FOTO, k.art)
        assertEquals("Vorher Bad", k.foto!!.titel)
        assertTrue(k.foto!!.abgelegt.isEmpty())
        val frage = json.decodeFromString(Karte.serializer(), """{"id":"e2","art":"frage"}""")
        assertNull(frage.foto)
    }

    @Test
    fun passtSchicktNurGeaendertenTitel() {
        val o = json.parseToJsonElement(json.encodeToString(Passt.serializer(), Passt(titel = "Zählerstand Wasser"))).jsonObject
        assertEquals(JsonPrimitive("Zählerstand Wasser"), o["titel"])
        val leer = json.parseToJsonElement(json.encodeToString(Passt.serializer(), Passt())).jsonObject
        assertFalse(leer.containsKey("titel"))
    }

    @Test
    fun korbAltLesbarOhneFotoIds() {
        val e = json.decodeFromString(EingangAuftrag.serializer(), """{"anfrage_id":"a1","text":"x"}""")
        assertTrue(e.foto_ids.isEmpty())
    }
}
