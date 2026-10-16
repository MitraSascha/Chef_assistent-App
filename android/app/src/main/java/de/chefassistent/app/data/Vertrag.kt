package de.chefassistent.app.data

import kotlinx.serialization.Serializable

/**
 * Datentypen aus `openapi.json` (Chef-Assistent v1.20.0).
 *
 * Die Feldnamen sind bewusst deutsch und identisch mit dem Vertrag - hier wird
 * nichts uebersetzt und nichts umbenannt. Neue Felder darf der Server jederzeit
 * ergaenzen; der Parser ist in [Netzwerk] auf `ignoreUnknownKeys` gestellt.
 *
 * Nullable, aber laut Vertrag immer vorhanden: `GespraechKurz.titel`, `Termin.ende`.
 * Deshalb stehen die ohne Default - fehlen sie, ist die Antwort tatsaechlich kaputt.
 */

@Serializable
data class Ich(
    val name: String,
    val anzeigename: String,
    val modell: String,
    val version: String,
)

@Serializable
data class ChatAnfrage(
    val text: String,
    val gespraech_id: String? = null,
    /**
     * Seit v1.17.0: von der App vergebene Kennung dieser Nachricht (API.md 1b). Mit
     * ihr laesst sich das Ergebnis nach einem Abbruch abholen, und eine Wiederholung
     * mit derselben Kennung fuehrt der Server **nie** doppelt aus.
     */
    val anfrage_id: String? = null,
    /**
     * Seit v1.5.0: bis zu [MAX_BILDER] Fotos aus der Ablage, die der Assistent
     * zu dieser Nachricht ansehen soll (Typenschild, Fehleranzeige). `null`
     * faellt aus dem JSON - ein Server vor 1.5.0 sieht dann nichts Neues.
     */
    val bilder: List<String>? = null,
    /**
     * Seit v1.20.0: Modell fuer dieses Gespraech, [MODELL_AUTO] oder ein `wert` aus
     * `GET /v1/modelle`. Der Server speichert es im Gespraech, es gilt schon fuer
     * diese Nachricht. `null` = Einstellung des Gespraechs bleibt.
     */
    val modell: String? = null,
    /** Seit v1.20.0: Denkaufwand, [MODELL_AUTO] oder ein `denkstufen[].wert`. */
    val denkaufwand: String? = null,
) {
    companion object {
        /** Grenze aus dem Vertrag (`maxItems`), mehr ergibt 422. */
        const val MAX_BILDER = 4
    }
}

@Serializable
data class ChatAntwort(
    val gespraech_id: String,
    /** Seit v1.17.0: die Kennung der Anfrage, auf die das die Antwort ist. */
    val anfrage_id: String? = null,
    val antwort: String,
    val dateien: List<String> = emptyList(),
    /** Seit v1.13.0: dieselben Pfade als Knoepfe. Ist die Liste da, ersetzt sie die Chips aus [dateien]. */
    val links: List<DateiLink> = emptyList(),
    val bestaetigungen: List<Bestaetigung> = emptyList(),
    val ausgefuehrt: List<Ausgefuehrt> = emptyList(),
    val quellen: List<Quelle> = emptyList(),
    /** Seit v1.20.0: Modell, das diese Antwort tatsaechlich erzeugt hat (bei Wechsel das letzte). */
    val modell: String? = null,
    val denkaufwand: String? = null,
    /** Anzeigename zu [denkaufwand], z. B. "mittel". */
    val denkaufwand_name: String? = null,
    /** Der Server hat waehrend der Antwort auf das Arbeitsmodell bzw. mehr Denkaufwand gewechselt. */
    val modell_gewechselt: Boolean = false,
    /** Seit v1.20.0: Verlauf der Bearbeitung (Schritte, Werkzeuge, Denken). */
    val fortschritt: Fortschritt? = null,
    /** Seit v1.20.0: vom Nutzer gestoppt. */
    val abgebrochen: Boolean = false,
)

/**
 * Datei oder Ordner aus einer Chat-Antwort, fertig fuer eine Karte mit Knoepfen
 * (seit v1.13.0). Enthaelt nur, was es noch gibt - Geloeschtes steht weiterhin
 * nur in `dateien`.
 */
@Serializable
data class DateiLink(
    /** Datei -> `GET /v1/datei`, Ordner -> `GET /v1/ordner`. */
    val pfad: String,
    val name: String,
    /** `ordner` oder `datei`. */
    val typ: String,
    /** Uebergeordneter Ordner. */
    val ordner: String = "",
    val groesse: String? = null,
    /** HERO-Projekt als fertige Zeile, z. B. "SAN-1270 · Angelov · Pacelliallee, 14195 Berlin". */
    val projekt: String? = null,
) {
    val istOrdner: Boolean get() = typ == OrdnerEintrag.TYP_ORDNER
}

@Serializable
data class Bestaetigung(
    val id: String,
    val beschreibung: String,
    val werkzeug: String,
)

/** Vom Assistenten selbst ausgefuehrt, weil der Nutzer im Chat zugestimmt hat. */
@Serializable
data class Ausgefuehrt(
    val id: String,
    val status: String,
    val beschreibung: String,
)

@Serializable
data class Quelle(
    val titel: String,
    val url: String,
)

@Serializable
data class OffeneBestaetigung(
    val id: String,
    val gespraech_id: String,
    val werkzeug: String,
    val beschreibung: String,
    val erstellt: String,
)

@Serializable
data class Entscheidung(
    val annehmen: Boolean,
)

@Serializable
data class EntscheidungAntwort(
    val status: String,
    val beschreibung: String,
    val fehler: String? = null,
    val dateien: List<String> = emptyList(),
) {
    val istAusgefuehrt: Boolean get() = status == STATUS_AUSGEFUEHRT

    companion object {
        const val STATUS_AUSGEFUEHRT = "ausgefuehrt"
        const val STATUS_ABGELEHNT = "abgelehnt"
        const val STATUS_FEHLGESCHLAGEN = "fehlgeschlagen"
    }
}

@Serializable
data class SpracheAntwort(
    val text: String,
    val chat: ChatAntwort? = null,
)

@Serializable
data class VorlesenAnfrage(
    val text: String,
    /**
     * Gewuenschte Stimme. Fehlt sie, gilt die Servervorgabe aus `STIMME` in
     * `assistent.env`. Ein unbekannter Wert wird mit 422 abgelehnt.
     */
    val stimme: String? = null,
)

