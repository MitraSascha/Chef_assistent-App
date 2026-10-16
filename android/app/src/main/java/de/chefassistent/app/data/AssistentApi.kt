package de.chefassistent.app.data

import kotlinx.serialization.json.JsonElement
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * 1:1-Abbild der Endpunkte aus `openapi.json`. Keine Bequemlichkeiten, keine
 * Umbenennungen - wer etwas vermisst, schaut in den Vertrag, nicht hierher.
 *
 * Aufrufe werfen bei Fehlern [ApiFehler]; dafuer sorgt der Interceptor in [Netzwerk].
 */
interface AssistentApi {

    /** Erreichbarkeit. Laut `API.md` ohne Anmeldung - der Vertrag listet trotzdem 401. */
    @GET("v1/gesund")
    suspend fun gesund(): Response<Unit>

    // --- Anmeldung, seit v1.10.0 ---

    /** Name + Passwort + Code -> Sitzungs-Token. Geht ohne Bearer raus, siehe [Netzwerk]. */
    @POST("v1/anmelden")
    suspend fun anmelden(@Body anmeldung: Anmeldung): AnmeldeAntwort

    /**
     * Beendet die Sitzung auf dem Server (204). Das Token wird hier ausdruecklich
     * uebergeben, weil es lokal zu diesem Zeitpunkt schon geloescht sein kann.
     */
    @POST("v1/abmelden")
    suspend fun abmelden(@Header("Authorization") bearer: String): Response<Unit>

    @GET("v1/geraete")
    suspend fun geraete(): List<Geraet>

    /** Meldet ein Geraet ab (204), z. B. ein verlorenes Handy. */
    @DELETE("v1/geraete/{gid}")
    suspend fun geraetAbmelden(@Path("gid") gid: Int): Response<Unit>

    @GET("v1/ich")
    suspend fun ich(): Ich

    @POST("v1/chat")
    suspend fun chat(@Body anfrage: ChatAnfrage): ChatAntwort

    /**
     * Seit v1.17.0: Stand einer Anfrage, deren Antwort nicht ankam (API.md 1b).
     * Seit v1.20.0 mit `seit` fuer den Fortschritt (API.md 1d): nur Ereignisse ab dieser Nummer.
     */
    @GET("v1/chat/{anfrage_id}")
    suspend fun chatStatus(
        @Path("anfrage_id") anfrageId: String,
        @Query("seit") seit: Int? = null,
    ): ChatStatus

    /** Seit v1.20.0: Stopp. Wirkt am naechsten Schritt, 409 = schon fertig. */
    @POST("v1/chat/{anfrage_id}/abbrechen")
    suspend fun chatAbbrechen(@Path("anfrage_id") anfrageId: String): ChatStatus

    /** Seit v1.20.0: Nachtrag waehrend einer laufenden Antwort. 409 = schon fertig. */
    @POST("v1/chat/{anfrage_id}/zwischenruf")
    suspend fun zwischenruf(
        @Path("anfrage_id") anfrageId: String,
        @Body zwischenruf: Zwischenruf,
    ): ZwischenrufAntwort

    /** Seit v1.20.0: waehlbare Modelle und Denkstufen. Ein alter Server meldet 404. */
    @GET("v1/modelle")
    suspend fun modelle(): ModellAuswahl

    @Multipart
    @POST("v1/sprache")
    suspend fun sprache(
        @Part audio: MultipartBody.Part,
        @Part("gespraech_id") gespraechId: RequestBody? = null,
        @Part("antworten") antworten: RequestBody? = null,
        /** Seit v1.17.0, siehe [ChatAnfrage.anfrage_id]. */
        @Part("anfrage_id") anfrageId: RequestBody? = null,
        /** Seit v1.20.0, siehe [ChatAnfrage.modell]. */
        @Part("modell") modell: RequestBody? = null,
        @Part("denkaufwand") denkaufwand: RequestBody? = null,
    ): SpracheAntwort

    /** Ein Foto pro Anfrage. JPEG, PNG oder WebP - HEIC ergibt 415. */
    @Multipart
    @POST("v1/fotos")
    suspend fun foto(
        @Part foto: MultipartBody.Part,
        @Part("notiz") notiz: RequestBody? = null,
        @Part("text") text: RequestBody? = null,
        @Part("gespraech_id") gespraechId: RequestBody? = null,
    ): FotoAntwort

    /** Ein Video pro Anfrage. MP4 oder MOV, max. 1 GB und 30 Minuten. */
    @Multipart
    @POST("v1/videos")
    suspend fun video(
        @Part video: MultipartBody.Part,
        @Part("notiz") notiz: RequestBody? = null,
        @Part("text") text: RequestBody? = null,
        @Part("abschreiben") abschreiben: RequestBody? = null,
        @Part("gespraech_id") gespraechId: RequestBody? = null,
    ): VideoAntwort

