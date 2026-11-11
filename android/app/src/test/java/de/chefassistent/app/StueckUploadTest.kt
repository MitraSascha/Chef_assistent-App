package de.chefassistent.app

import de.chefassistent.app.data.ApiFehler
import de.chefassistent.app.data.StueckUpload
import de.chefassistent.app.data.UploadAusgang
import de.chefassistent.app.data.UploadStand
import de.chefassistent.app.data.UploadStart
import de.chefassistent.app.data.UploadWeg
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream

/** Hochladen in Stuecken (1.35.0, API.md 4h) gegen einen nachgebauten Server. */
class StueckUploadTest {

    /** Verhaelt sich wie `assistent/teilupload.py`. */
    private class FalscherServer(
        var empfangenAmAnfang: Int = 0,
        val teilMax: Long = 8,
    ) : UploadWeg {
        var daten = ByteArray(0)
        var groesse = 0L
        var status = UploadStand.STATUS_OFFEN
        val offsets = mutableListOf<Long>()
        var abschluesse = 0

        /** Vor dem n-ten PUT (0-basiert) einmal werfen. */
        val stoerungen = mutableMapOf<Int, ApiFehler>()

        /** Kommt an, aber die Antwort geht verloren (Funkloch nach dem Senden). */
        val verlorenNach = mutableSetOf<Int>()
        var abschliessenFehler: ApiFehler? = null
        var gibtEsNicht = false
        var alt = false

        private fun stand() = UploadStand(
            upload_id = "a1b2c3d4", groesse = groesse, empfangen = daten.size.toLong(), teil_max = teilMax,
            status = status, ergebnis = if (status == UploadStand.STATUS_FERTIG) buildJsonObject { put("foto", "x.jpg") } else null,
        )

        override suspend fun starten(start: UploadStart): UploadStand {
            if (alt) throw ApiFehler.NichtGefunden("Not Found")
            if (groesse == 0L) {
                groesse = start.groesse
                daten = ByteArray(empfangenAmAnfang) { it.toByte() }
            }
            return stand()
        }

        override suspend fun teil(id: String, offset: Long, daten: ByteArray): UploadStand {
            val nr = offsets.size
            offsets += offset
            stoerungen.remove(nr)?.let { throw it }
            if (gibtEsNicht) throw ApiFehler.NichtGefunden("Upload nicht gefunden")
            if (offset != this.daten.size.toLong()) throw ApiFehler.Beschaeftigt("Falscher offset", this.daten.size.toLong())
            assertTrue("Stueck groesser als teil_max", daten.size <= teilMax)
            this.daten = this.daten + daten
            if (verlorenNach.remove(nr)) throw ApiFehler.KeineVerbindung(java.io.IOException("weg"))
            return stand()
        }

        override suspend fun stand(id: String): UploadStand {
            if (gibtEsNicht) throw ApiFehler.NichtGefunden("Upload nicht gefunden")
            return stand()
        }

        override suspend fun abschliessen(id: String): UploadStand {
            abschliessenFehler?.let {
                status = UploadStand.STATUS_FEHLER
                throw it
            }
            if (daten.size.toLong() != groesse) throw ApiFehler.Beschaeftigt("Noch nicht vollständig", daten.size.toLong())
            abschluesse++
            status = UploadStand.STATUS_FERTIG
            return stand()
        }
    }

    private val datei = ByteArray(30) { (it * 7).toByte() }

    private fun start() = UploadStart(
        upload_id = "a1b2c3d4", ziel = UploadStart.ZIEL_FOTO, dateiname = "f.jpg", groesse = datei.size.toLong(),
    )

    private fun hochladen(server: FalscherServer, nochGewollt: () -> Boolean = { true }) = runBlocking {
        StueckUpload.hochladen(
            weg = server,
            start = start(),
            oeffnen = { ByteArrayInputStream(datei) },
            teilGroesse = { 8 },
            nochGewollt = nochGewollt,
        )
    }

    @Test
    fun `ganze Datei in Stuecken, dann abschliessen`() {
        val server = FalscherServer()
        val ergebnis = hochladen(server)
        assertTrue(ergebnis is UploadAusgang.Fertig)
        assertEquals(JsonPrimitive("x.jpg"), (ergebnis as UploadAusgang.Fertig).ergebnis["foto"])
        assertArrayEquals(datei, server.daten)
        assertEquals(listOf(0L, 8L, 16L, 24L), server.offsets)
        assertEquals(1, server.abschluesse)
    }

