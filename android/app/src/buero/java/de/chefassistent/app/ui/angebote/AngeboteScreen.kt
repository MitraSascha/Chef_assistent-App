package de.chefassistent.app.ui.angebote

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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.chefassistent.app.R
import de.chefassistent.app.data.AngebotKurz
import de.chefassistent.app.ui.komponenten.EINZUG_MIT_SYMBOL
import de.chefassistent.app.ui.komponenten.GrosseKopfleiste
import de.chefassistent.app.ui.komponenten.GruppenBlock
import de.chefassistent.app.ui.komponenten.LeerHinweis
import de.chefassistent.app.ui.komponenten.RundesSymbol
import de.chefassistent.app.ui.komponenten.gruppenAnordnung
import de.chefassistent.app.ui.komponenten.listenAbstand

/** Liste der Angebote (`GET /v1/angebote`), neueste zuerst. */
@Composable
fun AngeboteScreen(
    viewModel: AngeboteViewModel,
    beiZurueck: () -> Unit,
    beiAbmeldung: () -> Unit,
    beiAngebot: (String) -> Unit,
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LifecycleResumeEffect(Unit) {
        viewModel.listeLaden()
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

    GrosseKopfleiste(
        titel = stringResource(R.string.angebote_titel),
        untertitel = stringResource(R.string.angebote_untertitel),
        beiZurueck = beiZurueck,
        snackbar = snackbar,
        aktionen = {
            IconButton(onClick = { viewModel.listeLaden() }) {
                Icon(Icons.Default.Refresh, stringResource(R.string.angebote_neu_laden))
            }
        },
    ) { innen ->
        val liste = zustand.liste
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = listenAbstand(innen),
            verticalArrangement = gruppenAnordnung,
        ) {
            if (zustand.laeuft && liste == null) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
            }
            zustand.nichtEingerichtet?.let { text ->
                item { LeerHinweis(text) }
            }
            zustand.nichtErreichbar?.let { text ->
                item { NochmalHinweis(text) { viewModel.listeLaden() } }
            }
            if (liste != null && liste.isEmpty()) {
                item { LeerHinweis(stringResource(R.string.angebote_leer)) }
            }
            itemsIndexed(liste.orEmpty(), key = { _, a -> a.id }) { index, angebot ->
                AngebotZeile(
                    angebot = angebot,
                    index = index,
                    anzahl = liste!!.size,
                    beiKlick = { beiAngebot(angebot.id) },
                )
            }
        }
    }
}

@Composable
private fun AngebotZeile(angebot: AngebotKurz, index: Int, anzahl: Int, beiKlick: () -> Unit) {
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
            RundesSymbol(Icons.Default.RequestQuote, FARBE_ANGEBOT, symbolFarbe = Color.White)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = angebot.titel.ifBlank { stringResource(R.string.angebote_ohne_titel) },
                    style = MaterialTheme.typography.bodyLarge,
                )
                val teile = listOfNotNull(
                    pluralStringResource(R.plurals.angebote_positionen, angebot.positionen, angebot.positionen),
                    zeitpunkt(angebot.geaendert),
                )
                Text(
                    text = teile.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = farben.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = farben.onSurfaceVariant)
        }
    }
}

internal val FARBE_ANGEBOT = Color(0xFF3A9D5D)
