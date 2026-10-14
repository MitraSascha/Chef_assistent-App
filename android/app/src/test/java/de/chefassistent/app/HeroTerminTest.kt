package de.chefassistent.app

import de.chefassistent.app.data.HeroTerminErgebnis
import de.chefassistent.app.data.HeroTerminNeu
import de.chefassistent.app.data.HeroTerminVerschieben
import de.chefassistent.app.data.KalenderAntwort
import de.chefassistent.app.data.KalenderTermin
import de.chefassistent.app.data.Netzwerk
import de.chefassistent.app.data.PERSON_ICH
import de.chefassistent.app.data.Personen
import de.chefassistent.app.data.TerminKategorie
import de.chefassistent.app.ui.kalender.FormularLuecke
import de.chefassistent.app.ui.kalender.HeroFormular
import de.chefassistent.app.ui.kalender.zeitspanne
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** HERO-Termine anlegen und verschieben (`API.md` 5b, seit v1.18.0). */
class HeroTerminTest {

    private val json = Netzwerk.json
    private val berlin = ZoneId.of("Europe/Berlin")

    private val heroTermin = KalenderTermin(
        id = "hero:616233015", quelle = "hero", titel = "Badsanierung",
        beginn = "2026-10-20T08:00+02:00", ende = "2026-10-20T09:00+02:00", ganztag = false,
        kategorie = "Umsetzung", nur_lesen = false,
    )

    private fun formular() = HeroFormular.neu(LocalDate.of(2026, 10, 20), null, LocalDateTime.of(2026, 10, 14, 9, 0))

    @Test
    fun `liest hero_schreiben und verschiebbare HERO-Termine aus API md`() {
        val antwort = json.decodeFromString<KalenderAntwort>(
            """{"von": "2026-10-12", "bis": "2026-10-18", "personen": ["ich"], "spiegel": true, "hero_schreiben": true,
               "termine": [{"id": "hero:616233015", "quelle": "hero", "titel": "Badsanierung", "beginn": "2026-10-12T14:30+02:00",
                 "ende": "2026-10-12T15:30+02:00", "ganztag": false, "kategorie": "Umsetzung", "personen": [],
                 "nur_lesen": false},
                {"id": "eigen:12", "quelle": "eigen", "titel": "Zahnarzt", "beginn": "2026-10-13T16:00+02:00",
                 "ende": "2026-10-13T17:00+02:00", "ganztag": false, "kategorie": "Eigener Termin", "nur_lesen": false}]}""",
        )
        assertTrue(antwort.hero_schreiben)
        val (hero, eigen) = antwort.termine
        assertEquals(616233015L, hero.heroId)
        assertTrue(hero.verschiebbar)
        // Eigene Termine haben auch `nur_lesen: false`, sind aber nicht verschiebbar.
        assertNull(eigen.heroId)
        assertFalse(eigen.verschiebbar)
    }

    @Test
    fun `alter Server ohne hero_schreiben heisst nur lesen`() {
        val antwort = json.decodeFromString<KalenderAntwort>("""{"von": "2026-10-12", "bis": "2026-10-18", "spiegel": true}""")
        assertFalse(antwort.hero_schreiben)
        assertFalse(heroTermin.copy(nur_lesen = true).verschiebbar)
    }

    @Test
    fun `liest Kategorien und Pruefergebnis`() {
        val kategorien = json.decodeFromString<List<TerminKategorie>>(
            """[{"id": 226842, "name": "Umsetzung", "farbe": "#009688"}, {"id": 359681, "name": "Kundendienst"}]""",
        )
        assertEquals(359681L, kategorien[1].id)
        assertNull(kategorien[1].farbe)

        val geprueft = json.decodeFromString<HeroTerminErgebnis>(
            """{"status": "geprueft", "beschreibung": "Termin für Julian am 20.10. 08:00–09:00", "neu": 1,
               "ueberschneidungen": [{"hero_id": 1, "titel": "Wartung", "beginn": "2026-10-20T08:30",
                 "ende": "2026-10-20T09:30", "wer": "Julian"}], "hinweis": null}""",
        )
        assertEquals(HeroTerminErgebnis.STATUS_GEPRUEFT, geprueft.status)
        assertNull(geprueft.termin)
        assertEquals("Julian", geprueft.ueberschneidungen.single().wer)
        assertEquals("20.10. 08:30–09:30", zeitspanne("2026-10-20T08:30", "2026-10-20T09:30", berlin))

        val angelegt = json.decodeFromString<HeroTerminErgebnis>(
            """{"status": "angelegt", "termin": {"id": "hero:9", "quelle": "hero", "titel": "Test", "beginn": "2026-10-20T08:00+02:00",
               "ende": "2026-10-20T09:00+02:00", "ganztag": false, "kategorie": "Büro", "nur_lesen": false}, "ueberschneidungen": []}""",
        )
        assertEquals("hero:9", angelegt.termin?.id)
    }