/**
 * Die vom Server erlaubten Stimmen (Stand Vertragserweiterung 2026-09-23).
 *
 * Steht hier als einzige Quelle im Client. Erweitert der Server die Liste,
 * gehoert die Aenderung hierher - die App darf keine Stimme anbieten, die der
 * Server nicht kennt, sonst laeuft der Nutzer in ein 422.
 */
val ERLAUBTE_STIMMEN: List<String> = listOf(
    "alloy", "ash", "ballad", "coral", "echo", "fable",
    "nova", "onyx", "sage", "shimmer", "verse",
)

/** Antwort auf `POST /v1/fotos`. Beide Pfade sind ueber `GET /v1/datei` abrufbar. */
@Serializable
data class FotoAntwort(
    val foto: String,
    /** Notiz, an die das Foto gehaengt wurde - fuer Folgefotos wieder mitschicken. */
    val notiz: String,
    /** `true` = es gab heute noch keine Notiz, der Server hat "Fotos" angelegt. */
    val notiz_neu: Boolean,
)

/** Antwort auf `POST /v1/videos`. */
@Serializable
data class VideoAntwort(
    val video: String,
    val notiz: String,
    val notiz_neu: Boolean,
    val dauer_s: Double,
    /** `laeuft` = Gesprochenes wird im Hintergrund abgeschrieben, `aus` = nicht. */
    val abschrift: String,
) {
    val schreibtAb: Boolean get() = abschrift == ABSCHRIFT_LAEUFT

    companion object {
        const val ABSCHRIFT_LAEUFT = "laeuft"
    }
}

/** Antwort auf `POST /v1/dokumente` (seit v1.12.0). */
@Serializable
data class DokumentAntwort(
    /** Pfad des gespeicherten Dokuments, ueber `GET /v1/datei` abrufbar. */
    val dokument: String,
    val notiz: String,
    val notiz_neu: Boolean,
    /** `false` = abgelegt, aber ohne lesbaren Text (Scan, Passwort) - [hinweis] anzeigen. */
    val lesbar: Boolean,
    val hinweis: String = "",
)

/**
 * Erlaubte Werte fuer `abschreiben` bei `POST /v1/videos`, Vorgabe zuerst.
 * Einzige Stelle im Client - ein Test vergleicht mit dem Vertrag.
 */
val ABSCHREIBEN_WERTE: List<String> = listOf("auto", "immer", "nie")

/**
 * Antwort auf `GET /v1/notizen`: eigene Notizen und Ordner, jeweils zuletzt
 * geaendert zuerst. Jeder `pfad` taugt direkt als `notiz` fuer Fotos und Videos.
 */
@Serializable
data class Notizen(
    /** Eigener Notizordner, z.B. "Notizen/Sascha". */
    val wurzel: String,
    val ordner: List<NotizOrdner> = emptyList(),
    val notizen: List<NotizKurz> = emptyList(),
)

@Serializable
data class NotizOrdner(
    val name: String,
    val pfad: String,
    /** Anzahl Notizen direkt darin. */
    val notizen: Int,
    /**
     * HERO-Projektnummer, wenn der Ordner eine HERO-Mappe ist (seit v1.19.0).
     * Alles darin geht automatisch ins HERO-Projekt.
     */
    val hero_projekt: String? = null,
    val geaendert: String,
) {
    /** Gemeinsamer Ordner eines GU-Projekts (seit v1.9.0), nicht der eigene. */
    val istGuProjekt: Boolean get() = pfad.startsWith(GU_PRAEFIX)
}

/** Pfade gemeinsamer GU-Projektordner beginnen so (`API.md` 4d). */
const val GU_PRAEFIX = "GU-Projekte/"

@Serializable
data class NotizKurz(
    /** Dateiname ohne `.md`. */
    val titel: String,
    val pfad: String,
    /**
     * Unterordner relativ zum eigenen Notizordner, "" = direkt darin.
     * Bei GU-Projekten der volle Pfad (beginnt mit [GU_PRAEFIX]).
     */
    val ordner: String,
    val geaendert: String,
)

/** Wurzel des HERO-Spiegels in der Ablage, Projekte liegen direkt darunter (`API.md` 4). */
const val HERO_ARCHIV = "HERO-Archiv"

/** Anfrage an `POST /v1/hero-mappe` (seit v1.19.0). */
@Serializable
data class HeroMappeAnfrage(
    /** Nummer ("SAN-660"), Ordnername oder Pfad im HERO-Archiv. */
    val projekt: String,
)

/**
 * HERO-Mappe einer Person (seit v1.19.0): ein normaler Notizordner, dessen
 * Inhalt der Server ins HERO-Projekt kopiert. Angeheftet wird mit [name] als
 * Bezug und [pfad] als `notiz` fuer Anhaenge.
 */
@Serializable
data class HeroMappe(
    val name: String,
    val pfad: String,
    /** HERO-Projektnummer. */
    val projekt: String,
    val hero_ordner: String = "",
    /** Was schon Richtung HERO ging, aelteste zuerst. */
    val dateien: List<HeroMappeDatei> = emptyList(),
)

@Serializable
data class HeroMappeDatei(
    val quelle: String,
    val ziel: String? = null,
    /** Einer aus [HERO_DATEI_STATUS], unbekannte Werte zaehlen nicht mit. */
    val status: String,
    val zeit: String = "",
)

/** Werte fuer `status` in [HeroMappeDatei]. Ein Test vergleicht sie mit dem Vertrag. */
const val HERO_STATUS_KOPIERT = "kopiert"
const val HERO_STATUS_ZU_GROSS = "zu_gross"
const val HERO_STATUS_SCHON_IN_HERO = "schon_in_hero"
const val HERO_STATUS_VIDEO = "video"
val HERO_DATEI_STATUS = listOf(HERO_STATUS_KOPIERT, HERO_STATUS_ZU_GROSS, HERO_STATUS_SCHON_IN_HERO, HERO_STATUS_VIDEO)

/**
 * Nur HERO-Projektordner selbst: `HERO-Archiv/<Projekt>`, nicht deren
 * Unterordner wie `Fotos` oder `Rechnungen` (Auftrag 1.19).
 */
fun istHeroProjektordner(pfad: String): Boolean {
    val teile = pfad.trim('/').split('/')
    return teile.size == 2 && teile[0] == HERO_ARCHIV && teile[1].isNotBlank()
}