    @Test
    fun `nach Neustart geht es beim Stand des Servers weiter`() {
        val server = FalscherServer(empfangenAmAnfang = 16)
        // Was der Server schon hat, ist bei ihm "echt" - hier die ersten 16 Byte der Datei.
        server.groesse = datei.size.toLong()
        server.daten = datei.copyOfRange(0, 16)
        hochladen(server)
        assertEquals(16L, server.offsets.first())
        assertArrayEquals(datei, server.daten)
    }

    @Test
    fun `Antwort verloren, Stueck aber da - 409 springt weiter, nichts doppelt`() {
        val server = FalscherServer()
        server.verlorenNach += 1
        // Erster Lauf: nach dem zweiten Stueck reisst die Verbindung ab.
        try {
            hochladen(server)
            fail("Verbindungsfehler muesste durchgereicht werden")
        } catch (fehler: ApiFehler.KeineVerbindung) {
            // erwartet - der Arbeiter macht spaeter weiter
        }
        // Zweiter Lauf: Stand 16, es geht dort weiter.
        server.offsets.clear()
        hochladen(server)
        assertEquals(16L, server.offsets.first())
        assertArrayEquals(datei, server.daten)
    }

    @Test
    fun `409 mitten drin - X-Empfangen sagt, wo es weitergeht`() {
        val server = FalscherServer()
        // Vor dem dritten PUT meldet der Server einen anderen Stand (8 statt 16).
        server.stoerungen[2] = ApiFehler.Beschaeftigt("Falscher offset", 8)
        server.daten = ByteArray(0)
        val ergebnis = runBlocking {
            server.starten(start())
            server.daten = datei.copyOfRange(0, 8) // Server hat nur 8
            StueckUpload.hochladen(server, start(), { ByteArrayInputStream(datei) }, { 8 })
        }
        assertTrue(ergebnis is UploadAusgang.Fertig)
        assertArrayEquals(datei, server.daten)
    }

    @Test
    fun `alter Server ohne uploads`() {
        val server = FalscherServer().apply { alt = true }
        assertEquals(UploadAusgang.AlterServer, hochladen(server))
    }

    @Test
    fun `im Ausgang geloescht - kein Abschliessen`() {
        val server = FalscherServer()
        var gefragt = 0
        val ergebnis = hochladen(server) { ++gefragt <= 2 }
        assertEquals(UploadAusgang.Abgebrochen, ergebnis)
        assertEquals(0, server.abschluesse)
    }

    @Test
    fun `fachlicher Fehler beim Abschliessen wird nicht wiederholt`() {
        val server = FalscherServer().apply { abschliessenFehler = ApiFehler.FalscherTyp("Kein Bild") }
        val ergebnis = hochladen(server)
        assertTrue(ergebnis is UploadAusgang.Abgelehnt)
        assertTrue((ergebnis as UploadAusgang.Abgelehnt).fehler is ApiFehler.FalscherTyp)
    }

    @Test
    fun `verfallener Upload braucht eine neue ID`() {
        val server = FalscherServer()
        server.stoerungen[1] = ApiFehler.NichtGefunden("Upload nicht gefunden")
        assertEquals(UploadAusgang.NeueIdNoetig, hochladen(server))
    }

    @Test
    fun `Server schon fertig - sofort Ergebnis, nichts gesendet`() {
        val server = FalscherServer().apply {
            groesse = datei.size.toLong()
            daten = datei
            status = UploadStand.STATUS_FERTIG
        }
        assertTrue(hochladen(server) is UploadAusgang.Fertig)
        assertTrue(server.offsets.isEmpty())
    }

    @Test
    fun `Datei kuerzer als angekuendigt ist defekt`() {
        val server = FalscherServer()
        val ergebnis = runBlocking {
            StueckUpload.hochladen(server, start(), { ByteArrayInputStream(datei.copyOf(10)) }, { 8 })
        }
        assertTrue(ergebnis is UploadAusgang.Defekt)
    }

    @Test
    fun `upload_id passt zum Vertrag`() {
        repeat(20) { assertTrue(UploadStart.UPLOAD_ID_MUSTER.matches(UploadStart.neueId())) }
    }
}
