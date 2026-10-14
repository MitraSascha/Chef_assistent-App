package de.chefassistent.app.ui.geraete

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.chefassistent.app.R
import de.chefassistent.app.data.Geraet
import de.chefassistent.app.ui.komponenten.EINZUG_MIT_SYMBOL
import de.chefassistent.app.ui.komponenten.GrosseKopfleiste
import de.chefassistent.app.ui.komponenten.GruppenBlock
import de.chefassistent.app.ui.komponenten.LeerHinweis
import de.chefassistent.app.ui.komponenten.RundesSymbol
import de.chefassistent.app.ui.komponenten.gruppenAnordnung
import de.chefassistent.app.ui.komponenten.listenAbstand

private val FARBE_GERAET = Color(0xFF6B7785)

@Composable
fun GeraeteScreen(
    viewModel: GeraeteViewModel,
    beiZurueck: () -> Unit,
    beiAbmeldung: () -> Unit,
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var zuAbmelden by remember { mutableStateOf<Geraet?>(null) }

    LaunchedEffect(zustand.abgemeldet) {
        if (zustand.abgemeldet) beiAbmeldung()
    }

    LaunchedEffect(zustand.fehler) {
        zustand.fehler?.let {
            snackbar.showSnackbar(it)
            viewModel.fehlerVerwerfen()
        }
    }

    // Abmelden ist fuer das andere Geraet nicht umkehrbar: es braucht danach
    // Passwort und Code. Deshalb immer mit Rueckfrage (CLAUDE.md, "Gefaehrliche Knoepfe").
    zuAbmelden?.let { geraet ->
        val name = geraet.bezeichnung?.takeIf { it.isNotBlank() } ?: stringResource(R.string.geraete_ohne_name)
        AlertDialog(
            onDismissRequest = { zuAbmelden = null },
            title = { Text(stringResource(R.string.geraete_abmelden_titel)) },
            text = {
                Text(
                    if (geraet.dieses) stringResource(R.string.geraete_abmelden_selbst)
                    else stringResource(R.string.geraete_abmelden_frage, name)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.abmelden(geraet)
                    zuAbmelden = null
                }) {
                    Text(stringResource(R.string.geraete_abmelden), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { zuAbmelden = null }) {
                    Text(stringResource(R.string.loeschen_abbrechen))
                }
            },
        )
    }

    GrosseKopfleiste(
        titel = stringResource(R.string.geraete_titel),
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
            if (zustand.laeuft) {
                item(key = "laedt") { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }
            when {
                zustand.hinweis == GeraeteHinweis.ZUGANGSSCHLUESSEL ->
                    item(key = "hinweis") { LeerHinweis(stringResource(R.string.geraete_schluessel)) }

                zustand.hinweis == GeraeteHinweis.NICHT_VERFUEGBAR ->
                    item(key = "hinweis") { LeerHinweis(stringResource(R.string.geraete_nicht_verfuegbar)) }

                zustand.geladen && zustand.geraete.isEmpty() ->
                    item(key = "leer") { LeerHinweis(stringResource(R.string.geraete_leer)) }
            }
            itemsIndexed(zustand.geraete, key = { _, g -> g.id }) { index, geraet ->
                GruppenBlock(
                    index = index,
                    anzahl = zustand.geraete.size,
                    einzug = EINZUG_MIT_SYMBOL,
                ) {
                    GeraetZeile(
                        geraet = geraet,
                        aktiv = !zustand.laeuft,
                        beiAbmelden = { zuAbmelden = geraet },
                    )
                }
            }
        }
    }
}

@Composable
private fun GeraetZeile(geraet: Geraet, aktiv: Boolean, beiAbmelden: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RundesSymbol(
            if (geraet.dieses) Icons.Default.PhoneAndroid else Icons.Default.Devices,
            farbe = if (geraet.dieses) MaterialTheme.colorScheme.primary else FARBE_GERAET,
            symbolFarbe = if (geraet.dieses) MaterialTheme.colorScheme.onPrimary else Color.White,
        )
        Column(modifier = Modifier.weight(1f).padding(start = 18.dp)) {
            Text(
                text = geraet.bezeichnung?.takeIf { it.isNotBlank() } ?: stringResource(R.string.geraete_ohne_name),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (geraet.dieses) {
                Text(
                    text = stringResource(R.string.geraete_dieses),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            val zeilen = listOfNotNull(
                geraet.zuletzt?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.geraete_zuletzt, ortszeit(it)) },
                geraet.erstellt?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.geraete_erstellt, ortszeit(it)) },
                geraet.ip?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.geraete_ip, it) },
            )
            zeilen.forEach { zeile ->
                Text(
                    text = zeile,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(
            onClick = beiAbmelden,
            enabled = aktiv,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text(stringResource(R.string.geraete_abmelden))
        }
    }
}
