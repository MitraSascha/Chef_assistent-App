package de.chefassistent.app.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.chefassistent.app.R
import de.chefassistent.app.data.GespraechKurz
import de.chefassistent.app.ui.komponenten.FehlerSnackbar
import de.chefassistent.app.ui.komponenten.LeerHinweis
import de.chefassistent.app.ui.theme.auswahl
import de.chefassistent.app.ui.theme.gold
import de.chefassistent.app.ui.theme.hauptflaeche

/**
 * Gespraechsliste als Startseite des Reiters Chat (seit v1.21.0, API.md 1e,
 * Auftrag 1.21 Teil A): nach Zeit gruppiert, angeheftete oben, Titelsuche.
 * Langer Druck oeffnet das Menue mit Anheften und Loeschen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GespraechListe(
    gespraeche: List<GespraechKurz>,
    treffer: List<GespraechKurz>?,
    laedt: Boolean,
    sucheLaeuft: Boolean,
    aktuellesGespraech: String?,
    umgeklappt: Set<String>,
    snackbar: SnackbarHostState,
    beiMenue: () -> Unit,
    beiSuche: (String) -> Unit,
    beiSucheBeenden: () -> Unit,
    beiOeffnen: (String) -> Unit,
    beiNeu: () -> Unit,
    beiAnheften: (String, Boolean) -> Unit,
    beiLoeschen: (String) -> Unit,
    beiUmklappen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sucheOffen by rememberSaveable { mutableStateOf(false) }
    var suchText by rememberSaveable { mutableStateOf("") }
    var zuLoeschen by remember { mutableStateOf<GespraechKurz?>(null) }
    val schliessen = {
        sucheOffen = false
        suchText = ""
        beiSucheBeenden()
    }
    // Zurueck schliesst erst die Suche.
    BackHandler(enabled = sucheOffen) { schliessen() }

    zuLoeschen?.let { gespraech ->
        LoeschenFrage(
            titel = gespraech.titel?.takeIf { it.isNotBlank() } ?: stringResource(R.string.chat_ohne_titel),
            beiJa = {
                beiLoeschen(gespraech.id)
                zuLoeschen = null
            },
            beiNein = { zuLoeschen = null },
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                navigationIcon = {
                    if (sucheOffen) {
                        IconButton(onClick = schliessen) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.liste_suche_schliessen))
                        }
                    } else {
                        IconButton(onClick = beiMenue) {
                            Icon(Icons.Default.Menu, stringResource(R.string.liste_menue))
                        }
                    }
                },
                title = {
                    if (sucheOffen) {
                        val fokus = remember { FocusRequester() }
                        LaunchedEffect(Unit) { fokus.requestFocus() }
                        TextField(
                            value = suchText,
                            onValueChange = {
                                suchText = it
                                beiSuche(it)
                            },
                            placeholder = { Text(stringResource(R.string.liste_suchen)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth().focusRequester(fokus),
                        )
                    } else {
                        Text(stringResource(R.string.chat_gespraeche), style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    if (sucheOffen) {
                        if (sucheLaeuft) {
                            CircularProgressIndicator(Modifier.padding(end = 16.dp).size(18.dp), strokeWidth = 2.dp)
                        }
                    } else {
                        IconButton(onClick = { sucheOffen = true }) {
                            Icon(Icons.Outlined.Search, stringResource(R.string.liste_suchen))
                        }
                    }
                },
            )
        },
        snackbarHost = { FehlerSnackbar(snackbar) },
        floatingActionButton = {
            // Navy, abgerundetes Quadrat (Auftrag 1.21, B3).
            FloatingActionButton(
                onClick = beiNeu,
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.hauptflaeche,
                contentColor = Color.White,
            ) {
                Icon(Icons.Default.Add, stringResource(R.string.chat_neues_gespraech))
            }
        },
    ) { innen ->
        val sucht = sucheOffen && treffer != null
        val gruppen = if (sucht) {
            listOf(GespraechGruppe("treffer", GruppenArt.TREFFER, gespraeche = treffer.orEmpty()))
        } else {
            gespraecheGruppieren(gespraeche)
        }
        // Neuer Scrollzustand, sobald Daten da sind bzw. die Suche wechselt. Sonst
        // haelt die Liste die Position des Leer-Hinweises und beginnt unter dem ersten Kopf.
        val scroll = remember(gespraeche.isEmpty(), sucht) { LazyListState() }
        // Nach dem Anheften oben ist die neue Gruppe sonst ueber dem sichtbaren Bereich.
        val angeheftet = gespraeche.count { it.istAngeheftet }
        LaunchedEffect(angeheftet) {
            if (scroll.firstVisibleItemIndex <= 3) scroll.animateScrollToItem(0)
        }
        LazyColumn(
            state = scroll,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 8.dp,
                end = 8.dp,
                top = innen.calculateTopPadding(),
                // Platz fuer den schwebenden Knopf.
                bottom = innen.calculateBottomPadding() + 88.dp,
            ),
        ) {
            if (sucht && treffer.isNullOrEmpty()) {
                item(key = "keine_treffer") {
                    Column {
                        GruppenKopf(stringResource(R.string.liste_treffer, suchText.trim()), 0, null, null)
                        LeerHinweis(stringResource(R.string.liste_keine_treffer), Modifier.padding(horizontal = 8.dp))
                    }
                }
            } else if (!sucht && gespraeche.isEmpty() && !laedt) {
                item(key = "leer") {
                    LeerHinweis(stringResource(R.string.chat_keine_gespraeche), Modifier.padding(8.dp))
                }
            }
            for (gruppe in gruppen) {
                if (gruppe.gespraeche.isEmpty()) continue
                val offen = gruppe.art == GruppenArt.TREFFER ||
                    gruppe.standardOffen != (gruppe.schluessel in umgeklappt)
                item(key = "kopf_${gruppe.schluessel}") {
                    GruppenKopf(
                        titel = gruppenTitel(gruppe, suchText.trim()),
                        anzahl = gruppe.gespraeche.size,
                        offen = if (gruppe.art == GruppenArt.TREFFER) null else offen,
                        beiKlick = if (gruppe.art == GruppenArt.TREFFER) null else {
                            { beiUmklappen(gruppe.schluessel) }
                        },
                    )
                }
                if (offen) {
                    items(gruppe.gespraeche, key = { "${gruppe.schluessel}_${it.id}" }) { gespraech ->
                        GespraechZeile(
                            gespraech = gespraech,
                            aktiv = gespraech.id == aktuellesGespraech,
                            beiOeffnen = { beiOeffnen(gespraech.id) },
                            beiAnheften = gespraech.angeheftet?.let { an -> { beiAnheften(gespraech.id, !an) } },
                            beiLoeschen = { zuLoeschen = gespraech },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun gruppenTitel(gruppe: GespraechGruppe, suche: String): String = when (gruppe.art) {
    GruppenArt.ANGEHEFTET -> stringResource(R.string.liste_angeheftet)
    GruppenArt.HEUTE -> stringResource(R.string.liste_heute)
    GruppenArt.GESTERN -> stringResource(R.string.liste_gestern)
    GruppenArt.SIEBEN_TAGE -> stringResource(R.string.liste_7_tage)
    GruppenArt.DREISSIG_TAGE -> stringResource(R.string.liste_30_tage)
    GruppenArt.MONAT -> gruppe.monat.orEmpty()
    GruppenArt.TREFFER -> stringResource(R.string.liste_treffer, suche)
}

/** Abschnittsueberschrift: klein, grau, mit Anzahl. Antippen klappt auf/zu. */
@Composable
private fun GruppenKopf(titel: String, anzahl: Int, offen: Boolean?, beiKlick: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(if (beiKlick != null) Modifier.clickable(onClick = beiKlick) else Modifier)
            .padding(start = 12.dp, end = 12.dp, top = 8.dp)
            .semantics { heading() },
    ) {
        Text(
            text = titel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (anzahl > 0) {
            Text(
                text = anzahl.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
        if (offen != null) {
            Icon(
                if (offen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GespraechZeile(
    gespraech: GespraechKurz,
    aktiv: Boolean,
    beiOeffnen: () -> Unit,
    /** `null` = alter Server ohne `angeheftet`, Menuepunkt fehlt. */
    beiAnheften: (() -> Unit)?,
    beiLoeschen: () -> Unit,
) {
    var menue by remember { mutableStateOf(false) }
    val laeuftText = stringResource(R.string.liste_laeuft)
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .background(
                    if (aktiv) MaterialTheme.colorScheme.auswahl else Color.Transparent,
                    RoundedCornerShape(10.dp),
                )
                .combinedClickable(onClick = beiOeffnen, onLongClick = { menue = true })
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (gespraech.istAngeheftet) {
                Icon(
                    Icons.Outlined.PushPin,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 10.dp).size(16.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = gespraech.titel?.takeIf { it.isNotBlank() } ?: stringResource(R.string.chat_ohne_titel),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                zeitZeile(gespraech.geaendert)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // Seit v1.17.0: der Assistent arbeitet dort gerade. Goldener Punkt (Auftrag 1.21).
            if (gespraech.laeuft) {
                Box(
                    Modifier
                        .padding(start = 12.dp)
                        .size(10.dp)
                        .background(MaterialTheme.colorScheme.gold, CircleShape)
                        .semantics { contentDescription = laeuftText },
                )
            }
        }
        DropdownMenu(expanded = menue, onDismissRequest = { menue = false }) {
            if (beiAnheften != null) {
                DropdownMenuItem(
                    text = {
                        Text(stringResource(if (gespraech.istAngeheftet) R.string.liste_loesen else R.string.liste_anheften))
                    },
                    leadingIcon = { Icon(Icons.Outlined.PushPin, null) },
                    onClick = {
                        menue = false
                        beiAnheften()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.loeschen_ja), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    menue = false
                    beiLoeschen()
                },
            )
        }
    }
}

/**
 * Loeschen geht an den Server und ist nicht umkehrbar - deshalb immer erst
 * nachfragen (siehe CLAUDE.md, „Gefaehrliche Knoepfe brauchen eine Rueckfrage“).
 */
@Composable
internal fun LoeschenFrage(titel: String, beiJa: () -> Unit, beiNein: () -> Unit) {
    AlertDialog(
        onDismissRequest = beiNein,
        title = { Text(stringResource(R.string.loeschen_titel)) },
        text = { Text(stringResource(R.string.loeschen_frage, titel)) },
        confirmButton = {
            TextButton(onClick = beiJa) {
                Text(stringResource(R.string.loeschen_ja), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = beiNein) { Text(stringResource(R.string.loeschen_abbrechen)) }
        },
    )
}
