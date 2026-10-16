package de.chefassistent.app.monteur.data

import kotlinx.serialization.Serializable

/*
 * DTOs der Monteur-Endpunkte (`/v1/monteur/…`, seit v1.22.0, API.md 5c).
 * Feldnamen unveraendert aus `openapi.json`. Wo der Vertrag ein Feld als Pflicht
 * fuehrt, steht trotzdem ein Vorgabewert, wenn ein Fehlen die App nicht
 * aufhalten soll (`ignoreUnknownKeys`, `coerceInputValues` in [Netzwerk.json]).
 */

// --- Einrichtung -------------------------------------------------------------

@Serializable
data class Einrichtung(
    val code: String,
    val geraet: String = "Monteur-App",
)

@Serializable
data class MonteurSitzung(
    val token: String,
    val geraet_id: Int = 0,
    val name: String = "",
    val anzeigename: String = "",
    val gueltig_tage: Int = 0,
)

// --- Heute -------------------------------------------------------------------

@Serializable
data class MonteurTag(
    val datum: String,
    val wochentag: String = "",
    val termine: List<TagTermin> = emptyList(),
)

@Serializable
data class TagTermin(
    /** `hero:<id>`, geht als `termin_id` an `POST /v1/monteur/berichte`. */
    val id: String,
    val titel: String = "",
    /** Ortszeit ISO, ganztaegig nur `YYYY-MM-DD`. */
    val beginn: String = "",
    val ende: String = "",
    val ganztag: Boolean = false,
    val kategorie: String = "",
    val beschreibung: String? = null,
    val kollegen: List<String> = emptyList(),
    val projekt: TagProjekt? = null,
    /** Leer = „Bericht fehlt“. */
    val berichte: List<BerichtKurz> = emptyList(),
)

@Serializable
data class TagProjekt(
    val id: Int,
    val nr: String = "",
    val titel: String = "",
    val kunde: String = "",
    val adresse: String = "",
    val geo: Geo? = null,
    val telefon: List<Telefon> = emptyList(),
)

@Serializable
data class Geo(val lat: Double, val lng: Double)

@Serializable
data class Telefon(
    val nummer: String,
    val name: String = "",
    val art: String = "",
)

@Serializable
data class BerichtKurz(
    val id: String,
    val status: String,
    val hero_nr: String? = null,
)

// --- Projektmappe --------------------------------------------------------------

@Serializable
data class Projektmappe(
    val id: Int,
    val nr: String = "",
    val titel: String = "",
    val kunde: String = "",
    val adresse: String = "",
    val geo: Geo? = null,
    val telefon: List<Telefon> = emptyList(),
    /** Notizfeld des Projekts in HERO, leer = keine. */
    val notizen: String = "",
    val unterlagen: List<Unterlage> = emptyList(),
    val fotos: List<Foto> = emptyList(),
    /** Seit 1.22.0: Videos bleiben auf dem Server (HERO nimmt keine). */
    val videos: List<Video> = emptyList(),
    /** Seit 1.23.0: Kommentare im HERO-Projektverlauf, neueste zuerst. */
    val verlauf: List<Verlaufseintrag> = emptyList(),
)

@Serializable
data class Video(
    /** Fuer `GET /v1/monteur/projekte/{id}/videos/{name}`. */
    val name: String,
    val groesse_mb: Double = 0.0,
    val datum: String = "",
)

/** Antwort auf `POST /v1/monteur/projekte/{id}/dateien`. */
@Serializable
data class Hochgeladen(
    /** foto | dokument | video */
    val art: String = "",
    val name: String = "",
    /** hero = kommt in wenigen Minuten ins HERO-Projekt, server = bleibt im Projektordner. */
    val ziel: String = "",
    val hinweis: String = "",
)

@Serializable
data class Unterlage(
    /** Angebot, Auftragsbestätigung, Aufmaß, Unterlage, Dokument, Bericht … */
    val art: String,
    val nr: String? = null,
    val name: String,
    val datum: String? = null,
    val status: String = "",
    /** uuid fuer `GET /v1/monteur/dateien/{uuid}`, `null` = PDF noch nicht im Archiv. */
    val datei: String? = null,
)

@Serializable
data class Foto(
    val uuid: String,
    val name: String = "",
    val datum: String? = null,
)

// --- Bericht -------------------------------------------------------------------

