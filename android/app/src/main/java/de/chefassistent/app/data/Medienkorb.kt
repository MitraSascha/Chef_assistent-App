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
) {
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
            tresor.fassungSchreiben(ordner, name, auftrag, MedienAuftrag.serializer())
            datei.delete()
            true
        } catch (_: Exception) {
            medium.delete()
            tresor.fassungenLoeschen(ordner, name)
            false
        }
    }

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
            .filter { name -> File(ordner, "$name$MEDIUM").exists() }
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
        File(ordner, "$name$MEDIUM").delete()
        tresor.fassungenLoeschen(ordner, name)
    }

    /** Siehe [Ausgangskorb.beiseitelegen]. */
    @Synchronized
    fun beiseitelegen(name: String) {
        tresor.alleDateien(ordner, name).forEach { it.renameTo(File(beiseite, it.name)) }
    }

    private companion object {
        const val MEDIUM = ".medium.enc"
    }
}
