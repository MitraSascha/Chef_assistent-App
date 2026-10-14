package de.chefassistent.app.data

import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File

/**
 * Spielt das MP3 aus `POST /v1/vorlesen` ab.
 *
 * Gegenstueck zu [Sprachausgabe]: dort spricht das Geraet selbst, hier kommt die
 * fertige Stimme vom Server. Bewusst [MediaPlayer] und nicht ExoPlayer - es geht
 * um eine einzelne lokale Datei, dafuer braucht es keine Abspielbibliothek.
 */
class Tonwiedergabe {

    private var spieler: MediaPlayer? = null

    /**
     * Die gerade abgespielte Datei. Wird mitgefuehrt, damit auch ein Abbruch
     * durch [stoppen] sie wegraeumt - sonst sammeln sich MP3s im Cache an,
     * weil der Abschluss-Rueckruf dann nie kommt.
     */
    private var aktuelleDatei: File? = null

    val laeuft: Boolean get() = spieler?.isPlaying == true

    /**
     * Spielt [datei] ab und ruft [beiEnde] auf, wenn das Stueck durch ist, mit
     * einem Fehler abbrach oder gar nicht erst startete - dann genau einmal, sonst
     * haengt die Oberflaeche im Zustand "spricht". **Nicht** bei [stoppen] oder
     * einem neuen [abspielen]: wer stoppt, setzt seinen Zustand selbst zurueck.
     */
    fun abspielen(datei: File, beiEnde: () -> Unit) {
        stoppen()

        val neuer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
        }

        aktuelleDatei = datei

        var beendet = false
        val einmalBeenden = {
            if (!beendet) {
                beendet = true
                aufraeumenDatei()
                beiEnde()
            }
        }

        try {
            neuer.setDataSource(datei.absolutePath)
            neuer.setOnCompletionListener {
                aufraeumen()
                einmalBeenden()
            }
            neuer.setOnErrorListener { _, _, _ ->
                aufraeumen()
                einmalBeenden()
                true
            }
            neuer.prepare()
            neuer.start()
            spieler = neuer
        } catch (_: Exception) {
            neuer.release()
            spieler = null
            einmalBeenden()
        }
    }

    fun stoppen() {
        spieler?.let { alter ->
            runCatching { alter.stop() }
            alter.release()
        }
        spieler = null
        aufraeumenDatei()
    }

    private fun aufraeumen() {
        spieler?.release()
        spieler = null
    }

    private fun aufraeumenDatei() {
        aktuelleDatei?.delete()
        aktuelleDatei = null
    }
}