/** Antwort auf `GET /v1/ordner`: Inhalt eines Ordners der Ablage. */
@Serializable
data class Ordner(
    /** Relativ zur Ablage, "" = oberste Ebene. */
    val pfad: String,
    /** Uebergeordneter Ordner, `null` = schon ganz oben. */
    val eltern: String? = null,
    /** Erst Ordner, dann Dateien, sortiert nach `sortierung`. */
    val eintraege: List<OrdnerEintrag> = emptyList(),
    /** `true` = nach dieser Seite kommt noch mehr, mit `ab = ab + anzahl` nachladen. */
    val abgeschnitten: Boolean = false,
    /** Eintraege im Ordner insgesamt (seit v1.13.0). */
    val gesamt: Int = 0,
)

/** Werte fuer `sortierung` bei `GET /v1/ordner` (seit v1.13.0). Ein Test vergleicht sie mit dem Vertrag. */
val ORDNER_SORTIERUNGEN = listOf("name", "name_ab", "geaendert")

/** HERO-Projektangaben eines Projektordners (seit v1.13.0). */
@Serializable
data class ProjektAngaben(
    val nr: String = "",
    val kunde: String = "",
    /** Adresse der Baustelle. */
    val bauvorhaben: String = "",
    val titel: String = "",
    val status: String = "",
    /** Nur in Suchtreffern: Pfad des Projektordners. */
    val pfad: String? = null,
)

@Serializable
data class OrdnerEintrag(
    val name: String,
    val pfad: String,
    /** `ordner` oder `datei`. */
    val typ: String,
    /** Nur Dateien, fertig formatiert ("1.2 MB"). */
    val groesse: String? = null,
    /** TT.MM.JJJJ HH:MM in Ortszeit - kommt so vom Server, nicht umrechnen. */
    val geaendert: String,
    /** Nur bei HERO-Projektordnern (seit v1.13.0). */
    val projekt: ProjektAngaben? = null,
) {
    val istOrdner: Boolean get() = typ == TYP_ORDNER

    companion object {
        const val TYP_ORDNER = "ordner"
    }
}

/** Antwort auf `GET /v1/suche` (seit v1.13.0). */
@Serializable
data class SuchErgebnis(
    /** Beste zuerst. */
    val treffer: List<SuchTreffer> = emptyList(),
    val anzahl: Int = 0,
    /** Mehr als `max` Treffer - Hinweis "Suche genauer fassen". */
    val abgeschnitten: Boolean = false,
    val stand: String = "",
)

@Serializable
data class SuchTreffer(
    val name: String,
    val pfad: String,
    /** `ordner` oder `datei`. */
    val typ: String,
    /** Uebergeordneter Ordner, fuer "Im Ordner zeigen". */
    val ordner: String = "",
    val groesse: String? = null,
    /** TT.MM.JJJJ HH:MM in Ortszeit. */
    val geaendert: String = "",
    /** Im Vertrag ein freies Objekt; die Felder aus `API.md` 6a. */
    val projekt: ProjektAngaben? = null,
) {
    val istOrdner: Boolean get() = typ == OrdnerEintrag.TYP_ORDNER
}

/**
 * Hintergrundauftrag, seit v1.6.0. `art` ist seit v1.8.0 "versicherungsbericht" oder
 * "begehungsprotokoll"; unbekannte Werte zeigt die App generisch an.
 * Gestartet wird er im Chat, die App zeigt nur den Stand und bietet
 * Abbrechen und Wiederholen an.
 *
 * `dateien`, `zusammenfassung` und `offene_punkte` gibt es nur bei [STATUS_FERTIG].
 * `fehler` kann auch bei [STATUS_WARTET] stehen: dann versucht der Server es selbst erneut.
 */
@Serializable
data class Auftrag(
    val id: String,
    val art: String,
    val titel: String,
    val status: String,
    /** Fertiger Anzeigetext, z.B. "Unterlagen auswerten (7/12)". */
    val schritt: String,
    /** Grob in Prozent, 0-100. */
    val fortschritt: Int,
    val erstellt: String,
    val geaendert: String,
    val dateien: List<String> = emptyList(),
    val zusammenfassung: String? = null,
    val offene_punkte: List<String> = emptyList(),
    val fehler: String? = null,
    val gespraech_id: String? = null,
) {
    /** Der Server arbeitet noch daran - solange wird nachgefragt. */
    val istAktiv: Boolean get() = status == STATUS_WARTET || status == STATUS_LAEUFT
    val kannAbbrechen: Boolean get() = istAktiv
    val kannWiederholen: Boolean get() = status == STATUS_FEHLER || status == STATUS_ABGEBROCHEN

    companion object {
        const val STATUS_WARTET = "wartet"
        const val STATUS_LAEUFT = "laeuft"
        const val STATUS_FERTIG = "fertig"
        const val STATUS_FEHLER = "fehler"
        const val STATUS_ABGEBROCHEN = "abgebrochen"

        const val ART_VERSICHERUNGSBERICHT = "versicherungsbericht"
        const val ART_BEGEHUNGSPROTOKOLL = "begehungsprotokoll"
    }
}

/** Alle Werte von `Auftrag.art` - ein Test vergleicht sie mit dem Vertrag. */
val AUFTRAG_ARTEN: List<String> = listOf(
    Auftrag.ART_VERSICHERUNGSBERICHT, Auftrag.ART_BEGEHUNGSPROTOKOLL,
)

/** Alle Werte von `Auftrag.status` - ein Test vergleicht sie mit dem Vertrag. */
val AUFTRAG_STATUS: List<String> = listOf(
    Auftrag.STATUS_WARTET, Auftrag.STATUS_LAEUFT, Auftrag.STATUS_FERTIG,
    Auftrag.STATUS_FEHLER, Auftrag.STATUS_ABGEBROCHEN,
)

/**
 * Eine Haekchen-Zeile aus einer Notiz (`GET /v1/aufgaben`, seit v1.7.0).
 *
 * **Die `id` ist nicht stabil**: aendert jemand den Text, bekommt die Aufgabe
 * eine neue. Deshalb nach jedem POST neu laden, und ein 404 heisst nur "neu laden".
 */
@Serializable
data class Aufgabe(
    val id: String,
    val text: String,
    val status: String,
    /** Frist `YYYY-MM-DD`. */
    val bis: String? = null,
    /** Anzeigename, wenn die Aufgabe an jemanden vergeben ist. */
    val fuer: String? = null,
    /** Wessen Notiz (Anzeigename). */
    val von: String,
    /** Pfad der Notiz, fuer `GET /v1/datei` bzw. `/v1/ordner`. */
    val notiz: String,
    val seit: String? = null,
    val erledigt_am: String? = null,
    val erledigt_von: String? = null,
    /** Liegt lange oder ueber der Frist - "Noch aktuell?" anzeigen. */
    val nachfragen: Boolean = false,
) {
    val istErledigt: Boolean get() = status == STATUS_ERLEDIGT

    companion object {
        const val STATUS_OFFEN = "offen"
        const val STATUS_ERLEDIGT = "erledigt"
        const val STATUS_GESTRICHEN = "gestrichen"
    }
}

