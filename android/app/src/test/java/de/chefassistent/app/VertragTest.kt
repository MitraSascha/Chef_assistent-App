package de.chefassistent.app

import de.chefassistent.app.data.ABSCHREIBEN_WERTE
import de.chefassistent.app.data.AUFGABE_STATUS
import de.chefassistent.app.data.AUFTRAG_ARTEN
import de.chefassistent.app.data.AUFTRAG_STATUS
import de.chefassistent.app.data.AufgabeAenderung
import de.chefassistent.app.data.AufgabenUebersicht
import de.chefassistent.app.data.Auftrag
import de.chefassistent.app.data.ChatAnfrage
import de.chefassistent.app.data.ChatAntwort
import de.chefassistent.app.data.ChatStatus
import de.chefassistent.app.data.ANFRAGE_ID_MUSTER
import de.chefassistent.app.data.AssistentRepository
import de.chefassistent.app.data.ERLAUBTE_STIMMEN
import de.chefassistent.app.data.FotoAntwort
import de.chefassistent.app.data.GespraechKurz
import de.chefassistent.app.data.Notizen
import de.chefassistent.app.data.Ordner
import de.chefassistent.app.data.ORDNER_SORTIERUNGEN
import de.chefassistent.app.data.SuchErgebnis
import de.chefassistent.app.data.Termine
import de.chefassistent.app.data.VideoAntwort
import de.chefassistent.app.data.DokumentAntwort
import de.chefassistent.app.data.VorlesenAnfrage
import de.chefassistent.app.ui.markdownEntfernen
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Prueft die Zusagen aus `API.md` unter "Verbindlichkeit": neue Felder duerfen
 * dazukommen, weggelassene Listen duerfen fehlen. Wenn diese Tests brechen,
 * stuerzt die App beim naechsten Server-Update ab.
 */
class VertragTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun `unbekannte Felder brechen das Lesen nicht ab`() {
        val roh = """
            {
              "gespraech_id": "c8719cc52c8f4d0a",
              "antwort": "Alles klar.",
              "brandneues_feld": {"kommt": "spaeter"},
              "noch_eins": [1, 2, 3]
            }
        """.trimIndent()

        val antwort = json.decodeFromString<ChatAntwort>(roh)

        assertEquals("c8719cc52c8f4d0a", antwort.gespraech_id)
        assertEquals("Alles klar.", antwort.antwort)
    }

    @Test
    fun `liest die Antwort auf ein Foto`() {
        // Beispiel aus API.md, Abschnitt 4.
        val antwort = json.decodeFromString<FotoAntwort>(
            """
            {"foto": "Notizen/Sascha/2026-09-24 Objektbegehung Hotel/2026-09-24 13-24-05.jpg",
             "notiz": "Notizen/Sascha/2026-09-24 Objektbegehung Hotel.md", "notiz_neu": false}
            """.trimIndent()
        )

        assertEquals("Notizen/Sascha/2026-09-24 Objektbegehung Hotel.md", antwort.notiz)
        assertTrue(antwort.foto.endsWith(".jpg"))
        assertTrue(!antwort.notiz_neu)
    }

    @Test
    fun `fehlende Listen werden zu leeren Listen`() {
        val antwort = json.decodeFromString<ChatAntwort>(
            """{"gespraech_id": "a", "antwort": "b"}"""
        )

        assertTrue(antwort.dateien.isEmpty())
        assertTrue(antwort.bestaetigungen.isEmpty())
        assertTrue(antwort.ausgefuehrt.isEmpty())
        assertTrue(antwort.quellen.isEmpty())
    }

    @Test
    fun `liest eine vollstaendige Antwort mit Rueckfrage`() {
        val roh = """
            {
              "gespraech_id": "c87",
              "antwort": "Im Projekt **HEZ-2003 Schulz** …",
              "dateien": ["Projekte/HEZ/Checkliste.docx"],
              "bestaetigungen": [
                {"id": "c22204aa60b9", "werkzeug": "verschieben", "beschreibung": "Datei verschieben"}
              ],
              "ausgefuehrt": [],
              "quellen": [{"titel": "BMWE", "url": "https://example.invalid"}]
            }
        """.trimIndent()

        val antwort = json.decodeFromString<ChatAntwort>(roh)

        assertEquals(1, antwort.dateien.size)
        assertEquals("c22204aa60b9", antwort.bestaetigungen.single().id)
        assertEquals("verschieben", antwort.bestaetigungen.single().werkzeug)
        assertEquals("BMWE", antwort.quellen.single().titel)
    }

    @Test
    fun `titel eines Gespraechs darf null sein`() {
        val kurz = json.decodeFromString<GespraechKurz>(
            """{"id": "a", "titel": null, "erstellt": "2026-09-23T09:00:00+02:00", "geaendert": "2026-09-23T09:05:00+02:00"}"""
        )

        assertNull(kurz.titel)
    }

    @Test
    fun `ganztaegige Termine haben kein Ende`() {
        val termine = json.decodeFromString<Termine>(
            """
            {
              "von": "2026-09-24", "bis": "2026-09-24",
              "termine": [{
                "id": 1, "titel": "Betriebsausflug", "person": "sascha", "wochentag": "Do",
                "beginn": "2026-09-24T00:00:00+02:00", "ende": null, "ganztag": true
              }]
            }
            """.trimIndent()
        )

        val termin = termine.termine.single()
        assertTrue(termin.ganztag)
        assertNull(termin.ende)
        assertNull(termin.ort)
    }
}