@Serializable
data class MonteurBericht(
    val id: String,
    /** `entwurf` bzw. `fertig`. */
    val status: String,
    val datum: String = "",
    val termin_id: String? = null,
    val projekt: BerichtProjekt? = null,
    val diktat: String = "",
    val entwurf: Entwurf = Entwurf(),
    val hero_id: Int? = null,
    /** z. B. BER-1330. */
    val hero_nr: String? = null,
    /** Nach dem Abschliessen: was nicht ganz geklappt hat. */
    val hinweise: List<String> = emptyList(),
    val erstellt: String = "",
    val geaendert: String = "",
) {
    val istFertig: Boolean get() = status == STATUS_FERTIG

    companion object {
        const val STATUS_ENTWURF = "entwurf"
        const val STATUS_FERTIG = "fertig"
    }
}

@Serializable
data class BerichtProjekt(
    val id: Int,
    val nr: String = "",
)

@Serializable
data class Entwurf(
    /** Text „Ausgeführte Arbeiten“. */
    val taetigkeit: String = "",
    val anstehend: List<String> = emptyList(),
    val monteur_stunden: Double = 0.0,
    val helfer_stunden: Double = 0.0,
    val anfahrt: Boolean = false,
    val kleinmaterial: Boolean = false,
    val material: List<Material> = emptyList(),
    val zeiten: List<ZeitAbschnitt> = emptyList(),
    val hinweise_buero: List<String> = emptyList(),
    val rueckfragen: List<String> = emptyList(),
)

@Serializable
data class Material(
    /** Wie diktiert. */
    val text: String,
    val menge: Double = 1.0,
    val einheit: String = "",
    val suchtext: String = "",
    /** Gewaehlter Katalogartikel, `null` = freie Position. */
    val artikel: Artikel? = null,
    val vorschlaege: List<Artikel> = emptyList(),
    /** Unklar (z. B. welche Dimension) - vor dem Abschliessen klaeren. */
    val rueckfrage: String? = null,
)

@Serializable
data class Artikel(
    val nr: String,
    val name: String,
    val einheit: String = "",
)

/** Zeiten im Entwurf: `HH:MM`. */
@Serializable
data class ZeitAbschnitt(
    val beginn: String,
    val ende: String,
    val art: String,
)

/** `PATCH /v1/monteur/berichte/{id}`. Nur gesetzte Felder gehen raus (`explicitNulls = false`). */
@Serializable
data class BerichtAenderung(
    val taetigkeit: String? = null,
    val anstehend: List<String>? = null,
    val monteur_stunden: Double? = null,
    val helfer_stunden: Double? = null,
    val anfahrt: Boolean? = null,
    val kleinmaterial: Boolean? = null,
    /** Vollstaendige neue Liste, gebaut mit [materialJson]. */
    val material: kotlinx.serialization.json.JsonArray? = null,
    val zeiten: List<ZeitAbschnitt>? = null,
    val hinweise_buero: List<String>? = null,
    val rueckfragen: List<String>? = null,
)

/**
 * Eine Position der neuen Materialliste fuer [BerichtAenderung.material].
 * [index] behaelt die Vorschlaege der bisherigen Position, `null` = neu.
 *
 * Kein `@Serializable`: `artikel_nr` hat drei Bedeutungen (weglassen = Wahl
 * behalten, Nummer = Vorschlag waehlen, `null` = freie Position), und mit
 * `explicitNulls = false` liesse sich das ausdrueckliche `null` nicht senden.
 * Den Koerper baut [materialJson].
 */
data class MaterialAenderung(
    val index: Int?,
    val menge: Double,
    val text: String? = null,
    val einheit: String? = null,
    val artikel: ArtikelWahl = ArtikelWahl.Behalten,
)

sealed interface ArtikelWahl {
    data object Behalten : ArtikelWahl
    data object Frei : ArtikelWahl
    data class Waehlen(val nr: String) : ArtikelWahl
}

fun materialJson(liste: List<MaterialAenderung>): kotlinx.serialization.json.JsonArray =
    kotlinx.serialization.json.buildJsonArray {
        liste.forEach { m ->
            add(kotlinx.serialization.json.buildJsonObject {
                m.index?.let { put("index", kotlinx.serialization.json.JsonPrimitive(it)) }
                put("menge", kotlinx.serialization.json.JsonPrimitive(m.menge))
                m.text?.let { put("text", kotlinx.serialization.json.JsonPrimitive(it)) }
                m.einheit?.let { put("einheit", kotlinx.serialization.json.JsonPrimitive(it)) }
                when (val w = m.artikel) {
                    ArtikelWahl.Behalten -> Unit
                    ArtikelWahl.Frei -> put("artikel_nr", kotlinx.serialization.json.JsonNull)
                    is ArtikelWahl.Waehlen -> put("artikel_nr", kotlinx.serialization.json.JsonPrimitive(w.nr))
                }
            })
        }
    }

@Serializable
data class Abschluss(
    /** PNG als base64 (auch data:-URL), `null` = ohne Unterschrift. */
    val unterschrift_png: String? = null,
    val unterschrift_name: String? = null,
    val zeiten_buchen: Boolean = true,
)

