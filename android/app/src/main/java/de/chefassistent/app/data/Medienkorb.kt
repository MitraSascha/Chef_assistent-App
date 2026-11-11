package de.chefassistent.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Begleitdaten eines wartenden Fotos, Videos oder Dokuments. Kein Vertragstyp -
 * lebt nur auf dem Geraet.
 *
 * @property serie Medien, die ohne feste Notiz in Folge entstanden sind. Sobald
 *   das erste davon angekommen ist, bekommen die restlichen dessen Notiz -
 *   sonst landeten sie in der Notiz, die *beim Nachsenden* die neueste ist.
 * @property abschreiben Nur bei Videos: `auto`, `immer` oder `nie`.
 * @property dateiname Nur bei Dokumenten: Originalname mit Endung. Der Server
 *   legt die Datei darunter ab und erkennt daran den Typ.
 */
@Serializable
data class MedienAuftrag(
    val art: String = ART_FOTO,
    val aufgenommen: Long,
    val notiz: String? = null,
    val text: String = "",
    val gespraech_id: String? = null,
    val serie: String? = null,
    val abschreiben: String? = null,
    val dateiname: String? = null,
    /**
     * Ohne [gespraech_id]: Faden des neuen Chats, in dem das Medium entstand
     * (wie [AusgangAuftrag.faden]). Beim Senden wird daraus das Gespraech, sobald
     * die erste Nachricht des Chats eins angelegt hat.
     */
    val faden: String? = null,
    /**
     * Seit 1.35.0: Kennung beim Hochladen in Stuecken (API.md 4h). Bleibt ueber alle
     * Versuche gleich, damit es nach einem Abbruch an der letzten Stelle weitergeht.
     */
    val upload_id: String? = null,
    /** Groesse des Klartexts in Bytes. `null` bei Eintraegen aus aelteren Versionen. */
    val groesse: Long? = null,
    /**
     * Video, das noch verkleinert werden muss. Das Original liegt dann als
     * `<name>.roh.<endung>` daneben (Auftrag 1.35: Senden darf die App nicht blockieren,
     * ein 15-Minuten-Video umzurechnen dauert Minuten).
     */
    val aufbereiten: Boolean = false,
) {
    /** Dateiname fuer den Server. Bei Dokumenten der Originalname, sonst nach der Art. */
    val serverDateiname: String
        get() = when (art) {
            ART_DOKUMENT -> dateiname ?: "Dokument"
            ART_VIDEO -> "Video_$aufgenommen.mp4"
            else -> "Foto_$aufgenommen.jpg"
        }

    companion object {
        const val ART_FOTO = "foto"
        const val ART_VIDEO = "video"
        const val ART_DOKUMENT = "dokument"
    }
}

/** Ein Medium im Korb: verschluesselte Datei plus Begleitdaten. */
data class WartendesMedium(val name: String, val auftrag: MedienAuftrag)

/**
 * Hebt Fotos, Videos und Dokumente auf, bis sie beim Server sind.
 *
 * Fotos und Dokumente landen hier nur, wenn keine Verbindung besteht oder
 * schon etwas wartet. Videos kommen **immer** hierher: sie sind gross, und der
 * Upload soll laufen, ohne dass jemand die App offen haelt (`API.md`).
 *
 * Alle Arten teilen sich bewusst **einen** Korb: was zu einer Begehung oder
 * einem Angebot gehoert, soll in dieselbe Notiz und in der Reihenfolge, in der
 * es entstand.
 *
 * Gleiches Prinzip wie der [Ausgangskorb]: verschluesselt in `filesDir`, nicht
 * im Cache und nicht im Klartext - auf Baustellenaufnahmen sind Namensschilder,
 * Zaehlerstaende und Wohnungen zu sehen, in der Unterschrift Kundennamen.
 *
 * Je Medium zwei Dateien: `<zeit>.medium.enc` und `<zeit>.<fassung>.json.enc`.
 * Wie das gegen halb geschriebene Dateien absichert, steht bei [Tresor].
 *
 * **Nur eine Instanz** (`ChefAssistentApp.medienkorb`): die Sperren gelten pro
 * Objekt, und Oberflaeche und [VersandArbeiter] greifen gleichzeitig zu.
 */
