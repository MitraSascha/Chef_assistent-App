package de.chefassistent.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import java.util.UUID

/**
 * Was eine wartende Nachricht beim Nachsenden tun soll. Kein Vertragstyp -
 * lebt nur auf dem Geraet.
 *
 * @property art [ART_SPRACHE] (Aufnahme liegt daneben) oder [ART_TEXT].
 * @property gespraech_id Gespraech, in das die Nachricht gehoert. Gibt es das
 *   nicht mehr (404), geht sie in ein neues.
 * @property faden Ohne [gespraech_id]: Nachrichten, die hintereinander im selben
 *   neuen Chat entstanden. Die erste legt das Gespraech an, die uebrigen folgen
 *   ihr dorthin - sonst entstuenden drei Gespraeche aus einer Unterhaltung.
 * @property antworten Nur Sprache: `false` heisst "Diktat pruefen". Die Aufnahme
 *   wird nur abgeschrieben, der Text kommt als [Rueckmeldung] zurueck in die App,
 *   und erst der Nutzer schickt ihn ab. Der Assistent fuehrt also auch nach dem
 *   Funkloch nichts aus, was falsch verstanden wurde.
 * @property vorspann Bezugszeilen (Projektkontext, gemeldete Dokumente), die vor
 *   den Text kommen. Bei Sprache heisst das: erst erkennen, dann mit Vorspann an
 *   den Chat - `/v1/sprache` hat kein Feld dafuer.
 * @property text Bei [ART_TEXT] die Nachricht, wie der Nutzer sie sah.
 * @property anfrage_id Kennung der Nachricht beim Server (v1.17.0, API.md 1b). Bleibt
 *   ueber alle Versuche gleich: der Versand fragt erst nach, ob sie schon ankam, und
 *   der Server fuehrt dieselbe Kennung nie zweimal aus. Aeltere Eintraege bekommen
 *   beim ersten Versuch eine.
 * @property bilder Fotopfade, die der Assistent zur Nachricht ansehen soll.
 */
@Serializable
data class AusgangAuftrag(
    val art: String = ART_SPRACHE,
    val aufgenommen: Long,
    val gespraech_id: String? = null,
    val faden: String? = null,
    val antworten: Boolean = true,
    val vorspann: String? = null,
    val text: String? = null,
    val anfrage_id: String? = null,
    val bilder: List<String> = emptyList(),
    /**
     * Seit v1.20.0: Modell und Denkaufwand von damals (API.md 1c). Gehen beim
     * Nachsenden unveraendert mit. `null` = Einstellung des Gespraechs bleibt.
     */
    val modell: String? = null,
    val denkaufwand: String? = null,
) {
    /** Die Wahl von damals fuer Chat und Sprache, `null`, wenn keine gesetzt war. */
    val wahl: ModellWahl?
        get() = if (modell == null && denkaufwand == null) null else ModellWahl(modell, denkaufwand)

    /** Was an `POST /v1/chat` geht. */
    val chatText: String
        get() = listOfNotNull(vorspann?.takeIf { it.isNotBlank() }, text).joinToString("\n\n")

    companion object {
        const val ART_SPRACHE = "sprache"
        const val ART_TEXT = "text"
    }
}

/** Eine Nachricht, die auf Netz wartet. */
data class WartendeNotiz(val name: String, val auftrag: AusgangAuftrag)

/**
 * Was der Versand im Hintergrund der App mitzuteilen hat und was nicht verloren
 * gehen darf: abgeschriebene Diktate zum Pruefen, Texte, die der Server
 * endgueltig abgelehnt hat, und Hinweise.
 */
@Serializable
data class Rueckmeldung(
    val id: String = UUID.randomUUID().toString(),
    val art: String,
    /** Wann die Nachricht entstand, nicht wann sie zurueckkam. */
    val zeit: Long,
    val text: String,
    val detail: String? = null,
) {
    companion object {
        /** Abgeschriebenes Diktat - gehoert ins Eingabefeld. */
        const val ART_DIKTAT = "diktat"

        /** Text, den der Server endgueltig abgelehnt hat - zum erneuten Senden. */
        const val ART_NICHT_GESENDET = "nicht_gesendet"

        /** Nur zur Kenntnis. */
        const val ART_HINWEIS = "hinweis"
    }
}

