package de.chefassistent.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import kotlin.math.max

/**
 * Bereitet Kamerafotos fuer den Versand auf.
 *
 * Beim Neukodieren gehen die EXIF-Daten verloren - auch die Drehung. Deshalb
 * wird die Drehung hier ins Bild selbst uebernommen, sonst laege ein hochkant
 * fotografierter Heizkessel beim Server auf der Seite.
 */
object FotoAufbereitung {

    /** Lange Seite, die der Server ohnehin behaelt (`API.md`). */
    private const val LANGE_SEITE = 2560
    private const val JPEG_QUALITAET = 85

    /**
     * Schreibt eine verkleinerte, gedrehte JPEG-Kopie neben [quelle].
     * Laesst sich das Bild nicht dekodieren, geht das Original unveraendert
     * raus - der Server antwortet dann selbst mit 415. Dasselbe gilt, wenn das
     * Verkleinern scheitert (Speicher voll, zu wenig Arbeitsspeicher): ein
     * grosses Foto ist besser als keins.
     */
    fun verkleinern(quelle: File): File {
        val ziel = File(quelle.parentFile, "${quelle.nameWithoutExtension}_versand.jpg")
        return try {
            val bild = laden(quelle, LANGE_SEITE) ?: return quelle
            val geschrieben = try {
                ziel.outputStream().use { bild.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITAET, it) }
            } finally {
                bild.recycle()
            }
            if (geschrieben && ziel.length() > 0) ziel else quelle.also { ziel.delete() }
        } catch (_: Exception) {
            ziel.delete()
            quelle
        } catch (_: OutOfMemoryError) {
            ziel.delete()
            quelle
        }
    }

    /** Kleines, richtig gedrehtes Vorschaubild fuer den Dialog. */
    fun vorschau(quelle: File, langeSeite: Int = 720): Bitmap? = laden(quelle, langeSeite)

    private fun laden(quelle: File, langeSeite: Int): Bitmap? {
        val masse = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(quelle.path, masse)
        if (masse.outWidth <= 0 || masse.outHeight <= 0) return null

        // Grob per Zweierpotenz verkleinern schont den Speicher; den Rest
        // erledigt die Matrix unten.
        var teiler = 1
        while (max(masse.outWidth, masse.outHeight) / (teiler * 2) >= langeSeite) teiler *= 2

        val roh = BitmapFactory.decodeFile(
            quelle.path,
            BitmapFactory.Options().apply { inSampleSize = teiler },
        ) ?: return null

        val faktor = minOf(1f, langeSeite.toFloat() / max(roh.width, roh.height))
        val matrix = Matrix().apply {
            postScale(faktor, faktor)
            postRotate(drehung(quelle))
        }
        if (matrix.isIdentity) return roh

        val fertig = Bitmap.createBitmap(roh, 0, 0, roh.width, roh.height, matrix, true)
        if (fertig != roh) roh.recycle()
        return fertig
    }

    private fun drehung(datei: File): Float = runCatching {
        when (
            ExifInterface(datei.path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }.getOrDefault(0f)
}
