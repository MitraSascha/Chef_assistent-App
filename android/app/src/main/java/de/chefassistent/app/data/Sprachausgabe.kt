package de.chefassistent.app.data

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Liest Antworten mit der Android-Sprachausgabe vor.
 *
 * `API.md` nennt zwei Wege. Dies ist der erste: kostenlos, sofort, offline.
 * Der zweite (`POST /v1/vorlesen`, natuerlichere Stimme, kostet) steckt in
 * [AssistentRepository.vorlesen].
 *
 * Der Text muss vorher durch `markdownEntfernen()` - sonst werden Sternchen
 * mitgesprochen.
 */
class Sprachausgabe(context: Context) {

    private val motor: TextToSpeech

    // Die Rueckrufe kommen auf einem Thread der Sprachausgabe an.
    @Volatile
    private var bereit = false

    /** Kennung der letzten Aeusserung eines Vorlesens - erst deren Ende zaehlt. */
    @Volatile
    private var letzteKennung: String? = null

    @Volatile
    private var beiEnde: (() -> Unit)? = null

    private var zaehler = 0

    init {
        motor = TextToSpeech(context.applicationContext) { status ->
            bereit = if (status != TextToSpeech.SUCCESS) {
                false
            } else {
                // Kein Fallback auf Englisch: eine deutsche Antwort englisch
                // vorgelesen ist unbrauchbar. Fehlt Deutsch, bleibt es stumm.
                val ergebnis = motor.setLanguage(Locale.GERMANY)
                ergebnis != TextToSpeech.LANG_MISSING_DATA &&
                    ergebnis != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }

        motor.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) = beendet(utteranceId)

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onError(utteranceId: String?) = beendet(utteranceId)

            override fun onError(utteranceId: String?, errorCode: Int) = beendet(utteranceId)
        })
    }

    val verfuegbar: Boolean get() = bereit

    /**
     * Liest [text] vor und ruft [beiEnde] auf, wenn alles gesprochen ist oder es
     * nicht ging. Lange Texte werden in Stuecke geteilt: die Sprachausgabe nimmt
     * nur [TextToSpeech.getMaxSpeechInputLength] Zeichen am Stueck und meldet bei
     * mehr gar nichts zurueck - der Freihandmodus bliebe dann im Zustand "spricht".
     */
    fun vorlesen(text: String, beiEnde: () -> Unit = {}) {
        if (!bereit || text.isBlank()) {
            beiEnde()
            return
        }
        val stuecke = stuecke(text, TextToSpeech.getMaxSpeechInputLength() - 100)
        val runde = ++zaehler
        val kennungen = stuecke.indices.map { "$AEUSSERUNG-$runde-$it" }
        this.beiEnde = beiEnde
        letzteKennung = kennungen.last()

        stuecke.forEachIndexed { i, stueck ->
            val modus = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            if (motor.speak(stueck, modus, null, kennungen[i]) != TextToSpeech.SUCCESS) {
                // Abgelehnt: es kommt kein Rueckruf mehr.
                letzteKennung = null
                this.beiEnde = null
                motor.stop()
                beiEnde()
                return
            }
        }
    }

    fun stoppen() {
        beiEnde = null
        letzteKennung = null
        motor.stop()
    }

    fun freigeben() {
        stoppen()
        motor.shutdown()
    }

    private fun beendet(kennung: String?) {
        // Nur das Ende des letzten Stuecks des aktuellen Vorlesens zaehlt -
        // nicht das eines abgebrochenen frueheren.
        if (kennung == null || kennung != letzteKennung) return
        letzteKennung = null
        val rueckruf = beiEnde
        beiEnde = null
        rueckruf?.invoke()
    }

    private companion object {
        const val AEUSSERUNG = "chef_assistent_antwort"

        /** Teilt an Absatz- oder Satzgrenzen, notfalls hart. */
        fun stuecke(text: String, max: Int): List<String> {
            if (text.length <= max) return listOf(text)
            val ergebnis = mutableListOf<String>()
            var rest = text
            while (rest.length > max) {
                val fenster = rest.substring(0, max)
                val schnitt = listOf("\n\n", "\n", ". ", "! ", "? ", ", ", " ")
                    .map { fenster.lastIndexOf(it).let { i -> if (i > max / 2) i + it.length else -1 } }
                    .firstOrNull { it > 0 } ?: max
                ergebnis += rest.substring(0, schnitt)
                rest = rest.substring(schnitt)
            }
            if (rest.isNotBlank()) ergebnis += rest
            return ergebnis
        }
    }
}