class Medienkorb(private val context: Context) {

    private val tresor = Tresor(context)

    private val ordner: File
        get() = File(context.filesDir, "medienausgang").apply { mkdirs() }

    /** Was sich nicht entschluesseln liess. Wird nicht geloescht, nur aus dem Weg geraeumt. */
    private val beiseite: File
        get() = File(context.filesDir, "medienausgang_beiseite").apply { mkdirs() }

    /**
     * Legt eine bereits aufbereitete Datei verschluesselt ab und loescht sie.
     * `false` heisst: das Ablegen ist gescheitert, die Datei bleibt liegen.
     */
    @Synchronized
    fun ablegen(datei: File, auftrag: MedienAuftrag): Boolean {
        var zahl = auftrag.aufgenommen
        while (tresor.alleDateien(ordner, zahl.toString()).isNotEmpty()) zahl++
        val name = zahl.toString()
        val medium = File(ordner, "$name$MEDIUM")

        return try {
            tresor.verschluesseln(datei, medium)
            // Zuletzt: die Begleitdaten machen den Eintrag sichtbar. Bis dahin
            // laesst [liste] das halb geschriebene Medium in Ruhe.
            val mitStand = auftrag.copy(groesse = datei.length(), upload_id = auftrag.upload_id ?: UploadStart.neueId())
            tresor.fassungSchreiben(ordner, name, mitStand, MedienAuftrag.serializer())
            datei.delete()
            true
        } catch (_: Exception) {
            medium.delete()
            tresor.fassungenLoeschen(ordner, name)
            false
        }
    }

    /**
     * Video, das erst noch verkleinert wird (1.35.0): das Original wird nur
     * verschoben, nicht verschluesselt - der [VersandArbeiter] rechnet es spaeter um
     * und legt erst das Ergebnis verschluesselt ab. So ist der Dialog sofort zu,
     * auch bei einem Video von 2 GB. Das Original lag vorher genauso unverschluesselt
     * im Cache der Kamera, liegt jetzt aber in `filesDir`, das Android nicht leert.
     */
    @Synchronized
    fun ablegenRoh(datei: File, auftrag: MedienAuftrag): Boolean {
        var zahl = auftrag.aufgenommen
        while (tresor.alleDateien(ordner, zahl.toString()).isNotEmpty()) zahl++
        val name = zahl.toString()
        val endung = datei.extension.filter { it.isLetterOrDigit() }.take(5).ifEmpty { "mp4" }
        val roh = File(ordner, "$name$ROH.$endung")
        return try {
            if (!datei.renameTo(roh)) {
                datei.copyTo(roh, overwrite = true)
                datei.delete()
            }
            tresor.fassungSchreiben(
                ordner, name,
                auftrag.copy(aufbereiten = true, upload_id = auftrag.upload_id ?: UploadStart.neueId()),
                MedienAuftrag.serializer(),
            )
            true
        } catch (_: Exception) {
            // Zurueck, wo es herkam: der Dialog haelt die Datei noch.
            if (roh.exists() && !datei.exists()) roh.renameTo(datei)
            roh.delete()
            tresor.fassungenLoeschen(ordner, name)
            false
        }
    }

    /** Das noch nicht verkleinerte Original eines Videos, sonst `null`. */
    fun rohDatei(name: String): File? =
        ordner.listFiles { d -> d.name.startsWith("$name$ROH.") }?.firstOrNull()

    /**
     * Das Video ist verkleinert: [klein] verschluesselt ablegen, neue Begleitdaten,
     * dann das Original weg. Stirbt die App dazwischen, gilt der Eintrag weiter als
     * unverkleinert und wird beim naechsten Mal neu umgerechnet.
     */
    @Synchronized
    fun aufbereitet(name: String, klein: File, auftrag: MedienAuftrag): MedienAuftrag {
        val neu = auftrag.copy(aufbereiten = false, groesse = klein.length())
        tresor.verschluesseln(klein, File(ordner, "$name$MEDIUM"))
        tresor.fassungSchreiben(ordner, name, neu, MedienAuftrag.serializer())
        rohDatei(name)?.delete()
        return neu
    }

