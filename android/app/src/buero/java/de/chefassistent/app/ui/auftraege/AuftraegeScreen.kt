package de.chefassistent.app.ui.auftraege

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.chefassistent.app.R
import de.chefassistent.app.data.Auftrag
import de.chefassistent.app.ui.dateiOeffnen
import de.chefassistent.app.ui.komponenten.GrosseKopfleiste
import de.chefassistent.app.ui.komponenten.LeerHinweis
import de.chefassistent.app.ui.komponenten.listenAbstand
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ZEIT = DateTimeFormatter.ofPattern("dd.MM. HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuftraegeScreen(
    viewModel: AuftraegeViewModel,
    beiZurueck: () -> Unit,
    beiAbmeldung: () -> Unit,
    beiOrdner: (String) -> Unit,
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val kontext = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var abbrechenFrage by remember { mutableStateOf<Auftrag?>(null) }

    // Nachfragen nur, solange die Ansicht wirklich zu sehen ist.
    LifecycleResumeEffect(Unit) {
        viewModel.sichtbar(true)
        onPauseOrDispose { viewModel.sichtbar(false) }
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

    // Ein Bericht wertet 5-20 Minuten lang Unterlagen aus. Ein versehentlicher
    // Tipp darf das nicht wegwerfen.
    abbrechenFrage?.let { auftrag ->
        AlertDialog(
            onDismissRequest = { abbrechenFrage = null },
            title = { Text(stringResource(R.string.auftrag_abbrechen_titel)) },
            text = { Text(stringResource(R.string.auftrag_abbrechen_frage, auftrag.titel)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.abbrechen(auftrag.id)
                    abbrechenFrage = null
                }) {
                    Text(stringResource(R.string.auftrag_abbrechen), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { abbrechenFrage = null }) {
                    Text(stringResource(R.string.auftrag_weiterlaufen))
                }
            },
        )
    }

    val aktive = zustand.auftraege.count { it.istAktiv }
    GrosseKopfleiste(
        titel = stringResource(R.string.auftraege_titel),
        untertitel = if (aktive > 0) pluralStringResource(R.plurals.auftraege_laufen, aktive, aktive) else null,
        beiZurueck = beiZurueck,
        snackbar = snackbar,
        aktionen = {
            IconButton(onClick = { viewModel.laden() }) {
                Icon(Icons.Default.Refresh, stringResource(R.string.neu_laden))
            }
        },
    ) { innen ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = listenAbstand(innen),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (zustand.laeuft) {
                item(key = "laedt") { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }
            if (zustand.geladen && zustand.auftraege.isEmpty()) {
                item(key = "leer") { LeerHinweis(stringResource(R.string.auftraege_leer)) }
            }
            items(zustand.auftraege, key = { it.id }) { auftrag ->
                AuftragKarte(
                    auftrag = auftrag,
                    neu = auftrag.id in zustand.neu,
                    beschaeftigt = zustand.aktion == auftrag.id,
                    laedtDatei = zustand.laedtDatei,
                    beiDatei = { pfad -> viewModel.dateiOeffnen(pfad) { dateiOeffnen(kontext, it) } },
                    beiOrdner = beiOrdner,
                    beiAbbrechen = { abbrechenFrage = auftrag },
                    beiWiederholen = { viewModel.wiederholen(auftrag.id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AuftragKarte(
    auftrag: Auftrag,
    neu: Boolean,
    beschaeftigt: Boolean,
    laedtDatei: String?,
    beiDatei: (String) -> Unit,
    beiOrdner: (String) -> Unit,
    beiAbbrechen: () -> Unit,
    beiWiederholen: () -> Unit,
) {
    val farben = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (neu) farben.secondaryContainer else farben.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    artSymbol(auftrag.art),
                    contentDescription = null,
                    tint = farben.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = artText(auftrag.art),
                    style = MaterialTheme.typography.labelMedium,
                    color = farben.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Text(auftrag.titel, style = MaterialTheme.typography.titleMedium)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = statusText(auftrag.status),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = statusFarbe(auftrag.status),
                )
                if (neu) {
                    Text(
                        text = " · " + stringResource(R.string.auftrag_neu),
                        style = MaterialTheme.typography.labelLarge,
                        color = farben.primary,
                    )
                }
                Text(
                    text = " · " + ortszeit(auftrag.geaendert),
                    style = MaterialTheme.typography.labelMedium,
                    color = farben.onSurfaceVariant,
                )
            }

            if (auftrag.istAktiv) {
                Text(auftrag.schritt, style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(
                    progress = { auftrag.fortschritt.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Bei "wartet" heisst ein Fehler: der Server versucht es gleich wieder.
            auftrag.fehler?.takeIf { auftrag.status != Auftrag.STATUS_FERTIG }?.let { fehler ->
                Text(
                    text = if (auftrag.status == Auftrag.STATUS_WARTET) {
                        stringResource(R.string.auftrag_neuer_versuch, fehler)
                    } else {
                        fehler
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (auftrag.status == Auftrag.STATUS_WARTET) farben.onSurfaceVariant else farben.error,
                )
            }

            auftrag.zusammenfassung?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            if (auftrag.dateien.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    auftrag.dateien.forEach { pfad ->
                        AssistChip(
                            onClick = { beiDatei(pfad) },
                            label = {
                                Text(pfad.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            leadingIcon = {
                                if (laedtDatei == pfad) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        if (pfad.endsWith(".pdf", ignoreCase = true)) Icons.Default.PictureAsPdf
                                        else Icons.AutoMirrored.Filled.InsertDriveFile,
                                        contentDescription = null,
                                    )
                                }
                            },
                        )
                    }
                    AssistChip(
                        onClick = { beiOrdner(auftrag.dateien.first()) },
                        label = { Text(stringResource(R.string.ablage_im_ordner)) },
                        leadingIcon = { Icon(Icons.Default.Folder, null) },
                    )
                }
            }

            if (auftrag.offene_punkte.isNotEmpty()) {
                Pruefliste(
                    auftragId = auftrag.id,
                    punkte = auftrag.offene_punkte,
                    // Ein Begehungsprotokoll wird nicht versandt, es ist Angebotsgrundlage.
                    ueberschrift = if (auftrag.art == Auftrag.ART_BEGEHUNGSPROTOKOLL) {
                        stringResource(R.string.auftrag_offene_punkte)
                    } else {
                        stringResource(R.string.auftrag_pruefen)
                    },
                )
            }

            if (auftrag.kannAbbrechen || auftrag.kannWiederholen) {
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    if (beschaeftigt) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else if (auftrag.kannAbbrechen) {
                        OutlinedButton(onClick = beiAbbrechen, shape = CircleShape) { Text(stringResource(R.string.auftrag_abbrechen)) }
                    } else {
                        FilledTonalButton(onClick = beiWiederholen, shape = CircleShape) { Text(stringResource(R.string.auftrag_wiederholen)) }
                    }
                }
            }
        }
    }
}

/**
 * "Vor dem Versand pruefen". Die Haken leben nur auf dem Bildschirm: der
 * Vertrag kennt kein Feld dafuer, und abgehakt ist erst, was im Bericht steht.
 */
@Composable
private fun Pruefliste(auftragId: String, punkte: List<String>, ueberschrift: String) {
    val erledigt = remember(auftragId) { mutableStateMapOf<Int, Boolean>() }
    Column {
        Text(
            text = ueberschrift,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp),
        )
        punkte.forEachIndexed { index, punkt ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { erledigt[index] = !(erledigt[index] ?: false) },
            ) {
                Checkbox(checked = erledigt[index] ?: false, onCheckedChange = { erledigt[index] = it })
                Text(punkt, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun artText(art: String): String = when (art) {
    Auftrag.ART_VERSICHERUNGSBERICHT -> stringResource(R.string.auftrag_art_versicherung)
    Auftrag.ART_BEGEHUNGSPROTOKOLL -> stringResource(R.string.auftrag_art_begehung)
    // Neue Art vom Server: generisch anzeigen (API.md 4b-2).
    else -> stringResource(R.string.auftrag_art_sonstig)
}

private fun artSymbol(art: String): ImageVector = when (art) {
    Auftrag.ART_VERSICHERUNGSBERICHT -> Icons.Default.Shield
    Auftrag.ART_BEGEHUNGSPROTOKOLL -> Icons.Default.Checklist
    else -> Icons.Default.WorkOutline
}

@Composable
private fun statusText(status: String): String = when (status) {
    Auftrag.STATUS_WARTET -> stringResource(R.string.auftrag_status_wartet)
    Auftrag.STATUS_LAEUFT -> stringResource(R.string.auftrag_status_laeuft)
    Auftrag.STATUS_FERTIG -> stringResource(R.string.auftrag_status_fertig)
    Auftrag.STATUS_FEHLER -> stringResource(R.string.auftrag_status_fehler)
    Auftrag.STATUS_ABGEBROCHEN -> stringResource(R.string.auftrag_status_abgebrochen)
    // Neuer Status vom Server: lieber roh zeigen als abstuerzen.
    else -> status
}

@Composable
private fun statusFarbe(status: String): Color = when (status) {
    Auftrag.STATUS_FERTIG -> MaterialTheme.colorScheme.primary
    Auftrag.STATUS_FEHLER -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Der Server liefert teils UTC - ohne Umrechnung stuende im Sommer zwei Stunden zu wenig da. */
private fun ortszeit(iso: String): String = runCatching {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(ZEIT)
}.getOrDefault(iso)
