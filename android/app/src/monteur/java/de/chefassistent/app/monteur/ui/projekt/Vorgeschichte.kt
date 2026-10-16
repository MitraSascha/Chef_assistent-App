package de.chefassistent.app.monteur.ui.projekt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.chefassistent.app.R
import de.chefassistent.app.monteur.data.Anlage
import de.chefassistent.app.monteur.data.Vorgeschichte
import de.chefassistent.app.monteur.data.VorgeschichteQuellen
import de.chefassistent.app.ui.theme.warnung

/**
 * „Gas-Brennwerttherme · Vaillant ecoTEC plus VC 206/5-5 · Baujahr 2016“ - nur vorhandene
 * Teile, Hersteller und Typ zusammen. Nichts da → `null`, die Zeile faellt weg.
 */
fun geraetZeile(anlage: Anlage?): String? {
    if (anlage == null) return null
    fun t(s: String?) = s?.trim()?.takeIf { it.isNotEmpty() }
    val modell = listOfNotNull(t(anlage.hersteller), t(anlage.typ)).joinToString(" ").ifEmpty { null }
    val teile = listOfNotNull(t(anlage.geraet), modell, t(anlage.baujahr)?.let { "Baujahr $it" })
    return teile.joinToString(" · ").ifEmpty { null }
}

/** „aus 3 Berichten, 1 Protokoll“ - klein und grau, nur was > 0 ist. */
fun quellenText(q: VorgeschichteQuellen?): String? {
    if (q == null) return null
    fun teil(n: Int, eins: String, viele: String) = if (n <= 0) null else "$n ${if (n == 1) eins else viele}"
    val teile = listOfNotNull(
        teil(q.berichte, "Bericht", "Berichten"),
        teil(q.protokolle, "Protokoll", "Protokollen"),
        teil(q.kommentare, "Kommentar", "Kommentaren"),
    )
    return if (teile.isEmpty()) null else "aus " + teile.joinToString(", ")
}

/** Gibt es ueberhaupt etwas zu zeigen? Sonst faellt der Block ganz weg. */
fun hatInhalt(v: Vorgeschichte?): Boolean =
    v != null && (v.zusammenfassung.isNotBlank() || geraetZeile(v.anlage) != null || !v.letzte_wartung.isNullOrBlank() ||
        v.letzte_arbeiten.any { it.text.isNotBlank() } || v.offen.any { it.isNotBlank() } || v.hinweise.any { it.isNotBlank() })

/**
 * Block „Vorgeschichte“ ganz oben in der Projektmappe (Auftrag 1.26, 2.): Zusammenfassung,
 * Gerät, letzte Wartung, letzte Einsätze, Offen als Aufzählung, Hinweise hervorgehoben.
 * Leere Teile fallen weg.
 */
@Composable
fun VorgeschichteBlock(v: Vorgeschichte) {
    val farben = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(12.dp), color = farben.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.vg_titel), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (v.zusammenfassung.isNotBlank()) Text(v.zusammenfassung, style = MaterialTheme.typography.bodyLarge)
            geraetZeile(v.anlage)?.let { Feld(stringResource(R.string.vg_geraet), it) }
            v.letzte_wartung?.takeIf { it.isNotBlank() }?.let { Feld(stringResource(R.string.vg_wartung), it) }
            val einsaetze = v.letzte_arbeiten.filter { it.text.isNotBlank() }
            if (einsaetze.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Beschriftung(stringResource(R.string.vg_einsaetze))
                    einsaetze.forEach { e ->
                        Text(listOf(e.datum, e.text).filter { it.isNotBlank() }.joinToString(" – "), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            val offen = v.offen.filter { it.isNotBlank() }
            if (offen.isNotEmpty()) {
                Column {
                    Beschriftung(stringResource(R.string.vg_offen))
                    offen.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
            // Das Wichtigste vor Ort - hervorgehoben, nicht grau.
            v.hinweise.filter { it.isNotBlank() }.forEach { h ->
                Surface(shape = RoundedCornerShape(10.dp), color = farben.warnung.copy(alpha = 0.16f), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Info, null, tint = farben.warnung, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(h, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }
            quellenText(v.quellen)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Beschriftung(text: String) =
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Feld(beschriftung: String, wert: String) {
    Column {
        Beschriftung(beschriftung)
        Text(wert, style = MaterialTheme.typography.bodyMedium)
    }
}
