package de.chefassistent.app

import de.chefassistent.app.data.ChatAntwort
import de.chefassistent.app.data.ChatStatus
import de.chefassistent.app.data.Ereignis
import de.chefassistent.app.data.Fortschritt
import de.chefassistent.app.data.Nachricht
import de.chefassistent.app.data.Netzwerk
import de.chefassistent.app.ui.chat.ChatZustand
import de.chefassistent.app.ui.chat.LaufendeAnfrage
import de.chefassistent.app.ui.chat.SendeKnopf
import de.chefassistent.app.ui.chat.arbeitetZeile
import de.chefassistent.app.ui.chat.dauerText
import de.chefassistent.app.ui.chat.ereignisseZusammenfuehren
import de.chefassistent.app.ui.chat.sendeKnopf
import de.chefassistent.app.ui.chat.verlaufTitel
import de.chefassistent.app.ui.chat.verlaufZeilen
import de.chefassistent.app.ui.chat.zeigtVerlauf
import de.chefassistent.app.ui.chat.zeitText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fortschritt, Denken, Stopp und Zwischenruf nach `API.md` 1d (v1.20.0). */
class FortschrittTest {

    private val json = Netzwerk.json

    private fun ereignis(nr: Int, art: String, text: String = "", werkzeug: String? = null, sekunde: Double = nr.toDouble(),
                         ok: Boolean? = null, dauerMs: Long? = null) =
        Ereignis(nr = nr, sekunde = sekunde, art = art, text = text, werkzeug = werkzeug, ok = ok, dauer_ms = dauerMs)

    /** Beispiel aus dem Auftrag 1.20 Teil B, ergaenzt um eine Art, die die App nicht kennt. */
    private val beispiel = """
        {"anfrage_id": "3f2c9a0e-1111", "status": "laeuft", "gespraech_id": "c8719cc52c8f4d0a", "antwort": null, "fehler": null,
         "fortschritt": {"ereignisse": [
            {"nr": 3, "zeit": "2026-10-15T09:12:04.120+00:00", "sekunde": 4.2, "art": "werkzeug", "text": "Sucht Projekt „Altkaulsdorf“", "werkzeug": "hero_projekt_suchen"},
            {"nr": 4, "zeit": "…", "sekunde": 4.6, "art": "werkzeug_fertig", "text": "fertig", "werkzeug": "hero_projekt_suchen", "ok": true, "dauer_ms": 380},
            {"nr": 5, "zeit": "…", "sekunde": 4.7, "art": "schritt", "text": "Schritt 3 · gpt-5.6-sol · mittel", "schritt": 3, "modell": "gpt-5.6-sol", "denkaufwand": "medium"},
            {"nr": 6, "zeit": "…", "sekunde": 9.8, "art": "denken", "text": "Ich vergleiche Angebot und Schlussrechnung …"},
            {"nr": 7, "sekunde": 9.9, "art": "teleportiert", "text": "Etwas Neues", "neues_feld": 1}],
          "naechster": 8, "schritte": 3, "werkzeuge": 5, "aktuell": "Denkt nach …", "dauer_s": 9.9,
          "gestartet": "2026-10-15T09:12:00+00:00", "kuenftig": true}}
    """.trimIndent()

    @Test
    fun beispielWirdGelesen() {
        val stand = json.decodeFromString<ChatStatus>(beispiel)
        assertEquals(ChatStatus.STATUS_LAEUFT, stand.status)
        assertNotNull(stand.fortschritt)
        val f = stand.fortschritt!!
        assertEquals(5, f.ereignisse.size)
        assertEquals(8, f.naechster)
        assertEquals(3, f.schritte)
        assertEquals(5, f.werkzeuge)
        assertEquals("Denkt nach …", f.aktuell)
        assertEquals(380L, f.ereignisse[1].dauer_ms)
        assertEquals(true, f.ereignisse[1].ok)
        assertEquals("teleportiert", f.ereignisse[4].art)
    }