/**
 * Hebt Nachrichten auf, die mangels Verbindung nicht rausgehen konnten:
 * Sprachaufnahmen und getippte Texte.
 *
 * Der Grund fuer diese Klasse ist der Heizungskeller: genau dort, wo der
 * Assistent am dringendsten gebraucht wird, ist kein Netz. Ohne Zwischenlager
 * waere der Gedanke weg, nicht nur die Verbindung.
 *
 * Die Dateien liegen **verschluesselt** in `filesDir/ausgang` - nicht im Cache,
 * den Android jederzeit leeren darf, und nicht im Klartext, weil auf einer
 * Sprachnotiz Kundennamen und Preise stehen koennen.
 *
 * Je Nachricht `<zeit>.<fassung>.json.enc` mit dem [AusgangAuftrag], bei Sprache
 * zusaetzlich `<zeit>.m4a.enc`. Wie das gegen halb geschriebene Dateien
 * absichert, steht bei [Tresor].
 *
 * **Nur eine Instanz** (`ChefAssistentApp.ausgangskorb`): die Sperren gelten pro
 * Objekt, und Oberflaeche und [VersandArbeiter] greifen gleichzeitig zu.
 */
class Ausgangskorb(private val context: Context) {

    private val tresor = Tresor(context)

    private val ordner: File
        get() = File(context.filesDir, "ausgang").apply { mkdirs() }

    /** Was sich nicht entschluesseln liess. Wird nicht geloescht, nur aus dem Weg geraeumt. */
    private val beiseite: File
        get() = File(context.filesDir, "ausgang_beiseite").apply { mkdirs() }

    /**
     * Nimmt eine Aufnahme auf, verschluesselt sie und loescht das Original.
     * Gibt den Namen im Korb zurueck, oder `null`, wenn das Ablegen selbst
     * scheitert - dann liegt die Aufnahme noch unverschluesselt da, und der
     * Aufrufer muss das sagen.
     */
    @Synchronized
    fun ablegen(aufnahme: File, auftrag: AusgangAuftrag): String? {
        val name = freierName(auftrag.aufgenommen)
        return try {
            tresor.verschluesseln(aufnahme, File(ordner, "$name$AUFNAHME"))
            // Zuletzt: die Begleitdaten machen den Eintrag sichtbar.
            tresor.fassungSchreiben(ordner, name, auftrag.copy(art = AusgangAuftrag.ART_SPRACHE), AusgangAuftrag.serializer())
            aufnahme.delete()
            name
        } catch (_: Exception) {
            File(ordner, "$name$AUFNAHME").delete()
            tresor.fassungenLoeschen(ordner, name)
            null
        }
    }

    /** Getippte Nachricht ablegen. `null`: Speichern gescheitert, der Text bleibt beim Aufrufer. */
    @Synchronized
    fun ablegenText(auftrag: AusgangAuftrag): String? {
        val name = freierName(auftrag.aufgenommen)
        return try {
            tresor.fassungSchreiben(ordner, name, auftrag.copy(art = AusgangAuftrag.ART_TEXT), AusgangAuftrag.serializer())
            name
        } catch (_: Exception) {
            tresor.fassungenLoeschen(ordner, name)
            null
        }
    }

    /**
     * Aelteste zuerst - die Reihenfolge der Gedanken bleibt erhalten.
     *
     * Aufnahmen aus App-Versionen vor den Begleitdaten haben keine. Sie gelten
     * als Sprachnotiz mit Antwort in ein neues Gespraech, so wie sie damals
     * gemeint waren - aber erst nach einer Minute, damit eine Aufnahme, die
     * gerade geschrieben wird, nicht dafuer gehalten wird.
     */
    @Synchronized
    fun liste(): List<WartendeNotiz> {
        // Nur Zeitstempel - die Rueckmeldungen liegen im selben Ordner.
        val mitDaten = tresor.namenMitFassung(ordner).filter { name -> name.all { it.isDigit() } }.toSet()
        val alteAufnahmen = ordner.listFiles { datei ->
            datei.name.endsWith(AUFNAHME) &&
                datei.name.removeSuffix(AUFNAHME) !in mitDaten &&
                System.currentTimeMillis() - datei.lastModified() > ALT_MS
        }?.map { it.name.removeSuffix(AUFNAHME) }.orEmpty()

        return (mitDaten + alteAufnahmen)
            .sortedBy { it.toLongOrNull() ?: 0L }
            .map { name ->
                val auftrag = tresor.fassungLesen(ordner, name, AusgangAuftrag.serializer())
                    ?: AusgangAuftrag(aufgenommen = name.toLongOrNull() ?: 0L)
                WartendeNotiz(name, auftrag)
            }
    }

    fun anzahl(): Int = liste().size

    /** Wartet in diesem Gespraech oder Faden noch etwas? Dann darf nichts Neues vorbei. */
    fun wartetIn(gespraechId: String?, faden: String?): Boolean = liste().any {
        (gespraechId != null && it.auftrag.gespraech_id == gespraechId) ||
            (faden != null && it.auftrag.faden == faden)
    }

