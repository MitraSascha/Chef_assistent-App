package de.chefassistent.app.ui.komponenten

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.chefassistent.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Eine Datei im Ausgang (Auftrag 1.35): Foto, Video oder Dokument, das im Hintergrund
 * hochgeladen wird. Gilt fuer beide Apps.
 *
 * @property name Schluessel im jeweiligen Korb, zum Loeschen.
 * @property art `foto`, `video` oder `dokument`.
 */
data class AusgangZeile(
    val name: String,
    val art: String,
    val titel: String,
    val aufgenommen: Long,
    val status: Status,
    /** Nur bei [Status.SENDET] und [Status.VERKLEINERT]. */
    val prozent: Int? = null,
) {
    enum class Status {
        /** Kein Netz. */
        WARTET,

        /** Netz ist da, kommt nach den anderen dran. */
        DRAN,
        SENDET,
        VERKLEINERT,
    }

    val laeuft: Boolean get() = status == Status.SENDET || status == Status.VERKLEINERT
}

/**
 * Leiste oben: „Video wird gesendet 42 %“ bzw. „1 Foto wartet auf Empfang“. Antippen
 * oeffnet den Ausgang. Ohne Eintraege nichts.
 */
@Composable
fun AusgangLeiste(zeilen: List<AusgangZeile>, beiKlick: () -> Unit, modifier: Modifier = Modifier) {
    if (zeilen.isEmpty()) return
    val laufend = zeilen.firstOrNull { it.laeuft }
    val text = when {
        laufend?.status == AusgangZeile.Status.VERKLEINERT ->
            stringResource(R.string.ausgang_leiste_verkleinert, laufend.titel, laufend.prozent ?: 0)
        laufend != null -> stringResource(R.string.ausgang_leiste_sendet, laufend.titel, laufend.prozent ?: 0)
        else -> pluralStringResource(R.plurals.ausgang_leiste_wartet, zeilen.size, zeilen.size, zeilen.first().titel)
    }
    val weitere = if (laufend != null) zeilen.size - 1 else 0

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        onClick = beiKlick,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = LocalContentColor.current,
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
                if (weitere > 0) {
                    Text(
                        text = pluralStringResource(R.plurals.ausgang_leiste_weitere, weitere, weitere),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            laufend?.prozent?.let { prozent ->
                LinearProgressIndicator(
                    progress = { prozent / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
    }
}

/**
 * Der Ausgang als Blatt: jede Datei mit Stand, Loeschen mit Rueckfrage. „Jetzt senden“
 * stoesst den Versand an, ohne auf WorkManager zu warten.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AusgangBlatt(
    zeilen: List<AusgangZeile>,
    beiLoeschen: (AusgangZeile) -> Unit,
    beiJetztSenden: () -> Unit,
    beiSchliessen: () -> Unit,
) {
    var loeschen by remember { mutableStateOf<AusgangZeile?>(null) }

    ModalBottomSheet(onDismissRequest = beiSchliessen) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.ausgang_titel),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                if (zeilen.any { !it.laeuft }) {
                    TextButton(onClick = beiJetztSenden) { Text(stringResource(R.string.ausgang_jetzt_senden)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (zeilen.isEmpty()) {
                Text(
                    stringResource(R.string.ausgang_fertig),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                itemsIndexed(zeilen, key = { _, z -> z.name }) { index, zeile ->
                    GruppenBlock(index = index, anzahl = zeilen.size, einzug = EINZUG_MIT_SYMBOL) {
                        AusgangEintrag(zeile, beiLoeschen = { loeschen = zeile })
                    }
                }
            }
        }
    }

    loeschen?.let { zeile ->
        AlertDialog(
            onDismissRequest = { loeschen = null },
            title = { Text(stringResource(R.string.ausgang_loeschen)) },
            text = { Text(stringResource(R.string.ausgang_loeschen_frage, zeile.titel)) },
            confirmButton = {
                TextButton(onClick = {
                    loeschen = null
                    beiLoeschen(zeile)
                }) { Text(stringResource(R.string.ausgang_loeschen_ja), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { loeschen = null }) { Text(stringResource(R.string.ausgang_abbrechen)) }
            },
        )
    }
}

@Composable
private fun AusgangEintrag(zeile: AusgangZeile, beiLoeschen: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RundesSymbol(
            symbol = when (zeile.art) {
                "video" -> Icons.Default.Videocam
                "dokument" -> Icons.Default.Description
                else -> Icons.Default.Photo
            },
            farbe = MaterialTheme.colorScheme.primary,
        )
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                text = zeile.titel + " · " + SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY).format(Date(zeile.aufgenommen)),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when (zeile.status) {
                    AusgangZeile.Status.WARTET -> stringResource(R.string.ausgang_status_wartet)
                    AusgangZeile.Status.DRAN -> stringResource(R.string.ausgang_status_der_reihe_nach)
                    AusgangZeile.Status.SENDET -> stringResource(R.string.ausgang_status_sendet, zeile.prozent ?: 0)
                    AusgangZeile.Status.VERKLEINERT -> stringResource(R.string.ausgang_status_verkleinert, zeile.prozent ?: 0)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            zeile.prozent?.let { prozent ->
                LinearProgressIndicator(
                    progress = { prozent / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
        IconButton(onClick = beiLoeschen) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.ausgang_loeschen))
        }
    }
}