/** Alle Werte von `Aufgabe.status` - ein Test vergleicht sie mit dem Vertrag. */
val AUFGABE_STATUS: List<String> = listOf(
    Aufgabe.STATUS_OFFEN, Aufgabe.STATUS_ERLEDIGT, Aufgabe.STATUS_GESTRICHEN,
)

/**
 * "Was ist offen?" fuer eine Person. Die Gruppen sind fertig sortiert und in
 * [gruppen] in der Anzeigereihenfolge aus `API.md`.
 */
@Serializable
data class AufgabenUebersicht(
    val person: String,
    /** Heute laut Server, `YYYY-MM-DD`. */
    val stand: String,
    /** Eigene offene Aufgaben (die vier Fristgruppen) - fuer den Zaehler. */
    val offen: Int,
    val ueberfaellig: List<Aufgabe> = emptyList(),
    val diese_woche: List<Aufgabe> = emptyList(),
    val spaeter: List<Aufgabe> = emptyList(),
    val ohne_datum: List<Aufgabe> = emptyList(),
    val an_andere: List<Aufgabe> = emptyList(),
    val heute_erledigt: List<Aufgabe> = emptyList(),
) {
    val gruppen: List<Pair<String, List<Aufgabe>>>
        get() = listOf(
            GRUPPE_UEBERFAELLIG to ueberfaellig,
            GRUPPE_DIESE_WOCHE to diese_woche,
            GRUPPE_SPAETER to spaeter,
            GRUPPE_OHNE_DATUM to ohne_datum,
            GRUPPE_AN_ANDERE to an_andere,
            GRUPPE_HEUTE_ERLEDIGT to heute_erledigt,
        )

    companion object {
        const val GRUPPE_UEBERFAELLIG = "ueberfaellig"
        const val GRUPPE_DIESE_WOCHE = "diese_woche"
        const val GRUPPE_SPAETER = "spaeter"
        const val GRUPPE_OHNE_DATUM = "ohne_datum"
        const val GRUPPE_AN_ANDERE = "an_andere"
        const val GRUPPE_HEUTE_ERLEDIGT = "heute_erledigt"
    }
}

/**
 * Nur die gesetzten Felder gehen raus (`explicitNulls = false` in [Netzwerk]).
 * `bis = ""` entfernt die Frist.
 */
@Serializable
data class AufgabeAenderung(
    val status: String? = null,
    val noch_aktuell: Boolean? = null,
    val bis: String? = null,
)

@Serializable
data class AufgabeAntwort(
    val status: String,
    val aufgabe: Aufgabe,
)

@Serializable
data class GespraechKurz(
    val id: String,
    val titel: String?,
    val erstellt: String,
    val geaendert: String,
    /** Seit v1.17.0: der Assistent arbeitet in diesem Gespraech gerade. */
    val laeuft: Boolean = false,
    /** Seit v1.20.0: im Gespraech gewaehltes Modell, [MODELL_AUTO] oder ein Wert aus `GET /v1/modelle`. */
    val modell: String = MODELL_AUTO,
    /** Seit v1.20.0: im Gespraech gewaehlter Denkaufwand. */
    val denkaufwand: String = MODELL_AUTO,
    /**
     * Seit v1.21.0: oben angeheftet. `null` heisst alter Server ohne das Feld:
     * gilt als nicht angeheftet, Anheften wird dann gar nicht angeboten.
     */
    val angeheftet: Boolean? = null,
) {
    val istAngeheftet: Boolean get() = angeheftet == true
}

@Serializable
data class Nachricht(
    val id: Int,
    val rolle: String,
    val text: String,
    val zeit: String,
    val dateien: List<String> = emptyList(),
    /** Wie bei [ChatAntwort.links], seit v1.13.0. */
    val links: List<DateiLink> = emptyList(),
    val bestaetigungen: List<Bestaetigung> = emptyList(),
    val quellen: List<Quelle> = emptyList(),
    /** Seit v1.20.0, nur `rolle = assistent`, wie bei [ChatAntwort]. */
    val modell: String? = null,
    val denkaufwand: String? = null,
    val denkaufwand_name: String? = null,
    val modell_gewechselt: Boolean = false,
    val fortschritt: Fortschritt? = null,
    val abgebrochen: Boolean = false,
    /** Seit v1.20.0, nur `rolle = nutzer`: als Zwischenruf waehrend einer laufenden Antwort geschickt. */
    val zwischenruf: Boolean = false,
) {
    companion object {
        const val ROLLE_NUTZER = "nutzer"
        const val ROLLE_ASSISTENT = "assistent"

        /** Systemzeile ("Ausgefuehrt: ... verschoben") - klein und grau anzeigen. */
        const val ROLLE_HINWEIS = "hinweis"
    }
}

@Serializable
data class Termin(
    val id: Int,
    val titel: String,
    val person: String,
    val wochentag: String,
    val beginn: String,
    val ende: String?,
    val ganztag: Boolean,
    val ort: String? = null,
    val notiz: String? = null,
    val eingetragen_von: String? = null,
)

@Serializable
data class Termine(
    val von: String,
    val bis: String,
    val termine: List<Termin>,
)


// --- Kalender mit HERO-Terminen (`/v1/kalender`, `/v1/personen`, seit v1.16.0) ----

/** Eintrag aus `GET /v1/personen`. [schluessel] geht als `hero:<id>` in den Filter. */
@Serializable
data class Person(
    val schluessel: String,
    val name: String,
    val hero_id: Int,
    /** Hat einen App-Zugang, z. B. `patrick`. */
    val app_benutzer: String? = null,
    /** Der angemeldete Nutzer. */
    val ich: Boolean = false,
    /** Grob, nur fuer die Sortierung. */
    val termine: Int = 0,
)

/** Reihenfolge vom Server: ich, andere App-Nutzer, dann Monteure nach Anzahl Termine. */
@Serializable
data class Personen(
    /** `null` = keine HERO-Zuordnung, dann zeigt `ich` nur die eigenen Termine. */
    val ich: String? = null,
    val personen: List<Person> = emptyList(),
)