    @Test
    fun abgebrochenMitAntwort() {
        val roh = """
            {"anfrage_id": "3f2c9a0e-1111", "status": "abgebrochen", "gespraech_id": "g1",
             "antwort": {"gespraech_id": "g1", "antwort": "⏹ Abgebrochen. Bis dahin ausgeführt: …", "abgebrochen": true,
                         "fortschritt": {"ereignisse": [{"nr": 0, "sekunde": 0.1, "art": "abbruch", "text": "Stopp angefordert"}],
                                         "naechster": 1, "schritte": 2, "werkzeuge": 0, "dauer_s": 12.0}}}
        """.trimIndent()
        val stand = json.decodeFromString<ChatStatus>(roh)
        assertEquals(ChatStatus.STATUS_ABGEBROCHEN, stand.status)
        val antwort = stand.antwort!!
        assertTrue(antwort.abgebrochen)
        assertEquals("abbruch", antwort.fortschritt!!.ereignisse.single().art)
    }

    @Test
    fun alteAntwortOhneFortschritt() {
        val antwort = json.decodeFromString<ChatAntwort>("""{"gespraech_id": "g", "antwort": "Hallo"}""")
        assertNull(antwort.fortschritt)
        assertFalse(antwort.abgebrochen)
        assertFalse(zeigtVerlauf(antwort.fortschritt))
        val nachricht = json.decodeFromString<Nachricht>("""{"id": 812, "rolle": "nutzer", "text": "nimm nur Bad", "zeit": "2026-10-15T09:12:05+00:00", "zwischenruf": true}""")
        assertTrue(nachricht.zwischenruf)
    }

    @Test
    fun zusammenfuehrenOhneDubletten() {
        val erste = Fortschritt(
            ereignisse = listOf(ereignis(0, Ereignis.ART_SCHRITT), ereignis(1, Ereignis.ART_WERKZEUG, werkzeug = "a"), ereignis(2, Ereignis.ART_DENKEN)),
            naechster = 3, schritte = 1, werkzeuge = 1, aktuell = "Sucht …",
        )
        // Nach einem Funkloch nochmals mit altem `seit`: 1 und 2 kommen doppelt.
        val zweite = Fortschritt(
            ereignisse = listOf(ereignis(1, Ereignis.ART_WERKZEUG, werkzeug = "a"), ereignis(2, Ereignis.ART_DENKEN), ereignis(3, Ereignis.ART_WERKZEUG_FERTIG, werkzeug = "a")),
            naechster = 4, schritte = 2, werkzeuge = 1, aktuell = "Denkt nach …",
        )
        val zusammen = ereignisseZusammenfuehren(ereignisseZusammenfuehren(null, erste), zweite)
        assertEquals(listOf(0, 1, 2, 3), zusammen.ereignisse.map { it.nr })
        assertEquals(4, zusammen.naechster)
        assertEquals(2, zusammen.schritte)
        assertEquals("Denkt nach …", zusammen.aktuell)

        // Leere Abfrage aendert die Liste nicht und laesst `naechster` nicht zurueckfallen.
        val leer = ereignisseZusammenfuehren(zusammen, Fortschritt(naechster = 0, schritte = 2))
        assertEquals(4, leer.ereignisse.size)
        assertEquals(4, leer.naechster)
    }

