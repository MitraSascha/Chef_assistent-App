package de.chefassistent.app.monteur

import de.chefassistent.app.data.Netzwerk
import de.chefassistent.app.monteur.data.ArtikelWahl
import de.chefassistent.app.monteur.data.BerichtAenderung
import de.chefassistent.app.monteur.data.MaterialAenderung
import de.chefassistent.app.monteur.data.MonteurBericht
import de.chefassistent.app.monteur.data.MonteurRepository
import de.chefassistent.app.monteur.data.MonteurTag
import de.chefassistent.app.monteur.data.Projektmappe
import de.chefassistent.app.monteur.data.Tageszeiten
import de.chefassistent.app.monteur.data.Verstanden
import de.chefassistent.app.monteur.data.materialJson
import kotlinx.serialization.json.JsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Monteur-Vertrag 1.22.0 (API.md 5c): lesen, tolerant gegen neue Felder, Koerper wie verlangt. */
class MonteurVertragTest {

    private val json = Netzwerk.json

    @Test
    fun `QR-Code wird zerlegt`() {
        assertEquals(
            "https://assistent.mitra-sanitaer.de/" to "ABCD-EFGH-JKLM",
            MonteurRepository.qrLesen("mitra-monteur:https://assistent.mitra-sanitaer.de#abcd-efgh-jklm"),
        )
        assertNull(MonteurRepository.qrLesen("https://example.com"))
        assertNull(MonteurRepository.qrLesen("mitra-monteur:https://x.de#"))
        assertNull(MonteurRepository.qrLesen("mitra-monteur:ftp://x.de#ABCD"))
    }

    @Test
    fun `Code abgetippt ohne Bindestriche`() {
        assertEquals("ABCD-EFGH-JKLM", MonteurRepository.codeNormalisieren("abcd efgh jklm"))
        assertEquals("ABCD-EFGH-JKLM", MonteurRepository.codeNormalisieren("ABCDEFGHJKLM"))
    }

    @Test
    fun `anfrage_id passt zum Muster`() {
        assertTrue(Regex("^[A-Za-z0-9_-]{8,64}$").matches(MonteurRepository.neueAnfrageId()))
    }

    @Test
    fun `einrichten geht ohne Token raus`() {
        assertTrue(Netzwerk.istAnmeldung("/v1/monteur/einrichten"))
        assertFalse(Netzwerk.istAnmeldung("/v1/monteur/tage"))
    }

    @Test
    fun `Tag mit Termin, Projekt und Bericht`() {
        val tage = json.decodeFromString<List<MonteurTag>>(
            """[{"datum":"2026-10-16","wochentag":"Freitag","neu":1,"termine":[{"id":"hero:5","titel":"Wartung",
            "beginn":"2026-10-16T08:00:00+02:00","ende":"2026-10-16T10:00:00+02:00","ganztag":false,"kategorie":"Kundendienst",
            "beschreibung":null,"kollegen":["Mehmet"],"projekt":{"id":42,"nr":"SAN-1","titel":"Bad","kunde":"Böse",
            "adresse":"Grimmstr. 7","geo":{"lat":52.5,"lng":13.4},"telefon":[{"nummer":"030 1","name":"Böse","art":"mobil"}]},
            "berichte":[{"id":"abc","status":"fertig","hero_nr":"BER-1330"}]}]}]"""
        )
        val t = tage.single().termine.single()
        assertEquals("hero:5", t.id)
        assertEquals(42, t.projekt?.id)
        assertEquals("BER-1330", t.berichte.single().hero_nr)
    }

    @Test
    fun `Projektmappe mit Unterlage ohne Datei`() {
        val m = json.decodeFromString<Projektmappe>(
            """{"id":1,"nr":"SAN-1","titel":"t","kunde":"k","adresse":"a","telefon":[],"notizen":"",
            "unterlagen":[{"art":"Angebot","nr":"ANG-1","name":"a.pdf","datum":"2026-10-01","status":"Erstellt","datei":null}],
            "fotos":[{"uuid":"u1","name":"f.jpg","datum":null}]}"""
        )
        assertNull(m.unterlagen.single().datei)
        assertEquals("u1", m.fotos.single().uuid)
    }