@Serializable
data class KalenderPerson(
    val hero_id: Int? = null,
    val name: String,
    val app_benutzer: String? = null,
)

@Serializable
data class KalenderProjekt(
    val nr: String = "",
    val kunde: String = "",
    val titel: String = "",
    val adresse: String = "",
    /** Ordner im HERO-Archiv, `GET /v1/ordner?pfad=…`. */
    val pfad: String? = null,
)

@Serializable
data class KalenderTermin(
    /** `hero:<id>` oder `eigen:<id>`. */
    val id: String,
    /** `hero` oder `eigen`. */
    val quelle: String,
    val titel: String,
    /** Ortszeit ISO mit Offset, ganztaegig nur `YYYY-MM-DD`. */
    val beginn: String,
    val ende: String,
    val ganztag: Boolean,
    /** HERO-Kategorie bzw. "Eigener Termin". */
    val kategorie: String,
    /** `#RRGGBB` der HERO-Kategorie, nur als kleiner Streifen. */
    val farbe: String? = null,
    val projekt: KalenderProjekt? = null,
    val personen: List<KalenderPerson> = emptyList(),
    val ort: String? = null,
    val notiz: String? = null,
    /** `true` = HERO-Termin nur lesbar. HERO-Termine mit `false` lassen sich seit 1.18.0 verschieben, eigene aendert der Chat. */
    val nur_lesen: Boolean,
) {
    val istHero: Boolean get() = quelle == QUELLE_HERO

    /** Zahl aus `hero:<id>` fuer `PATCH /v1/kalender/hero/{id}`, sonst `null`. */
    val heroId: Long? get() = if (istHero) id.removePrefix("hero:").toLongOrNull() else null

    /** Seit 1.18.0: verschiebbar, wenn der Server schreiben darf (`hero_schreiben`). */
    val verschiebbar: Boolean get() = istHero && !nur_lesen && heroId != null

    companion object {
        const val QUELLE_HERO = "hero"
        const val QUELLE_EIGEN = "eigen"
    }
}

@Serializable
data class KalenderAntwort(
    val von: String,
    val bis: String,
    val personen: List<String> = emptyList(),
    /** `false` = HERO-Spiegel fehlt auf diesem Server, nur eigene Termine. */
    val spiegel: Boolean,
    /** Seit 1.18.0: `true` = HERO-Termine anlegen und verschieben moeglich. */
    val hero_schreiben: Boolean = false,
    val termine: List<KalenderTermin> = emptyList(),
)

// --- HERO-Termine anlegen und verschieben (`/v1/kalender/hero`, seit v1.18.0) ---

/** Eintrag aus `GET /v1/kalender/kategorien`. HERO verlangt fuer jeden Termin eine. */
@Serializable
data class TerminKategorie(
    val id: Long,
    val name: String,
    val farbe: String? = null,
)

/**
 * `POST /v1/kalender/hero`. Zeiten in Ortszeit ohne Zone (`YYYY-MM-DDTHH:MM`),
 * ganztaegig nur Datum, `ende` dann der letzte Tag einschliesslich.
 */
@Serializable
data class HeroTerminNeu(
    /** `ich` oder `hero:<id>` aus `/v1/personen`, mehrere = ein gemeinsamer Termin. */
    val personen: List<String>,
    val titel: String,
    val beginn: String,
    /** `null` = 1 Stunde. */
    val ende: String? = null,
    val ganztag: Boolean = false,
    val kategorie_id: Long,
    /** HERO-Projektnummer wie `SAN-1270`. */
    val projekt: String? = null,
    val notiz: String? = null,
) {
    companion object {
        /** `maxLength` im Vertrag. */
        const val TITEL_MAX = 200
        const val NOTIZ_MAX = 4000
        /** Ganztaegig hoechstens 14 Tage (`API.md` 5b). */
        const val GANZTAG_TAGE_MAX = 14
    }
}

/** `PATCH /v1/kalender/hero/{id}`. Nur die Zeit aendert sich, `ende = null` heisst: Dauer bleibt. */
@Serializable
data class HeroTerminVerschieben(
    val beginn: String,
    val ende: String? = null,
    val ganztag: Boolean = false,
)

/** Termin derselben Personen zur selben Zeit. Hinweis, kein Hindernis. */
@Serializable
data class Ueberschneidung(
    val hero_id: Long,
    val titel: String,
    /** Ortszeit `YYYY-MM-DDTHH:MM`. */
    val beginn: String,
    val ende: String,
    /** Betroffene der gewaehlten Personen. */
    val wer: String,
)

@Serializable
data class HeroTerminErgebnis(
    /** [STATUS_GEPRUEFT], [STATUS_ANGELEGT] oder [STATUS_VERSCHOBEN]. */
    val status: String,
    /** Bei `geprueft`: Satz fuer den Bestaetigungsdialog. */
    val beschreibung: String? = null,
    /** Der neue bzw. verschobene Termin, nicht bei `geprueft`. */
    val termin: KalenderTermin? = null,
    val ueberschneidungen: List<Ueberschneidung> = emptyList(),
    /** z. B. HERO gerade nicht lesbar, Ueberschneidungen aus dem Spiegel. */
    val hinweis: String? = null,
) {
    companion object {
        const val STATUS_GEPRUEFT = "geprueft"
        const val STATUS_ANGELEGT = "angelegt"
        const val STATUS_VERSCHOBEN = "verschoben"
    }
}

/** Filterwerte, die kein `hero:<id>` sind. */
const val PERSON_ICH = "ich"
const val PERSON_ALLE = "alle"
/** Fehlerkoerper des Servers. `detail` ist bereits deutscher Klartext fuer die Anzeige. */
@Serializable
data class Fehler(
    val detail: String,
)

// --- GU-Plantafel (`/v1/bauablauf`, seit v1.9.0) ------------------------------

/** Ein GU-Projekt: gemeinsamer Ordner und eigene Plantafel. */
@Serializable
data class GuProjekt(
    val id: Int,
    val name: String,
    /** Pfad in der Ablage, taugt auch als `notiz` fuer Fotos und Videos. */
    val ordner: String,
    /** Erster geplanter Tag `YYYY-MM-DD`, `null` = noch nichts geplant. */
    val von: String? = null,
    val bis: String? = null,
    /** Anzahl Einsaetze. */
    val einsaetze: Int,
)

@Serializable
data class GuProjekte(val projekte: List<GuProjekt> = emptyList())

@Serializable
data class GuProjektNeu(val name: String) {
    companion object {
        /** `maxLength` im Vertrag. */
        const val MAX_NAME = 100
    }
}