    /**
     * Ein Dokument pro Anfrage (seit v1.12.0): PDF, DOCX, XLSX/XLSM, TXT, MD, CSV,
     * TSV, EML, max. 50 MB. Den Typ erkennt der Server am Dateinamen - der muss
     * die Endung tragen.
     */
    @Multipart
    @POST("v1/dokumente")
    suspend fun dokument(
        @Part dokument: MultipartBody.Part,
        @Part("notiz") notiz: RequestBody? = null,
        @Part("text") text: RequestBody? = null,
        @Part("gespraech_id") gespraechId: RequestBody? = null,
    ): DokumentAntwort

    @Streaming
    @POST("v1/vorlesen")
    suspend fun vorlesen(@Body anfrage: VorlesenAnfrage): ResponseBody

    /** Eigene Hintergrundauftraege, neueste zuerst. */
    @GET("v1/auftraege")
    suspend fun auftraege(): List<Auftrag>

    @GET("v1/auftraege/{aid}")
    suspend fun auftrag(@Path("aid") aid: String): Auftrag

    @POST("v1/auftraege/{aid}/abbrechen")
    suspend fun auftragAbbrechen(@Path("aid") aid: String): Auftrag

    /** Nur bei `fehler`/`abgebrochen`, sonst 409. Bereits Ausgewertetes wird wiederverwendet. */
    @POST("v1/auftraege/{aid}/wiederholen")
    suspend fun auftragWiederholen(@Path("aid") aid: String): Auftrag

    /** "Was ist offen?" - ohne `person` die eigene Uebersicht. 404 = unbekannte Person. */
    @GET("v1/aufgaben")
    suspend fun aufgaben(@Query("person") person: String? = null): AufgabenUebersicht

    /** Abhaken, zuruecknehmen, streichen, "noch aktuell", Frist. Danach neu laden - die ID kann sich aendern. */
    @POST("v1/aufgaben/{aid}")
    suspend fun aufgabeAendern(@Path("aid") aid: String, @Body aenderung: AufgabeAenderung): AufgabeAntwort

    // --- GU-Plantafel, seit v1.9.0 ---

    @GET("v1/bauablauf")
    suspend fun guProjekte(): GuProjekte

    /** Legt den gemeinsamen Ordner `GU-Projekte/<name>` an. */
    @POST("v1/bauablauf")
    suspend fun guProjektNeu(@Body neu: GuProjektNeu): GuProjekt

    @GET("v1/bauablauf/alle")
    suspend fun guAlle(): GuAlle

    @GET("v1/bauablauf/{pid}")
    suspend fun guPlan(@Path("pid") pid: Int): GuPlan

    @POST("v1/bauablauf/{pid}/einsaetze")
    suspend fun einsatzNeu(@Path("pid") pid: Int, @Body neu: EinsatzNeu): EinsatzAntwort

    @PATCH("v1/bauablauf/einsaetze/{eid}")
    suspend fun einsatzAendern(@Path("eid") eid: Int, @Body aenderung: EinsatzAenderung): EinsatzAntwort

    /** Antwort ohne festes Schema - wird nicht ausgewertet. */
    @DELETE("v1/bauablauf/einsaetze/{eid}")
    suspend fun einsatzLoeschen(@Path("eid") eid: Int): JsonElement

    @PUT("v1/bauablauf/{pid}/gewerke")
    suspend fun gewerkSetzen(@Path("pid") pid: Int, @Body gewerk: GewerkSetzen): GewerkAntwort

    /** Nur ohne Einsaetze, sonst 422. */
    @DELETE("v1/bauablauf/gewerke/{gid}")
    suspend fun gewerkLoeschen(@Path("gid") gid: Int): JsonElement

    /** Bauzeitenplan als PDF in den Projektordner, `pfad` fuer `GET /v1/datei`. */
    @POST("v1/bauablauf/{pid}/pdf")
    suspend fun guPdf(@Path("pid") pid: Int): PfadAntwort

    // --- Angebote, seit v1.11.0 ---

    /** Angebote aus dem Angebotsgenerator, neueste zuerst. 503 = auf diesem Server nicht eingerichtet. */
    @GET("v1/angebote")
    suspend fun angebote(): List<AngebotKurz>

    @GET("v1/angebote/{aid}")
    suspend fun angebot(@Path("aid") aid: String): Angebot

    /** Entscheidung beantworten (seit v1.15.0). Antwort: das aktualisierte Angebot. 404 = Punkt unbekannt. */
    @POST("v1/angebote/{aid}/antworten")
    suspend fun angebotAntworten(@Path("aid") aid: String, @Body anfrage: AngebotAntwort): Angebot

    /** UGL-Datei fuer den CRM-Import. `Response`, weil der Dateiname in `Content-Disposition` steht. */
    @Streaming
    @GET("v1/angebote/{aid}/ugl")
    suspend fun angebotUgl(
        @Path("aid") aid: String,
        @Query("titelzeilen") titelzeilen: Boolean? = null,
    ): Response<ResponseBody>

    @GET("v1/bestaetigungen")
    suspend fun offeneBestaetigungen(): List<OffeneBestaetigung>

    @POST("v1/bestaetigungen/{bid}")
    suspend fun entscheiden(
        @Path("bid") bid: String,
        @Body entscheidung: Entscheidung,
    ): EntscheidungAntwort