    @Test
    fun `Bericht mit Material-Rueckfrage`() {
        val b = json.decodeFromString<MonteurBericht>(
            """{"id":"x","status":"entwurf","datum":"2026-10-16","termin_id":"hero:5","projekt":{"id":42,"nr":"SAN-1"},
            "diktat":"WC neu","entwurf":{"taetigkeit":"WC demontiert.","anstehend":[],"monteur_stunden":3.0,
            "helfer_stunden":0,"anfahrt":true,"kleinmaterial":true,"material":[{"text":"Eckventil","menge":2,"einheit":"Stk",
            "artikel":null,"vorschlaege":[{"nr":"123","name":"Eckventil 1/2","einheit":"Stk"}],"rueckfrage":"1/2 oder 3/8?"}],
            "zeiten":[{"beginn":"08:00","ende":"11:00","art":"arbeit"}],"hinweise_buero":[],"rueckfragen":[]},
            "hinweise":[],"erstellt":"x","geaendert":"y"}"""
        )
        assertFalse(b.istFertig)
        assertEquals("1/2 oder 3/8?", b.entwurf.material.single().rueckfrage)
        assertEquals(3.0, b.entwurf.monteur_stunden, 0.0)
    }

    @Test
    fun `Material-Aenderung unterscheidet behalten, waehlen und frei`() {
        val liste = materialJson(
            listOf(
                MaterialAenderung(index = 0, menge = 2.0),
                MaterialAenderung(index = 1, menge = 1.0, artikel = ArtikelWahl.Waehlen("123")),
                MaterialAenderung(index = 2, menge = 1.0, artikel = ArtikelWahl.Frei),
                MaterialAenderung(index = null, menge = 3.0, text = "Silikon", einheit = "Stk"),
            )
        )
        assertEquals(
            """[{"index":0,"menge":2.0},{"index":1,"menge":1.0,"artikel_nr":"123"},""" +
                """{"index":2,"menge":1.0,"artikel_nr":null},{"menge":3.0,"text":"Silikon","einheit":"Stk"}]""",
            liste.toString(),
        )
        // Im Koerper bleibt das ausdrueckliche null erhalten, ungesetzte Felder fehlen.
        val koerper = json.encodeToString(BerichtAenderung.serializer(), BerichtAenderung(material = liste))
        assertTrue(koerper.contains("\"artikel_nr\":null"))
        assertFalse(koerper.contains("taetigkeit"))
    }

    @Test
    fun `Zeiten mit laufender Stempeluhr`() {
        val z = json.decodeFromString<Tageszeiten>(
            """{"datum":"2026-10-16","eintraege":[{"id":7,"beginn":"2026-10-16T07:30","ende":"2026-10-16T12:00","art":"arbeit",
            "status":"neu","projekt_nr":"SAN-1","projekt_id":null,"kommentar":"","minuten":270}],"arbeit_minuten":270,
            "pause_minuten":0,"eingereicht":false,"stempel":{"beginn":"2026-10-16T12:30","art":"arbeit","kommentar":""}}"""
        )
        assertTrue(z.eintraege.single().aenderbar)
        assertEquals("arbeit", z.stempel?.art)
    }

    @Test
    fun `Verstehen liefert Zeitvorschlaege in HH-MM`() {
        val v = json.decodeFromString<Verstanden>(
            """{"text":"Feierabend","datum":"2026-10-16","zeiten":[{"beginn":"12:00","ende":"12:30","art":"pause"}],
            "abwesenheit":null,"rueckfrage":null}"""
        )
        assertEquals("12:30", v.zeiten.single().ende)
    }

    @Test
    fun `Projektmappe mit Videos und Antwort aufs Hochladen`() {
        val m = json.decodeFromString<Projektmappe>(
            """{"id":1,"nr":"SAN-1","titel":"t","kunde":"k","adresse":"a","telefon":[],"notizen":"","unterlagen":[],
            "fotos":[],"videos":[{"name":"Begehung.mp4","groesse_mb":12.5,"datum":"2026-10-16"}]}"""
        )
        assertEquals(12.5, m.videos.single().groesse_mb, 0.0)
        val h = json.decodeFromString<de.chefassistent.app.monteur.data.Hochgeladen>(
            """{"art":"video","name":"Begehung.mp4","ziel":"server","hinweis":"Bleibt auf dem Server"}"""
        )
        assertEquals("server", h.ziel)
        // Alte Mappe ohne Videos bleibt lesbar.
        val alt = json.decodeFromString<Projektmappe>(
            """{"id":1,"nr":"","titel":"","kunde":"","adresse":"","telefon":[],"notizen":"","unterlagen":[],"fotos":[]}"""
        )
        assertTrue(alt.videos.isEmpty())
    }

    @Test
    fun `Groesse fuer den Hochladen-Dialog`() {
        assertEquals("1 kB", de.chefassistent.app.monteur.ui.projekt.groesseText(200))
        assertEquals("1,5 MB", de.chefassistent.app.monteur.ui.projekt.groesseText(1_572_864))
        assertEquals("1,0 GB", de.chefassistent.app.monteur.ui.projekt.groesseText(1L shl 30))
    }

    @Test
    fun `leeres Array bleibt erhalten`() {
        assertEquals("[]", materialJson(emptyList()).toString())
        assertTrue(materialJson(emptyList()) is JsonArray)
    }
}
