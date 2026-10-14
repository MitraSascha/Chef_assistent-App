package de.chefassistent.app.data

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Nimmt Sprache fuer `POST /v1/sprache` auf.
 *
 * Format nach `API.md`: MPEG_4 mit AAC, mono, 44,1 kHz. Der Server akzeptiert
 * laut Vertrag auch mp3/wav/webm/ogg - m4a ist das, was Android ohne Umwege kann.
 */
class AudioAufnahme(private val context: Context) {

    private var rekorder: MediaRecorder? = null
    private var datei: File? = null

    val laeuft: Boolean get() = rekorder != null

    /**
     * Startet die Aufnahme und gibt die Zieldatei zurueck.
     * Wirft [IllegalStateException], wenn bereits eine Aufnahme laeuft.
     */
    fun starten(): File {
        check(rekorder == null) { "Es laeuft bereits eine Aufnahme." }

        val ordner = File(context.cacheDir, "aufnahmen").apply { mkdirs() }
        val ziel = File(ordner, "aufnahme_${System.currentTimeMillis()}.m4a")

        val neuer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        try {
            neuer.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(44_100)
                setAudioEncodingBitRate(96_000)
                setOutputFile(ziel.absolutePath)
                prepare()
                start()
            }
        } catch (fehler: Exception) {
            // Mikrofon belegt oder Berechtigung entzogen: freigeben, sonst haelt
            // der native Rekorder das Mikrofon womoeglich weiter fest.
            neuer.release()
            ziel.delete()
            throw fehler
        }

        rekorder = neuer
        datei = ziel
        return ziel
    }

    /**
     * Lautstaerke seit dem letzten Aufruf, 0 bis 32767.
     *
     * Der Wert wird bei jedem Abruf zurueckgesetzt - regelmaessig abfragen, sonst
     * misst man den Spitzenwert seit Aufnahmebeginn. Wird im Freihandmodus
     * benutzt, um zu erkennen, wann jemand aufgehoert hat zu sprechen.
     */
    fun maximalerAusschlag(): Int = try {
        rekorder?.maxAmplitude ?: 0
    } catch (_: IllegalStateException) {
        0
    }

    /**
     * Beendet die Aufnahme und gibt die fertige Datei zurueck, oder `null`, wenn
     * nichts Brauchbares entstanden ist.
     *
     * `stop()` wirft, wenn zu kurz aufgenommen wurde (Nutzer tippt zweimal schnell).
     * Das ist kein Fehler, den jemand sehen muss - die Datei wird dann verworfen.
     */
    fun beenden(): File? {
        val laufender = rekorder ?: return null
        val ziel = datei
        rekorder = null
        datei = null

        val erfolgreich = try {
            laufender.stop()
            true
        } catch (_: RuntimeException) {
            false
        } finally {
            laufender.release()
        }

        if (!erfolgreich || ziel == null || !ziel.exists() || ziel.length() == 0L) {
            ziel?.delete()
            return null
        }
        return ziel
    }

    /** Bricht ab und loescht die Datei - etwa wenn der Bildschirm verlassen wird. */
    fun abbrechen() {
        beenden()?.delete()
    }
}