/** Eine Zeile der Plantafel. */
@Serializable
data class Gewerk(
    val id: Int,
    val name: String,
    val firma: String,
    val kontakt: String,
    val telefon: String,
    /** `#rrggbb`, Balkenfarbe. */
    val farbe: String,
)

/** Ein Balken der Plantafel, ganztaegig. */
@Serializable
data class Einsatz(
    val id: Int,
    val gewerk: String,
    val gewerk_id: Int,
    val farbe: String,
    val firma: String,
    /** `YYYY-MM-DD`. */
    val von: String,
    /** `YYYY-MM-DD`, **einschliesslich**. */
    val bis: String,
    val was: String,
    val bereich: String,
    val status: String,
    /** Beginnt erst nach diesem Einsatz. */
    val nach: Int? = null,
) {
    companion object {
        const val STATUS_GEPLANT = "geplant"
        const val STATUS_BESTAETIGT = "bestaetigt"
        const val STATUS_FERTIG = "fertig"
        const val STATUS_ABGESAGT = "abgesagt"
    }
}

/** Alle Werte von `Einsatz.status` - ein Test vergleicht sie mit dem Vertrag. */
val EINSATZ_STATUS: List<String> = listOf(
    Einsatz.STATUS_GEPLANT, Einsatz.STATUS_BESTAETIGT, Einsatz.STATUS_FERTIG, Einsatz.STATUS_ABGESAGT,
)

/** Einsatz aus der Gesamtansicht `GET /v1/bauablauf/alle`. */
@Serializable
data class EinsatzMitProjekt(
    val id: Int,
    val gewerk: String,
    val gewerk_id: Int,
    val farbe: String,
    val firma: String,
    val von: String,
    val bis: String,
    val was: String,
    val bereich: String,
    val status: String,
    val nach: Int? = null,
    val projekt_id: Int,
    val projekt: String,
)

@Serializable
data class GuWarnung(
    val art: String,
    /** IDs der betroffenen Einsaetze. */
    val einsaetze: List<Int> = emptyList(),
    val text: String,
) {
    companion object {
        const val ART_REIHENFOLGE = "reihenfolge"
        const val ART_UEBERSCHNEIDUNG = "ueberschneidung"
        const val ART_UNBESTAETIGT = "unbestaetigt"
        const val ART_UEBERFAELLIG = "ueberfaellig"
    }
}

/** Alle Werte von `GuWarnung.art` - ein Test vergleicht sie mit dem Vertrag. */
val GU_WARNUNG_ARTEN: List<String> = listOf(
    GuWarnung.ART_REIHENFOLGE, GuWarnung.ART_UEBERSCHNEIDUNG,
    GuWarnung.ART_UNBESTAETIGT, GuWarnung.ART_UEBERFAELLIG,
)

/** Plantafel eines Projekts. `gewerke` in der Reihenfolge des Servers anzeigen. */
@Serializable
data class GuPlan(
    val projekt: GuProjekt,
    val gewerke: List<Gewerk> = emptyList(),
    val einsaetze: List<Einsatz> = emptyList(),
    val warnungen: List<GuWarnung> = emptyList(),
)

@Serializable
data class GuAlle(
    val projekte: List<GuProjekt> = emptyList(),
    val einsaetze: List<EinsatzMitProjekt> = emptyList(),
)

@Serializable
data class EinsatzNeu(
    /** Name; ein unbekanntes Gewerk legt der Server an. */
    val gewerk: String,
    val von: String,
    val bis: String? = null,
    val was: String? = null,
    val bereich: String? = null,
    val status: String? = null,
    val nach: Int? = null,
)

/**
 * Nur die gesetzten Felder gehen raus (`explicitNulls = false`).
 * Verschieben: neues `von` genuegt, die Dauer bleibt. `nach = 0` loest die Abhaengigkeit.
 */
@Serializable
data class EinsatzAenderung(
    val gewerk: String? = null,
    val von: String? = null,
    val bis: String? = null,
    val was: String? = null,
    val bereich: String? = null,
    val status: String? = null,
    val nach: Int? = null,
    val folgende_mitverschieben: Boolean? = null,
)

@Serializable
data class EinsatzAntwort(
    val status: String,
    val einsatz: Einsatz,
    val warnungen: List<GuWarnung> = emptyList(),
    val mitverschoben: List<Einsatz> = emptyList(),
)

/** Gewerk anlegen oder aendern - Schluessel ist `name`, `neuer_name` benennt um. */
@Serializable
data class GewerkSetzen(
    val name: String,
    val firma: String? = null,
    val kontakt: String? = null,
    val telefon: String? = null,
    val farbe: String? = null,
    val neuer_name: String? = null,
)

@Serializable
data class GewerkAntwort(
    val status: String,
    val gewerk: Gewerk,
)

@Serializable
data class PfadAntwort(val pfad: String)

// --- Anmeldung, seit v1.10.0 ---------------------------------------------------
//
// Stand 2026-09-30: `openapi.json` im Repo ist noch v1.9.0. Die Felder stammen aus
// `API.md` Abschnitt 0 und stimmen mit dem live abgerufenen Vertrag 1.10.0 ueberein.
// Antworten sind trotzdem grosszuegig mit Vorgaben versehen: fehlt ein Feld, das
// die App nicht zwingend braucht, soll die Anmeldung nicht daran scheitern.

/** `POST /v1/anmelden`. Das Passwort lebt nur in diesem Objekt und wird nie gespeichert. */
@Serializable
data class Anmeldung(
    val name: String,
    val passwort: String,
    /** 6-stelliger Code aus der Authenticator-App, gilt nur einmal. */
    val code: String,
    /** Bezeichnung fuer die Geraeteliste, z. B. "Galaxy S25 – App". Vertrag: max. 80 Zeichen. */
    val geraet: String,
) {
    companion object {
        const val CODE_LAENGE = 6
        const val MAX_GERAET = 80
    }
}

@Serializable
data class AnmeldeAntwort(
    /** Sitzungs-Token `cs_…`, ersetzt den alten `ca_`-Zugangsschluessel. */
    val token: String,
    val geraet_id: Int? = null,
    val name: String? = null,
    val anzeigename: String? = null,
    /** Verfaellt nach so vielen Tagen ohne Nutzung. */
    val gueltig_tage: Int? = null,
)

