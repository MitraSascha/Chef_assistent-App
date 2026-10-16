package de.chefassistent.app.monteur.data

import android.content.Context
import android.graphics.Bitmap
import de.chefassistent.app.data.FotoAufbereitung
import de.chefassistent.app.data.Netzwerk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/**
 * Ein Foto, das der Monteur gerade ins Projekt gelegt hat (Auftrag 1.29). [id] ist die
 * `anfrage_id` des Uploads im Ausgangskorb, [fotoId] kommt vom Server, sobald es oben ist.
 */
@Serializable
data class GeradeFoto(
    val id: String,
    val projektId: Int,
    /** Millisekunden seit 1970. */
    val zeit: Long,
    /** Kleine Vorschau im Cache, nur fuer die Leiste. */
    val vorschau: String,
    val fotoId: String? = null,
    /** Schon in einer Sprach-/Texteingabe verwendet - gilt dann nicht mehr automatisch als „gemeint“. */
    val verwendet: Boolean = false,
)

/**
 * Leiste „gerade aufgenommen“: jedes Foto geht sofort ins Projekt (ueber den Korb), hier
 * steht es fuer [FENSTER_MS], damit sich Sprechen oder Tippen darauf beziehen kann.
 * Nichts davon ist geheim (IDs, Vorschaupfade); die Bilder selbst liegen verschluesselt im Korb.
 */
class GeradeAufgenommen(private val kontext: Context) {
    private val prefs = kontext.getSharedPreferences("monteur_gerade", Context.MODE_PRIVATE)
    private val serializer = ListSerializer(GeradeFoto.serializer())

    private val _fotos = MutableStateFlow(laden())
    val fotos: StateFlow<List<GeradeFoto>> = _fotos.asStateFlow()

    /** Fotos dieses Projekts aus dem Zeitfenster, aelteste zuerst. */
    fun fuerProjekt(projektId: Int?, liste: List<GeradeFoto> = _fotos.value, jetzt: Long = System.currentTimeMillis()): List<GeradeFoto> =
        if (projektId == null) emptyList() else liste.filter { it.projektId == projektId && jetzt - it.zeit <= FENSTER_MS }

    @Synchronized
    fun hinzu(foto: GeradeFoto) = aendern { it + foto }

    /** Vom Korb nach erfolgreichem Upload. */
    @Synchronized
    fun fotoIdSetzen(id: String, fotoId: String) = aendern { l -> l.map { if (it.id == id) it.copy(fotoId = fotoId) else it } }

    fun fotoIdFuer(id: String): String? = _fotos.value.firstOrNull { it.id == id }?.fotoId

    @Synchronized
    fun alsVerwendet(ids: Collection<String>) = aendern { l -> l.map { if (it.id in ids) it.copy(verwendet = true) else it } }

    @Synchronized
    fun leeren() {
        _fotos.value.forEach { File(it.vorschau).delete() }
        aendern { emptyList() }
    }

    /** Vorschau (320 px) fuer die Leiste schreiben; das Original geht in den Korb. */
    fun vorschauSchreiben(original: File, id: String): String {
        val ordner = File(kontext.cacheDir, ORDNER).apply { mkdirs() }
        val ziel = File(ordner, "$id.jpg")
        runCatching {
            val bild = FotoAufbereitung.vorschau(original, 320) ?: android.graphics.BitmapFactory.decodeFile(original.path)
            bild?.let { b -> ziel.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 80, it) } }
        }
        return ziel.path
    }

    private fun aendern(f: (List<GeradeFoto>) -> List<GeradeFoto>) {
        _fotos.update { alt ->
            val jetzt = System.currentTimeMillis()
            // Abgelaufene fallen weg, ihre Vorschau auch.
            val neu = f(alt).filter { jetzt - it.zeit <= AUFHEBEN_MS }
            alt.filter { a -> neu.none { it.id == a.id } }.forEach { File(it.vorschau).delete() }
            prefs.edit().putString(K_LISTE, Netzwerk.json.encodeToString(serializer, neu)).apply()
            neu
        }
    }

    private fun laden(): List<GeradeFoto> {
        val jetzt = System.currentTimeMillis()
        return runCatching { Netzwerk.json.decodeFromString(serializer, prefs.getString(K_LISTE, "[]").orEmpty()) }
            .getOrDefault(emptyList())
            .filter { jetzt - it.zeit <= AUFHEBEN_MS && File(it.vorschau).exists() }
    }

    companion object {
        /** So lange steht ein Foto in der Leiste (Auftrag: z. B. 2 Stunden). */
        const val FENSTER_MS = 2 * 60 * 60 * 1000L

        /** Laenger aufheben, damit der Korb die ID auch nach einem langen Funkloch noch findet. */
        const val AUFHEBEN_MS = 48 * 60 * 60 * 1000L

        const val ORDNER = "gerade"
        private const val K_LISTE = "liste"
    }
}

/**
 * Welche Fotos sind „gemeint“, wenn der Monteur spricht oder tippt (Auftrag 1.29)?
 * Markierte, sonst automatisch das letzte noch nicht verwendete. Antippen des automatisch
 * gemeinten schaltet es ab ([autoAus]).
 */
data class FotoAuswahl(val markiert: Set<String> = emptySet(), val autoAus: String? = null) {
    fun gemeint(liste: List<GeradeFoto>): List<GeradeFoto> {
        val m = liste.filter { it.id in markiert }
        if (m.isNotEmpty()) return m
        val letztes = liste.lastOrNull() ?: return emptyList()
        return if (!letztes.verwendet && letztes.id != autoAus) listOf(letztes) else emptyList()
    }

    fun umschalten(foto: GeradeFoto, liste: List<GeradeFoto>): FotoAuswahl {
        val jetzt = gemeint(liste)
        return when {
            foto.id in markiert -> copy(markiert = markiert - foto.id)
            jetzt.any { it.id == foto.id } -> copy(autoAus = foto.id)
            // Das automatisch gemeinte bleibt dabei, wenn man ein zweites dazu antippt.
            else -> copy(markiert = markiert + jetzt.map { it.id } + foto.id)
        }
    }
}
