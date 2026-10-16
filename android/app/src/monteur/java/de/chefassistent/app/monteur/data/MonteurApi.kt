package de.chefassistent.app.monteur.data

import de.chefassistent.app.data.Ich
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/** Endpunkte der Monteur-App (API.md 5c). Monteure erreichen nur diese und `/v1/ich`. */
interface MonteurApi {

    @GET("v1/ich")
    suspend fun ich(): Ich

    @POST("v1/abmelden")
    suspend fun abmelden(): Response<Unit>

    /** Ohne Anmeldung: Code aus dem QR-Code gegen ein `cs_`-Token. */
    @POST("v1/monteur/einrichten")
    suspend fun einrichten(@Body einrichtung: Einrichtung): MonteurSitzung

    @GET("v1/monteur/tage")
    suspend fun tage(@Query("von") von: String? = null, @Query("bis") bis: String? = null): List<MonteurTag>

    @GET("v1/monteur/projekte/{pid}")
    suspend fun projekt(@Path("pid") id: Int): Projektmappe

    /** Foto, Video oder Dokument vom Handy ins Projekt (eine Datei je Aufruf). */
    @Multipart
    @POST("v1/monteur/projekte/{pid}/dateien")
    suspend fun hochladen(
        @Path("pid") projektId: Int,
        @Part datei: MultipartBody.Part,
        @Part("text") text: RequestBody? = null,
        @Part("anfrage_id") anfrageId: RequestBody? = null,
    ): Hochgeladen

    @Streaming
    @GET("v1/monteur/dateien/{uuid}")
    suspend fun datei(@Path("uuid") uuid: String, @Query("vorschau") vorschau: Boolean? = null): Response<ResponseBody>

    /** Artikelstamm (Gut Mollenhauer), ohne Preise. Unter 2 Zeichen leere Liste. */
    @GET("v1/monteur/artikel")
    suspend fun artikel(@Query("q") q: String, @Query("max") max: Int? = null): List<Artikel>

    // --- Eingang: eine Karte fuer alles (1.23.0) ---

    @Multipart
    @POST("v1/monteur/eingang")
    suspend fun eingang(
        @Part audio: MultipartBody.Part? = null,
        @Part("text") text: RequestBody? = null,
        @Part fotos: List<MultipartBody.Part> = emptyList(),
        @Part("termin_id") terminId: RequestBody? = null,
        @Part("projekt_nr") projektNr: RequestBody? = null,
        @Part("datum") datum: RequestBody? = null,
        @Part("bericht_id") berichtId: RequestBody? = null,
        @Part("bezug_id") bezugId: RequestBody? = null,
        @Part("anfrage_id") anfrageId: RequestBody? = null,
    ): Karte

    @GET("v1/monteur/eingang")
    suspend fun karten(): List<Karte>

    @GET("v1/monteur/eingang/{eid}")
    suspend fun karte(@Path("eid") id: String): Karte

    @POST("v1/monteur/eingang/{eid}/passt")
    suspend fun passt(@Path("eid") id: String, @Body passt: Passt): Karte

    @DELETE("v1/monteur/eingang/{eid}")
    suspend fun karteVerwerfen(@Path("eid") id: String): Response<Unit>

    /** Protokoll als PDF: vor „Passt“ Vorschau, danach das an HERO geschickte (1.25.0). */
    @Streaming
    @GET("v1/monteur/eingang/{eid}/protokoll.pdf")
    suspend fun protokollPdf(@Path("eid") id: String): Response<ResponseBody>

    /** Alle Protokollvorlagen (leer) fuer den Vorlagenwechsel. */
    @GET("v1/monteur/protokolle/vorlagen")
    suspend fun protokollVorlagen(): List<Protokoll>

    /** Herstelleranleitung (PDF) aus unserer Bibliothek. */
    @Streaming
    @GET("v1/monteur/anleitung")
    suspend fun anleitung(@Query("pfad") pfad: String): Response<ResponseBody>

    // --- Benachrichtigungen (1.24.0) ---

    @GET("v1/monteur/nachrichten")
    suspend fun nachrichten(@Query("seit") seit: Int? = null): Nachrichten