/** Eintrag aus `GET /v1/geraete`. */
@Serializable
data class Geraet(
    val id: Int,
    val bezeichnung: String? = null,
    val ip: String? = null,
    val erstellt: String? = null,
    val zuletzt: String? = null,
    /** `true` = die Sitzung, mit der diese App gerade angemeldet ist. */
    val dieses: Boolean = false,
)

// --- Angebote (`/v1/angebote`, seit v1.11.0) -----------------------------------
//
// `openapi.json` legt fuer die Antworten kein Schema fest (`{}`). Die Felder stammen
// aus `API.md` Abschnitt 4e. Deshalb haben fast alle eine Vorgabe: fehlt etwas, das
// nur der Anzeige dient, soll das Angebot trotzdem aufgehen.
//
// Betraege und Mengen kommen als **String** mit Punkt (`"109.91"`). Zum Rechnen und
// Formatieren in `BigDecimal` umwandeln, nie in `Double`.

/** Eintrag aus `GET /v1/angebote`, neueste zuerst. */
@Serializable
data class AngebotKurz(
    val id: String,
    val titel: String = "",
    /** Anzahl Positionen. */
    val positionen: Int = 0,
    val geaendert: String? = null,
)

/** Hervorhebung einer Position, siehe [MARKIERUNG_ARTEN]. */
@Serializable
data class Markierung(
    val art: String,
    val text: String = "",
)

/**
 * Bekannte Werte von [Markierung.art]: `pruefen` rot, `alternative` gelb (Ersatz
 * fuer einen LV-Artikel), `info` blau. Unbekannte zeigt die App neutral an.
 */
val MARKIERUNG_ARTEN: List<String> = listOf("pruefen", "alternative", "info")

@Serializable
data class AngebotPosition(
    val id: String,
    val nr: String = "",
    /** Artikelnummer beim Grosshaendler bzw. Kuerzel der Pauschale. */
    val art: String = "",
    val menge: String = "",
    val name: String = "",
    val ek: String = "0",
    val ek_gesamt: String = "0",
    val lp: String = "0",
    val bemerkung: String = "",
    /** Nur bei Pauschalen: Verkaufspreis, dort statt `ek` anzeigen. */
    val vk: String? = null,
    val vk_gesamt: String? = null,
    val pauschale: Boolean = false,
    val quelle: String = "",
    val markierung: Markierung? = null,
)

/** Ueberschrift mit Positionen. `ebene` 1 = Bereich, 2 = Unterbereich (eingerueckt). */
@Serializable
data class AngebotGruppe(
    val nr: String = "",
    val titel: String = "",
    val ebene: Int = 1,
    val bereich: String = "",
    val unterbereich: String = "",
    val positionen: List<AngebotPosition> = emptyList(),
)

/** Offene Frage zum Angebot. `positionen` sind IDs, `positionen_nr` die angezeigten Nummern. */
@Serializable
data class AngebotFrage(
    val id: String,
    val text: String,
    val erledigt: Boolean = false,
    val positionen: List<String> = emptyList(),
    val positionen_nr: List<String> = emptyList(),
    val gruppe_nr: String = "",
)

/** Eintrag aus `markiert`: alle markierten Positionen auf einen Blick. `id` ist die Position. */
@Serializable
data class AngebotMarkiert(
    val id: String,
    val nr: String = "",
    val art_nr: String = "",
    val name: String = "",
    val art: String = "",
    val text: String = "",
)

/** `GET /v1/angebote/{id}`. */
@Serializable
data class Angebot(
    val id: String,
    val titel: String = "",
    val bearbeiter: String? = null,
    val version: Int? = null,
    val geaendert: String? = null,
    /** Material im Einkauf; der Aufschlag kommt erst im CRM dazu. */
    val ek_summe: String = "0",
    /** Pauschalen als Verkaufspreis. */
    val pauschalen_summe: String = "0",
    /** Link zur Web-Vorschau, nur anzeigen, wenn gesetzt. */
    val vorschau: String? = null,
    val gruppen: List<AngebotGruppe> = emptyList(),
    /** Seit v1.15.0 nicht mehr angezeigt, `punkte` ersetzt sie. Bleibt fuer aeltere Server lesbar. */
    val fragen: List<AngebotFrage> = emptyList(),
    val markiert: List<AngebotMarkiert> = emptyList(),
    val hinweise: List<String> = emptyList(),
    /** Was ein Mensch entscheiden muss, mit eingebetteten Positionen (seit v1.15.0). */
    val punkte: List<AngebotPunkt> = emptyList(),
    /** Beantwortet, aber vom Assistenten noch nicht eingearbeitet. */
    val beantwortet: List<AngebotBeantwortet> = emptyList(),
)

// --- Entscheidungen, seit v1.15.0 (`API.md` 4e) --------------------------------

/** Betroffene Position, schon mit Name, Menge und Preis. `pauschale`: `gesamt` ist VK, sonst EK. */
@Serializable
data class PunktPosition(
    val id: String,
    val nr: String = "",
    val art_nr: String = "",
    val name: String = "",
    val menge: String = "",
    val ek: String = "0",
    val gesamt: String = "0",
    val pauschale: Boolean = false,
    val bemerkung: String = "",
    val ort: String = "",
)

/** Eine offene Entscheidung. `id` geht unveraendert als `punkt` an `POST …/antworten`. */
@Serializable
data class AngebotPunkt(
    val id: String,
    /** Siehe [PUNKT_ARTEN]. */
    val art: String = "",
    val ort: String = "",
    val text: String = "",
    val positionen: List<PunktPosition> = emptyList(),
    /** Antworten zum Antippen, je ein Knopf. Kann leer sein. */
    val optionen: List<String> = emptyList(),
    /** `true` = zusaetzlich ein Feld "Eigene Antwort …". */
    val frei: Boolean = false,
)

/** `frage` und `pruefen` rot, `alternative` gelb, `hinweis` grau. */
val PUNKT_ARTEN: List<String> = listOf("frage", "pruefen", "alternative", "hinweis")

/** Beantworteter Punkt; `positionen` sind hier nur IDs. */
@Serializable
data class AngebotBeantwortet(
    val id: String,
    val text: String = "",
    val antwort: String = "",
    val positionen: List<String> = emptyList(),
)

/** `POST /v1/angebote/{aid}/antworten`. Antwort ist das ganze Angebot wie bei GET. */
@Serializable
data class AngebotAntwort(
    val punkt: String,
    /** Eine der `optionen`, eigener Text oder [ANTWORT_PASST]. */
    val antwort: String,
)

/** Knopf "Passt so", bei `hinweis` "Ausblenden". */
const val ANTWORT_PASST = "PASST"

