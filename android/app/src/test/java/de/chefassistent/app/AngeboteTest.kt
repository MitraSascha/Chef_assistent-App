package de.chefassistent.app

import de.chefassistent.app.data.Angebot
import de.chefassistent.app.data.ANTWORT_PASST
import de.chefassistent.app.data.AngebotAntwort
import de.chefassistent.app.data.AngebotKurz
import de.chefassistent.app.data.AngebotPunkt
import de.chefassistent.app.data.PUNKT_ARTEN
import de.chefassistent.app.data.AssistentRepository
import de.chefassistent.app.data.Netzwerk
import de.chefassistent.app.ui.angebote.Eintrag
import de.chefassistent.app.ui.angebote.betrag
import de.chefassistent.app.ui.angebote.eintraegeAus
import de.chefassistent.app.ui.angebote.menge
import de.chefassistent.app.ui.angebote.zeitpunkt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** Angebote (`/v1/angebote`, seit v1.11.0, Entscheidungen seit v1.15.0). */
class AngeboteTest {

    private val json = Netzwerk.json

    /** Beispiel aus API.md, Abschnitt 4e, dazu ein unbekanntes Feld. */
    private val beispiel = """
        {"id": "b35a08287b405d8d", "titel": "PET44 WE 01 – Petersburger Str. 44", "bearbeiter": "Sascha Richter", "version": 21,
         "geaendert": "2026-10-09T13:30:12", "ek_summe": "9195.69", "pauschalen_summe": "4468.00",
         "vorschau": "https://angebote.mitra-sanitaer.de/a/b35a08287b405d8d", "neu_vom_server": true,
         "gruppen": [
          {"nr": "1", "titel": "Arbeits- und Beschaffungskosten", "ebene": 1, "bereich": "Arbeits- und Beschaffungskosten", "unterbereich": "",
           "positionen": [{"id": "p52", "nr": "1._.001", "art": "ANF", "menge": "1", "name": "Anfahrtskosten", "ek": "0", "lp": "0",
                           "ek_gesamt": "0", "vk": "250.00", "vk_gesamt": "250.00", "pauschale": true, "bemerkung": "",
                           "quelle": "pauschale/anfahrt", "genutzt": 0, "markierung": {"art": "info", "text": "Rechenweg: …"}}]},
          {"nr": "2.3", "titel": "Waschtisch", "ebene": 2, "bereich": "Badezimmer", "unterbereich": "Waschtisch",
           "positionen": [{"id": "p17", "nr": "2.3.004", "art": "SEVEN", "menge": "1", "name": "Einhand Waschtischbatterie Seven …",
                           "ek": "114.5", "lp": "173.5", "ek_gesamt": "114.50", "bemerkung": "Alt. zu LV: Duravit DU C.1",
                           "quelle": "waschtisch_up/standard:vorgabe set", "genutzt": 16,
                           "markierung": {"art": "alternative", "text": "Alternative zu LV: Duravit DU C.1"}},
                          {"id": "p23", "nr": "2.4.002", "art": "X", "menge": "2.50", "name": "Ablauf", "ek": "10", "ek_gesamt": "25",
                           "markierung": null}]}],
         "fragen": [{"id": "f2", "text": "Ablauf und Füße … passen sie zur Alternative?", "erledigt": false,
                     "positionen": ["p23"], "positionen_nr": ["2.4.002"], "gruppe_nr": ""},
                    {"id": "f3", "text": "Schon erledigt", "erledigt": true}],
         "markiert": [{"id": "p17", "nr": "2.3.004", "art_nr": "SEVEN", "name": "Einhand Waschtischbatterie Seven …",
                       "art": "alternative", "text": "Alternative zu LV: Duravit DU C.1"}],
         "hinweise": ["Paket 'Dusche mit Duschwanne' ist ein Entwurf – Artikelauswahl von Mitra noch nicht bestätigt."]}
    """.trimIndent()

    @Test
    fun `liest das Angebot aus API md`() {
        val angebot = json.decodeFromString<Angebot>(beispiel)
        assertEquals(21, angebot.version)
        assertEquals("9195.69", angebot.ek_summe)
        val anfahrt = angebot.gruppen.first().positionen.single()
        assertTrue(anfahrt.pauschale)
        assertEquals("250.00", anfahrt.vk_gesamt)
        assertEquals("info", anfahrt.markierung?.art)
        assertNull(angebot.gruppen[1].positionen[1].markierung)
        assertEquals(listOf("2.4.002"), angebot.fragen.first().positionen_nr)
    }

    @Test
    fun `liest die Liste und ein karges Angebot`() {
        val liste = json.decodeFromString<List<AngebotKurz>>(
            """[{"id": "b35a", "titel": "PET44", "positionen": 55, "geaendert": "2026-10-09T13:30:12"}]"""
        )
        assertEquals(55, liste.single().positionen)
        val karg = json.decodeFromString<Angebot>("""{"id": "x"}""")
        assertEquals("0", karg.pauschalen_summe)
        assertTrue(karg.gruppen.isEmpty())
    }