    @Test
    fun werkzeugFertigHaengtAnDerWerkzeugzeile() {
        val zeilen = verlaufZeilen(
            listOf(
                ereignis(1, Ereignis.ART_WERKZEUG, "Sucht Projekt", "hero_projekt_suchen"),
                ereignis(2, Ereignis.ART_WERKZEUG, "Liest PDF", "datei_lesen"),
                ereignis(3, Ereignis.ART_WERKZEUG_FERTIG, "Datei fehlt", "datei_lesen", ok = false),
                ereignis(4, Ereignis.ART_WERKZEUG_FERTIG, "fertig", "hero_projekt_suchen", ok = true, dauerMs = 380),
                ereignis(5, Ereignis.ART_DENKEN, "Ich vergleiche …"),
                ereignis(6, "unbekannt", "Neue Art"),
            )
        )
        assertEquals(4, zeilen.size)
        assertEquals("hero_projekt_suchen", zeilen[0].ereignis.werkzeug)
        assertEquals(380L, zeilen[0].fertig?.dauer_ms)
        assertEquals(false, zeilen[1].fertig?.ok)
        assertEquals(Ereignis.ART_DENKEN, zeilen[2].ereignis.art)
        assertEquals("unbekannt", zeilen[3].ereignis.art)
        assertEquals("0,4 s", dauerText(380))
    }

    @Test
    fun fertigOhneStartBleibtStehen() {
        val zeilen = verlaufZeilen(listOf(ereignis(4, Ereignis.ART_WERKZEUG_FERTIG, "fertig", "x", ok = true)))
        assertEquals(1, zeilen.size)
        assertNull(zeilen[0].fertig)
    }

    @Test
    fun fortschrittszeile() {
        assertEquals("Arbeitet · Schritt 3 · 5 Werkzeuge · 0:42", arbeitetZeile(3, 5, 42))
        assertEquals("Arbeitet · Schritt 1 · 1 Werkzeug · 1:05", arbeitetZeile(1, 1, 65))
        assertEquals("Arbeitet · 0:03", arbeitetZeile(0, 0, 3))
        assertEquals("0:04", zeitText(4.7))
        assertEquals("12:00", zeitText(720.0))
    }

    @Test
    fun verlaufUnterDerAntwort() {
        val mitWerkzeugen = Fortschritt(werkzeuge = 7, dauer_s = 72.4)
        assertTrue(zeigtVerlauf(mitWerkzeugen))
        assertEquals("Verlauf: 7 Werkzeuge · 1:12 min", verlaufTitel(mitWerkzeugen))

        val nurDenken = Fortschritt(ereignisse = listOf(ereignis(0, Ereignis.ART_DENKEN, "…")), dauer_s = 8.0)
        assertTrue(zeigtVerlauf(nurDenken))

        // Kurzes "Hallo" ohne Werkzeuge und Denken: nichts anzeigen.
        val schlicht = Fortschritt(ereignisse = listOf(ereignis(0, Ereignis.ART_SCHRITT), ereignis(1, Ereignis.ART_FERTIG)), schritte = 1, dauer_s = 2.0)
        assertFalse(zeigtVerlauf(schlicht))
        assertFalse(zeigtVerlauf(null))
    }

    @Test
    fun knopfWirdStoppOderZwischenruf() {
        assertEquals(SendeKnopf.SENDEN, ChatZustand(entwurf = "Hallo").sendeKnopf)

        // Alter Server ohne Fortschritt: kein Stopp, Senden bleibt (gesperrt).
        val ohne = ChatZustand(laeuft = true, laufend = LaufendeAnfrage("id-12345678", 0L))
        assertEquals(SendeKnopf.SENDEN, ohne.sendeKnopf)
        assertFalse(ohne.kannSenden)

        val mit = ohne.copy(laufend = LaufendeAnfrage("id-12345678", 0L, fortschritt = Fortschritt()))
        assertEquals(SendeKnopf.STOPP, mit.sendeKnopf)
        assertEquals(SendeKnopf.ZWISCHENRUF, mit.copy(entwurf = "nimm nur Bad-Projekte").sendeKnopf)
        // Eine zweite normale Nachricht geht nie parallel raus.
        assertFalse(mit.copy(entwurf = "x").kannSenden)

        // Nach "Stopp" weder Stopp noch Zwischenruf.
        val stoppt = mit.copy(laufend = mit.laufend!!.copy(stoppt = true), entwurf = "x")
        assertEquals(SendeKnopf.SENDEN, stoppt.sendeKnopf)
    }
}
