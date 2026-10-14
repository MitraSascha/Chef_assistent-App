package de.chefassistent.app.ui.ablage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.chefassistent.app.R
import de.chefassistent.app.data.ORDNER_SORTIERUNGEN
import de.chefassistent.app.data.OrdnerEintrag
import de.chefassistent.app.data.ProjektAngaben
import de.chefassistent.app.data.SuchTreffer
import de.chefassistent.app.ui.dateiOeffnen
import de.chefassistent.app.ui.komponenten.EINZUG_MIT_SYMBOL
import de.chefassistent.app.ui.komponenten.GrosseKopfleiste
import de.chefassistent.app.ui.komponenten.GruppenBlock
import de.chefassistent.app.ui.komponenten.LeerHinweis
import de.chefassistent.app.ui.komponenten.RundesSymbol
import de.chefassistent.app.ui.komponenten.SEITENRAND

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AblageScreen(
    viewModel: AblageViewModel,
    beiZurueck: () -> Unit,
    beiAbmeldung: () -> Unit,
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val kontext = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val liste = rememberLazyListState()
    val zurueck: () -> Unit = { if (!viewModel.zurueck()) beiZurueck() }

    BackHandler(onBack = zurueck)

    LaunchedEffect(zustand.abgemeldet) {
        if (zustand.abgemeldet) beiAbmeldung()
    }
    LaunchedEffect(zustand.fehler) {
        zustand.fehler?.let {
            snackbar.showSnackbar(it)
            viewModel.fehlerVerwerfen()
        }
    }
    // Die markierte Datei soll sichtbar sein, ohne dass man sie suchen muss.
    LaunchedEffect(zustand.ordner?.pfad, zustand.markiert, zustand.suchmodus) {
        if (zustand.suchmodus) return@LaunchedEffect
        val eintraege = zustand.ordner?.eintraege ?: return@LaunchedEffect
        val stelle = eintraege.indexOfFirst { it.pfad == zustand.markiert }
        val versatz = if (zustand.kannZurueck) 1 else 0
        if (stelle >= 0) liste.animateScrollToItem(stelle + versatz) else liste.scrollToItem(0)
    }
    LaunchedEffect(zustand.suche) {
        if (zustand.suche != null) liste.scrollToItem(0)
    }
    // Seitenweise nachladen, sobald das Ende der Liste in Sicht kommt.
    val amEnde by remember {
        derivedStateOf {
            val info = liste.layoutInfo
            val letzter = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && letzter >= info.totalItemsCount - 5
        }
    }
    LaunchedEffect(amEnde, zustand.ordner?.eintraege?.size) {
        if (amEnde && !zustand.suchmodus) viewModel.mehrLaden()
    }

    val pfad = zustand.ordner?.pfad.orEmpty()
    val beiDatei: (String) -> Unit = { p -> viewModel.dateiOeffnen(p) { datei -> dateiOeffnen(kontext, datei) } }

    GrosseKopfleiste(
        titel = pfad.substringAfterLast('/').ifBlank { stringResource(R.string.ablage_titel) },
        untertitel = pfad.substringBeforeLast('/', "").ifBlank { null },
        beiZurueck = zurueck,
        snackbar = snackbar,
        aktionen = {
            SortierMenue(zustand.sortierung, viewModel::sortierungWaehlen)
            IconButton(onClick = viewModel::neuLaden) {
                Icon(Icons.Default.Refresh, stringResource(R.string.neu_laden))
            }
        },
    ) { innen ->
        Column(modifier = Modifier.fillMaxSize().padding(top = innen.calculateTopPadding())) {
            Suchfeld(
                text = zustand.suchtext,
                nurHier = zustand.nurHier,
                // Auf der obersten Ebene ist "nur hier" dasselbe wie alles.
                zeigeNurHier = pfad.isNotEmpty() && zustand.suchtext.isNotEmpty(),
                beiAenderung = viewModel::suchtextGeaendert,
                beiLoeschen = viewModel::sucheBeenden,
                beiNurHier = viewModel::nurHierUmschalten,
            )

            if (zustand.laeuft || zustand.sucht) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = SEITENRAND))
            }

            val inhaltAbstand = PaddingValues(
                start = SEITENRAND,
                end = SEITENRAND,
                top = 4.dp,
                bottom = innen.calculateBottomPadding() + 24.dp,
            )

            if (zustand.suchmodus) {
                Trefferliste(
                    zustand = zustand,
                    liste = liste,
                    abstand = inhaltAbstand,
                    beiDatei = beiDatei,
                    beiOrdner = viewModel::ordnerOeffnen,
                )
            } else {
                Ordnerliste(
                    zustand = zustand,
                    liste = liste,
                    abstand = inhaltAbstand,
                    beiDatei = beiDatei,
                    beiOrdner = viewModel::ordnerOeffnen,
                    beiNachOben = viewModel::nachOben,
                )
            }
        }
    }
}