    /** Beispiel aus API.md 4e "Entscheidungen" (1.15.0), Auslassungen ergaenzt. */
    private val punkte = """
        {"id": "b35a", "titel": "PET44",
         "gruppen": [{"nr": "5.4", "titel": "Dusche", "ebene": 2,
           "positionen": [{"id": "p22", "nr": "5.4.002", "menge": "1", "name": "Duravit Duschwannenablauf", "ek": "42.75", "ek_gesamt": "42.75"}]}],
         "punkte": [
          {"id": "f3", "art": "frage", "ort": "Badezimmer / Dusche",
           "text": "Duscharmatur: Unterputz-Thermostat oder Aufputz?",
           "positionen": [{"id": "p22", "nr": "5.4.002", "art_nr": "D-CODE-AB", "name": "Duravit Duschwannenablauf …",
                           "menge": "1", "ek": "42.75", "gesamt": "42.75", "pauschale": false, "bemerkung": "", "ort": "Dusche"}],
           "optionen": ["UP-Thermostat hansgrohe ShowerSelect (EK 312 €)", "AP-Thermostat Grohtherm 800 (EK 145 €)"],
           "frei": true},
          {"id": "m:p21", "art": "alternative", "ort": "Dusche", "text": "Alternative zu LV: Duravit D-Code 1200x900",
           "positionen": [], "optionen": [], "frei": true},
          {"id": "h:Heizpatrone: Elektroanschluss durch Elektriker.", "art": "hinweis", "text": "…", "positionen": [],
           "optionen": [], "frei": false}],
         "beantwortet": [{"id": "f2", "text": "Ablauf und Füße …?", "antwort": "Füße tauschen", "positionen": ["p22"]}]}
    """.trimIndent()

    @Test
    fun `liest Entscheidungen aus API md`() {
        val angebot = json.decodeFromString<Angebot>(punkte)
        assertEquals(3, angebot.punkte.size)
        val frage = angebot.punkte.first()
        assertEquals("frage", frage.art)
        assertEquals("42.75", frage.positionen.single().gesamt)
        assertEquals(2, frage.optionen.size)
        assertTrue(frage.frei)
        assertTrue(PUNKT_ARTEN.containsAll(angebot.punkte.map { it.art }))
        assertEquals("Füße tauschen", angebot.beantwortet.single().antwort)
        // Aelterer Server ohne die neuen Felder
        assertTrue(json.decodeFromString<Angebot>(beispiel).punkte.isEmpty())
    }

    @Test
    fun `Antwort geht mit punkt und antwort raus`() {
        assertEquals(
            """{"punkt":"m:p21","antwort":"PASST"}""",
            json.encodeToString(AngebotAntwort("m:p21", ANTWORT_PASST)),
        )
    }

    @Test
    fun `Entscheidungen oben, alte Listen nicht mehr`() {
        val eintraege = eintraegeAus(json.decodeFromString<Angebot>(punkte))
        assertTrue(eintraege[0] is Eintrag.Summen)
        assertTrue(eintraege[1] is Eintrag.Entscheidungen)
        assertEquals(listOf("f3", "m:p21", "h:Heizpatrone: Elektroanschluss durch Elektriker."),
            eintraege.filterIsInstance<Eintrag.Punkt>().map { it.punkt.id })
        assertTrue(eintraege.any { it is Eintrag.Beantwortet })
        // Hinweise und Fragen aus den alten Feldern erscheinen nicht mehr.
        val alt = eintraegeAus(json.decodeFromString<Angebot>(beispiel))
        assertTrue(alt.none { it is Eintrag.Entscheidungen || it is Eintrag.Punkt })
        assertEquals(alt.size, alt.map { it.schluessel }.toSet().size)
    }

    @Test
    fun `ab 6 Punkten erst 5 und weitere anzeigen`() {
        val basis = json.decodeFromString<Angebot>(punkte)
        fun mit(n: Int) = basis.copy(punkte = (1..n).map { AngebotPunkt(id = "p$it", art = "frage") })

        assertEquals(5, eintraegeAus(mit(5)).filterIsInstance<Eintrag.Punkt>().size)
        assertTrue(eintraegeAus(mit(5)).none { it is Eintrag.MehrPunkte })

        val sechs = eintraegeAus(mit(6))
        assertEquals(5, sechs.filterIsInstance<Eintrag.Punkt>().size)
        assertEquals(1, sechs.filterIsInstance<Eintrag.MehrPunkte>().single().anzahl)

        val alle = eintraegeAus(mit(6), allePunkte = true)
        assertEquals(6, alle.filterIsInstance<Eintrag.Punkt>().size)
        assertEquals(alle.size, alle.map { it.schluessel }.toSet().size)
    }
    @Test
    fun `Betraege und Mengen deutsch ohne Double`() {
        assertEquals("9.195,69 €", betrag("9195.69"))
        assertEquals("0,10 €", betrag("0.1"))
        assertEquals("114,50 €", betrag("114.5"))
        assertEquals("2,5", menge("2.50"))
        assertEquals("1", menge("1"))
        assertEquals("kaputt", betrag("kaputt"))
    }

    @Test
    fun `Zeit ohne Offset bleibt, mit Offset in die Geraetezone`() {
        val berlin = ZoneId.of("Europe/Berlin")
        assertEquals("09.10.2026 13:30", zeitpunkt("2026-10-09T13:30:12", berlin))
        assertEquals("09.10.2026 13:30", zeitpunkt("2026-10-09T11:30:12+00:00", berlin))
        assertNull(zeitpunkt(null, berlin))
    }

    @Test
    fun `Dateiname aus Content-Disposition`() {
        assertEquals("PET44.ugl", AssistentRepository.dateinameAusDisposition("attachment; filename=\"PET44.ugl\""))
        assertEquals("a.ugl", AssistentRepository.dateinameAusDisposition("attachment; filename=a.ugl"))
        assertEquals(
            "Petersburger Str 44.ugl",
            AssistentRepository.dateinameAusDisposition("attachment; filename=\"x.ugl\"; filename*=UTF-8''Petersburger%20Str%2044.ugl"),
        )
        assertEquals("boese.ugl", AssistentRepository.dateinameAusDisposition("attachment; filename=\"../../boese.ugl\""))
        assertNull(AssistentRepository.dateinameAusDisposition(null))
        assertNull(AssistentRepository.dateinameAusDisposition("attachment"))
    }
}
