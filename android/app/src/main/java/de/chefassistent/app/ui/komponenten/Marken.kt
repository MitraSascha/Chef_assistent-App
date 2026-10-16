package de.chefassistent.app.ui.komponenten

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.chefassistent.app.ui.theme.gold
import de.chefassistent.app.ui.theme.hauptflaeche

/**
 * Das „M“-Zeichen der Weboberflaeche (`.marke` in `app.css`): Navy-Quadrat mit
 * goldenem M. Steht vor jeder Antwort des Assistenten (Auftrag 1.21, B3).
 */
@Composable
fun MZeichen(modifier: Modifier = Modifier, groesse: Dp = 28.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(groesse)
            .background(MaterialTheme.colorScheme.hauptflaeche, RoundedCornerShape(groesse * 0.27f)),
    ) {
        Text(
            text = "M",
            color = MaterialTheme.colorScheme.gold,
            fontWeight = FontWeight.Bold,
            fontSize = (groesse.value * 0.5f).sp,
        )
    }
}

/** Dateiart fuer die Typmarke: Kuerzel und Farbton. */
enum class DateiArt { PDF, BILD, VIDEO, TABELLE, TEXT, WORD, ORDNER, SONST }

fun dateiArt(name: String, istOrdner: Boolean = false): DateiArt = if (istOrdner) DateiArt.ORDNER else
    when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> DateiArt.PDF
        "jpg", "jpeg", "png", "webp", "heic", "gif" -> DateiArt.BILD
        "mp4", "mov", "m4v", "webm" -> DateiArt.VIDEO
        "xls", "xlsx", "xlsm", "ods", "csv", "tsv" -> DateiArt.TABELLE
        "md", "txt", "log", "markdown" -> DateiArt.TEXT
        "doc", "docx", "odt" -> DateiArt.WORD
        else -> DateiArt.SONST
    }

/**
 * Kleine Marke mit Kuerzel statt 📄/🖼️ (Auftrag 1.21, B2): PDF rot getoent,
 * JPG/PNG blau, XLSX gruen, Ordner gold. Die Toene sind duenn deckend, damit
 * sie hell und dunkel funktionieren; die Schrift traegt die volle Farbe.
 */
@Composable
fun TypMarke(name: String, istOrdner: Boolean = false, modifier: Modifier = Modifier) {
    val art = dateiArt(name, istOrdner)
    val dunkel = MaterialTheme.colorScheme.background.let { (it.red + it.green + it.blue) / 3f < 0.5f }
    val farbe = when (art) {
        DateiArt.PDF -> if (dunkel) Color(0xFFF2A49C) else Color(0xFFB3261E)
        DateiArt.BILD, DateiArt.VIDEO -> if (dunkel) Color(0xFF8CC0EA) else Color(0xFF2A5F86)
        DateiArt.TABELLE -> if (dunkel) Color(0xFF7FD3A8) else Color(0xFF1E7A4C)
        DateiArt.WORD -> if (dunkel) Color(0xFF9DB4F0) else Color(0xFF2B4FA8)
        DateiArt.ORDNER -> if (dunkel) Color(0xFFE8B673) else Color(0xFF8A5A17)
        DateiArt.TEXT, DateiArt.SONST -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val grund = if (art == DateiArt.ORDNER) MaterialTheme.colorScheme.gold.copy(alpha = 0.22f) else farbe.copy(alpha = 0.14f)
    val kuerzel = when (art) {
        DateiArt.ORDNER -> "DIR"
        else -> name.substringAfterLast('.', "").uppercase().take(4).ifEmpty { "?" }
            .let { if (it == "JPEG") "JPG" else if (it == "MARKDOWN") "MD" else it }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width = 36.dp, height = 36.dp)
            .background(grund, RoundedCornerShape(8.dp)),
    ) {
        if (art == DateiArt.ORDNER) {
            Icon(Icons.Outlined.Folder, contentDescription = null, tint = farbe, modifier = Modifier.size(20.dp))
            return@Box
        }
        Text(
            text = kuerzel,
            color = farbe,
            fontWeight = FontWeight.Bold,
            fontSize = if (kuerzel.length > 3) 9.5.sp else 11.sp,
            maxLines = 1,
        )
    }
}
