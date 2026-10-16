package de.chefassistent.app.monteur.ui.karte

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.chefassistent.app.R
import de.chefassistent.app.monteur.data.FOTO_TITEL_MAX
import de.chefassistent.app.monteur.data.Karte

/**
 * Karte `foto` (Auftrag 1.28, 3.): Vorschaubilder, darunter der Titel als einzeiliges,
 * direkt bearbeitbares Feld. „Passt“ legt die Fotos mit diesem Titel ins Projekt.
 */
@Composable
fun FotoInhalt(karte: Karte, eingabe: KartenEingabe, fotos: List<String>, aktiv: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FotoReihe(fotos)
        val weitere = karte.fotos - fotos.size
        if (weitere > 0) {
            Text(
                pluralStringResource(R.plurals.bs_fotos_mehr, weitere, weitere),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Die Ueberschrift „Liegt im Projekt“ zeigt hier schon das Ergebnis darunter.
        FotoTitel(karte, eingabe, aktiv, beschriftung = stringResource(R.string.k_foto_titel), mitUeberschrift = false)
    }
}

/**
 * Frage-Karte mit Fotos (Auftrag 1.28, 4.): unter der Antwort klein „Foto ins Projekt legen
 * als: <Titel>“. Abgelegt wird mit „Passt – Foto ablegen“ unten, „Fertig“ schliesst ohne.
 */
@Composable
fun FotoAblageZeile(karte: Karte, eingabe: KartenEingabe, aktiv: Boolean) {
    if (karte.foto == null) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (karte.foto.abgelegt.isEmpty()) {
            Text(
                stringResource(R.string.k_foto_ablegen_als),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FotoTitel(karte, eingabe, aktiv, beschriftung = null)
    }
}

/** Titelfeld bzw. nach dem Ablegen „Liegt im Projekt“ mit den Dateinamen. */
@Composable
private fun FotoTitel(karte: Karte, eingabe: KartenEingabe, aktiv: Boolean, beschriftung: String?, mitUeberschrift: Boolean = true) {
    val ablage = karte.foto ?: return
    if (ablage.abgelegt.isNotEmpty()) {
        Column {
            if (mitUeberschrift) Text(stringResource(R.string.k_erg_foto), style = MaterialTheme.typography.titleSmall)
            ablage.abgelegt.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    val original = ablage.titel
    var titel by rememberSaveable(karte.id, original) { mutableStateOf(eingabe.titel ?: original) }
    // Nur ein geaenderter Titel geht mit.
    LaunchedEffect(karte.id, titel, original) { eingabe.titel = titel.trim().takeIf { it.isNotEmpty() && it != original } }
    OutlinedTextField(
        value = titel,
        onValueChange = { titel = it.replace('\n', ' ').take(FOTO_TITEL_MAX) },
        enabled = aktiv,
        singleLine = true,
        label = beschriftung?.let { b -> { Text(b) } },
        modifier = Modifier.fillMaxWidth(),
    )
}
