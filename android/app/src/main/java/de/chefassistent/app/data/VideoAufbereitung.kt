package de.chefassistent.app.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min

/** Laenge und kurze Seite eines Videos, soweit das Geraet sie lesen kann. */
data class VideoMasse(val dauerMs: Long, val kurzeSeite: Int)

/**
 * Verkleinert Kameravideos vor dem Upload auf 720p.
 *
 * Der Server rechnet bewusst nicht um (keine Grafikkarte, `API.md`). Ein
 * 5-Minuten-Video in 4K sind schnell 600 MB, in 720p mit 2,5 Mbit/s etwa 90 MB -
 * auf der Baustelle ueber Mobilfunk ist das der Unterschied zwischen "geht" und
 * "geht nicht".
 */
@UnstableApi
object VideoAufbereitung {

    private const val KURZE_SEITE = 720
    private const val BITRATE = 2_500_000

    fun masse(datei: File): VideoMasse? = runCatching {
        lesen(datei) { leser ->
            val dauer = leser.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val breite = leser.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val hoehe = leser.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            VideoMasse(dauer, min(breite, hoehe))
        }
    }.getOrNull()

    /** Standbild fuer den Dialog. Richtig gedreht liefert es Android selbst. */
    fun vorschau(datei: File): Bitmap? = runCatching {
        lesen(datei) { leser ->
            leser.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 720, 720)
        }
    }.getOrNull()

    /** `MediaMetadataRetriever` ist erst ab API 29 `AutoCloseable` - daher von Hand. */
    private fun <T> lesen(datei: File, block: (MediaMetadataRetriever) -> T): T {
        val leser = MediaMetadataRetriever()
        try {
            leser.setDataSource(datei.path)
            return block(leser)
        } finally {
            leser.release()
        }
    }

    /**
     * Rechnet auf 720p (kurze Seite) und H.264 um. Hochkant bleibt hochkant.
     * Ist das Video schon klein genug, wird nichts angefasst und [quelle]
     * zurueckgegeben.
     *
     * @param fortschritt 0-100, wird auf dem Hauptthread gemeldet.
     */
    suspend fun verkleinern(
        context: Context,
        quelle: File,
        fortschritt: (Int) -> Unit,
    ): File {
        val masse = masse(quelle)
        if (masse != null && masse.kurzeSeite in 1..KURZE_SEITE) return quelle

        val ziel = File(quelle.parentFile, "${quelle.nameWithoutExtension}_720p.mp4")
        ziel.delete()

        // Transformer verlangt einen Thread mit Looper - der Hauptthread hat einen.
        return withContext(Dispatchers.Main) {
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setEncoderFactory(
                    DefaultEncoderFactory.Builder(context)
                        .setRequestedVideoEncoderSettings(
                            VideoEncoderSettings.Builder().setBitrate(BITRATE).build()
                        )
                        .build()
                )
                .build()

            val beobachter = launch {
                val stand = ProgressHolder()
                while (true) {
                    if (transformer.getProgress(stand) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        fortschritt(stand.progress)
                    }
                    delay(500)
                }
            }

            try {
                suspendCancellableCoroutine { fortsetzung ->
                    transformer.addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, result: ExportResult) {
                            fortsetzung.resume(ziel)
                        }

                        override fun onError(
                            composition: Composition,
                            result: ExportResult,
                            fehler: ExportException,
                        ) {
                            ziel.delete()
                            fortsetzung.resumeWithException(fehler)
                        }
                    })

                    val eintrag = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(quelle)))
                        .setEffects(Effects(emptyList(), listOf(Presentation.createForShortSide(KURZE_SEITE))))
                        .build()
                    transformer.start(eintrag, ziel.path)

                    fortsetzung.invokeOnCancellation {
                        transformer.cancel()
                        ziel.delete()
                    }
                }
            } finally {
                beobachter.cancel()
            }
        }
    }
}