    @Test
    fun `Anfrage in Ortszeit ohne Zone, leere Felder fallen weg`() {
        val f = formular().copy(personen = listOf("hero:106018"), titel = "  Wartung  ", kategorieId = 359681)
        val text = json.encodeToString(HeroTerminNeu.serializer(), f.anfrageNeu())
        val obj = Json.parseToJsonElement(text).jsonObject
        assertEquals("2026-10-20T08:00", obj["beginn"]!!.jsonPrimitive.content)
        assertEquals("2026-10-20T09:00", obj["ende"]!!.jsonPrimitive.content)
        assertEquals("Wartung", obj["titel"]!!.jsonPrimitive.content)
        assertFalse(obj.containsKey("projekt"))
        assertFalse(obj.containsKey("notiz"))
    }

    @Test
    fun `ganztaegig nur mit Datum und hoechstens 14 Tage`() {
        val f = formular().copy(personen = listOf(PERSON_ICH), titel = "Urlaub", kategorieId = 1).mitGanztag(true)
            .mitBisDatum(LocalDate.of(2026, 10, 24))
        val anfrage = f.anfrageNeu()
        assertEquals("2026-10-20", anfrage.beginn)
        assertEquals("2026-10-24", anfrage.ende)
        assertTrue(anfrage.ganztag)
        assertNull(f.luecke)
        // 20.10. bis 2.11. sind einschliesslich genau 14 Tage, einer mehr ist zu viel.
        assertNull(f.mitBisDatum(LocalDate.of(2026, 11, 2)).luecke)
        assertEquals(FormularLuecke.ZU_LANG, f.mitBisDatum(LocalDate.of(2026, 11, 3)).luecke)
    }

    @Test
    fun `ohne Kategorie Titel oder Person laesst sich nicht pruefen`() {
        val voll = formular().copy(personen = listOf(PERSON_ICH), titel = "Test", kategorieId = 1)
        assertNull(voll.luecke)
        assertEquals(FormularLuecke.KATEGORIE, voll.copy(kategorieId = null).luecke)
        assertEquals(FormularLuecke.TITEL, voll.copy(titel = " ").luecke)
        assertEquals(FormularLuecke.PERSONEN, voll.copy(personen = emptyList()).luecke)
        assertEquals(FormularLuecke.ENDE_VOR_BEGINN, voll.mitBis(LocalTime.of(7, 0)).luecke)
    }

    @Test
    fun `jede Aenderung verlangt eine neue Pruefung`() {
        val geprueft = formular().copy(
            geprueft = HeroTerminErgebnis(status = HeroTerminErgebnis.STATUS_GEPRUEFT),
            fehler = "steht schon im HERO-Kalender",
        )
        val geaendert = geprueft.aendern { copy(titel = "Neu") }
        assertNull(geaendert.geprueft)
        assertNull(geaendert.fehler)
    }

    @Test
    fun `Verschieben auf 10 Uhr behaelt die Dauer`() {
        val f = requireNotNull(HeroFormular.verschieben(heroTermin, berlin)).mitVon(LocalTime.of(10, 0))
        val anfrage = f.anfrageVerschieben()
        assertEquals(HeroTerminVerschieben(beginn = "2026-10-20T10:00", ende = "2026-10-20T11:00"), anfrage)
        // Beim Verschieben zaehlen Personen, Titel und Kategorie nicht.
        assertNull(f.luecke)
    }