/**
 * `GET /v1/chat/{anfrage_id}` (seit v1.17.0, API.md 1b): Stand einer Anfrage, deren
 * Antwort nicht ankam (Funkloch, App beendet). 404 heisst: die Nachricht kam nie an.
 */
@Serializable
data class ChatStatus(
    val anfrage_id: String,
    /** [STATUS_LAEUFT], [STATUS_FERTIG] oder [STATUS_FEHLER]. */
    val status: String,
    val gespraech_id: String? = null,
    /** Nur bei [STATUS_FERTIG]: genau die Antwort von `POST /v1/chat`. */
    val antwort: ChatAntwort? = null,
    /** Nur bei [STATUS_FEHLER]. */
    val fehler: String? = null,
    /** Seit v1.20.0: Ereignisse ab `?seit=`, Zaehler und aktuelle Taetigkeit. */
    val fortschritt: Fortschritt? = null,
) {
    companion object {
        const val STATUS_LAEUFT = "laeuft"
        const val STATUS_FERTIG = "fertig"
        const val STATUS_FEHLER = "fehler"
        /** Seit v1.20.0: vom Nutzer gestoppt, [antwort] ist gesetzt. */
        const val STATUS_ABGEBROCHEN = "abgebrochen"
    }
}

/** Erlaubte Form einer `anfrage_id` laut Vertrag. */
val ANFRAGE_ID_MUSTER = Regex("^[A-Za-z0-9_-]{8,64}$")

// --- Modellwahl (seit v1.20.0, API.md 1c) -----------------------------------

/** Steht nicht in den Listen von [ModellAuswahl] und ist immer erlaubt: der Server waehlt. */
const val MODELL_AUTO = "auto"

/** `GET /v1/modelle`. Die Listen kann der Server aendern, deshalb nie fest einbauen. */
@Serializable
data class ModellAuswahl(
    val modelle: List<ModellEintrag> = emptyList(),
    val denkstufen: List<Denkstufe> = emptyList(),
    /** Was "Automatisch" bedeutet, als Hilfetext. */
    val automatisch: String = "",
    val standard_modell: String = "",
    val arbeits_modell: String? = null,
    val standard_denkaufwand: Denkstufe? = null,
    val arbeits_denkaufwand: Denkstufe? = null,
) {
    fun modell(wert: String?): ModellEintrag? = modelle.firstOrNull { it.wert == wert }

    fun istTeuer(wert: String?): Boolean = modell(wert)?.teuer == true
}

@Serializable
data class ModellEintrag(
    /** Geht an die API. */
    val wert: String,
    /** Wird angezeigt. */
    val name: String,
    /** Abgesetzt und in Warnfarbe zeigen, vor dem Waehlen nachfragen. */
    val teuer: Boolean = false,
    val hinweis: String? = null,
)

@Serializable
data class Denkstufe(
    val wert: String,
    val name: String,
)

/** `PATCH /v1/gespraeche/{gid}`. Nur gesetzte Felder gehen raus (`explicitNulls = false`). */
@Serializable
data class ModellWahl(
    val modell: String? = null,
    val denkaufwand: String? = null,
    /** Seit v1.21.0: oben anheften bzw. loesen. */
    val angeheftet: Boolean? = null,
)

// --- Fortschritt, Stopp, Zwischenruf (seit v1.20.0, API.md 1d) ---------------

@Serializable
data class Fortschritt(
    /** Ab `?seit=`, sonst alle. */
    val ereignisse: List<Ereignis> = emptyList(),
    /** Beim naechsten Abfragen als `?seit=` mitgeben. */
    val naechster: Int = 0,
    /** Modellaufrufe bisher. */
    val schritte: Int = 0,
    /** Werkzeugaufrufe bisher (inkl. Websuche). */
    val werkzeuge: Int = 0,
    /** Was gerade passiert, nur solange die Anfrage laeuft. */
    val aktuell: String? = null,
    val dauer_s: Double = 0.0,
    val gestartet: String? = null,
)

@Serializable
data class Ereignis(
    /** Laufende Nummer je Anfrage, fuer `?seit=`. */
    val nr: Int,
    val zeit: String = "",
    /** Sekunden seit Beginn der Anfrage. */
    val sekunde: Double = 0.0,
    /** Eine der `ART_*`, unbekannte Werte als einfache Zeile zeigen. */
    val art: String,
    val text: String = "",
    val werkzeug: String? = null,
    val ok: Boolean? = null,
    val dauer_ms: Long? = null,
    val modell: String? = null,
    val denkaufwand: String? = null,
    val schritt: Int? = null,
) {
    companion object {
        const val ART_SCHRITT = "schritt"
        const val ART_WERKZEUG = "werkzeug"
        const val ART_WERKZEUG_FERTIG = "werkzeug_fertig"
        const val ART_WECHSEL = "wechsel"
        /** Nie in der Antwort zeigen, vorlesen oder kopieren. */
        const val ART_DENKEN = "denken"
        const val ART_ZWISCHENRUF = "zwischenruf"
        const val ART_ABBRUCH = "abbruch"
        const val ART_ABGEBROCHEN = "abgebrochen"
        const val ART_FEHLER = "fehler"
        const val ART_FERTIG = "fertig"
    }
}

@Serializable
data class Zwischenruf(
    val text: String,
) {
    companion object {
        /** `maxLength` aus dem Vertrag. */
        const val MAX_ZEICHEN = 4000
    }
}

@Serializable
data class ZwischenrufAntwort(
    val angenommen: Boolean = true,
    val nachricht_id: Long,
    val hinweis: String = "",
)

/**
 * Ist ein 422 bei Chat oder Sprache die Ablehnung der Modellwahl? Dann hat sich die
 * Liste auf dem Server geaendert (Auftrag 1.20, Fehlerfaelle). Nur wenn tatsaechlich
 * etwas anderes als "auto" mitging - "auto" ist immer erlaubt -, und nur, wenn
 * `detail` von Modell oder Denkaufwand spricht. Ein 422 aus anderem Grund soll die
 * Auswahl nicht zuruecksetzen.
 */
fun istModellFehler(detail: String, wahl: ModellWahl?): Boolean {
    if (wahl == null) return false
    val festGewaehlt = (wahl.modell ?: MODELL_AUTO) != MODELL_AUTO ||
        (wahl.denkaufwand ?: MODELL_AUTO) != MODELL_AUTO
    if (!festGewaehlt) return false
    return detail.contains("Modell", ignoreCase = true) || detail.contains("Denkaufwand", ignoreCase = true)
}
