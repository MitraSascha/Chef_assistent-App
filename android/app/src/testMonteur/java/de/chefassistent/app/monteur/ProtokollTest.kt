package de.chefassistent.app.monteur

import de.chefassistent.app.data.Netzwerk
import de.chefassistent.app.monteur.data.Karte
import de.chefassistent.app.monteur.data.Passt
import de.chefassistent.app.monteur.data.ProtokollFeld
import de.chefassistent.app.monteur.ui.karte.FeldArt
import de.chefassistent.app.monteur.ui.karte.aenderungSetzen
import de.chefassistent.app.monteur.ui.karte.aktuellerWert
import de.chefassistent.app.monteur.ui.karte.feldArt
import de.chefassistent.app.monteur.ui.karte.fehlendeFelder
import de.chefassistent.app.monteur.ui.karte.umschalten
import de.chefassistent.app.monteur.ui.karte.werteJson
import de.chefassistent.app.monteur.ui.karte.zahlEingabe
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtokollTest {
    private val json = Netzwerk.json

    private val karteJson = """
        {"id":"e1","art":"protokoll","status":"offen","projekt":{"id":5,"nr":"UNB-795","kunde":"Firma WEG"},
         "protokoll":{"vorlage":"druckpruefung_trinkwasser","titel":"Druckprüfung Trinkwasserinstallation",
           "grundlage":"DIN EN 806-4",
           "felder":[
             {"k":"abschnitt","l":"Geprüfter Abschnitt","t":"text","e":null,"o":null,"p":true,"wert":null},
             {"k":"werkstoff","l":"Werkstoff","t":"auswahl","e":null,"o":["Metall","Kunststoff"],"p":true,"wert":null},
             {"k":"pruefdruck","l":"Prüfdruck","t":"zahl","e":"bar","o":null,"p":true,"wert":"10"},
             {"k":"dicht","l":"Ergebnis: Anlage dicht","t":"janein","e":null,"o":null,"p":true,"wert":"ja"},
             {"k":"bemerkung","l":"Bemerkung","t":"text","e":null,"o":null,"p":false,"wert":null},
             {"k":"neu","l":"Neu","t":"unterschrift_skizze","p":false,"zukunft":1}
           ],
           "fehlend":["Geprüfter Abschnitt","Werkstoff"]}}
    """.trimIndent()

    @Test
    fun karteLesbarAuchMitUnbekanntemTyp() {
        val k = json.decodeFromString(Karte.serializer(), karteJson)
        assertEquals(Karte.ART_PROTOKOLL, k.art)
        val p = k.protokoll!!
        assertEquals(6, p.felder.size)
        assertEquals("bar", p.felder[2].e)
        assertEquals(FeldArt.TEXT, feldArt(p.felder[5]))
        assertEquals(FeldArt.MEHRZEILIG, feldArt(p.felder[4]))
        assertEquals(FeldArt.AUSWAHL, feldArt(p.felder[1]))
        assertEquals(FeldArt.ZAHL, feldArt(p.felder[2]))
        assertEquals(FeldArt.JANEIN, feldArt(p.felder[3]))
    }

    @Test
    fun fehlendLokalNeuGeprueft() {
        val p = json.decodeFromString(Karte.serializer(), karteJson).protokoll!!
        assertEquals(listOf("Geprüfter Abschnitt", "Werkstoff"), fehlendeFelder(p.felder, emptyMap()))
        var m = aenderungSetzen(emptyMap(), p.felder[0], "Keller bis OG")
        m = aenderungSetzen(m, p.felder[3], null) // dicht geleert
        assertEquals(listOf("Werkstoff", "Ergebnis: Anlage dicht"), fehlendeFelder(p.felder, m))
        // Nur Leerzeichen fuellt kein Pflichtfeld.
        assertEquals(listOf("Werkstoff", "Ergebnis: Anlage dicht"), fehlendeFelder(p.felder, m + ("werkstoff" to "  ")))
    }

    @Test
    fun nurGeaendertesGehtRaus() {
        val f = ProtokollFeld(k = "pruefdruck", l = "Prüfdruck", t = "zahl", p = true, wert = "10")
        var m = aenderungSetzen(emptyMap(), f, "12")
        assertEquals("12", aktuellerWert(f, m))
        m = aenderungSetzen(m, f, "10") // zurueck auf Serverwert
        assertTrue(m.isEmpty())
        assertNull(werteJson(m))
        m = aenderungSetzen(m, f, "")
        assertEquals(JsonObject(mapOf("pruefdruck" to JsonNull)), werteJson(m))
    }

    @Test
    fun passtSchicktNullZumLeeren() {
        val p = Passt(werte = werteJson(mapOf("druckabfall" to "0", "bemerkung" to null)), nur_speichern = true)
        val o = json.parseToJsonElement(json.encodeToString(Passt.serializer(), p)).jsonObject
        val w = o["werte"]!!.jsonObject
        assertEquals(JsonPrimitive("0"), w["druckabfall"])
        assertEquals(JsonNull, w["bemerkung"])
        assertEquals(JsonPrimitive(true), o["nur_speichern"])
        assertFalse(o.containsKey("vorlage"))
        assertFalse(o.containsKey("text"))
    }

    @Test
    fun auswahlNochmalTippenLeert() {
        assertEquals("ja", umschalten(null, "ja"))
        assertNull(umschalten("ja", "ja"))
        assertNull(umschalten("Metall", "metall"))
        assertEquals("nein", umschalten("ja", "nein"))
    }

    @Test
    fun zahlenfeld() {
        assertEquals("0,2", zahlEingabe("0.2"))
        assertEquals("10,53", zahlEingabe("10,5,3"))
        assertEquals("-1", zahlEingabe("-1"))
        assertEquals("15", zahlEingabe("1-5 bar"))
    }
}
