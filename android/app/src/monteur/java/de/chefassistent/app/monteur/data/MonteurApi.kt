package de.chefassistent.app.monteur.data

import de.chefassistent.app.data.Ich
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
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

    @Streaming
    @GET("v1/monteur/dateien/{uuid}")
    suspend fun datei(@Path("uuid") uuid: String, @Query("vorschau") vorschau: Boolean? = null): Response<ResponseBody>

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