@Composable
private fun Suchfeld(
    text: String,
    nurHier: Boolean,
    zeigeNurHier: Boolean,
    beiAenderung: (String) -> Unit,
    beiLoeschen: () -> Unit,
    beiNurHier: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = SEITENRAND, vertical = 4.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = beiAenderung,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.ablage_suchen)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (text.isNotEmpty()) {
                    IconButton(onClick = beiLoeschen) {
                        Icon(Icons.Default.Close, stringResource(R.string.ablage_suche_loeschen))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        )
        if (zeigeNurHier) {
            FilterChip(
                selected = nurHier,
                onClick = beiNurHier,
                label = { Text(stringResource(R.string.ablage_nur_hier)) },
                leadingIcon = if (nurHier) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun SortierMenue(aktuell: String, beiWahl: (String) -> Unit) {
    Box {
        var offen by remember { mutableStateOf(false) }
        IconButton(onClick = { offen = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.ablage_sortierung))
        }
        DropdownMenu(expanded = offen, onDismissRequest = { offen = false }, shape = RoundedCornerShape(20.dp)) {
            ORDNER_SORTIERUNGEN.forEach { wert ->
                val text = when (wert) {
                    "name" -> stringResource(R.string.ablage_sort_name)
                    "name_ab" -> stringResource(R.string.ablage_sort_name_ab)
                    "geaendert" -> stringResource(R.string.ablage_sort_geaendert)
                    else -> wert
                }
                DropdownMenuItem(
                    text = { Text(text) },
                    leadingIcon = if (wert == aktuell) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null,
                    onClick = {
                        offen = false
                        beiWahl(wert)
                    },
                )
            }
        }
    }
}

@Composable
private fun Ordnerliste(
    zustand: AblageZustand,
    liste: androidx.compose.foundation.lazy.LazyListState,
    abstand: PaddingValues,
    beiDatei: (String) -> Unit,
    beiOrdner: (String) -> Unit,
    beiNachOben: () -> Unit,
) {
    val ordner = zustand.ordner
    val eintraege = ordner?.eintraege.orEmpty()
    val mitNachOben = ordner?.eltern != null
    val anzahl = eintraege.size + if (mitNachOben) 1 else 0
    LazyColumn(state = liste, modifier = Modifier.fillMaxSize(), contentPadding = abstand) {
        if (mitNachOben) {
            item(key = "..") {
                GruppenBlock(index = 0, anzahl = anzahl, einzug = EINZUG_MIT_SYMBOL) {
                    Zeile(
                        icon = {
                            RundesSymbol(
                                Icons.Default.ArrowUpward,
                                farbe = MaterialTheme.colorScheme.surfaceContainerHighest,
                                symbolFarbe = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        titel = stringResource(R.string.ablage_nach_oben),
                        unter = null,
                        markiert = false,
                        laedt = false,
                        beiKlick = beiNachOben,
                    )
                }
            }
        }
        itemsIndexed(eintraege, key = { _, e -> e.pfad }) { index, eintrag ->
            GruppenBlock(
                index = index + if (mitNachOben) 1 else 0,
                anzahl = anzahl,
                einzug = EINZUG_MIT_SYMBOL,
            ) {
                Zeile(
                    icon = { EintragSymbol(eintrag.istOrdner, eintrag.name) },
                    titel = eintrag.name,
                    unter = unterzeile(eintrag),
                    markiert = eintrag.pfad == zustand.markiert,
                    laedt = eintrag.pfad == zustand.laedtDatei,
                    beiKlick = { if (eintrag.istOrdner) beiOrdner(eintrag.pfad) else beiDatei(eintrag.pfad) },
                )
            }
        }
        if (ordner != null && ordner.eintraege.isEmpty() && !zustand.laeuft) {
            item(key = "leer") { LeerHinweis(stringResource(R.string.ablage_leer), Modifier.padding(top = 8.dp)) }
        }
        if (zustand.laedtMehr) {
            item(key = "mehr") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
private fun Trefferliste(
    zustand: AblageZustand,
    liste: androidx.compose.foundation.lazy.LazyListState,
    abstand: PaddingValues,
    beiDatei: (String) -> Unit,
    beiOrdner: (String) -> Unit,
) {
    val suche = zustand.suche
    val treffer = suche?.treffer.orEmpty()
    LazyColumn(state = liste, modifier = Modifier.fillMaxSize(), contentPadding = abstand) {
        itemsIndexed(treffer, key = { _, t -> t.pfad }) { index, t ->
            GruppenBlock(index = index, anzahl = treffer.size, einzug = EINZUG_MIT_SYMBOL) {
                Zeile(
                    icon = { EintragSymbol(t.istOrdner, t.name) },
                    titel = t.name,
                    unter = unterzeile(t),
                    markiert = false,
                    laedt = t.pfad == zustand.laedtDatei,
                    beiKlick = { if (t.istOrdner) beiOrdner(t.pfad) else beiDatei(t.pfad) },
                    // Ordner mit markierter Datei, wie bei den Datei-Chips im Chat.
                    beiLangemDruck = if (t.istOrdner) null else { { beiOrdner(t.pfad) } },
                )
            }
        }
        if (suche != null && treffer.isEmpty() && !zustand.sucht) {
            item(key = "leer") { LeerHinweis(stringResource(R.string.ablage_suche_leer), Modifier.padding(top = 8.dp)) }
        }
        if (suche?.abgeschnitten == true) {
            item(key = "abgeschnitten") {
                Text(
                    text = stringResource(R.string.ablage_suche_abgeschnitten, treffer.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun EintragSymbol(istOrdner: Boolean, name: String) {
    if (istOrdner) {
        RundesSymbol(
            Icons.Default.Folder,
            farbe = MaterialTheme.colorScheme.primaryContainer,
            symbolFarbe = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    } else {
        RundesSymbol(
            dateiSymbol(name),
            farbe = MaterialTheme.colorScheme.surfaceContainerHighest,
            symbolFarbe = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Symbol nach Endung, wie in Eigene Dateien. Auch fuer die Link-Karten im Chat. */
internal fun dateiSymbol(name: String): ImageVector = when (name.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg", "png", "webp", "heic", "gif" -> Icons.Default.Image
    "mp4", "mov", "m4v" -> Icons.Default.Movie
    "pdf" -> Icons.Default.PictureAsPdf
    "md", "txt", "log", "csv" -> Icons.AutoMirrored.Filled.Notes
    "doc", "docx", "odt" -> Icons.Default.Description
    "xls", "xlsx", "xlsm", "ods" -> Icons.Default.TableChart
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

/** Bei Projektordnern Baustelle und Status - daran erkennt man im HERO-Archiv, welches Projekt welches ist. */
private fun unterzeile(eintrag: OrdnerEintrag): String {
    eintrag.projekt?.let { p ->
        val zeile = listOf(p.bauvorhaben, p.status).filter { it.isNotBlank() }.joinToString(" · ")
        if (zeile.isNotBlank()) return zeile
    }
    return listOfNotNull(eintrag.groesse, eintrag.geaendert.takeIf { it.isNotBlank() }).joinToString(" · ")
}

/** "SAN-1166 · Herr Mark Boese · Grimmstraße 7", sonst der Ordner, in dem der Treffer liegt. */
private fun unterzeile(treffer: SuchTreffer): String =
    treffer.projekt?.let(::projektZeile)?.takeIf { it.isNotBlank() } ?: treffer.ordner

internal fun projektZeile(p: ProjektAngaben): String =
    listOf(p.nr, p.kunde, p.bauvorhaben).filter { it.isNotBlank() }.joinToString(" · ")

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Zeile(
    icon: @Composable () -> Unit,
    titel: String,
    unter: String?,
    markiert: Boolean,
    laedt: Boolean,
    beiKlick: () -> Unit,
    beiLangemDruck: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (markiert) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            .combinedClickable(
                onClick = beiKlick,
                onLongClick = beiLangemDruck,
                onLongClickLabel = if (beiLangemDruck != null) stringResource(R.string.ablage_im_ordner) else null,
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Column(modifier = Modifier.weight(1f).padding(start = 18.dp)) {
            Text(
                text = titel,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!unter.isNullOrBlank()) {
                Text(
                    text = unter,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (laedt) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
    }
}