// --- Zeiten --------------------------------------------------------------------

@Serializable
data class Tageszeiten(
    val datum: String,
    val eintraege: List<Zeiteintrag> = emptyList(),
    val arbeit_minuten: Int = 0,
    val pause_minuten: Int = 0,
    /** `true` = eingereicht, nur noch im Buero aenderbar. */
    val eingereicht: Boolean = false,
    /** Laufende Stempeluhr. */
    val stempel: Stempel? = null,
)

@Serializable
data class Zeiteintrag(
    val id: Int,
    val beginn: String,
    val ende: String,
    /** arbeit | fahrt | pause | material | buero | vor_ort | sonstige */
    val art: String,
    /** neu | eingereicht | bestaetigt */
    val status: String,
    val projekt_nr: String? = null,
    val projekt_id: Int? = null,
    val kommentar: String = "",
    val minuten: Int = 0,
) {
    val aenderbar: Boolean get() = status == "neu"
}

@Serializable
data class Stempel(
    val beginn: String,
    val art: String,
    val projekt_id: Int? = null,
    val kommentar: String = "",
)

@Serializable
data class ZeitNeu(
    /** Ortszeit, z. B. 2026-10-20T07:30. */
    val beginn: String,
    val ende: String,
    val art: String,
    val projekt_nr: String? = null,
    val projekt_id: Int? = null,
    val kommentar: String = "",
    val anfrage_id: String? = null,
)

@Serializable
data class ZeitAenderung(
    val beginn: String? = null,
    val ende: String? = null,
    val art: String? = null,
    val projekt_nr: String? = null,
    val kommentar: String? = null,
)

@Serializable
data class StempelAktion(
    /** start | stopp | wechsel */
    val aktion: String,
    val art: String = ART_ARBEIT,
    val projekt_nr: String? = null,
    val projekt_id: Int? = null,
    val kommentar: String = "",
)

@Serializable
data class Stundenkonto(
    val von: String,
    val bis: String,
    val soll_minuten: Int = 0,
    val bestaetigt_minuten: Int = 0,
    val eingereicht_minuten: Int = 0,
    val saldo_minuten: Int = 0,
)

/** Arten aus `ZeitAenderung.art` (enum im Vertrag). Seit 1.23.0 mit `lager` (Arbeit ohne Projekt). */
const val ART_ARBEIT = "arbeit"
const val ART_LAGER = "lager"
val ZEIT_ARTEN = listOf("arbeit", "lager", "fahrt", "pause", "material", "buero", "vor_ort")

/** Was der Monteur selbst anlegen darf (Auftrag 1.23, 6.); `buero`, `vor_ort` nur anzeigen. */
val ZEIT_ARTEN_ANLEGEN = listOf("lager", "fahrt", "arbeit", "material", "pause")

// --- Urlaub --------------------------------------------------------------------

@Serializable
data class Abwesenheiten(
    val jahr: Int,
    val stand: Urlaubsstand = Urlaubsstand(),
    val eintraege: List<Abwesenheit> = emptyList(),
)

@Serializable
data class Urlaubsstand(
    val jahresurlaub: Double? = null,
    val genehmigt: Double? = null,
    val beantragt: Double? = null,
    val verfuegbar: Double? = null,
)

@Serializable
data class Abwesenheit(
    val id: Int,
    val art: String,
    /** entwurf | beantragt | genehmigt | abgelehnt */
    val status: String,
    val von: String,
    val bis: String,
    val halber_tag_anfang: Boolean = false,
    val halber_tag_ende: Boolean = false,
    val kommentar: String = "",
    val tage: Double? = null,
)

@Serializable
data class AbwesenheitNeu(
    val art: String,
    val von: String,
    val bis: String,
    val halber_tag_anfang: Boolean = false,
    val halber_tag_ende: Boolean = false,
    val kommentar: String = "",
    val anfrage_id: String? = null,
)

/** Arten aus API.md 5c. Krank/Kind krank nur ueber „Krank melden“. */
val URLAUB_ARTEN = listOf("urlaub", "ueberstunden", "sonderurlaub", "unbezahlt")
val KRANK_ARTEN = listOf("krank", "kind_krank")

// --- Verstehen -----------------------------------------------------------------

@Serializable
data class Verstanden(
    val text: String = "",
    val datum: String = "",
    /** Vorschlaege (HH:MM am [datum]), erst mit `POST /v1/monteur/zeiten` buchen. */
    val zeiten: List<ZeitAbschnitt> = emptyList(),
    val abwesenheit: AbwVorschlag? = null,
    val rueckfrage: String? = null,
)

