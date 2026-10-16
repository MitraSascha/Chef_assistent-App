package de.chefassistent.app.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Baut den HTTP-Zugang zum Assistenten.
 *
 * Zwei Dinge sind hier nicht verhandelbar:
 * - **Lese-Timeout 150 s**, fuer Chat und Sprache seit v1.17.0 **keiner** (API.md 1b):
 *   ein Angebot mit HERO-Uebertragung dauert Minuten. Damit eine tote Verbindung
 *   trotzdem auffaellt, prueft ein Ping alle 30 s (HTTP/2). Reisst sie ab, holt die
 *   App das Ergebnis per `anfrage_id` ab ([AssistentRepository.chatSicher]).
 * - **`ignoreUnknownKeys`.** Der Server darf unter `/v1/` jederzeit Felder
 *   ergaenzen. Ohne das stuerzt die App beim naechsten Backend-Update ab.
 */
object Netzwerk {

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private const val LESE_TIMEOUT_SEKUNDEN = 150L

    /**
     * Videos: der Server entfernt nach dem Empfang die Metadaten einer Datei von
     * bis zu 1 GB, bevor er antwortet. `API.md` verlangt dafuer mindestens 300 s.
     */
    private const val VIDEO_TIMEOUT_SEKUNDEN = 300L
    private const val VERBINDUNGS_TIMEOUT_SEKUNDEN = 20L
    private const val PING_SEKUNDEN = 30L

    /** Chat und Sprache warten, solange der Assistent arbeitet (API.md 1b). */
    fun ohneLeseTimeout(pfad: String): Boolean =
        pfad.trimEnd('/').let { it.endsWith("/v1/chat") || it.endsWith("/v1/sprache") }

    fun api(basisUrl: String, token: () -> String?): AssistentApi =
        dienst(basisUrl, token, AssistentApi::class.java)

    /**
     * Derselbe Zugang fuer eine andere Retrofit-Schnittstelle, etwa die der
     * Monteur-App. Token, Fehleruebersetzung und Timeouts bleiben gleich.
     */
    fun <T> dienst(basisUrl: String, token: () -> String?, schnittstelle: Class<T>): T {
        val client = OkHttpClient.Builder()
            .connectTimeout(VERBINDUNGS_TIMEOUT_SEKUNDEN, TimeUnit.SECONDS)
            .readTimeout(LESE_TIMEOUT_SEKUNDEN, TimeUnit.SECONDS)
            .writeTimeout(LESE_TIMEOUT_SEKUNDEN, TimeUnit.SECONDS)
            .pingInterval(PING_SEKUNDEN, TimeUnit.SECONDS)
            .addInterceptor(VideoTimeoutInterceptor(VIDEO_TIMEOUT_SEKUNDEN))
            .addInterceptor(AnmeldeInterceptor(token))
            .addInterceptor(FehlerInterceptor(json))
            .build()

        return Retrofit.Builder()
            .baseUrl(normalisiereUrl(basisUrl))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(schnittstelle)
    }

    /**
     * `POST /v1/anmelden` und `POST /v1/monteur/einrichten` (Monteur-App, 1.22.0)
     * schicken nie ein Token mit.
     */
    fun istAnmeldung(pfad: String): Boolean = pfad.trimEnd('/').let {
        it.endsWith("/v1/anmelden") || it.endsWith("/v1/monteur/einrichten")
    }

    /**
     * Macht aus dem, was der Nutzer eintippt, eine Basis-URL, die Retrofit akzeptiert:
     * Schema ergaenzen, Leerzeichen entfernen, abschliessenden Schraegstrich sicherstellen.
     */
    fun normalisiereUrl(eingabe: String): String {
        var url = eingabe.trim()
        if (url.isEmpty()) return url
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        if (!url.endsWith("/")) {
            url = "$url/"
        }
        return url
    }
}

/** Gibt Video- und Dokument-Uploads mehr Zeit, ohne alle anderen Anfragen traeger zu machen. */
private class VideoTimeoutInterceptor(private val sekunden: Long) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val pfad = chain.request().url.encodedPath
        // Dokumente: bis 50 MB, danach liest der Server den Text heraus.
        val lang = pfad.endsWith("/v1/videos") || pfad.endsWith("/v1/dokumente")
        val kette = when {
            lang -> chain.withReadTimeout(sekunden.toInt(), TimeUnit.SECONDS)
                .withWriteTimeout(sekunden.toInt(), TimeUnit.SECONDS)
            // 0 = kein Timeout. Nur das Lesen: der Upload einer Aufnahme behaelt seinen.
            Netzwerk.ohneLeseTimeout(pfad) -> chain.withReadTimeout(0, TimeUnit.SECONDS)
            else -> chain
        }
        return kette.proceed(chain.request())
    }
}

/**
 * Haengt `Authorization: Bearer <token>` an (`cs_…` oder der alte `ca_…`),
 * sobald eines hinterlegt ist.
 *
 * Zwei Ausnahmen:
 * - `POST /v1/anmelden` geht immer **ohne** Bearer raus (Vertrag).
 * - Traegt die Anfrage schon einen eigenen Header (`POST /v1/abmelden` mit dem
 *   alten Token), bleibt der stehen. Sonst koennte eine verspaetete Abmeldung
 *   die gerade neu angelegte Sitzung beenden.
 */
private class AnmeldeInterceptor(private val token: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val urspruenglich = chain.request()
        val ohneBearer = Netzwerk.istAnmeldung(urspruenglich.url.encodedPath) ||
            urspruenglich.header("Authorization") != null
        val vorhandenes = if (ohneBearer) null else token()
        val anfrage = if (vorhandenes.isNullOrBlank()) {
            urspruenglich
        } else {
            urspruenglich.newBuilder()
                .header("Authorization", "Bearer $vorhandenes")
                .build()
        }
        return chain.proceed(anfrage)
    }
}

/**
 * Wandelt jede Fehlerantwort in einen [ApiFehler] mit dem `detail`-Text des Servers.
 *
 * Bewusst hier und nicht in jedem Aufrufer: so gibt es genau eine Stelle, an der
 * aus einem Statuscode eine Bedeutung wird.
 */
private class FehlerInterceptor(private val json: Json) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val antwort = chain.proceed(chain.request())
        if (antwort.isSuccessful) return antwort

        // Der Koerper wird hier verbraucht - die Antwort wird ohnehin verworfen.
        val koerper = antwort.use { it.body?.string() }
        val detail = ApiFehler.detailAusKoerper(json, koerper, antwort.code)
        throw ApiFehler.ausStatus(antwort.code, detail)
    }
}