    /** `q` seit v1.21.0: Suche im Titel ueber alle Gespraeche, alle Woerter muessen vorkommen. */
    @GET("v1/gespraeche")
    suspend fun gespraeche(@Query("q") q: String? = null): List<GespraechKurz>

    @GET("v1/gespraeche/{gid}")
    suspend fun verlauf(@Path("gid") gid: String): List<Nachricht>

    /** Seit v1.20.0: Modell und Denkaufwand des Gespraechs speichern. */
    @PATCH("v1/gespraeche/{gid}")
    suspend fun gespraechEinstellen(@Path("gid") gid: String, @Body wahl: ModellWahl): GespraechKurz

    @DELETE("v1/gespraeche/{gid}")
    suspend fun gespraechLoeschen(@Path("gid") gid: String): Response<Unit>

    /** `von` ist Pflicht. `bis` ist einschliesslich. */
    @GET("v1/termine")
    suspend fun termine(
        @Query("von") von: String,
        @Query("bis") bis: String? = null,
        @Query("person") person: String? = null,
        @Query("alle") alle: Boolean? = null,
    ): Termine

    /** Seit v1.16.0: HERO-Termine plus eigene. `personen` kommagetrennt (`ich`, `hero:<id>`, `alle`). */
    @GET("v1/kalender")
    suspend fun kalender(
        @Query("von") von: String,
        @Query("bis") bis: String? = null,
        @Query("personen") personen: String? = null,
    ): KalenderAntwort

    /** Personen fuer den Kalenderfilter. 503 = HERO-Spiegel fehlt. */
    @GET("v1/personen")
    suspend fun personen(): Personen

    /** Seit v1.18.0: HERO-Terminkategorien, nach Name sortiert. 503 = nicht eingerichtet. */
    @GET("v1/kalender/kategorien")
    suspend fun kalenderKategorien(): List<TerminKategorie>

    /** Seit v1.18.0: HERO-Termin anlegen. Erst mit `nur_pruefen=true`, dann ohne. */
    @POST("v1/kalender/hero")
    suspend fun heroTerminAnlegen(
        @Body termin: HeroTerminNeu,
        @Query("nur_pruefen") nurPruefen: Boolean = false,
    ): HeroTerminErgebnis

    /** Seit v1.18.0: HERO-Termin zeitlich verschieben, Ablauf wie beim Anlegen. */
    @PATCH("v1/kalender/hero/{hero_id}")
    suspend fun heroTerminVerschieben(
        @Path("hero_id") heroId: Long,
        @Body termin: HeroTerminVerschieben,
        @Query("nur_pruefen") nurPruefen: Boolean = false,
    ): HeroTerminErgebnis

    /** Eigene Notizen und Ordner - Auswahl, wohin Fotos und Videos gehen. */
    @GET("v1/notizen")
    suspend fun notizen(): Notizen

    /** Ordnerinhalt. Ist `pfad` eine Datei, kommt ihr Ordner. */
    @GET("v1/ordner")
    suspend fun ordner(
        @Query("pfad") pfad: String = "",
        /** `name`, `name_ab` oder `geaendert`, seit v1.13.0. */
        @Query("sortierung") sortierung: String? = null,
        @Query("ab") ab: Int? = null,
        @Query("anzahl") anzahl: Int? = null,
    ): Ordner

    /**
     * Suche ueber die ganze Ablage (seit v1.13.0). `ordner` begrenzt auf einen
     * Ordner samt Unterordnern, leer = alles. `typ`: `alle`, `ordner`, `datei`.
     */
    @GET("v1/suche")
    suspend fun suche(
        @Query("q") q: String,
        @Query("ordner") ordner: String? = null,
        @Query("typ") typ: String? = null,
        @Query("max") max: Int? = null,
    ): SuchErgebnis

    /** HERO-Projekt anheften: Mappe holen, beim ersten Mal anlegen (seit v1.19.0). */
    @POST("v1/hero-mappe")
    suspend fun heroMappeOeffnen(@Body anfrage: HeroMappeAnfrage): HeroMappe

    /** Wie [heroMappeOeffnen], legt aber nichts an. 404, solange es keine Mappe gibt. */
    @GET("v1/hero-mappe")
    suspend fun heroMappeStand(@Query("projekt") projekt: String): HeroMappe

    @Streaming
    @GET("v1/datei")
    suspend fun datei(
        @Query("pfad") pfad: String,
        /** `true` = inline statt Download. Nur zum Ansehen in der App (Auftrag 1.21). */
        @Query("anzeigen") anzeigen: Boolean? = null,
    ): ResponseBody

    /**
     * Token-Verbrauch je Monat.
     *
     * `openapi.json` legt fuer diese Antwort **kein** Schema fest (`{}`), deshalb
     * hier bewusst [JsonElement] statt eines ausgedachten Datentyps. Die Anzeige
     * stellt dar, was kommt. Sobald der Server ein Schema nachliefert, kann das
     * hier durch einen echten Typ ersetzt werden.
     */
    @GET("v1/verbrauch")
    suspend fun verbrauch(): JsonElement
}
