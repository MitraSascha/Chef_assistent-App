package de.chefassistent.app.ui.plantafel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.chefassistent.app.R
import de.chefassistent.app.data.GuProjekt
import de.chefassistent.app.data.GuProjektNeu
import de.chefassistent.app.ui.komponenten.EINZUG_MIT_SYMBOL
import de.chefassistent.app.ui.komponenten.GrosseKopfleiste
import de.chefassistent.app.ui.komponenten.GruppenBlock
import de.chefassistent.app.ui.komponenten.LeerHinweis
import de.chefassistent.app.ui.komponenten.RundesSymbol
import de.chefassistent.app.ui.komponenten.gruppenAnordnung
import de.chefassistent.app.ui.komponenten.listenAbstand

/** Liste der GU-Projekte (`GET /v1/bauablauf`), je eins mit eigener Plantafel. */
@Composable
fun GuProjekteScreen(
    viewModel: PlantafelViewModel,
    beiZurueck: () -> Unit,
    beiAbmeldung: () -> Unit,
    beiProjekt: (Int) -> Unit,
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var neuOffen by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        viewModel.projekteLaden()
        onPauseOrDispose { }
    }
    LaunchedEffect(zustand.abgemeldet) {
        if (zustand.abgemeldet) beiAbmeldung()
    }
    LaunchedEffect(zustand.fehler) {
        zustand.fehler?.let {
            snackbar.showSnackbar(it)
            viewModel.fehlerVerwerfen()
        }
    }

    if (neuOffen) {
        NeuesProjektDialog(
            beschaeftigt = zustand.beschaeftigt,
            beiAnlegen = { name ->
                viewModel.projektAnlegen(name) { id ->
                    neuOffen = false
                    beiProjekt(id)
                }
            },
            beiAbbruch = { neuOffen = false },
        )
    }

    GrosseKopfleiste(
        titel = stringResource(R.string.gu_titel),
        untertitel = stringResource(R.string.gu_untertitel),
        beiZurueck = beiZurueck,
        snackbar = snackbar,
        aktionen = {
            IconButton(onClick = { viewModel.projekteLaden() }) {
                Icon(Icons.Default.Refresh, stringResource(R.string.gu_neu_laden))
            }
        },
        knopf = {
            ExtendedFloatingActionButton(
                onClick = { neuOffen = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.gu_neu)) },
            )
        },
    ) { innen ->
        val projekte = zustand.projekte
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = listenAbstand(innen),
            verticalArrangement = gruppenAnordnung,
        ) {
            if (zustand.laeuft && projekte == null) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
            }
            if (projekte != null && projekte.isEmpty()) {
                item { LeerHinweis(stringResource(R.string.gu_leer)) }
            }
            itemsIndexed(projekte.orEmpty(), key = { _, p -> p.id }) { index, projekt ->
                ProjektZeile(
                    projekt = projekt,
                    index = index,
                    anzahl = projekte!!.size,
                    beiKlick = { beiProjekt(projekt.id) },
                )
            }
        }
    }
}

@Composable
private fun ProjektZeile(projekt: GuProjekt, index: Int, anzahl: Int, beiKlick: () -> Unit) {
    val farben = MaterialTheme.colorScheme
    GruppenBlock(index = index, anzahl = anzahl, einzug = EINZUG_MIT_SYMBOL) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = beiKlick)
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            RundesSymbol(Icons.Default.Engineering, farben.primary, symbolFarbe = farben.onPrimary)
            Column(modifier = Modifier.weight(1f)) {
                Text(projekt.name, style = MaterialTheme.typography.bodyLarge)
                val zeitraum = if (projekt.von != null) {
                    "${kurzDatum(projekt.von)} – ${kurzDatum(projekt.bis ?: projekt.von)}"
                } else {
                    stringResource(R.string.gu_nichts_geplant)
                }
                Text(
                    text = zeitraum + " · " +
                        pluralStringResource(R.plurals.gu_einsaetze_anzahl, projekt.einsaetze, projekt.einsaetze),
                    style = MaterialTheme.typography.bodyMedium,
                    color = farben.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = farben.onSurfaceVariant)
        }
    }
}

@Composable
private fun NeuesProjektDialog(beschaeftigt: Boolean, beiAnlegen: (String) -> Unit, beiAbbruch: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = beiAbbruch,
        title = { Text(stringResource(R.string.gu_neu)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(GuProjektNeu.MAX_NAME) },
                label = { Text(stringResource(R.string.gu_neu_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !beschaeftigt,
                onClick = { beiAnlegen(name.trim()) },
            ) { Text(stringResource(R.string.gu_anlegen)) }
        },
        dismissButton = {
            TextButton(onClick = beiAbbruch) { Text(stringResource(R.string.gu_abbrechen)) }
        },
    )
}
