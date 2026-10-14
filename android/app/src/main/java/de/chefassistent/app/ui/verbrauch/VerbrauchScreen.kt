package de.chefassistent.app.ui.verbrauch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.chefassistent.app.R
import de.chefassistent.app.ui.komponenten.GrosseKopfleiste
import de.chefassistent.app.ui.komponenten.GruppenBlock
import de.chefassistent.app.ui.komponenten.GruppenTitel
import de.chefassistent.app.ui.komponenten.LeerHinweis
import de.chefassistent.app.ui.komponenten.gruppenAnordnung
import de.chefassistent.app.ui.komponenten.listenAbstand
import androidx.compose.foundation.lazy.itemsIndexed
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Eine Zeile der flachgeklopften JSON-Antwort. */
private data class Zeile(
    val schluessel: String,
    val bezeichnung: String,
    val wert: String?,
    val tiefe: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerbrauchScreen(
    viewModel: VerbrauchViewModel,
    beiZurueck: () -> Unit,
    beiAbmeldung: () -> Unit,
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

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
        titel = stringResource(R.string.verbrauch_titel),
        beiZurueck = beiZurueck,
        snackbar = snackbar,
        aktionen = {
            IconButton(onClick = viewModel::laden) {
                Icon(Icons.Default.Refresh, stringResource(R.string.neu_laden))
            }
        },
    ) { innen ->
        val gruppen = zustand.daten?.let { gruppieren(flachklopfen(it)) }.orEmpty()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = listenAbstand(innen),
            verticalArrangement = gruppenAnordnung,
        ) {
            if (zustand.laeuft) {
                item(key = "laedt") { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }
            if (gruppen.isEmpty() && !zustand.laeuft) {
                item(key = "leer") { LeerHinweis(stringResource(R.string.verbrauch_leer)) }
            }
            gruppen.forEach { (kopf, werte) ->
                if (kopf != null) {
                    item(key = "k" + kopf.schluessel) { GruppenTitel(kopf.bezeichnung) }
                }
                itemsIndexed(werte, key = { _, z -> z.schluessel }) { index, zeile ->
                    GruppenBlock(index = index, anzahl = werte.size) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = zeile.bezeichnung,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = zeile.wert.orEmpty(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Jede Zwischenueberschrift (Monat, Eintrag, ...) beginnt eine neue Karte, die
 * Werte darunter liegen darin. Ueberschriften ohne eigene Werte fallen weg.
 */
private fun gruppieren(zeilen: List<Zeile>): List<Pair<Zeile?, List<Zeile>>> {
    val ergebnis = mutableListOf<Pair<Zeile?, MutableList<Zeile>>>()
    var kopf: Zeile? = null
    for (zeile in zeilen) {
        if (zeile.wert == null) {
            kopf = zeile
        } else {
            val letzte = ergebnis.lastOrNull()
            if (letzte != null && letzte.first === kopf) letzte.second.add(zeile) else ergebnis.add(kopf to mutableListOf(zeile))
        }
    }
    return ergebnis
}

/**
 * Macht aus beliebigem JSON eine Liste anzeigbarer Zeilen.
 *
 * `openapi.json` legt fuer `GET /v1/verbrauch` kein Schema fest. Statt eine
 * Struktur zu erfinden, die beim naechsten Server-Update nicht mehr stimmt,
 * wird hier dargestellt, was tatsaechlich ankommt.
 */
private fun flachklopfen(
    element: JsonElement,
    bezeichnung: String = "",
    pfad: String = "",
    tiefe: Int = 0,
): List<Zeile> = when (element) {
    is JsonObject -> buildList {
        if (bezeichnung.isNotEmpty()) {
            add(Zeile(pfad, lesbar(bezeichnung), null, tiefe))
        }
        element.forEach { (schluessel, wert) ->
            addAll(flachklopfen(wert, schluessel, "$pfad/$schluessel", tiefe + if (bezeichnung.isEmpty()) 0 else 1))
        }
    }

    is JsonArray -> buildList {
        if (bezeichnung.isNotEmpty()) {
            add(Zeile(pfad, lesbar(bezeichnung), null, tiefe))
        }
        element.forEachIndexed { index, wert ->
            addAll(flachklopfen(wert, "#${index + 1}", "$pfad/$index", tiefe + 1))
        }
    }

    is JsonNull -> listOf(Zeile(pfad, lesbar(bezeichnung), "–", tiefe))

    is JsonPrimitive -> listOf(Zeile(pfad, lesbar(bezeichnung), element.content, tiefe))
}

/** `token_eingabe` -> `Token eingabe`. Mehr Kosmetik geht ohne Schema nicht. */
private fun lesbar(schluessel: String): String =
    schluessel.replace('_', ' ').replaceFirstChar { it.uppercase() }