    /** Ob der Eintrag noch da ist - der Nutzer kann ihn im Ausgang loeschen. */
    fun existiert(name: String): Boolean = tresor.hatFassung(ordner, name)

    /** Die Datei entschluesselt als Strom, fuers Hochladen in Stuecken. */
    internal fun lesen(name: String): java.io.InputStream = tresor.lesen(File(ordner, "$name$MEDIUM"))

    /** Klartextgroesse, fuer Eintraege ohne [MedienAuftrag.groesse]. */
    internal fun groesse(name: String): Long = tresor.klartextGroesse(File(ordner, "$name$MEDIUM"))

    /**
     * Aelteste zuerst, nur fertig abgelegte Eintraege.
     *
     * Begleitdaten, die sich nicht lesen lassen, fuehren nicht zum Loeschen: das
     * Medium wird beiseitegelegt. Ein Medium ohne Begleitdaten wird gerade
     * geschrieben (oder das Ablegen ist abgestuerzt, dann lag die Aufnahme noch
     * im Dialog) und wird nicht angefasst.
     */
    @Synchronized
    fun liste(): List<WartendesMedium> =
        tresor.namenMitFassung(ordner)
            .filter { name -> File(ordner, "$name$MEDIUM").exists() || rohDatei(name) != null }
            .sortedBy { it.toLongOrNull() ?: 0L }
            .mapNotNull { name ->
                val auftrag = tresor.fassungLesen(ordner, name, MedienAuftrag.serializer())
                if (auftrag == null) {
                    beiseitelegen(name)
                    null
                } else {
                    WartendesMedium(name, auftrag)
                }
            }

    fun anzahl(): Int = liste().size

    /** Entschluesselt in den Cache. Der Aufrufer loescht die Kopie. */
    internal fun auspacken(medium: WartendesMedium): Auspacken {
        val endung = when (medium.auftrag.art) {
            MedienAuftrag.ART_VIDEO -> "mp4"
            // Nur Buchstaben und Ziffern: der Name kommt von fremden Apps.
            MedienAuftrag.ART_DOKUMENT -> medium.auftrag.dateiname.orEmpty().substringAfterLast('.', "")
                .filter { it.isLetterOrDigit() }.take(8).ifEmpty { "bin" }
            else -> "jpg"
        }
        val ziel = File(context.cacheDir, "medienausgang_${medium.name}.$endung")
        return tresor.auspacken(File(ordner, "${medium.name}$MEDIUM"), ziel)
    }

    /**
     * Die erste Antwort einer Serie steht fest: alle noch wartenden Medien
     * derselben Serie gehen in dieselbe Notiz.
     */
    @Synchronized
    fun serieFestlegen(serie: String, notiz: String) {
        liste().filter { it.auftrag.serie == serie && it.auftrag.notiz == null }.forEach {
            auftragAendern(it.name, it.auftrag.copy(notiz = notiz))
        }
    }

    /**
     * Ersetzt die Begleitdaten, etwa wenn ein Feld beim Server nicht mehr passt.
     * Erst die neue Fassung, dann faellt die alte weg - ein Absturz dazwischen
     * kostet hoechstens die Aenderung, nie das Medium.
     */
    @Synchronized
    fun auftragAendern(name: String, auftrag: MedienAuftrag) {
        tresor.fassungSchreiben(ordner, name, auftrag, MedienAuftrag.serializer())
    }

    @Synchronized
    fun entfernen(name: String) {
        // Begleitdaten zuerst: ab da gilt der Eintrag als weg (auch fuer einen laufenden Upload).
        tresor.fassungenLoeschen(ordner, name)
        // Dann alles andere, auch ein noch unverkleinertes Original (`.roh.mp4`).
        tresor.alleDateien(ordner, name).forEach { it.delete() }
    }

    /** Siehe [Ausgangskorb.beiseitelegen]. */
    @Synchronized
    fun beiseitelegen(name: String) {
        tresor.alleDateien(ordner, name).forEach { it.renameTo(File(beiseite, it.name)) }
    }

    private companion object {
        const val MEDIUM = ".medium.enc"
        const val ROH = ".roh"
    }
}