    /**
     * Entschluesselt die Aufnahme in den Cache, damit sie hochgeladen werden kann.
     * Der Aufrufer loescht die Kopie nach dem Senden.
     */
    internal fun auspacken(notiz: WartendeNotiz): Auspacken {
        val ziel = File(context.cacheDir, "ausgang_${notiz.name}.m4a")
        return tresor.auspacken(File(ordner, "${notiz.name}$AUFNAHME"), ziel)
    }

    /** Neue Begleitdaten, etwa wenn aus der Aufnahme Text geworden ist. */
    @Synchronized
    fun auftragAendern(name: String, auftrag: AusgangAuftrag) {
        tresor.fassungSchreiben(ordner, name, auftrag, AusgangAuftrag.serializer())
    }

    /** Die Aufnahme ist abgeschrieben, ab jetzt reicht der Text. */
    @Synchronized
    fun zuText(name: String, auftrag: AusgangAuftrag, text: String) {
        auftragAendern(name, auftrag.copy(art = AusgangAuftrag.ART_TEXT, text = text))
        File(ordner, "$name$AUFNAHME").delete()
    }

    /**
     * Die erste Nachricht eines Fadens hat ein Gespraech angelegt: die uebrigen
     * gehen in dasselbe.
     */
    @Synchronized
    fun fadenFestlegen(faden: String, gespraechId: String) {
        liste().filter { it.auftrag.faden == faden && it.auftrag.gespraech_id == null }.forEach {
            auftragAendern(it.name, it.auftrag.copy(gespraech_id = gespraechId))
        }
        // Damit der offene Chat erfaehrt, wohin seine Nachrichten gegangen sind -
        // auch wenn danach nichts mehr aus dem Faden wartet.
        val bekannt = faeden().filterKeys { it != faden }.entries.toList().takeLast(MAX_FAEDEN - 1)
        val neu = bekannt.associate { it.key to it.value } + (faden to gespraechId)
        tresor.fassungSchreiben(ordner, FAEDEN, neu, MapSerializer(String.serializer(), String.serializer()))
    }

    /** Gespraech, das die erste Nachricht eines Fadens angelegt hat, sonst `null`. */
    @Synchronized
    fun gespraechZuFaden(faden: String): String? = faeden()[faden]

    private fun faeden(): Map<String, String> =
        tresor.fassungLesen(ordner, FAEDEN, MapSerializer(String.serializer(), String.serializer())).orEmpty()

    @Synchronized
    fun entfernen(name: String) {
        File(ordner, "$name$AUFNAHME").delete()
        tresor.fassungenLoeschen(ordner, name)
    }

    /**
     * Laesst sich nicht entschluesseln - etwa nach einem Geraetewechsel ohne den
     * Schluessel. Loeschen waere endgueltig, liegenlassen blockierte den Korb.
     * Also aus dem Weg, aber aufgehoben. Umbenennen geht, weil der Name gleich bleibt.
     */
    @Synchronized
    fun beiseitelegen(name: String) {
        tresor.alleDateien(ordner, name).forEach { it.renameTo(File(beiseite, it.name)) }
    }

    // --- Rueckmeldungen -------------------------------------------------------

    @Synchronized
    fun rueckmeldungen(): List<Rueckmeldung> =
        tresor.fassungLesen(ordner, RUECKMELDUNGEN, ListSerializer(Rueckmeldung.serializer())).orEmpty()

    @Synchronized
    fun rueckmelden(rueckmeldung: Rueckmeldung) {
        val neu = rueckmeldungen() + rueckmeldung
        tresor.fassungSchreiben(ordner, RUECKMELDUNGEN, neu, ListSerializer(Rueckmeldung.serializer()))
    }

    @Synchronized
    fun rueckmeldungErledigt(id: String) {
        val neu = rueckmeldungen().filterNot { it.id == id }
        tresor.fassungSchreiben(ordner, RUECKMELDUNGEN, neu, ListSerializer(Rueckmeldung.serializer()))
    }

    /** Eindeutiger Name je Eintrag. Ziffern, damit die Sortierung nach Zeit stimmt. */
    private fun freierName(zeit: Long): String {
        var name = zeit
        while (ordner.listFiles { datei -> datei.name.startsWith("$name.") }?.isNotEmpty() == true) name++
        return name.toString()
    }

    private companion object {
        const val AUFNAHME = ".m4a.enc"

        /** Keine Ziffern, faellt also nie in die Liste der Nachrichten. */
        const val RUECKMELDUNGEN = "rueckmeldungen"
        const val FAEDEN = "faeden"
        const val MAX_FAEDEN = 20

        const val ALT_MS = 60_000L
    }
}
