package de.chefassistent.app.ui.ansehen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.chefassistent.app.data.ApiFehler
import de.chefassistent.app.data.AssistentRepository
import de.chefassistent.app.ui.komponenten.DateiArt
import de.chefassistent.app.ui.komponenten.dateiArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class AnsehenZustand(
    val datei: File? = null,
    /** Bei Text: der Inhalt, sonst `null`. */
    val text: String? = null,
    val laeuft: Boolean = true,
    val fehler: String? = null,
    val abgemeldet: Boolean = false,
)

/**
 * Laedt eine Datei der Ablage zum Ansehen in der App (Auftrag 1.21, B3) ueber
 * `GET /v1/datei?pfad=…&anzeigen=true`. Gezeigt wird sie in [AnsehenScreen].
 */
class AnsehenViewModel(
    /** Dateiname bzw. Pfad, entscheidet ueber die Ansicht (PDF, Bild, Text …). */
    val pfad: String,
    /** Laedt die Datei in den Cache. Buero: `GET /v1/datei`, Monteur: `GET /v1/monteur/dateien/{uuid}`. */
    private val quelle: suspend () -> File,
    /** Nach 401: Token lokal verwerfen. */
    private val abmelden: () -> Unit,
) : ViewModel() {

    private val _zustand = MutableStateFlow(AnsehenZustand())
    val zustand: StateFlow<AnsehenZustand> = _zustand.asStateFlow()

    init {
        laden()
    }

    fun neuLaden() = laden()

    private fun laden() {
        _zustand.update { it.copy(laeuft = true, fehler = null) }
        viewModelScope.launch {
            try {
                val datei = quelle()
                val text = if (dateiArt(pfad) in TEXTARTEN || pfad.endsWith(".csv", true) || pfad.endsWith(".tsv", true)) {
                    // Notizen sind klein; grosse Logdateien werden gekuerzt statt den Speicher zu sprengen.
                    withContext(Dispatchers.IO) { datei.bufferedReader().use { it.readTextBis(MAX_ZEICHEN) } }
                } else null
                _zustand.update { it.copy(datei = datei, text = text) }
            } catch (fehler: ApiFehler) {
                if (fehler is ApiFehler.NichtAngemeldet) {
                    abmelden()
                    _zustand.update { it.copy(abgemeldet = true) }
                } else {
                    _zustand.update { it.copy(fehler = fehler.anzeigetext) }
                }
            } finally {
                _zustand.update { it.copy(laeuft = false) }
            }
        }
    }

    companion object {
        private val TEXTARTEN = setOf(DateiArt.TEXT)
        private const val MAX_ZEICHEN = 400_000

        fun factory(repository: AssistentRepository, pfad: String) = viewModelFactory {
            initializer { AnsehenViewModel(pfad, { repository.dateiLaden(pfad, anzeigen = true) }, repository::abmelden) }
        }

        /** Beliebige Quelle, etwa die Projektmappe der Monteur-App. */
        fun factory(name: String, laden: suspend () -> File, abmelden: () -> Unit) = viewModelFactory {
            initializer { AnsehenViewModel(name, laden, abmelden) }
        }
    }
}

private fun java.io.Reader.readTextBis(grenze: Int): String {
    val puffer = CharArray(grenze)
    var gelesen = 0
    while (gelesen < grenze) {
        val n = read(puffer, gelesen, grenze - gelesen)
        if (n < 0) break
        gelesen += n
    }
    val text = String(puffer, 0, gelesen)
    return if (gelesen == grenze && read() >= 0) "$text\n\n…" else text
}

/**
 * Was die App selbst zeigen kann: PDF, Fotos, Videos, Text und Notizen.
 * Alles andere (Word, Excel …) geht wie bisher per ACTION_VIEW an eine andere App.
 */
fun kannAnsehen(pfad: String): Boolean {
    val endung = pfad.substringAfterLast('.', "").lowercase()
    // HEIC dekodiert BitmapFactory erst ab Android 9.
    if (endung == "heic") return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P
    return dateiArt(pfad) in setOf(DateiArt.PDF, DateiArt.BILD, DateiArt.VIDEO, DateiArt.TEXT) ||
        endung in setOf("csv", "tsv")
}