    @POST("v1/monteur/nachrichten/gelesen")
    suspend fun gelesen(@Body gelesen: Gelesen): Response<Unit>

    @POST("v1/monteur/push")
    suspend fun pushAnmelden(@Body geraet: PushGeraet): Response<Unit>

    @HTTP(method = "DELETE", path = "v1/monteur/push", hasBody = true)
    suspend fun pushAbmelden(@Body geraet: PushGeraet): Response<Unit>

    // --- Bericht ---

    @Multipart
    @POST("v1/monteur/berichte")
    suspend fun berichtNeu(
        @Part audio: MultipartBody.Part? = null,
        @Part("text") text: RequestBody? = null,
        @Part("termin_id") terminId: RequestBody? = null,
        @Part("projekt_nr") projektNr: RequestBody? = null,
        @Part("datum") datum: RequestBody? = null,
        @Part("anfrage_id") anfrageId: RequestBody? = null,
    ): MonteurBericht

    @GET("v1/monteur/berichte")
    suspend fun berichte(@Query("von") von: String? = null, @Query("bis") bis: String? = null): List<MonteurBericht>

    @GET("v1/monteur/berichte/{bid}")
    suspend fun bericht(@Path("bid") id: String): MonteurBericht

    @PATCH("v1/monteur/berichte/{bid}")
    suspend fun berichtAendern(@Path("bid") id: String, @Body aenderung: BerichtAenderung): MonteurBericht

    @DELETE("v1/monteur/berichte/{bid}")
    suspend fun berichtVerwerfen(@Path("bid") id: String): Response<Unit>

    @Multipart
    @POST("v1/monteur/berichte/{bid}/diktat")
    suspend fun berichtDiktat(
        @Path("bid") id: String,
        @Part audio: MultipartBody.Part? = null,
        @Part("text") text: RequestBody? = null,
    ): MonteurBericht

    @POST("v1/monteur/berichte/{bid}/abschliessen")
    suspend fun berichtAbschliessen(@Path("bid") id: String, @Body abschluss: Abschluss): MonteurBericht

    // --- Zeiten ---

    @GET("v1/monteur/zeiten")
    suspend fun zeiten(@Query("datum") datum: String? = null): Tageszeiten

    @POST("v1/monteur/zeiten")
    suspend fun zeitNeu(@Body zeit: ZeitNeu): Zeiteintrag

    @PATCH("v1/monteur/zeiten/{zid}")
    suspend fun zeitAendern(@Path("zid") id: Int, @Body aenderung: ZeitAenderung): Zeiteintrag

    @DELETE("v1/monteur/zeiten/{zid}")
    suspend fun zeitLoeschen(@Path("zid") id: Int): Response<Unit>

    @POST("v1/monteur/zeiten/{datum}/einreichen")
    suspend fun tagEinreichen(@Path("datum") datum: String): Tageszeiten

    @POST("v1/monteur/stempel")
    suspend fun stempeln(@Body aktion: StempelAktion): Tageszeiten

    @GET("v1/monteur/stundenkonto")
    suspend fun stundenkonto(@Query("von") von: String? = null, @Query("bis") bis: String? = null): Stundenkonto

    // --- Urlaub ---

    @GET("v1/monteur/abwesenheiten")
    suspend fun abwesenheiten(@Query("jahr") jahr: Int? = null): Abwesenheiten

    @POST("v1/monteur/abwesenheiten")
    suspend fun abwesenheitNeu(@Body abwesenheit: AbwesenheitNeu): Abwesenheit

    @DELETE("v1/monteur/abwesenheiten/{aid}")
    suspend fun abwesenheitZurueck(@Path("aid") id: Int, @Query("jahr") jahr: Int): Response<Unit>

    // --- Sprache fuer Zeiten und Urlaub ---

    @Multipart
    @POST("v1/monteur/verstehen")
    suspend fun verstehen(
        @Part audio: MultipartBody.Part? = null,
        @Part("text") text: RequestBody? = null,
        @Part("datum") datum: RequestBody? = null,
    ): Verstanden
}
