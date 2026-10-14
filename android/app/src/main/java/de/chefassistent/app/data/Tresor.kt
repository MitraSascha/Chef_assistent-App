// EncryptedFile ist in security-crypto 1.1.0 als veraltet markiert, ohne dass
// Google einen Nachfolger benannt haette. Siehe auch Einstellungen.kt.
@file:Suppress("DEPRECATION")

package de.chefassistent.app.data

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * Verschluesselte Ablage fuer [Ausgangskorb] und [Medienkorb].
 *
 * **EncryptedFile bindet den Dateinamen in die Verschluesselung ein.** Eine Datei
 * laesst sich deshalb nicht unter einem Hilfsnamen fertig schreiben und dann
 * umbenennen - sie waere danach unlesbar. Abgesichert wird stattdessen so:
 *
 * - Grosse Dateien (Aufnahme, Foto, Video) werden zuerst geschrieben.
 * - Die kleinen Begleitdaten kommen **zuletzt** und gelten als Zeichen "fertig".
 *   Ein Eintrag ohne Begleitdaten wird noch geschrieben und nicht angefasst.
 * - Begleitdaten tragen eine Fassungsnummer im Namen (`<name>.<fassung>.json.enc`).
 *   Eine Aenderung schreibt eine neue Fassung, erst danach faellt die alte weg.
 *   Stirbt die App dazwischen, bleibt die alte lesbar.
 */
internal class Tresor(private val context: Context) {

    private val schluessel by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** Verschluesselt [quelle] nach [ziel]. Scheitert es, ist [ziel] wieder weg. */
    fun verschluesseln(quelle: File, ziel: File) {
        try {
            datei(ziel).openFileOutput().use { aus ->
                quelle.inputStream().use { ein -> ein.copyTo(aus) }
            }
        } catch (fehler: Exception) {
            ziel.delete()
            throw fehler
        }
    }

    /** Entschluesselt [quelle] nach [ziel]. Scheitert es, ist [ziel] wieder weg. */
    fun entschluesseln(quelle: File, ziel: File) {
        try {
            datei(quelle).openFileInput().use { ein ->
                ziel.outputStream().use { aus -> ein.copyTo(aus) }
            }
        } catch (fehler: Exception) {
            ziel.delete()
            throw fehler
        }
    }

    /**
     * Ob [groesse] Bytes entschluesselt in den Cache passen. Ist kein Platz, ist ein
     * Fehler beim Auspacken voruebergehend und kein Grund, die Aufnahme aufzugeben.
     */
    fun platzFuer(groesse: Long): Boolean =
        context.cacheDir.usableSpace > groesse + PLATZ_RESERVE

    fun <T> fassungSchreiben(ordner: File, name: String, wert: T, serializer: KSerializer<T>) {
        val alte = fassungen(ordner, name)
        val naechste = (alte.maxOfOrNull { it.first } ?: -1) + 1
        val ziel = File(ordner, "$name.$naechste$DATEN")
        try {
            datei(ziel).openFileOutput().use {
                it.write(json.encodeToString(serializer, wert).toByteArray())
            }
        } catch (fehler: Exception) {
            ziel.delete()
            throw fehler
        }
        alte.forEach { it.second.delete() }
    }

    /**
     * Neueste lesbare Fassung. `null`, wenn es keine gibt oder keine lesbar ist -
     * der Aufrufer unterscheidet das mit [hatFassung].
     */
    fun <T> fassungLesen(ordner: File, name: String, serializer: KSerializer<T>): T? =
        fassungen(ordner, name).sortedByDescending { it.first }.firstNotNullOfOrNull { (_, datei) ->
            try {
                val text = datei(datei).openFileInput().use { it.readBytes().decodeToString() }
                json.decodeFromString(serializer, text)
            } catch (_: Exception) {
                null
            }
        }

    fun hatFassung(ordner: File, name: String): Boolean = fassungen(ordner, name).isNotEmpty()

    fun fassungenLoeschen(ordner: File, name: String) {
        fassungen(ordner, name).forEach { it.second.delete() }
    }

    /** Alle Namen im Ordner, zu denen es Begleitdaten gibt. */
    fun namenMitFassung(ordner: File): Set<String> =
        ordner.listFiles()?.mapNotNull { zerlegen(it.name)?.first }?.toSet().orEmpty()

    /** Alle Dateien eines Eintrags - fuers Beiseitelegen. */
    fun alleDateien(ordner: File, name: String): List<File> =
        ordner.listFiles { datei -> datei.name.startsWith("$name.") }?.toList().orEmpty()

    private fun fassungen(ordner: File, name: String): List<Pair<Int, File>> =
        ordner.listFiles()?.mapNotNull { datei ->
            zerlegen(datei.name)?.takeIf { it.first == name }?.let { it.second to datei }
        }.orEmpty()

    private fun datei(datei: File) = EncryptedFile.Builder(
        context,
        datei,
        schluessel,
        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
    ).build()

    companion object {
        const val DATEN = ".json.enc"

        /** Platz, der beim Auspacken im Cache frei bleiben soll. */
        private const val PLATZ_RESERVE = 50L * 1024 * 1024

        private val FASSUNG = Regex("""^([^.]+)\.(\d+)\.json\.enc$""")

        /** Vor den Fassungsnummern: `<name>.json.enc` gilt als Fassung 0. */
        private val ALT = Regex("""^([^.]+)\.json\.enc$""")

        /** Name und Fassung aus einem Dateinamen, `null` fuer alles andere. */
        fun zerlegen(dateiname: String): Pair<String, Int>? {
            FASSUNG.matchEntire(dateiname)?.let { return it.groupValues[1] to it.groupValues[2].toInt() }
            ALT.matchEntire(dateiname)?.let { return it.groupValues[1] to 0 }
            return null
        }
    }
}

/** Beim Auspacken: voruebergehend (kein Platz) oder endgueltig (nicht zu entschluesseln). */
internal sealed interface Auspacken {
    data class Fertig(val datei: File) : Auspacken
    data object KeinPlatz : Auspacken
    data class Defekt(val ursache: Exception) : Auspacken
}

internal fun Tresor.auspacken(quelle: File, ziel: File): Auspacken {
    if (!platzFuer(quelle.length())) return Auspacken.KeinPlatz
    return try {
        entschluesseln(quelle, ziel)
        Auspacken.Fertig(ziel)
    } catch (fehler: IOException) {
        // Platz kann waehrend des Schreibens knapp werden. Dann ist die Datei
        // nicht kaputt, nur der Moment schlecht.
        if (!platzFuer(quelle.length())) Auspacken.KeinPlatz else Auspacken.Defekt(fehler)
    } catch (fehler: Exception) {
        Auspacken.Defekt(fehler)
    }
}
