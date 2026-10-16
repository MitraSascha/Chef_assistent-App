package de.chefassistent.app.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okhttp3.MediaType
import okio.BufferedSink
import okio.source
import java.io.File
import java.io.IOException

/**
 * Einzige Stelle, an der die App mit dem Assistenten spricht.
 *
 * Alles darueber (ViewModels, Oberflaeche) kennt nur diese Fassade - der Transport
 * dahinter laesst sich austauschen, ohne die Oberflaeche anzufassen.
 *
 * Aufgaben:
 * - Retrofit-Zugang passend zur aktuell eingestellten Adresse bereitstellen
 * - jede Ausnahme in einen [ApiFehler] uebersetzen, damit oben niemand mehr
 *   `IOException` von `HttpException` unterscheiden muss
 * - alles auf [Dispatchers.IO] ausfuehren
 */
class AssistentRepository(
    private val context: Context,
    val einstellungen: Einstellungen,
) {

    /** Fuer Aufrufe, die den Bildschirm ueberleben muessen (Abmelden beim Server). */
    private val hintergrund = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var zwischengespeicherteUrl: String? = null
    private var zwischengespeicherteApi: AssistentApi? = null

    /** Baut den Zugang neu, sobald der Nutzer eine andere Adresse einstellt. */
    @Synchronized
    private fun api(): AssistentApi {
        val url = einstellungen.basisUrl
        val vorhanden = zwischengespeicherteApi
        if (vorhanden != null && zwischengespeicherteUrl == url) return vorhanden

        // Retrofit lehnt Adressen wie "assistent mitra" mit einer
        // IllegalArgumentException ab - die soll als Meldung ankommen, nicht als Absturz.
        val neu = try {
            Netzwerk.api(url) { einstellungen.token }
        } catch (_: IllegalArgumentException) {
            throw ApiFehler.AdresseUngueltig(url)
        }
        zwischengespeicherteUrl = url
        zwischengespeicherteApi = neu
        return neu
    }

    @Synchronized
    fun zugangVerwerfen() {
        zwischengespeicherteApi = null
        zwischengespeicherteUrl = null
        // Anderer Zugang = womoeglich andere Person hinter "ich".
        personenStand = null
    }

    // --- Anmeldung -----------------------------------------------------------

    /**
     * Anmeldung seit v1.10.0: Name + Passwort + Code gegen `POST /v1/anmelden`.
     *
     * Das Passwort wird nur durchgereicht und nirgends abgelegt. Gespeichert werden
     * das zurueckgegebene Sitzungs-Token (ersetzt einen alten `ca_`-Schluessel) und
     * der Name zum Vorbelegen. Danach wie bisher `GET /v1/ich`.
     *
     * Fehler kommen unveraendert als [ApiFehler] heraus: 401 = Name, Passwort oder
     * Code falsch, 403 = Zwei-Faktor nicht eingerichtet, 429 = gesperrt. Ein 401
     * hier meldet **nicht** global ab - es gibt noch nichts abzumelden.
     */
    suspend fun anmeldenMitPasswort(basisUrl: String, name: String, passwort: String, code: String): Ich {
        einstellungen.basisUrl = basisUrl
        zugangVerwerfen()
        val antwort = ruf {
            it.anmelden(
                Anmeldung(
                    name = name.trim(),
                    passwort = passwort,
                    code = code.trim(),
                    geraet = geraetBezeichnung(),
                )
            )
        }
        einstellungen.token = antwort.token
        einstellungen.name = name.trim()
        return runCatching { ruf { it.ich() } }
            .onFailure { einstellungen.abmelden() }
            .getOrThrow()
    }

    /**
     * Alter Weg mit Zugangsschluessel `ca_…`. Gilt laut `API.md` nur noch ueber
     * Tailscale. Prueft Adresse und Token in einem Schritt. Erfolg heisst: angemeldet.
     * Ein [ApiFehler.NichtAngemeldet] heisst: Token falsch oder gesperrt.
     */
    suspend fun anmelden(basisUrl: String, token: String): Ich {
        einstellungen.basisUrl = basisUrl
        einstellungen.token = token
        zugangVerwerfen()
        return runCatching { ruf { it.ich() } }
            .onFailure { einstellungen.abmelden() }
            .getOrThrow()
    }

    suspend fun ich(): Ich = ruf { it.ich() }

    /**
     * Nur lokal abmelden: Token weg, Name bleibt. Fuer den Fall, dass der Server
     * das Token ohnehin nicht mehr kennt (401).
     */
    fun abmelden() {
        einstellungen.abmelden()
        zugangVerwerfen()
    }

    /**
     * Abmelden auf Wunsch des Nutzers: lokal sofort, `POST /v1/abmelden` danach im
     * Hintergrund mit dem alten Token. Fehler dort (kein Netz, 401, Server vor
     * 1.10.0) werden verschluckt - die Abmeldung darf nie daran haengen bleiben.
     *
     * Eigener [hintergrund], weil der Chat-Bildschirm samt ViewModel beim Wechsel
     * zur Anmeldung verschwindet und dessen Coroutinen mitnimmt.
     */
    fun abmeldenUndSitzungBeenden() {
        val altesToken = einstellungen.token
        abmelden()
        if (altesToken.isNullOrBlank()) return
        hintergrund.launch {
            withTimeoutOrNull(ABMELDEN_TIMEOUT_MS) {
                runCatching { ruf { it.abmelden("Bearer $altesToken") } }
            }
        }
    }

    // --- Geraete -------------------------------------------------------------

    suspend fun geraete(): List<Geraet> = ruf { it.geraete() }

    suspend fun geraetAbmelden(id: Int) {
        ruf { it.geraetAbmelden(id) }
    }

    /** Ob das gespeicherte Token noch ein alter Zugangsschluessel `ca_…` ist. */
    val mitZugangsschluessel: Boolean
        get() = einstellungen.token?.startsWith(PRAEFIX_ZUGANGSSCHLUESSEL) == true

    /**
     * Lesbarer Name fuer die Geraeteliste. Samsung legt unter `device_name` den
     * Verkaufsnamen ab ("Galaxy S25"), `Build.MODEL` waere nur "SM-S931B".
     */
    private fun geraetBezeichnung(): String {
        val systemName = runCatching {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        }.getOrNull()
        return geraetBezeichnung(systemName, Build.MODEL)
    }

    // --- Chat ----------------------------------------------------------------

    /**
     * @param bilder Fotopfade aus der Ablage, die der Assistent ansehen soll
     *   (seit v1.5.0). Leer = Feld weglassen.
     */
    suspend fun chat(
        text: String,
        gespraechId: String?,
        bilder: List<String> = emptyList(),
        anfrageId: String? = null,
        wahl: ModellWahl? = null,
    ): ChatAntwort =
        ruf {
            it.chat(
                ChatAnfrage(
                    text = text,
                    gespraech_id = gespraechId,
                    anfrage_id = anfrageId,
                    bilder = bilder.take(ChatAnfrage.MAX_BILDER).ifEmpty { null },
                    modell = wahl?.modell,
                    denkaufwand = wahl?.denkaufwand,
                )
            )
        }

    /**
     * Stand einer Anfrage (`GET /v1/chat/{anfrage_id}`, seit v1.17.0). `null` heisst
     * 404: die Nachricht kam nie an und kann mit derselben Kennung erneut raus.
     */
    suspend fun chatStatus(anfrageId: String, seit: Int? = null): ChatStatus? = try {
        ruf { it.chatStatus(anfrageId, seit) }
    } catch (_: ApiFehler.NichtGefunden) {
        null
    }

    /**
     * Chat nach API.md 1b: Reisst die Verbindung ab oder meldet ein Proxy 502-504,
     * heisst das **nicht**, dass die Nachricht verloren ist - der Server arbeitet
     * weiter. Dann wird alle paar Sekunden der Stand abgeholt, statt neu zu senden.
     *
     * Wirft [ApiFehler.KeineVerbindung], wenn auch das Abholen eine Weile nicht
     * durchkommt. Der Aufrufer reiht die Nachricht dann **mit derselben Kennung**
     * ein - doppelt ausgefuehrt wird sie trotzdem nie.
     */
    suspend fun chatSicher(
        text: String,
        gespraechId: String?,
        anfrageId: String,
        bilder: List<String> = emptyList(),
        wahl: ModellWahl? = null,
    ): ChatAntwort {
        val senden: suspend () -> ChatAntwort = { chat(text, gespraechId, bilder, anfrageId, wahl) }
        return try {
            senden()
        } catch (fehler: ApiFehler) {
            if (!fehler.istUnklar) throw fehler
            abholen(anfrageId, senden)
        }
    }

    /** Wie [chatSicher], fuer eine Sprachnachricht mit Antwort. */
    suspend fun spracheSicher(
        aufnahme: File,
        gespraechId: String?,
        anfrageId: String,
        wahl: ModellWahl? = null,
    ): SpracheAntwort {
        val senden: suspend () -> SpracheAntwort = {
            sprache(aufnahme, gespraechId, antworten = true, anfrageId = anfrageId, wahl = wahl)
        }
        return try {
            senden()
        } catch (fehler: ApiFehler) {
            if (!fehler.istUnklar) throw fehler
            // Abgeholt wird nur die Chat-Antwort; den erkannten Text hat dann der
            // Verlauf (die Nachricht steht dort seit 1.17.0 sofort).
            val antwort = abholen(anfrageId) { senden().chat ?: throw ApiFehler.UnlesbareAntwort(IOException("Sprache ohne Antwort")) }
            SpracheAntwort(text = "", chat = antwort)
        }
    }

    private suspend fun abholen(anfrageId: String, erneutSenden: suspend () -> ChatAntwort): ChatAntwort {
        var fehlversuche = 0
        // Ein Server vor 1.17.0 kennt das Abholen nicht und meldet immer 404 -
        // dann nicht endlos neu senden, sondern den Fehler durchreichen.
        var neuGesendet = 0
        while (true) {
            delay(ABHOLEN_TAKT_MS)
            val stand = try {
                chatStatus(anfrageId)
            } catch (fehler: ApiFehler) {
                if (!fehler.istUnklar) throw fehler
                if (++fehlversuche >= ABHOLEN_MAX_FEHLVERSUCHE) throw ApiFehler.KeineVerbindung(fehler)
                continue
            }
            fehlversuche = 0
            when (stand?.status) {
                // Kam nie an: mit derselben Kennung noch einmal.
                null -> try {
                    return erneutSenden()
                } catch (fehler: ApiFehler) {
                    if (!fehler.istUnklar || ++neuGesendet >= ABHOLEN_MAX_FEHLVERSUCHE) throw fehler
                }
                ChatStatus.STATUS_FERTIG ->
                    return stand.antwort ?: throw ApiFehler.UnlesbareAntwort(IOException("Fertig ohne Antwort"))
                ChatStatus.STATUS_FEHLER ->
                    throw ApiFehler.AnfrageFehlgeschlagen(stand.fehler)
                ChatStatus.STATUS_LAEUFT -> Unit
                // abgebrochen (seit 1.20.0) oder ein kuenftiger Zustand: mit Antwort
                // gilt er wie fertig (API.md 1d), sonst weiter warten.
                else -> stand.antwort?.let { return it }
            }
        }
    }

    // --- Sprache -------------------------------------------------------------

    /**
     * Schickt eine Aufnahme an `POST /v1/sprache`.
     *
     * @param antworten `true` gibt den erkannten Text direkt an den Chat weiter,
     *   `false` erkennt nur den Text - etwa zum Korrigieren vor dem Senden.
     */
    suspend fun sprache(
        aufnahme: File,
        gespraechId: String?,
        antworten: Boolean = true,
        anfrageId: String? = null,
        wahl: ModellWahl? = null,
    ): SpracheAntwort {
        if (aufnahme.length() > MAX_AUFNAHME_BYTES) {
            // Der Server wuerde mit 413 antworten. Das hier spart die Uebertragung
            // von mehr als 25 MB ueber eine Mobilfunkverbindung.
            throw ApiFehler.ZuGross("Die Aufnahme ist zu lang (maximal 25 MB).")
        }

        val koerper = aufnahme.asRequestBody("audio/mp4".toMediaType())
        val teil = MultipartBody.Part.createFormData("audio", aufnahme.name, koerper)

        return ruf {
            it.sprache(
                audio = teil,
                gespraechId = gespraechId?.toTextTeil(),
                antworten = antworten.toString().toTextTeil(),
                anfrageId = anfrageId?.toTextTeil(),
                modell = wahl?.modell?.toTextTeil(),
                denkaufwand = wahl?.denkaufwand?.toTextTeil(),
            )
        }
    }

    // --- Fotos ---------------------------------------------------------------

    /**
     * Haengt ein Foto an eine Notiz (`POST /v1/fotos`).
     *
     * Vorher wird es auf dem Geraet verkleinert, wie `API.md` empfiehlt: ein
     * Kamerabild hat schnell 5-10 MB, der Server behaelt ohnehin nur 2560 px.
     * Auf der Baustelle zaehlt jedes Megabyte Mobilfunk.
     *
     * @param notiz Pfad einer eigenen Notiz; `null` = neueste von heute.
     * @param gespraechId Gespraech, in dem das Foto entstand - dann weiss der
     *   Assistent beim naechsten Zug davon.
     * @param aufbereiten `false` fuer Fotos aus dem [Medienkorb] - die sind schon
     *   verkleinert, ein zweites JPEG-Kodieren kostet nur Qualitaet.
     */
    suspend fun foto(
        bild: File,
        notiz: String?,
        text: String,
        gespraechId: String?,
        aufbereiten: Boolean = true,
    ): FotoAntwort {
        val jpeg = if (aufbereiten) {
            withContext(Dispatchers.Default) { FotoAufbereitung.verkleinern(bild) }
        } else {
            bild
        }
        try {
            if (jpeg.length() > MAX_AUFNAHME_BYTES) {
                throw ApiFehler.ZuGross("Das Foto ist zu gross (maximal 25 MB).")
            }
            val teil = MultipartBody.Part.createFormData(
                "foto",
                "${bild.nameWithoutExtension}.jpg",
                jpeg.asRequestBody("image/jpeg".toMediaType()),
            )
            return ruf {
                it.foto(
                    foto = teil,
                    notiz = notiz?.toTextTeil(),
                    text = text.trim().take(MAX_BILDUNTERSCHRIFT).takeIf { t -> t.isNotBlank() }?.toTextTeil(),
                    gespraechId = gespraechId?.toTextTeil(),
                )
            }
        } finally {
            if (jpeg != bild) jpeg.delete()
        }
    }

    /**
     * Haengt ein Video an eine Notiz (`POST /v1/videos`).
     *
     * Erwartet eine bereits verkleinerte Datei (siehe [VideoAufbereitung]) -
     * aufgerufen wird das aus dem [VersandArbeiter], nicht aus der Oberflaeche.
     *
     * @param abschreiben einer aus [ABSCHREIBEN_WERTE]; `null` = Servervorgabe.
     * @param fortschritt Prozent des hochgeladenen Anteils.
     */
    suspend fun video(
        datei: File,
        notiz: String?,
        text: String,
        abschreiben: String?,
        gespraechId: String?,
        fortschritt: (Int) -> Unit = {},
    ): VideoAntwort {
        if (datei.length() > MAX_VIDEO_BYTES) {
            throw ApiFehler.ZuGross("Das Video ist zu gross (maximal 1 GB).")
        }
        val teil = MultipartBody.Part.createFormData(
            "video",
            datei.name,
            // MOV aus der Galerie geht unverkleinert raus, wenn es schon klein genug ist.
            FortschrittsKoerper(datei, videoTyp(datei), fortschritt),
        )
        return ruf {
            it.video(
                video = teil,
                notiz = notiz?.toTextTeil(),
                text = text.trim().take(MAX_BILDUNTERSCHRIFT).takeIf { t -> t.isNotBlank() }?.toTextTeil(),
                abschreiben = abschreiben?.toTextTeil(),
                gespraechId = gespraechId?.toTextTeil(),
            )
        }
    }

    /**
     * Haengt ein Dokument an eine Notiz (`POST /v1/dokumente`, seit v1.12.0).
     *
     * Die Datei geht unveraendert raus. Ob der Typ passt, entscheidet der Server
     * (415) - er erkennt ihn am Dateinamen, deshalb geht [dateiname] mit Endung mit.
     *
     * @param dateiname Originalname, unter dem der Server die Datei ablegt.
     * @param fortschritt Prozent des hochgeladenen Anteils.
     */
    suspend fun dokument(
        datei: File,
        dateiname: String,
        notiz: String?,
        text: String,
        gespraechId: String?,
        fortschritt: (Int) -> Unit = {},
    ): DokumentAntwort {
        if (datei.length() > MAX_DOKUMENT_BYTES) {
            throw ApiFehler.ZuGross("Das Dokument ist zu gross (maximal 50 MB).")
        }
        val typ = android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(dateiname.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
        val teil = MultipartBody.Part.createFormData(
            "dokument",
            dateiname,
            FortschrittsKoerper(datei, typ.toMediaType(), fortschritt),
        )
        return ruf {
            it.dokument(
                dokument = teil,
                notiz = notiz?.toTextTeil(),
                text = text.trim().take(MAX_BILDUNTERSCHRIFT).takeIf { t -> t.isNotBlank() }?.toTextTeil(),
                gespraechId = gespraechId?.toTextTeil(),
            )
        }
    }

    /**
     * `POST /v1/vorlesen` - MP3 vom Server. Alternative zur Android-Sprachausgabe.
     *
     * Das Modell legt der Server fest. Die Stimme darf die App seit der
     * Vertragserweiterung mitschicken; ohne [stimme] gilt die Servervorgabe.
     * Laengere Texte werden auf die im Vertrag genannten 4000 Zeichen gekuerzt,
     * statt in ein 422 zu laufen.
     */
    suspend fun vorlesen(text: String, stimme: String? = null): File = withContext(Dispatchers.IO) {
        val gekuerzt = if (text.length > MAX_VORLESE_ZEICHEN) {
            text.trim().take(MAX_VORLESE_ZEICHEN)
        } else {
            text
        }
        val koerper = ruf { it.vorlesen(VorlesenAnfrage(gekuerzt, stimme)) }
        val ordner = File(context.cacheDir, "sprache").apply { mkdirs() }
        val ziel = File(ordner, "vorlesen_${System.currentTimeMillis()}.mp3")
        herunterladen(koerper, ziel)
        ziel
    }

    // --- Verbrauch -----------------------------------------------------------

    /** Ohne Schema im Vertrag - kommt als rohes JSON zurueck. Siehe [AssistentApi.verbrauch]. */
    suspend fun verbrauch(): JsonElement = ruf { it.verbrauch() }

    // --- Auftraege -----------------------------------------------------------

    suspend fun auftraege(): List<Auftrag> = ruf { it.auftraege() }

    suspend fun auftragAbbrechen(id: String): Auftrag = ruf { it.auftragAbbrechen(id) }

    suspend fun auftragWiederholen(id: String): Auftrag = ruf { it.auftragWiederholen(id) }

    // --- Aufgaben ------------------------------------------------------------

    suspend fun aufgaben(person: String? = null): AufgabenUebersicht = ruf { it.aufgaben(person) }

    suspend fun aufgabeAendern(id: String, aenderung: AufgabeAenderung): Aufgabe =
        ruf { it.aufgabeAendern(id, aenderung) }.aufgabe

    // --- GU-Plantafel --------------------------------------------------------

    suspend fun guProjekte(): List<GuProjekt> = ruf { it.guProjekte() }.projekte

    suspend fun guProjektNeu(name: String): GuProjekt = ruf { it.guProjektNeu(GuProjektNeu(name.trim())) }

    suspend fun guAlle(): GuAlle = ruf { it.guAlle() }

    suspend fun guPlan(projektId: Int): GuPlan = ruf { it.guPlan(projektId) }

    suspend fun einsatzNeu(projektId: Int, neu: EinsatzNeu): EinsatzAntwort = ruf { it.einsatzNeu(projektId, neu) }

    suspend fun einsatzAendern(einsatzId: Int, aenderung: EinsatzAenderung): EinsatzAntwort =
        ruf { it.einsatzAendern(einsatzId, aenderung) }

    suspend fun einsatzLoeschen(einsatzId: Int) {
        ruf { it.einsatzLoeschen(einsatzId) }
    }

    suspend fun gewerkSetzen(projektId: Int, gewerk: GewerkSetzen): Gewerk =
        ruf { it.gewerkSetzen(projektId, gewerk) }.gewerk

    suspend fun gewerkLoeschen(gewerkId: Int) {
        ruf { it.gewerkLoeschen(gewerkId) }
    }

    /** Legt den Bauzeitenplan als PDF in den Projektordner und gibt dessen Pfad zurueck. */
    suspend fun guPdf(projektId: Int): String = ruf { it.guPdf(projektId) }.pfad

    // --- Bestaetigungen ------------------------------------------------------

    // --- Angebote, seit v1.11.0 ----------------------------------------------

    suspend fun angebote(): List<AngebotKurz> = ruf { it.angebote() }

    suspend fun angebot(id: String): Angebot = ruf { it.angebot(id) }

    /** `antwort`: eine der `optionen`, eigener Text oder [ANTWORT_PASST]. */
    suspend fun angebotAntworten(id: String, punkt: String, antwort: String): Angebot =
        ruf { it.angebotAntworten(id, AngebotAntwort(punkt, antwort)) }

    /**
     * Laedt die UGL-Datei in den Cache, unter dem Namen aus `Content-Disposition`.
     * Der Aufrufer gibt sie per FileProvider weiter (Teilen-Menue).
     */
    suspend fun angebotUgl(id: String): File = withContext(Dispatchers.IO) {
        val antwort = ruf { it.angebotUgl(id) }
        val koerper = antwort.body() ?: throw ApiFehler.UnlesbareAntwort(IOException("UGL ohne Inhalt"))
        val name = dateinameAusDisposition(antwort.headers()["Content-Disposition"]) ?: "angebot-$id.ugl"
        val ordner = File(context.cacheDir, "dateien").apply { mkdirs() }
        val ziel = File(ordner, name)
        herunterladen(koerper, ziel)
        ziel
    }

    suspend fun offeneBestaetigungen(): List<OffeneBestaetigung> = ruf { it.offeneBestaetigungen() }

    suspend fun entscheiden(id: String, annehmen: Boolean): EntscheidungAntwort =
        ruf { it.entscheiden(id, Entscheidung(annehmen)) }

    // --- Verlauf -------------------------------------------------------------

    suspend fun gespraeche(): List<GespraechKurz> = ruf { it.gespraeche() }

    /** Seit v1.21.0: Gespraeche, deren Titel alle Woerter aus [text] enthaelt. */
    suspend fun gespraecheSuchen(text: String): List<GespraechKurz> = ruf { it.gespraeche(text) }

    /** Seit v1.21.0: oben anheften bzw. loesen. Aendert `geaendert` nicht. */
    suspend fun gespraechAnheften(gespraechId: String, angeheftet: Boolean): GespraechKurz =
        ruf { it.gespraechEinstellen(gespraechId, ModellWahl(angeheftet = angeheftet)) }

    suspend fun verlauf(gespraechId: String): List<Nachricht> = ruf { it.verlauf(gespraechId) }

    suspend fun gespraechLoeschen(gespraechId: String) {
        ruf { it.gespraechLoeschen(gespraechId) }
    }

    /**
     * Seit v1.20.0: Modell und Denkaufwand eines bestehenden Gespraechs speichern.
     * Nur gesetzte Felder gehen raus. Antwort sind die gespeicherten Werte.
     */
    suspend fun gespraechEinstellen(gespraechId: String, wahl: ModellWahl): GespraechKurz =
        ruf { it.gespraechEinstellen(gespraechId, wahl) }

    // --- Modellwahl, Stopp, Zwischenruf (seit v1.20.0) ----------------------

    /** `GET /v1/modelle`. `null` heisst: alter Server ohne Modellwahl (404), Auswahl ausblenden. */
    suspend fun modelle(): ModellAuswahl? = try {
        ruf { it.modelle() }
    } catch (_: ApiFehler.NichtGefunden) {
        null
    }

    /**
     * Stopp (`POST /v1/chat/{anfrage_id}/abbrechen`). Wirkt erst am naechsten Schritt,
     * die Antwort kommt danach normal ueber den wartenden Chat-Aufruf.
     * `false` heisst 409 (schon fertig) oder 404 (noch nicht angekommen), beides ohne Meldung.
     */
    suspend fun chatAbbrechen(anfrageId: String): Boolean = try {
        ruf { it.chatAbbrechen(anfrageId) }
        true
    } catch (_: ApiFehler.Beschaeftigt) {
        false
    } catch (_: ApiFehler.NichtGefunden) {
        false
    }

    /**
     * Zwischenruf waehrend einer laufenden Antwort. 409 (schon fertig oder wird gerade
     * abgebrochen) kommt als [ApiFehler.Beschaeftigt] heraus: dann als neue Nachricht senden.
     */
    suspend fun zwischenruf(anfrageId: String, text: String): ZwischenrufAntwort =
        ruf { it.zwischenruf(anfrageId, Zwischenruf(text.take(Zwischenruf.MAX_ZEICHEN))) }

    // --- Kalender ------------------------------------------------------------

    suspend fun termine(
        von: String,
        bis: String? = null,
        person: String? = null,
        alle: Boolean? = null,
    ): Termine = ruf { it.termine(von, bis, person, alle) }

    /** `bis` einschliesslich, hoechstens 62 Tage. `personen` aus [PERSON_ICH], [PERSON_ALLE], `hero:<id>`. */
    suspend fun kalender(von: String, bis: String, personen: List<String>): KalenderAntwort =
        ruf { it.kalender(von, bis, personen.joinToString(",").ifBlank { null }) }

    private var personenStand: Pair<Long, Personen>? = null

    /** `GET /v1/personen`, 5 Minuten zwischengespeichert (Auftrag zu 1.16.0). */
    suspend fun personen(neu: Boolean = false): Personen {
        val jetzt = System.currentTimeMillis()
        personenStand?.let { (zeit, personen) ->
            if (!neu && jetzt - zeit < PERSONEN_GUELTIG_MS) return personen
        }
        return ruf { it.personen() }.also { personenStand = jetzt to it }
    }

    /** `GET /v1/kalender/kategorien` (seit v1.18.0). */
    suspend fun kalenderKategorien(): List<TerminKategorie> = ruf { it.kalenderKategorien() }

    /** HERO-Termin anlegen; mit [nurPruefen] wird nichts geschrieben (`API.md` 5b). */
    suspend fun heroTerminAnlegen(termin: HeroTerminNeu, nurPruefen: Boolean): HeroTerminErgebnis =
        ruf { it.heroTerminAnlegen(termin, nurPruefen) }

    /** HERO-Termin verschieben; mit [nurPruefen] wird nichts geaendert. */
    suspend fun heroTerminVerschieben(heroId: Long, termin: HeroTerminVerschieben, nurPruefen: Boolean): HeroTerminErgebnis =
        ruf { it.heroTerminVerschieben(heroId, termin, nurPruefen) }

    // --- Dateien -------------------------------------------------------------

    suspend fun notizen(): Notizen = ruf { it.notizen() }

    suspend fun ordner(
        pfad: String,
        sortierung: String? = null,
        ab: Int? = null,
        anzahl: Int? = null,
    ): Ordner = ruf { it.ordner(pfad, sortierung, ab, anzahl) }

    suspend fun suche(q: String, ordner: String? = null): SuchErgebnis =
        ruf { it.suche(q, ordner?.ifBlank { null }) }

    /** HERO-Projekte zum Anheften: nur die Projektordner direkt im HERO-Archiv (Auftrag 1.19). */
    suspend fun heroProjekteSuchen(q: String): List<SuchTreffer> =
        ruf { it.suche(q, ordner = HERO_ARCHIV, typ = "ordner", max = 30) }
            .treffer.filter { it.istOrdner && istHeroProjektordner(it.pfad) }

    suspend fun heroMappeOeffnen(projekt: String): HeroMappe =
        ruf { it.heroMappeOeffnen(HeroMappeAnfrage(projekt)) }

    suspend fun heroMappeStand(projekt: String): HeroMappe = ruf { it.heroMappeStand(projekt) }

    /**
     * Laedt eine Datei aus `dateien` der Chat-Antwort in den Cache und gibt sie
     * als [File] zurueck. Der Aufrufer reicht sie per FileProvider weiter.
     */
    suspend fun dateiLaden(pfad: String, anzeigen: Boolean = false): File = withContext(Dispatchers.IO) {
        val koerper = ruf { it.datei(pfad, if (anzeigen) true else null) }
        val ordner = File(context.cacheDir, "dateien").apply { mkdirs() }
        val ziel = File(ordner, dateiname(pfad))
        herunterladen(koerper, ziel)
        ziel
    }

    /**
     * Laedt ein Bild der Ablage fuer ein Vorschaubild im Chat.
     *
     * Eigener Ordner mit dem Pfad als Schluessel: Fotos heissen nach der
     * Uhrzeit und koennen in verschiedenen Notizen gleich heissen. Einmal
     * geladen, wird beim Scrollen durch den Verlauf nicht erneut geladen.
     */
    suspend fun vorschauLaden(pfad: String): File = withContext(Dispatchers.IO) {
        val ordner = File(context.cacheDir, "vorschau").apply { mkdirs() }
        val ziel = File(ordner, "${pfad.hashCode().toUInt()}.${pfad.substringAfterLast('.', "jpg")}")
        if (ziel.length() > 0) return@withContext ziel
        val koerper = ruf { it.datei(pfad) }
        val teil = File(ordner, "${ziel.name}.teil")
        herunterladen(koerper, teil)
        // Erst umbenennen, wenn alles da ist - ein abgebrochener Download soll
        // nicht als fertiges Bild im Speicher liegen bleiben.
        if (!teil.renameTo(ziel)) {
            teil.delete()
            throw ApiFehler.KeineVerbindung(IOException("Vorschau ließ sich nicht speichern"))
        }
        ziel
    }

    /**
     * Schreibt einen gestreamten Koerper in [ziel]. Bei `@Streaming` kommt der
     * Inhalt erst hier ueber die Leitung, also ausserhalb von [ruf] - ein
     * Funkloch mitten im Download waere sonst eine rohe [IOException] und
     * braeche die App ab. Eine halbe Datei bleibt nicht liegen.
     */
    private fun herunterladen(koerper: ResponseBody, ziel: File) {
        try {
            koerper.use {
                it.byteStream().use { quelle ->
                    ziel.outputStream().use { ausgabe -> quelle.copyTo(ausgabe) }
                }
            }
        } catch (fehler: IOException) {
            ziel.delete()
            throw ApiFehler.KeineVerbindung(fehler)
        }
    }

    private fun videoTyp(datei: File) =
        (if (datei.extension.equals("mov", ignoreCase = true)) "video/quicktime" else "video/mp4").toMediaType()

    private fun dateiname(pfad: String): String =
        pfad.substringAfterLast('/').ifBlank { "datei" }

    private fun String.toTextTeil(): RequestBody =
        toRequestBody("text/plain".toMediaType())

    // --- Fehlerbehandlung ----------------------------------------------------

    companion object {
        /** Laenger wartet die Abmeldung im Hintergrund nicht auf den Server. */
        private const val ABMELDEN_TIMEOUT_MS = 15_000L

        private const val PERSONEN_GUELTIG_MS = 5 * 60_000L

        /** API.md 1b: alle 3-5 s abholen. */
        private const val ABHOLEN_TAKT_MS = 4_000L

        /** So oft darf das Abholen hintereinander scheitern, bevor die Nachricht in den Korb geht. */
        private const val ABHOLEN_MAX_FEHLVERSUCHE = 4

        /** Neue Kennung fuer eine Nachricht (UUID, passt zu [ANFRAGE_ID_MUSTER]). */
        fun neueAnfrageId(): String = java.util.UUID.randomUUID().toString()

        private const val PRAEFIX_ZUGANGSSCHLUESSEL = "ca_"

        /**
         * "<Geraetename> – App", gekuerzt auf die Vertragsgrenze. Ohne Systemnamen
         * bleibt das Modellkuerzel, ohne beides nur "Android".
         */
        fun geraetBezeichnung(systemName: String?, modell: String?): String {
            val basis = systemName?.trim()?.takeIf { it.isNotEmpty() }
                ?: modell?.trim()?.takeIf { it.isNotEmpty() }
                ?: "Android"
            val zusatz = " – App"
            return basis.take(Anmeldung.MAX_GERAET - zusatz.length) + zusatz
        }

        /**
         * Dateiname aus `Content-Disposition`, `filename*=UTF-8''…` vor `filename=`.
         * Pfadanteile fallen weg, damit kein Name aus dem Cache-Ordner herausfuehrt.
         */
        fun dateinameAusDisposition(kopf: String?): String? {
            if (kopf.isNullOrBlank()) return null
            val erweitert = Regex("""filename\*\s*=\s*([^']*)'[^']*'([^;]+)""", RegexOption.IGNORE_CASE)
                .find(kopf)?.let { treffer ->
                    val zeichensatz = treffer.groupValues[1].ifBlank { "UTF-8" }
                    runCatching { java.net.URLDecoder.decode(treffer.groupValues[2].trim().replace("+", "%2B"), zeichensatz) }
                        .getOrNull()
                }
            val einfach = Regex("""filename\s*=\s*("[^"]*"|[^;]+)""", RegexOption.IGNORE_CASE)
                .find(kopf)?.groupValues?.get(1)?.trim()?.removeSurrounding("\"")
            return (erweitert ?: einfach)
                ?.substringAfterLast('/')?.substringAfterLast('\\')
                ?.trim()
                ?.takeIf { it.isNotEmpty() && it != "." && it != ".." }
        }

        /** Grenzen aus `API.md`. */
        private const val MAX_AUFNAHME_BYTES = 25L * 1024 * 1024
        private const val MAX_VORLESE_ZEICHEN = 4000
        private const val MAX_BILDUNTERSCHRIFT = 4000

        /** Obergrenze fuer `POST /v1/videos` - auch die Oberflaeche prueft vorab. */
        const val MAX_VIDEO_BYTES = 1024L * 1024 * 1024

        /** Obergrenze fuer `POST /v1/dokumente` - auch die Oberflaeche prueft vorab. */
        const val MAX_DOKUMENT_BYTES = 50L * 1024 * 1024
    }

    private suspend fun <T> ruf(block: suspend (AssistentApi) -> T): T = withContext(Dispatchers.IO) {
        try {
            block(api())
        } catch (fehler: ApiFehler) {
            // Kommt schon fertig uebersetzt aus dem FehlerInterceptor.
            throw fehler
        } catch (fehler: SerializationException) {
            throw ApiFehler.UnlesbareAntwort(fehler)
        } catch (fehler: IOException) {
            // Verbindung kam nicht zustande - auf dem Handy meist inaktives Tailscale.
            throw ApiFehler.KeineVerbindung(fehler)
        }
    }
}

/** Dateikoerper, der mitzaehlt - fuer die Fortschrittsanzeige beim Video- und Dokument-Upload. */
private class FortschrittsKoerper(
    private val datei: File,
    private val typ: MediaType,
    private val melden: (Int) -> Unit,
) : RequestBody() {
    override fun contentType(): MediaType = typ

    override fun contentLength(): Long = datei.length()

    override fun writeTo(sink: BufferedSink) {
        val gesamt = contentLength().coerceAtLeast(1)
        var geschrieben = 0L
        var zuletzt = -1
        datei.source().use { quelle ->
            while (true) {
                val gelesen = quelle.read(sink.buffer, 64L * 1024)
                if (gelesen == -1L) break
                sink.flush()
                geschrieben += gelesen
                val prozent = (geschrieben * 100 / gesamt).toInt()
                if (prozent != zuletzt) {
                    zuletzt = prozent
                    melden(prozent)
                }
            }
        }
    }
}