/**
 * Die Stimmenwahl aus der Vertragserweiterung vom 2026-09-23.
 *
 * Entscheidend ist, dass eine nicht gewaehlte Stimme **gar nicht** im JSON
 * auftaucht - `stimme: null` waere laut Schema zwar erlaubt, aber "weglassen"
 * ist das, was der Server als Standard behandelt.
 */
class VorlesenTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `ohne Stimme faellt das Feld aus dem JSON`() {
        val roh = json.encodeToString(VorlesenAnfrage(text = "Guten Morgen"))
        assertEquals("""{"text":"Guten Morgen"}""", roh)
    }

    @Test
    fun `gewaehlte Stimme wird mitgeschickt`() {
        val roh = json.encodeToString(VorlesenAnfrage(text = "Guten Morgen", stimme = "nova"))
        assertEquals("""{"text":"Guten Morgen","stimme":"nova"}""", roh)
    }

    /**
     * Liest das `enum` direkt aus `openapi.json` im Projektstamm.
     *
     * Bewusst nicht gegen eine abgetippte Liste: erweitert der Server die
     * Stimmen, faellt der Test auf - und niemand bietet dem Nutzer eine Stimme
     * an, die es nicht gibt (422), oder unterschlaegt eine neue.
     */
    @Test
    fun `liest die Antwort auf ein Video`() {
        // Beispiel aus API.md, Abschnitt Videos.
        val antwort = json.decodeFromString<VideoAntwort>(
            """
            {"video": "Notizen/Sascha/2026-09-24 Objektbegehung Hotel/2026-09-24 13-30-12.mp4",
             "notiz": "Notizen/Sascha/2026-09-24 Objektbegehung Hotel.md", "notiz_neu": false,
             "dauer_s": 292.4, "abschrift": "laeuft"}
            """.trimIndent()
        )

        assertEquals(292.4, antwort.dauer_s, 0.001)
        assertTrue(antwort.schreibtAb)
    }

    @Test
    fun `liest die Antwort auf ein Dokument`() {
        // Beispiel aus API.md, Abschnitt Dokumente (v1.12.0).
        val antwort = json.decodeFromString<DokumentAntwort>(
            """
            {"dokument": "Notizen/Sascha/2026-10-12 Angebot Petersburger 44/LV Bad.pdf",
             "notiz": "Notizen/Sascha/2026-10-12 Angebot Petersburger 44.md", "notiz_neu": false,
             "lesbar": true, "hinweis": ""}
            """.trimIndent()
        )

        assertEquals("Notizen/Sascha/2026-10-12 Angebot Petersburger 44/LV Bad.pdf", antwort.dokument)
        assertTrue(antwort.lesbar)
    }

    @Test
    fun `Dokument ohne hinweis ist kein Fehler`() {
        // hinweis ist im Vertrag nicht Pflicht - fehlt es, bleibt es leer.
        val antwort = json.decodeFromString<DokumentAntwort>(
            """{"dokument": "a/Scan.pdf", "notiz": "a.md", "notiz_neu": true, "lesbar": false, "neu": 1}"""
        )

        assertEquals(false, antwort.lesbar)
        assertEquals("", antwort.hinweis)
    }

    @Test
    fun `liest Notizen und Ordner fuer die Zielauswahl`() {
        // Beispiel aus API.md, "Ziel auswaehlen".
        val ziele = json.decodeFromString<Notizen>(
            """
            {"wurzel": "Notizen/Sascha",
             "ordner": [{"name": "Begehung Simon-Dach-Straße 46", "pfad": "Notizen/Sascha/Begehung Simon-Dach-Straße 46",
                         "notizen": 1, "geaendert": "24.09.2026 13:40"}],
             "notizen": [{"titel": "2026-09-24 Begehung Simon-Dach-Straße 46",
                          "pfad": "Notizen/Sascha/Begehung Simon-Dach-Straße 46/2026-09-24 Begehung Simon-Dach-Straße 46.md",
                          "ordner": "Begehung Simon-Dach-Straße 46", "geaendert": "24.09.2026 13:40"}]}
            """.trimIndent()
        )

        assertEquals("Notizen/Sascha/Begehung Simon-Dach-Straße 46", ziele.ordner.single().pfad)
        assertTrue(ziele.notizen.single().pfad.endsWith(".md"))
    }

    @Test
    fun `liest einen Ordner der Ablage`() {
        // Beispiel aus API.md, "Ablage durchblaettern".
        val ordner = json.decodeFromString<Ordner>(
            """
            {"pfad": "Notizen/Sascha", "eltern": "Notizen", "abgeschnitten": false,
             "eintraege": [
               {"name": "2026-09-24 Objektbegehung Hotel", "pfad": "Notizen/Sascha/2026-09-24 Objektbegehung Hotel",
                "typ": "ordner", "groesse": null, "geaendert": "24.09.2026 13:31"},
               {"name": "2026-09-24 Objektbegehung Hotel.md", "pfad": "Notizen/Sascha/2026-09-24 Objektbegehung Hotel.md",
                "typ": "datei", "groesse": "2.1 KB", "geaendert": "24.09.2026 13:31"}]}
            """.trimIndent()
        )

        assertTrue(ordner.eintraege[0].istOrdner)
        assertEquals("2.1 KB", ordner.eintraege[1].groesse)
        // Ganz oben: eltern fehlt oder ist null.
        assertNull(json.decodeFromString<Ordner>("""{"pfad": "", "eintraege": []}""").eltern)
    }

    @Test
    fun `ohne Bilder faellt das Feld aus der Chat-Anfrage`() {
        // Wichtig fuer Server vor v1.5.0 - und fuer jede normale Textnachricht.
        val roh = json.encodeToString(ChatAnfrage(text = "Hallo", gespraech_id = "c87"))
        assertEquals("""{"text":"Hallo","gespraech_id":"c87"}""", roh)
    }

    @Test
    fun `Bilder gehen als Liste von Pfaden raus`() {
        // Beispiel aus API.md, Abschnitt 4a.
        val roh = json.encodeToString(
            ChatAnfrage(
                text = "Was heißt F.28?",
                gespraech_id = "c8719cc52c8f4d0a",
                bilder = listOf("Notizen/Sascha/2026-09-26 Kundendienst Müller/2026-09-26 10-14-03.jpg"),
            )
        )
        assertTrue(roh.contains(""""bilder":["Notizen/Sascha/2026-09-26 Kundendienst Müller/2026-09-26 10-14-03.jpg"]"""))
    }

    @Test
    fun `Obergrenze fuer Bilder stimmt mit dem Vertrag ueberein`() {
        val schema = Json.parseToJsonElement(vertragsDatei().readText()).jsonObject
        val feld = schema["components"]!!.jsonObject["schemas"]!!.jsonObject["ChatAnfrage"]!!
            .jsonObject["properties"]!!.jsonObject["bilder"]!!.jsonObject
        assertEquals(feld["maxItems"]!!.jsonPrimitive.content.toInt(), ChatAnfrage.MAX_BILDER)
    }

    /** Wie bei den Stimmen: die Auswahl im Video-Dialog kommt aus dem Vertrag. */
    @Test
    fun `Abschreib-Optionen stimmen mit dem Vertrag ueberein`() {
        val schema = Json.parseToJsonElement(vertragsDatei().readText()).jsonObject
        val feld = schema["components"]!!.jsonObject["schemas"]!!.jsonObject["Body_video_v1_videos_post"]!!
            .jsonObject["properties"]!!.jsonObject["abschreiben"]!!.jsonObject
        val ausVertrag = feld["enum"]!!.jsonArray.map { it.jsonPrimitive.content }

        assertEquals(ausVertrag.toSet(), ABSCHREIBEN_WERTE.toSet())
        // Die Vorauswahl im Dialog ist der erste Wert - er muss die Servervorgabe sein.
        assertEquals(feld["default"]!!.jsonPrimitive.content, ABSCHREIBEN_WERTE.first())
    }

    @Test
    fun `angebotene Stimmen stimmen mit dem Vertrag ueberein`() {
        val vertrag = vertragsDatei()
        val schema = Json.parseToJsonElement(vertrag.readText()).jsonObject
        val ausVertrag = schema["components"]!!.jsonObject["schemas"]!!.jsonObject["VorlesenAnfrage"]!!
            .jsonObject["properties"]!!.jsonObject["stimme"]!!
            .jsonObject["anyOf"]!!.jsonArray
            .first { it.jsonObject.containsKey("enum") }
            .jsonObject["enum"]!!.jsonArray
            .map { it.jsonPrimitive.content }

        assertEquals(ausVertrag, ERLAUBTE_STIMMEN)
    }

    @Test
    fun `liest einen laufenden Auftrag`() {
        // Beispiel aus API.md, Abschnitt 4b.
        val liste = json.decodeFromString<List<Auftrag>>(
            """
            [{"id": "3f9c0a1b22de", "art": "versicherungsbericht", "titel": "SAN-653 Wasserschaden Hilbert",
              "status": "laeuft", "schritt": "Unterlagen auswerten (7/12)", "fortschritt": 31,
              "erstellt": "2026-09-26T13:02:11+00:00", "geaendert": "2026-09-26T13:04:40+00:00",
              "dateien": [], "zusammenfassung": null, "offene_punkte": [], "fehler": null,
              "gespraech_id": "c8719cc52c8f4d0a"}]
            """.trimIndent()
        )
        val auftrag = liste.single()
        assertTrue(auftrag.istAktiv)
        assertTrue(auftrag.kannAbbrechen)
        assertEquals(31, auftrag.fortschritt)
        assertNull(auftrag.zusammenfassung)
    }

    @Test
    fun `Auftrag ohne optionale Felder`() {
        // Nur die Pflichtfelder aus dem Vertrag - der Rest darf fehlen.
        val auftrag = json.decodeFromString<Auftrag>(
            """{"id": "a", "art": "versicherungsbericht", "titel": "T", "status": "fehler",
               "schritt": "", "fortschritt": 0, "erstellt": "x", "geaendert": "x"}"""
        )
        assertTrue(auftrag.kannWiederholen)
        assertTrue(auftrag.dateien.isEmpty())
        assertTrue(auftrag.offene_punkte.isEmpty())
    }

    @Test
    fun `Auftragsstatus stimmt mit dem Vertrag ueberein`() {
        val schema = Json.parseToJsonElement(vertragsDatei().readText()).jsonObject
        val feld = schema["components"]!!.jsonObject["schemas"]!!.jsonObject["Auftrag"]!!
            .jsonObject["properties"]!!.jsonObject["status"]!!.jsonObject
        assertEquals(feld["enum"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet(), AUFTRAG_STATUS.toSet())
    }

    @Test
    fun `Auftragsarten stimmen mit dem Vertrag ueberein`() {
        val schema = Json.parseToJsonElement(vertragsDatei().readText()).jsonObject
        val feld = schema["components"]!!.jsonObject["schemas"]!!.jsonObject["Auftrag"]!!
            .jsonObject["properties"]!!.jsonObject["art"]!!.jsonObject
        assertEquals(feld["enum"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet(), AUFTRAG_ARTEN.toSet())
    }

    @Test
    fun `unbekannte Auftragsart bringt die App nicht um`() {
        val auftrag = json.decodeFromString<Auftrag>(
            """{"id": "a", "art": "gibt_es_noch_nicht", "titel": "T", "status": "laeuft",
               "schritt": "", "fortschritt": 10, "erstellt": "x", "geaendert": "x"}"""
        )
        assertEquals("gibt_es_noch_nicht", auftrag.art)
    }

    @Test
    fun `liest die Aufgabenuebersicht`() {
        // Beispiel aus API.md, Abschnitt 4c, gekuerzt; an_andere ohne optionale Felder.
        val u = json.decodeFromString<AufgabenUebersicht>(
            """
            {"person": "Sascha", "stand": "2026-09-28", "offen": 3,
             "ueberfaellig": [{"id": "08959e5d50", "text": "Pico klären: Trocknungsfirma schon drin?", "status": "offen",
               "bis": "2026-09-26", "fuer": null, "von": "Sascha", "notiz": "Notizen/Sascha/2026-09-24 SAN-1191.md",
               "seit": "2026-09-24", "erledigt_am": null, "erledigt_von": null, "nachfragen": true}],
             "diese_woche": [], "spaeter": [], "ohne_datum": [],
             "an_andere": [{"id": "5b0e", "text": "Angebot ANG-797 freigeben", "status": "offen", "fuer": "Patrick",
               "von": "Sascha", "notiz": "Notizen/Sascha/x.md", "nachfragen": false}],
             "heute_erledigt": [], "neue_gruppe": []}
            """.trimIndent()
        )
        assertEquals(3, u.offen)
        assertTrue(u.ueberfaellig.single().nachfragen)
        assertEquals("Patrick", u.an_andere.single().fuer)
        // Reihenfolge aus API.md: ueberfaellig zuerst, heute_erledigt zuletzt.
        assertEquals(AufgabenUebersicht.GRUPPE_UEBERFAELLIG, u.gruppen.first().first)
        assertEquals(AufgabenUebersicht.GRUPPE_HEUTE_ERLEDIGT, u.gruppen.last().first)
    }

    @Test
    fun `Aufgabenaenderung schickt nur gesetzte Felder`() {
        assertEquals("""{"status":"erledigt"}""", json.encodeToString(AufgabeAenderung.serializer(), AufgabeAenderung(status = "erledigt")))
        assertEquals("""{"noch_aktuell":true}""", json.encodeToString(AufgabeAenderung.serializer(), AufgabeAenderung(noch_aktuell = true)))
        // "" entfernt die Frist und darf nicht wegfallen.
        assertEquals("""{"bis":""}""", json.encodeToString(AufgabeAenderung.serializer(), AufgabeAenderung(bis = "")))
    }

    @Test
    fun `Aufgabenstatus stimmt mit dem Vertrag ueberein`() {
        val schema = Json.parseToJsonElement(vertragsDatei().readText()).jsonObject
        val feld = schema["components"]!!.jsonObject["schemas"]!!.jsonObject["Aufgabe"]!!
            .jsonObject["properties"]!!.jsonObject["status"]!!.jsonObject
        assertEquals(feld["enum"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet(), AUFGABE_STATUS.toSet())
    }

    @Test
    fun `liest Links aus der Chat-Antwort`() {
        // Beispiel aus API.md, Abschnitt 1 (v1.13.0).
        val antwort = json.decodeFromString<ChatAntwort>(
            """
            {"gespraech_id": "g1", "antwort": "Hier:",
             "dateien": ["HERO-Archiv/SAN-1166 Herr Mark Boese/Rechnungen/Rechnung-RE-1410-Boese.pdf"],
             "links": [{"pfad": "HERO-Archiv/SAN-1166 Herr Mark Boese/Rechnungen/Rechnung-RE-1410-Boese.pdf",
                        "name": "Rechnung-RE-1410-Boese.pdf", "typ": "datei", "groesse": "84.2 KB",
                        "ordner": "HERO-Archiv/SAN-1166 Herr Mark Boese/Rechnungen",
                        "projekt": "SAN-1166 · Herr Mark Boese · Grimmstraße 7, 10967 Berlin"},
                       {"pfad": "HERO-Archiv/SAN-1166 Herr Mark Boese", "name": "SAN-1166 Herr Mark Boese",
                        "typ": "ordner", "ordner": "HERO-Archiv", "groesse": null, "projekt": null}]}
            """.trimIndent()
        )

        assertEquals(2, antwort.links.size)
        assertEquals(false, antwort.links[0].istOrdner)
        assertEquals("84.2 KB", antwort.links[0].groesse)
        assertTrue(antwort.links[1].istOrdner)
        assertNull(antwort.links[1].projekt)
    }

    @Test
    fun `Antwort ohne Links bleibt bei den Datei-Chips`() {
        val antwort = json.decodeFromString<ChatAntwort>("""{"gespraech_id": "g1", "antwort": "x", "dateien": ["a.md"]}""")
        assertTrue(antwort.links.isEmpty())
        assertEquals(listOf("a.md"), antwort.dateien)
    }

    @Test
    fun `liest ein Suchergebnis`() {
        // Beispiel aus API.md, Abschnitt 6a.
        val ergebnis = json.decodeFromString<SuchErgebnis>(
            """
            {"anzahl": 3, "abgeschnitten": false, "stand": "13.10.2026 11:42:07",
             "treffer": [
               {"name": "Rechnung-RE-1410-Boese-07-08-2026.pdf",
                "pfad": "HERO-Archiv/SAN-1166 Herr Mark Boese/Rechnungen/Rechnung-RE-1410-Boese-07-08-2026.pdf",
                "typ": "datei", "ordner": "HERO-Archiv/SAN-1166 Herr Mark Boese/Rechnungen", "groesse": "84.2 KB",
                "geaendert": "07.08.2026 10:12",
                "projekt": {"nr": "SAN-1166", "kunde": "Herr Mark Boese", "bauvorhaben": "Grimmstraße 7, 10967 Berlin",
                            "titel": "Strang", "status": "Abgeschlossen", "pfad": "HERO-Archiv/SAN-1166 Herr Mark Boese"}}]}
            """.trimIndent()
        )

        val treffer = ergebnis.treffer.single()
        assertEquals("SAN-1166", treffer.projekt?.nr)
        assertEquals("HERO-Archiv/SAN-1166 Herr Mark Boese", treffer.projekt?.pfad)
        assertEquals(false, treffer.istOrdner)
    }

    @Test
    fun `Suchtreffer ohne Projekt und mit unbekannten Projektfeldern`() {
        val ergebnis = json.decodeFromString<SuchErgebnis>(
            """
            {"anzahl": 2, "abgeschnitten": true, "stand": "",
             "treffer": [
               {"name": "Notizen", "pfad": "Notizen", "typ": "ordner", "ordner": "", "geaendert": "", "projekt": null},
               {"name": "x", "pfad": "x", "typ": "ordner", "ordner": "", "geaendert": "",
                "projekt": {"nr": "HEZ-1", "neu": 42}}]}
            """.trimIndent()
        )
        assertNull(ergebnis.treffer[0].projekt)
        assertEquals("HEZ-1", ergebnis.treffer[1].projekt?.nr)
        assertTrue(ergebnis.abgeschnitten)
    }

    @Test
    fun `liest Projektangaben und Seiten im Ordner`() {
        val ordner = json.decodeFromString<Ordner>(
            """
            {"pfad": "HERO-Archiv", "eltern": "", "abgeschnitten": true, "gesamt": 812,
             "eintraege": [{"name": "SAN-1166 Herr Mark Boese", "pfad": "HERO-Archiv/SAN-1166 Herr Mark Boese",
               "typ": "ordner", "groesse": null, "geaendert": "07.08.2026 10:12",
               "projekt": {"nr": "SAN-1166", "kunde": "Herr Mark Boese", "bauvorhaben": "Grimmstraße 7, 10967 Berlin",
                           "titel": "Strang", "status": "Abgeschlossen"}}]}
            """.trimIndent()
        )
        assertEquals(812, ordner.gesamt)
        assertEquals("Abgeschlossen", ordner.eintraege.single().projekt?.status)
    }

    @Test
    fun `Sortierungen stimmen mit dem Vertrag ueberein`() {
        val schema = Json.parseToJsonElement(vertragsDatei().readText()).jsonObject
        val parameter = schema["paths"]!!.jsonObject["/v1/ordner"]!!.jsonObject["get"]!!
            .jsonObject["parameters"]!!.jsonArray
            .map { it.jsonObject }
            .single { it["name"]!!.jsonPrimitive.content == "sortierung" }
        val werte = parameter["schema"]!!.jsonObject["enum"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(werte.toSet(), ORDNER_SORTIERUNGEN.toSet())
    }

    // --- v1.17.0: Lange Anfragen sicher (API.md 1b) ---------------------------

    @Test
    fun `anfrage_id geht mit, ohne faellt sie aus dem JSON`() {
        val mit = json.encodeToString(ChatAnfrage(text = "Hallo", anfrage_id = "3f2c9a0e-1234"))
        assertTrue(mit.contains(""""anfrage_id":"3f2c9a0e-1234""""))
        val ohne = json.encodeToString(ChatAnfrage(text = "Hallo"))
        assertEquals("""{"text":"Hallo"}""", ohne)
    }

    @Test
    fun `liest den Stand einer laufenden und einer fertigen Anfrage`() {
        val laeuft = json.decodeFromString<ChatStatus>(
            """{"anfrage_id": "3f2c9a0e-1234", "status": "laeuft", "gespraech_id": "c87", "antwort": null, "fehler": null}"""
        )
        assertEquals(ChatStatus.STATUS_LAEUFT, laeuft.status)
        assertNull(laeuft.antwort)

        val fertig = json.decodeFromString<ChatStatus>(
            """{"anfrage_id": "3f2c9a0e-1234", "status": "fertig", "gespraech_id": "c87",
               "antwort": {"gespraech_id": "c87", "anfrage_id": "3f2c9a0e-1234", "antwort": "Erledigt."}}"""
        )
        assertEquals("Erledigt.", fertig.antwort!!.antwort)
        assertEquals("3f2c9a0e-1234", fertig.antwort!!.anfrage_id)
    }

    @Test
    fun `Gespraech ohne laeuft gilt als ruhend`() {
        val g = json.decodeFromString<GespraechKurz>(
            """{"id": "c87", "titel": null, "erstellt": "2026-10-13T09:00:00+02:00", "geaendert": "2026-10-13T09:00:00+02:00"}"""
        )
        assertEquals(false, g.laeuft)
    }

    @Test
    fun `Status und Form der anfrage_id stimmen mit dem Vertrag ueberein`() {
        val schemas = Json.parseToJsonElement(vertragsDatei().readText()).jsonObject["components"]!!
            .jsonObject["schemas"]!!.jsonObject
        val status = schemas["ChatStatus"]!!.jsonObject["properties"]!!.jsonObject["status"]!!
            .jsonObject["enum"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(
            setOf(ChatStatus.STATUS_LAEUFT, ChatStatus.STATUS_FERTIG, ChatStatus.STATUS_FEHLER),
            status.toSet(),
        )

        val muster = schemas["ChatAnfrage"]!!.jsonObject["properties"]!!.jsonObject["anfrage_id"]!!
            .jsonObject["anyOf"]!!.jsonArray.first { it.jsonObject.containsKey("pattern") }
            .jsonObject["pattern"]!!.jsonPrimitive.content
        assertEquals(muster, ANFRAGE_ID_MUSTER.pattern)
        // Was die App erzeugt, muss der Server annehmen.
        assertTrue(ANFRAGE_ID_MUSTER.matches(AssistentRepository.neueAnfrageId()))
    }

    /** `openapi.json` liegt im Projektstamm, die Tests laufen in `android/app`. */
    private fun vertragsDatei(): File {
        var ordner: File? = File("").absoluteFile
        while (ordner != null) {
            val treffer = File(ordner, "openapi.json")
            if (treffer.isFile) return treffer
            ordner = ordner.parentFile
        }
        throw AssertionError(
            "openapi.json nicht gefunden - der Vertrag gehoert in den Projektstamm."
        )
    }
}

class MarkdownTest {

    @Test
    fun `Diagramm mit Tilden wird nicht vorgelesen`() {
        val ergebnis = markdownEntfernen("Vorher\n\n~~~diagramm\n{\"art\": \"balken\"}\n~~~\n\nNachher")
        assertTrue(!ergebnis.contains("balken"))
        assertTrue(ergebnis.startsWith("Vorher") && ergebnis.endsWith("Nachher"))
    }

    @Test
    fun `Liste nach Leerzeile klebt nicht an den Absatz davor`() {
        assertEquals("Absatz\n\nPunkt eins", markdownEntfernen("Absatz\n\n- Punkt eins"))
    }

    @Test
    fun `entfernt Auszeichnungen vor der Sprachausgabe`() {
        val ergebnis = markdownEntfernen("Im Projekt **HEZ-2003** liegt `Checkliste.docx`.")
        assertEquals("Im Projekt HEZ-2003 liegt Checkliste.docx.", ergebnis)
    }

    @Test
    fun `macht aus Listen sprechbare Zeilen`() {
        val ergebnis = markdownEntfernen("- Erstens\n- Zweitens")
        assertEquals("Erstens\nZweitens", ergebnis)
    }

    @Test
    fun `behaelt nur den Linktext`() {
        val ergebnis = markdownEntfernen("Siehe [die Förderung](https://example.invalid).")
        assertEquals("Siehe die Förderung.", ergebnis)
    }

    @Test
    fun `entfernt Ueberschriften`() {
        assertEquals("Titel", markdownEntfernen("## Titel"))
    }

    @Test
    fun `macht aus einer Tabelle sprechbare Zeilen`() {
        // Ohne das liest die Stimme zwischen jeder Zelle einen senkrechten
        // Strich vor, und die Trennzeile als Folge von Bindestrichen.
        val tabelle = """
            | Projekt | Status |
            |---|---|
            | HEZ-2003 | offen |
        """.trimIndent()

        val erwartet = listOf("Projekt, Status", "HEZ-2003, offen").joinToString("\n")
        assertEquals(erwartet, markdownEntfernen(tabelle))
    }

    @Test
    fun `entfernt Zitatzeichen und Durchstreichung`() {
        assertEquals("Ein Zitat", markdownEntfernen("> Ein Zitat"))
        assertEquals("veraltet", markdownEntfernen("~~veraltet~~"))
    }
}
