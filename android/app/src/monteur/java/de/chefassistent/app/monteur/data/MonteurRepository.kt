package de.chefassistent.app.monteur.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import de.chefassistent.app.data.ApiFehler
import de.chefassistent.app.data.AssistentRepository
import de.chefassistent.app.data.Einstellungen
import de.chefassistent.app.data.Ich
import de.chefassistent.app.data.Netzwerk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Einzige Stelle, an der die Monteur-App mit dem Server spricht - das Gegenstueck
 * zu [AssistentRepository]. Token, Adresse und Fehleruebersetzung sind dieselben
 * ([Einstellungen], [Netzwerk], [ApiFehler]). ViewModels kennen nur diese Fassade.
 */
class MonteurRepository(
    private val context: Context,
    val einstellungen: Einstellungen,
) {
    private var zwischengespeicherteUrl: String? = null
    private var zwischengespeicherteApi: MonteurApi? = null

    @Synchronized
    private fun api(): MonteurApi {
        val url = einstellungen.basisUrl
        zwischengespeicherteApi?.takeIf { zwischengespeicherteUrl == url }?.let { return it }
        val neu = try {
            Netzwerk.dienst(url, { einstellungen.token }, MonteurApi::class.java)
        } catch (_: IllegalArgumentException) {
            throw ApiFehler.AdresseUngueltig(url)
        }
        zwischengespeicherteUrl = url
        zwischengespeicherteApi = neu
        return neu
    }

    @Synchronized
    private fun zugangVerwerfen() {
        zwischengespeicherteApi = null
        zwischengespeicherteUrl = null
    }

    // --- Einrichtung -----------------------------------------------------------

    /**
     * Einmal je Handy: Code aus dem QR-Code (oder abgetippt) gegen ein `cs_`-Token.
     * Danach `GET /v1/ich`; die Rolle wird nicht geprueft (das Buero testet mit
     * eigenem Zugang). 401 = Code ungueltig, benutzt oder abgelaufen, 429 = zu viele Versuche.
     */
    suspend fun einrichten(basisUrl: String, code: String): Ich {
        einstellungen.basisUrl = basisUrl
        einstellungen.token = null
        zugangVerwerfen()
        val sitzung = ruf { it.einrichten(Einrichtung(code = codeNormalisieren(code), geraet = geraetName())) }
        einstellungen.token = sitzung.token
        einstellungen.name = sitzung.name.ifBlank { null }
        return runCatching { ruf { it.ich() } }
            .onFailure { einstellungen.abmelden() }
            .getOrThrow()
    }

    suspend fun ich(): Ich = ruf { it.ich() }

    /** Lokal sofort, beim Server nach Moeglichkeit. Fehler beim Server werden verschluckt. */
    suspend fun abmelden() {
        if (einstellungen.istAngemeldet) runCatching { ruf { it.abmelden() } }
        einstellungen.abmelden()
        zugangVerwerfen()
    }

    /** Nach einem 401 nur lokal: das Token kennt der Server ohnehin nicht mehr. */
    fun lokalAbmelden() {
        einstellungen.abmelden()
        zugangVerwerfen()
    }

    // --- Heute und Projektmappe ------------------------------------------------

    suspend fun tage(von: String? = null, bis: String? = null): List<MonteurTag> = ruf { it.tage(von, bis) }

    suspend fun projekt(id: Int): Projektmappe = ruf { it.projekt(id) }

    /**
     * PDF oder Foto aus der Projektmappe in den Cache. Eigener Ordner je uuid und
     * Vorschau, einmal geladen bleibt es dort (Unterlagen aendern sich unter
     * derselben uuid nicht).
     */
    suspend fun datei(uuid: String, name: String, vorschau: Boolean = false): File = withContext(Dispatchers.IO) {
        val ordner = File(context.cacheDir, if (vorschau) "monteur/vorschau" else "dateien").apply { mkdirs() }
        val sicher = name.substringAfterLast('/').ifBlank { uuid }
        val ziel = if (vorschau) File(ordner, "$uuid.jpg") else File(ordner, sicher)
        if (vorschau && ziel.length() > 0) return@withContext ziel
        val antwort = ruf { it.datei(uuid, if (vorschau) true else null) }
        val koerper = antwort.body() ?: throw ApiFehler.UnlesbareAntwort(IOException("leere Antwort"))
        val teil = File(ordner, "${ziel.name}.teil")
        try {
            koerper.use { k -> k.byteStream().use { ein -> teil.outputStream().use { aus -> ein.copyTo(aus) } } }
        } catch (fehler: IOException) {
            teil.delete()
            throw ApiFehler.KeineVerbindung(fehler)
        }
        ziel.delete()
        if (!teil.renameTo(ziel)) {
            teil.delete()
            throw ApiFehler.KeineVerbindung(IOException("Datei ließ sich nicht speichern"))
        }
        ziel
    }

    /**
     * Foto, Video oder Dokument ins Projekt (seit 1.22.0). Der Server erkennt die Art
     * am Dateinamen; Fotos verkleinert er selbst. [fortschritt] meldet Prozent.
     */
    suspend fun hochladen(
        projektId: Int,
        datei: File,
        text: String,
        anfrageId: String,
        fortschritt: (Int) -> Unit = {},
    ): Hochgeladen {
        val typ = (android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(datei.extension.lowercase())
            ?: "application/octet-stream").toMediaType()
        val teil = MultipartBody.Part.createFormData("datei", datei.name, ZaehlenderKoerper(datei, typ, fortschritt))
        return ruf {
            it.hochladen(projektId, teil, text.trim().takeIf { t -> t.isNotEmpty() }?.toTextTeil(), anfrageId.toTextTeil())
        }
    }

    /** Adresse zum Abspielen eines Projektvideos; Spulen geht (Range). Token kommt als Kopfzeile dazu, siehe [token]. */
    fun videoAdresse(projektId: Int, name: String): String =
        Netzwerk.normalisiereUrl(einstellungen.basisUrl) + "v1/monteur/projekte/$projektId/videos/" + android.net.Uri.encode(name)

    /** Fuer den Videospieler, der nicht ueber Retrofit laeuft. */
    fun token(): String? = einstellungen.token

    // --- Bericht -----------------------------------------------------------------

    /**
     * Neuer Berichtsentwurf aus Diktat **oder** Text. [anfrageId] ist zugleich die
     * Berichts-ID: wird dieselbe erneut geschickt (Ausgangskorb), legt der Server
     * keinen zweiten Bericht an.
     */
    suspend fun berichtNeu(
        anfrageId: String,
        aufnahme: File? = null,
        text: String? = null,
        terminId: String? = null,
        projektNr: String? = null,
        datum: String? = null,
    ): MonteurBericht {
        aufnahme?.let(::groessePruefenFuer)
        return ruf {
            it.berichtNeu(
                audio = aufnahme?.let(::audioTeil),
                text = text?.takeIf { t -> t.isNotBlank() }?.toTextTeil(),
                terminId = terminId?.toTextTeil(),
                projektNr = projektNr?.toTextTeil(),
                datum = datum?.toTextTeil(),
                anfrageId = anfrageId.toTextTeil(),
            )
        }
    }

    /** „+ Material“: Artikelstamm durchsuchen, oft genutzte zuerst (API.md 5c). */
    suspend fun artikelSuchen(q: String, max: Int = 20): List<Artikel> = ruf { it.artikel(q, max) }

    suspend fun berichte(von: String? = null, bis: String? = null): List<MonteurBericht> = ruf { it.berichte(von, bis) }

    suspend fun bericht(id: String): MonteurBericht = ruf { it.bericht(id) }

    suspend fun berichtAendern(id: String, aenderung: BerichtAenderung): MonteurBericht =
        ruf { it.berichtAendern(id, aenderung) }

    suspend fun berichtVerwerfen(id: String) {
        ruf { it.berichtVerwerfen(id) }
    }

    suspend fun berichtDiktat(id: String, aufnahme: File? = null, text: String? = null): MonteurBericht {
        aufnahme?.let(::groessePruefenFuer)
        return ruf { it.berichtDiktat(id, aufnahme?.let(::audioTeil), text?.takeIf { t -> t.isNotBlank() }?.toTextTeil()) }
    }

    suspend fun berichtAbschliessen(id: String, abschluss: Abschluss): MonteurBericht =
        ruf { it.berichtAbschliessen(id, abschluss) }

    // --- Zeiten --------------------------------------------------------------------

    suspend fun zeiten(datum: String? = null): Tageszeiten = ruf { it.zeiten(datum) }

    suspend fun zeitNeu(zeit: ZeitNeu): Zeiteintrag = ruf { it.zeitNeu(zeit) }

    suspend fun zeitAendern(id: Int, aenderung: ZeitAenderung): Zeiteintrag = ruf { it.zeitAendern(id, aenderung) }

    suspend fun zeitLoeschen(id: Int) {
        ruf { it.zeitLoeschen(id) }
    }

    suspend fun tagEinreichen(datum: String): Tageszeiten = ruf { it.tagEinreichen(datum) }

    suspend fun stempeln(aktion: StempelAktion): Tageszeiten = ruf { it.stempeln(aktion) }

    suspend fun stundenkonto(von: String? = null, bis: String? = null): Stundenkonto = ruf { it.stundenkonto(von, bis) }

    // --- Urlaub --------------------------------------------------------------------

    suspend fun abwesenheiten(jahr: Int? = null): Abwesenheiten = ruf { it.abwesenheiten(jahr) }

    suspend fun abwesenheitNeu(neu: AbwesenheitNeu): Abwesenheit = ruf { it.abwesenheitNeu(neu) }

    suspend fun abwesenheitZurueck(id: Int, jahr: Int) {
        ruf { it.abwesenheitZurueck(id, jahr) }
    }

    // --- Sprache fuer Zeiten und Urlaub ----------------------------------------------

    suspend fun verstehen(aufnahme: File? = null, text: String? = null, datum: String? = null): Verstanden {
        aufnahme?.let(::groessePruefenFuer)
        return ruf {
            it.verstehen(aufnahme?.let(::audioTeil), text?.takeIf { t -> t.isNotBlank() }?.toTextTeil(), datum?.toTextTeil())
        }
    }

    // --- Hilfen ----------------------------------------------------------------------

    private fun groessePruefenFuer(aufnahme: File) {
        if (aufnahme.length() > MAX_AUFNAHME_BYTES) throw ApiFehler.ZuGross("Die Aufnahme ist zu lang (maximal 25 MB).")
    }

    private fun audioTeil(aufnahme: File): MultipartBody.Part =
        MultipartBody.Part.createFormData("audio", aufnahme.name, aufnahme.asRequestBody("audio/mp4".toMediaType()))

    private fun String.toTextTeil(): RequestBody = toRequestBody("text/plain".toMediaType())

    private fun geraetName(): String {
        val system = runCatching { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
        val basis = system?.trim()?.takeIf { it.isNotEmpty() } ?: Build.MODEL?.trim()?.takeIf { it.isNotEmpty() } ?: "Android"
        return (basis + " – Monteur-App").take(80)
    }

    /** Fuer Aufrufe mit `Response<…>`: Fehler kommen schon als [ApiFehler] aus dem Interceptor. */
    private suspend fun <T> ruf(block: suspend (MonteurApi) -> T): T = withContext(Dispatchers.IO) {
        try {
            block(api())
        } catch (fehler: ApiFehler) {
            throw fehler
        } catch (fehler: SerializationException) {
            throw ApiFehler.UnlesbareAntwort(fehler)
        } catch (fehler: IOException) {
            throw ApiFehler.KeineVerbindung(fehler)
        }
    }

    /** Dateikoerper, der mitzaehlt - fuer den Fortschritt grosser Videos. */
    private class ZaehlenderKoerper(
        private val datei: File,
        private val typ: okhttp3.MediaType,
        private val melden: (Int) -> Unit,
    ) : RequestBody() {
        override fun contentType() = typ
        override fun contentLength() = datei.length()
        override fun writeTo(sink: okio.BufferedSink) {
            val gesamt = contentLength().coerceAtLeast(1)
            var geschrieben = 0L
            var zuletzt = -1
            datei.inputStream().use { ein ->
                val puffer = ByteArray(64 * 1024)
                while (true) {
                    val n = ein.read(puffer)
                    if (n < 0) break
                    sink.write(puffer, 0, n)
                    geschrieben += n
                    val prozent = (geschrieben * 100 / gesamt).toInt()
                    if (prozent != zuletzt) {
                        zuletzt = prozent
                        melden(prozent)
                    }
                }
            }
        }
    }

    companion object {
        private const val MAX_AUFNAHME_BYTES = 25L * 1024 * 1024

        /** Vorgabe fuer die Einrichtung, in den versteckten Einstellungen aenderbar. */
        const val STANDARD_URL = Einstellungen.STANDARD_BASIS_URL

        /** `anfrage_id`: neue UUID ohne Bindestriche (Muster `^[A-Za-z0-9_-]{8,64}$`). */
        fun neueAnfrageId(): String = UUID.randomUUID().toString().replace("-", "")

        /** Gross-/Kleinschreibung und Bindestriche egal: `abcd efgh-jklm` -> `ABCD-EFGH-JKLM`. */
        fun codeNormalisieren(eingabe: String): String {
            val roh = eingabe.uppercase().filter { it.isLetterOrDigit() }
            return if (roh.length == 12) roh.chunked(4).joinToString("-") else eingabe.trim().uppercase()
        }

        /**
         * QR-Inhalt `mitra-monteur:<Server-URL>#<Code>` zerlegen. `null`, wenn es
         * kein Einrichtungscode ist (etwa ein fremder QR-Code).
         */
        fun qrLesen(inhalt: String): Pair<String, String>? {
            val text = inhalt.trim()
            if (!text.startsWith(QR_VORSATZ, ignoreCase = true)) return null
            val rest = text.substring(QR_VORSATZ.length)
            val raute = rest.lastIndexOf('#')
            if (raute <= 0 || raute == rest.lastIndex) return null
            val url = rest.substring(0, raute).trim()
            val code = rest.substring(raute + 1).trim()
            if (!url.startsWith("http://") && !url.startsWith("https://")) return null
            return Netzwerk.normalisiereUrl(url) to codeNormalisieren(code)
        }

        private const val QR_VORSATZ = "mitra-monteur:"    }
}