    @Test
    fun `Datum verschieben nimmt mehrtaegige Termine mit`() {
        val baustelle = heroTermin.copy(beginn = "2026-10-05T07:00+02:00", ende = "2026-10-07T16:00+02:00")
        val f = requireNotNull(HeroFormular.verschieben(baustelle, berlin))
        assertTrue(f.mehrtaegig)
        val neu = f.mitDatum(LocalDate.of(2026, 10, 12))
        assertEquals(LocalDate.of(2026, 10, 14), neu.bisDatum)
        assertEquals("2026-10-14T16:00", neu.anfrageVerschieben().ende)
    }

    @Test
    fun `ganztaegige HERO-Termine werden mit Datum verschoben`() {
        val urlaub = heroTermin.copy(beginn = "2026-10-20", ende = "2026-10-22", ganztag = true)
        val f = requireNotNull(HeroFormular.verschieben(urlaub, berlin)).mitDatum(LocalDate.of(2026, 10, 27))
        assertEquals(HeroTerminVerschieben(beginn = "2026-10-27", ende = "2026-10-29", ganztag = true), f.anfrageVerschieben())
    }

    @Test
    fun `neu ist mit ich vorbelegt, wenn man in HERO zugeordnet ist`() {
        val tag = LocalDate.of(2026, 10, 20)
        assertEquals(listOf(PERSON_ICH), HeroFormular.neu(tag, Personen(ich = "hero:94314")).personen)
        assertTrue(HeroFormular.neu(tag, Personen(ich = null)).personen.isEmpty())
        assertNull(HeroFormular.neu(tag, null).kategorieId)
    }

    @Test
    fun `Status und Pflichtfelder stimmen mit dem Vertrag ueberein`() {
        val schemas = Json.parseToJsonElement(vertragsDatei().readText()).jsonObject["components"]!!
            .jsonObject["schemas"]!!.jsonObject
        fun pflicht(name: String) = schemas[name]!!.jsonObject["required"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()

        val status = schemas["HeroTerminErgebnis"]!!.jsonObject["properties"]!!.jsonObject["status"]!!
            .jsonObject["enum"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
        assertEquals(
            setOf(HeroTerminErgebnis.STATUS_GEPRUEFT, HeroTerminErgebnis.STATUS_ANGELEGT, HeroTerminErgebnis.STATUS_VERSCHOBEN),
            status,
        )
        // Was die App immer schickt, muss alles abdecken, was der Server verlangt.
        val neu = Json.parseToJsonElement(
            json.encodeToString(HeroTerminNeu.serializer(), HeroTerminNeu(listOf(PERSON_ICH), "T", "2026-10-20T08:00", kategorie_id = 1)),
        ).jsonObject.keys
        assertTrue(neu.containsAll(pflicht("HeroTerminNeu")))
        val verschieben = Json.parseToJsonElement(
            json.encodeToString(HeroTerminVerschieben.serializer(), HeroTerminVerschieben("2026-10-20T08:00")),
        ).jsonObject.keys
        assertTrue(verschieben.containsAll(pflicht("HeroTerminVerschieben")))

        val titel = schemas["HeroTerminNeu"]!!.jsonObject["properties"]!!.jsonObject["titel"]!!.jsonObject
        assertEquals(HeroTerminNeu.TITEL_MAX, titel["maxLength"]!!.jsonPrimitive.content.toInt())
        assertNotNull(schemas["KalenderAntwort"]!!.jsonObject["properties"]!!.jsonObject["hero_schreiben"])
    }

    /** `openapi.json` liegt im Projektstamm, die Tests laufen in `android/app`. */
    private fun vertragsDatei(): File {
        var ordner: File? = File("").absoluteFile
        while (ordner != null) {
            val treffer = File(ordner, "openapi.json")
            if (treffer.isFile) return treffer
            ordner = ordner.parentFile
        }
        throw AssertionError("openapi.json nicht gefunden - der Vertrag gehoert in den Projektstamm.")
    }
}