@Serializable
data class AbwVorschlag(
    val art: String,
    val von: String,
    val bis: String,
    val halber_tag_anfang: Boolean = false,
    val halber_tag_ende: Boolean = false,
    val kommentar: String = "",
)

// --- Eingang: eine Karte fuer alles (seit 1.23.0, API.md 5d) ------------------------

/**
 * Antwort von `POST /v1/monteur/eingang`: die KI erkennt die Absicht ([art]) und
 * fuellt **genau eines** der Felder [bericht], [antwort], [bestellung], [meldung],
 * [zeiten], [abwesenheit]. Bestaetigt wird immer ueber `…/passt`.
 */
@Serializable
data class Karte(
    val id: String,
    /** bericht | frage | bestellung | meldung | zeiten | urlaub */
    val art: String,
    /** offen | erledigt | verworfen */
    val status: String = KARTE_OFFEN,
    val datum: String = "",
    /** Erkannter bzw. gesendeter Text. */
    val text: String = "",
    val termin_id: String? = null,
    val projekt: KarteProjekt? = null,
    /** Anzahl mitgeschickter Fotos. */
    val fotos: Int = 0,
    val bericht: MonteurBericht? = null,
    val antwort: Antwort? = null,
    val bestellung: Bestellung? = null,
    val meldung: Meldung? = null,
    val zeiten: List<ZeitAbschnitt>? = null,
    val abwesenheit: AbwVorschlag? = null,
    /** Frage der KI an den Monteur, gold markiert. */
    val rueckfrage: String? = null,
    /** Nach „Passt“: hero_nr / hero / gebucht / abwesenheit_id, hinweise[]. Frei geformt. */
    val ergebnis: kotlinx.serialization.json.JsonObject? = null,
) {
    val istOffen: Boolean get() = status == KARTE_OFFEN
    val istErledigt: Boolean get() = status == KARTE_ERLEDIGT

    /** `ergebnis.hinweise` - immer anzeigen, wenn nicht leer. */
    val hinweise: List<String>
        get() = (ergebnis?.get("hinweise") as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }.orEmpty()

    /** Ein Textfeld aus [ergebnis], z. B. `hero_nr` oder `hero`. */
    fun ergebnisText(feld: String): String? =
        (ergebnis?.get(feld) as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }

    companion object {
        const val ART_BERICHT = "bericht"
        const val ART_FRAGE = "frage"
        const val ART_BESTELLUNG = "bestellung"
        const val ART_MELDUNG = "meldung"
        const val ART_ZEITEN = "zeiten"
        const val ART_URLAUB = "urlaub"
    }
}

const val KARTE_OFFEN = "offen"
const val KARTE_ERLEDIGT = "erledigt"
const val KARTE_VERWORFEN = "verworfen"

@Serializable
data class KarteProjekt(
    val id: Int,
    val nr: String = "",
    val kunde: String = "",
)

@Serializable
data class Antwort(
    val text: String = "",
    val quellen: List<Quelle> = emptyList(),
    val anleitungen: List<AnleitungTreffer> = emptyList(),
    /** Katalogartikel, ohne Preise. */
    val artikel: List<Artikel> = emptyList(),
)

@Serializable
data class Quelle(val titel: String = "", val url: String)

@Serializable
data class AnleitungTreffer(
    /** Fuer `GET /v1/monteur/anleitung?pfad=`. */
    val pfad: String,
    val seite: Int = 1,
    val titel: String = "",
)

@Serializable
data class Bestellung(
    val material: List<Material> = emptyList(),
    /** Termin, Dringlichkeit, Lieferort, wenn gesagt. */
    val hinweis: String = "",
)

@Serializable
data class Meldung(
    /** nicht_angetroffen | verspaetung | mangel | nachtrag | sonstiges */
    val art: String = "sonstiges",
    /** Von der KI formuliert, kommt so in den HERO-Projektverlauf. */
    val text: String = "",
)

/**
 * `POST /v1/monteur/eingang/{id}/passt`. Nur Geaendertes schicken
 * (`explicitNulls = false`). [material] wie beim Bericht ueber [materialJson].
 */
@Serializable
data class Passt(
    val unterschrift_png: String? = null,
    val unterschrift_name: String? = null,
    val zeiten_buchen: Boolean? = null,
    val text: String? = null,
    val material: kotlinx.serialization.json.JsonArray? = null,
    val zeiten: List<ZeitAbschnitt>? = null,
    val abwesenheit: AbwVorschlag? = null,
)

/** Eintrag im HERO-Projektverlauf (Kommentare von Buero und Monteuren). */
@Serializable
data class Verlaufseintrag(
    val datum: String? = null,
    /** Oft leer - dann ist die erste Zeile von [text] die Ueberschrift. */
    val titel: String = "",
    val text: String = "",
)
