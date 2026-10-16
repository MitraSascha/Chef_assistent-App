package de.chefassistent.app.ui.plantafel

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.chefassistent.app.R
import de.chefassistent.app.data.Einsatz
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val KURZ = DateTimeFormatter.ofPattern("dd.MM.")
private val LANG = DateTimeFormatter.ofPattern("EE, dd.MM.yyyy", java.util.Locale.GERMANY)

/** `YYYY-MM-DD`, bei unlesbarem Wert `null`. */
internal fun datum(text: String?): LocalDate? = text?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

/** `YYYY-MM-DD` -> `TT.MM.`, unbekanntes Format roh. */
internal fun kurzDatum(text: String): String = datum(text)?.format(KURZ) ?: text

internal fun langDatum(text: String): String = datum(text)?.format(LANG) ?: text

/** `#rrggbb` aus dem Vertrag, sonst [ersatz]. */
internal fun farbeAus(hex: String?, ersatz: Color): Color {
    val wert = hex?.trim()?.removePrefix("#") ?: return ersatz
    if (wert.length != 6) return ersatz
    return wert.toLongOrNull(16)?.let { Color(0xFF000000 or it) } ?: ersatz
}

internal fun Color.alsHex(): String {
    val r = (red * 255).toInt()
    val g = (green * 255).toInt()
    val b = (blue * 255).toInt()
    return "#%02x%02x%02x".format(r, g, b)
}

/** Vorgaben fuer die Balkenfarbe eines Gewerks - gut unterscheidbar, hell und dunkel lesbar. */
internal val GEWERK_FARBEN: List<String> = listOf(
    "#718096", "#2b6cb0", "#2f855a", "#c05621", "#b7791f",
    "#6b46c1", "#c53030", "#00838f", "#8d6e63", "#d53f8c",
)

@Composable
internal fun statusText(status: String): String = when (status) {
    Einsatz.STATUS_GEPLANT -> stringResource(R.string.gu_status_geplant)
    Einsatz.STATUS_BESTAETIGT -> stringResource(R.string.gu_status_bestaetigt)
    Einsatz.STATUS_FERTIG -> stringResource(R.string.gu_status_fertig)
    Einsatz.STATUS_ABGESAGT -> stringResource(R.string.gu_status_abgesagt)
    // Neuer Status vom Server: roh zeigen statt abstuerzen.
    else -> status
}

/** Knopf mit Datum, der einen DatePicker oeffnet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatumFeld(
    beschriftung: String,
    wert: String?,
    beiWahl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var offen by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { offen = true }, modifier = modifier) {
        Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(18.dp))
        Text(
            text = "$beschriftung: " + (wert?.let { kurzDatum(it) } ?: "–"),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    if (offen) {
        // Der DatePicker rechnet in UTC-Mitternacht - Umrechnung in beide Richtungen ueber UTC.
        val auswahl = rememberDatePickerState(
            initialSelectedDateMillis = (datum(wert) ?: LocalDate.now())
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { offen = false },
            confirmButton = {
                TextButton(
                    enabled = auswahl.selectedDateMillis != null,
                    onClick = {
                        auswahl.selectedDateMillis?.let {
                            beiWahl(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())
                        }
                        offen = false
                    },
                ) { Text(stringResource(R.string.gu_datum_waehlen)) }
            },
            dismissButton = {
                TextButton(onClick = { offen = false }) { Text(stringResource(R.string.gu_abbrechen)) }
            },
        ) {
            DatePicker(state = auswahl)
        }
    }
}

/** Zwei Datumsknoepfe nebeneinander. */
@Composable
internal fun VonBisFelder(von: String?, bis: String?, beiVon: (String) -> Unit, beiBis: (String) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        DatumFeld(stringResource(R.string.gu_von), von, beiVon, modifier = Modifier.weight(1f).padding(end = 4.dp))
        DatumFeld(stringResource(R.string.gu_bis), bis, beiBis, modifier = Modifier.weight(1f).padding(start = 4.dp))
    }
}

@Composable
internal fun Hinweistext(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
