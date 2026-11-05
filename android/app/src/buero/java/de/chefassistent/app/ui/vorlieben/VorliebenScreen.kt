package de.chefassistent.app.ui.vorlieben

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.chefassistent.app.R
import de.chefassistent.app.data.VORLIEBE_MAX_ZEICHEN
import de.chefassistent.app.data.Vorliebe
import de.chefassistent.app.ui.komponenten.GrosseKopfleiste
import de.chefassistent.app.ui.komponenten.GruppenBlock
import de.chefassistent.app.ui.komponenten.GruppenKarte
import de.chefassistent.app.ui.komponenten.GruppenTitel
import de.chefassistent.app.ui.komponenten.LeerHinweis
import de.chefassistent.app.ui.komponenten.gruppenAnordnung
import de.chefassistent.app.ui.komponenten.listenAbstand

/**
 * "Vorlieben" (Auftrag 1.32 B): zwei Listen, "Nur fuer mich" und "Fuer die ganze
 * Firma", je Zeile Stift und Muelleimer, unten das Feld zum Merken.
 */
@Composable
fun VorliebenScreen(
    viewModel: VorliebenViewModel,
    beiZurueck: () -> Unit,
    beiAbmeldung: () -> Unit,
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var zuAendern by remember { mutableStateOf<Vorliebe?>(null) }
    var zuLoeschen by remember { mutableStateOf<Vorliebe?>(null) }

    LaunchedEffect(zustand.abgemeldet) {
        if (zustand.abgemeldet) beiAbmeldung()
    }
    LaunchedEffect(zustand.meldung) {
        zustand.meldung?.let {
            snackbar.showSnackbar(it)
            viewModel.meldungVerwerfen()
        }
    }

    zuAendern?.let { vorliebe ->
        AendernDialog(
            vorliebe = vorliebe,
            beiSpeichern = { text ->
                viewModel.aendern(vorliebe, text)
                zuAendern = null
            },
            beiAbbrechen = { zuAendern = null },
        )
    }

    // Vergessen ist nicht umkehrbar: immer mit Rueckfrage (CLAUDE.md, "Gefaehrliche Knoepfe").
    zuLoeschen?.let { vorliebe ->
        AlertDialog(
            onDismissRequest = { zuLoeschen = null },
            title = { Text(stringResource(R.string.vorlieben_vergessen_titel)) },
            text = { Text(stringResource(R.string.vorlieben_vergessen_frage, vorliebe.text)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.loeschen(vorliebe)
                    zuLoeschen = null
                }) {
                    Text(stringResource(R.string.vorlieben_vergessen), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { zuLoeschen = null }) {
                    Text(stringResource(R.string.loeschen_abbrechen))
                }
            },
        )
    }

    GrosseKopfleiste(
        titel = stringResource(R.string.vorlieben_titel),
        beiZurueck = beiZurueck,
        snackbar = snackbar,
        aktionen = {
            IconButton(onClick = viewModel::laden) {
                Icon(Icons.Default.Refresh, stringResource(R.string.neu_laden))
            }
        },
    ) { innen ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = listenAbstand(innen),
            verticalArrangement = gruppenAnordnung,
        ) {
            item(key = "erklaerung") {
                Text(
                    text = stringResource(R.string.vorlieben_erklaerung),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            if (zustand.laeuft) {
                item(key = "laedt") { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }
            if (zustand.nichtVerfuegbar) {
                item(key = "alt") { LeerHinweis(stringResource(R.string.vorlieben_nicht_verfuegbar)) }
                return@LazyColumn
            }
            val liste = zustand.liste ?: return@LazyColumn

            bereich(
                schluessel = "eigene",
                titel = R.string.vorlieben_eigene,
                eintraege = liste.eigene,
                aenderbar = { true },
                aktiv = !zustand.laeuft,
                beiAendern = { zuAendern = it },
                beiLoeschen = { zuLoeschen = it },
            )
            bereich(
                schluessel = "firma",
                titel = R.string.vorlieben_firma,
                eintraege = liste.firma,
                aenderbar = { liste.firma_aendern },
                aktiv = !zustand.laeuft,
                beiAendern = { zuAendern = it },
                beiLoeschen = { zuLoeschen = it },
            )

            item(key = "neu") {
                NeueVorliebe(
                    text = zustand.neu,
                    fuerFirma = zustand.fuerFirma,
                    firmaErlaubt = liste.firma_aendern,
                    aktiv = !zustand.laeuft,
                    beiText = viewModel::neuGeaendert,
                    beiFirma = viewModel::fuerFirmaSetzen,
                    beiMerken = viewModel::merken,
                )
            }
        }
    }
}

private fun LazyListScope.bereich(
    schluessel: String,
    titel: Int,
    eintraege: List<Vorliebe>,
    aenderbar: () -> Boolean,
    aktiv: Boolean,
    beiAendern: (Vorliebe) -> Unit,
    beiLoeschen: (Vorliebe) -> Unit,
) {
    item(key = "titel_$schluessel") { GruppenTitel(stringResource(titel)) }
    if (eintraege.isEmpty()) {
        item(key = "leer_$schluessel") { LeerHinweis(stringResource(R.string.vorlieben_leer)) }
        return
    }
    itemsIndexed(eintraege, key = { _, v -> "${schluessel}_${v.id}" }) { index, vorliebe ->
        GruppenBlock(index = index, anzahl = eintraege.size) {
            VorliebeZeile(
                vorliebe = vorliebe,
                aenderbar = aenderbar(),
                aktiv = aktiv,
                beiAendern = { beiAendern(vorliebe) },
                beiLoeschen = { beiLoeschen(vorliebe) },
            )
        }
    }
}

@Composable
private fun VorliebeZeile(
    vorliebe: Vorliebe,
    aenderbar: Boolean,
    aktiv: Boolean,
    beiAendern: () -> Unit,
    beiLoeschen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = vorliebe.text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (aenderbar) {
            IconButton(onClick = beiAendern, enabled = aktiv) {
                Icon(Icons.Outlined.Edit, stringResource(R.string.vorlieben_aendern))
            }
            IconButton(onClick = beiLoeschen, enabled = aktiv) {
                Icon(Icons.Outlined.Delete, stringResource(R.string.vorlieben_vergessen))
            }
        }
    }
}

@Composable
private fun NeueVorliebe(
    text: String,
    fuerFirma: Boolean,
    firmaErlaubt: Boolean,
    aktiv: Boolean,
    beiText: (String) -> Unit,
    beiFirma: (Boolean) -> Unit,
    beiMerken: () -> Unit,
) {
    GruppenKarte(modifier = Modifier.padding(top = 12.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = beiText,
                placeholder = { Text(stringResource(R.string.vorlieben_neu_hinweis)) },
                supportingText = { Text("${text.length} / $VORLIEBE_MAX_ZEICHEN") },
                minLines = 2,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (firmaErlaubt) {
                    Switch(checked = fuerFirma, onCheckedChange = beiFirma)
                    Text(
                        text = stringResource(R.string.vorlieben_fuer_firma),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp).weight(1f),
                    )
                } else {
                    Row(Modifier.weight(1f)) {}
                }
                Button(onClick = beiMerken, enabled = aktiv && text.isNotBlank()) {
                    Text(stringResource(R.string.vorlieben_merken))
                }
            }
        }
    }
}

@Composable
private fun AendernDialog(vorliebe: Vorliebe, beiSpeichern: (String) -> Unit, beiAbbrechen: () -> Unit) {
    var text by rememberSaveable(vorliebe.id) { mutableStateOf(vorliebe.text) }
    AlertDialog(
        onDismissRequest = beiAbbrechen,
        title = { Text(stringResource(R.string.vorlieben_aendern)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(VORLIEBE_MAX_ZEICHEN) },
                supportingText = { Text("${text.length} / $VORLIEBE_MAX_ZEICHEN") },
                minLines = 2,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { beiSpeichern(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.vorlieben_speichern))
            }
        },
        dismissButton = {
            TextButton(onClick = beiAbbrechen) {
                Text(stringResource(R.string.loeschen_abbrechen))
            }
        },
    )
}
