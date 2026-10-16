package de.chefassistent.app.monteur.ui.bericht

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.chefassistent.app.R
import de.chefassistent.app.monteur.data.Artikel
import de.chefassistent.app.monteur.data.MaterialAenderung

/**
 * „+ Material“ (Auftrag 1.22, 3.): Suche im Artikelstamm, Treffer antippen,
 * Menge waehlen, hinzufuegen. Ohne passenden Treffer als freie Position mit
 * Bezeichnung und Einheit. **Keine Preise** - die liefert der Server auch nicht.
 *
 * Kennt kein ViewModel: Bericht und Bestellung (Auftrag 1.23, 2.) suchen auf
 * eigenen Wegen, der Dialog bekommt nur den Stand [suche] und die beiden
 * Aufrufe. [beiFertig] liefert neben der Position den gewaehlten Treffer, weil
 * die Bestellung ihn bis „Passt“ selbst anzeigen muss (den Bericht schickt der
 * Server mit Artikelnamen zurueck).
 */
@Composable
internal fun MaterialDialog(
    suche: ArtikelSuche,
    beiSuche: (String) -> Unit,
    beiSucheBeenden: () -> Unit,
    beiFertig: (MaterialAenderung, Artikel?) -> Unit,
    beiAbbruch: () -> Unit,
) {
    var eingabe by rememberSaveable { mutableStateOf("") }
    var gewaehlt by remember { mutableStateOf<Artikel?>(null) }
    var frei by rememberSaveable { mutableStateOf(false) }
    var einheit by rememberSaveable { mutableStateOf("Stk") }
    var menge by rememberSaveable { mutableDoubleStateOf(1.0) }
    val fokus = remember { FocusRequester() }
    LaunchedEffect(Unit) { fokus.requestFocus() }

    val bereit = gewaehlt != null || (frei && eingabe.isNotBlank())
    AlertDialog(
        onDismissRequest = beiAbbruch,
        title = { Text(stringResource(R.string.b_material_plus)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val artikel = gewaehlt
                if (artikel == null) {
                    OutlinedTextField(
                        value = eingabe,
                        onValueChange = {
                            eingabe = it
                            if (!frei) beiSuche(it)
                        },
                        label = { Text(stringResource(if (frei) R.string.b_material_name else R.string.b_material_suchen)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(fokus),
                    )
                }
                when {
                    artikel != null -> {
                        Text(artikel.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.b_material_artikelnr, artikel.nr),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MengeWahl(menge, artikel.einheit) { menge = it }
                    }
                    frei -> {
                        OutlinedTextField(
                            value = einheit,
                            onValueChange = { einheit = it.take(10) },
                            label = { Text(stringResource(R.string.b_material_einheit)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        MengeWahl(menge, einheit) { menge = it }
                        Text(
                            stringResource(R.string.b_material_frei_hinweis),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        if (suche.laeuft) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        suche.fehler?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (!suche.laeuft && suche.fehler == null && suche.suchtext.isNotEmpty() && suche.treffer.isEmpty()) {
                            Text(stringResource(R.string.b_material_keine_treffer), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LazyColumn(Modifier.heightIn(max = 280.dp)) {
                            items(suche.treffer, key = { it.nr }) { a ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clickable { gewaehlt = a }
                                        .padding(vertical = 8.dp),
                                ) {
                                    // Volle Textfarbe: im Dialog ist der Inhalt sonst gedaempft und wirkt wie deaktiviert.
                                    Text(a.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOf(a.nr, a.einheit).filter { it.isNotBlank() }.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                        // Kein passender Artikel: als freie Position, der Suchtext wird zur Bezeichnung.
                        if (eingabe.trim().length >= 2) {
                            TextButton(onClick = { frei = true; beiSucheBeenden() }) {
                                Text(stringResource(R.string.b_material_frei))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = bereit,
                onClick = {
                    val a = gewaehlt
                    beiFertig(
                        if (a != null) neuePosition(menge, a.nr)
                        else neuePosition(menge, null, text = eingabe, einheit = einheit),
                        a,
                    )
                },
            ) { Text(stringResource(R.string.b_material_uebernehmen)) }
        },
        dismissButton = {
            TextButton(onClick = {
                when {
                    gewaehlt != null -> gewaehlt = null
                    frei -> frei = false
                    else -> beiAbbruch()
                }
            }) { Text(stringResource(if (gewaehlt != null || frei) R.string.b_zurueck else R.string.b_abbrechen)) }
        },
    )
}

/** Menge in ganzen Schritten (Stueck, Meter); Kommawerte gehen ueber das Diktat. */
@Composable
private fun MengeWahl(menge: Double, einheit: String, beiAenderung: (Double) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.b_material_menge), modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { beiAenderung((menge - 1).coerceAtLeast(1.0)) }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.Remove, contentDescription = null)
        }
        Text(
            "${mengeText(menge)} $einheit".trim(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(80.dp).padding(horizontal = 8.dp),
            maxLines = 1,
        )
        FilledTonalIconButton(onClick = { beiAenderung(menge + 1) }, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.Add, contentDescription = null)
        }
    }
}

private fun mengeText(m: Double): String = if (m % 1.0 == 0.0) m.toLong().toString() else m.toString().replace('.', ',')
