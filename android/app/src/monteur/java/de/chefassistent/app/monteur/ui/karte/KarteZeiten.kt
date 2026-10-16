package de.chefassistent.app.monteur.ui.karte

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.chefassistent.app.R
import de.chefassistent.app.monteur.data.ART_LAGER
import de.chefassistent.app.monteur.data.Karte
import de.chefassistent.app.monteur.data.ZeitAbschnitt
import de.chefassistent.app.monteur.ui.zeiten.ARTEN_AUF_PROJEKT
import de.chefassistent.app.monteur.ui.zeiten.ArtHilfe
import de.chefassistent.app.monteur.ui.zeiten.UHRZEIT
import de.chefassistent.app.monteur.ui.zeiten.ZeitKnopf
import de.chefassistent.app.monteur.ui.zeiten.abschnittEntfernen
import de.chefassistent.app.monteur.ui.zeiten.abschnittErsetzen
import de.chefassistent.app.monteur.ui.zeiten.arbeitMinuten
import de.chefassistent.app.monteur.ui.zeiten.artName
import de.chefassistent.app.monteur.ui.zeiten.artSymbol
import de.chefassistent.app.monteur.ui.zeiten.artenZurWahl
import de.chefassistent.app.monteur.ui.zeiten.endetAmFolgetag
import de.chefassistent.app.monteur.ui.zeiten.minutenText
import de.chefassistent.app.monteur.ui.zeiten.uhrzeitLesen
import de.chefassistent.app.ui.theme.warnung
import java.time.LocalTime

/**
 * Karte „Zeiten“ (Auftrag 1.23, 2.): die erkannten Abschnitte als Liste. Antippen
 * aendert Uhrzeit und Art; jede Aenderung schreibt die **vollstaendige** Liste nach
 * [KartenEingabe.zeiten], denn `…/passt` ersetzt die Abschnitte als Ganzes.
 * Angezeigt wird immer die geaenderte Fassung, sonst die der KI.
 */
@Composable
fun ZeitenInhalt(karte: Karte, eingabe: KartenEingabe, aktiv: Boolean) {
    val abschnitte = eingabe.zeiten ?: karte.zeiten.orEmpty()
    var bearbeiten by rememberSaveable(karte.id) { mutableStateOf<Int?>(null) }

    if (abschnitte.isEmpty()) {
        Text(
            stringResource(R.string.kz_leer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            abschnitte.forEachIndexed { i, abschnitt ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)
                AbschnittZeile(abschnitt, karte.projekt?.nr, aktiv) { bearbeiten = i }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp),
            ) {
                Text(
                    stringResource(R.string.kz_summe, minutenText(arbeitMinuten(abschnitte))),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.kz_summe_hinweis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (aktiv) {
        Text(
            stringResource(R.string.kz_antippen),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    val index = bearbeiten
    val ziel = index?.let { abschnitte.getOrNull(it) }
    if (aktiv && index != null && ziel != null) {
        AbschnittDialog(
            abschnitt = ziel,
            beiUebernehmen = { neu ->
                eingabe.zeiten = abschnittErsetzen(abschnitte, index, neu)
                bearbeiten = null
            },
            beiEntfernen = {
                eingabe.zeiten = abschnittEntfernen(abschnitte, index)
                bearbeiten = null
            },
            beiSchliessen = { bearbeiten = null },
        )
    }
}

@Composable
private fun AbschnittZeile(abschnitt: ZeitAbschnitt, projektNr: String?, aktiv: Boolean, beiTippen: () -> Unit) {
    // Bei Arbeit/Material steht dabei, auf welches Projekt es geht; Lager hat bewusst keins.
    val unterzeile = when {
        abschnitt.art == ART_LAGER -> stringResource(R.string.kz_ohne_projekt)
        abschnitt.art in ARTEN_AUF_PROJEKT && !projektNr.isNullOrBlank() -> stringResource(R.string.kz_auf_projekt, projektNr)
        else -> null
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = aktiv, onClick = beiTippen)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(artSymbol(abschnitt.art), null, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${abschnitt.beginn}–${abschnitt.ende}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    artName(abschnitt.art),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            if (unterzeile != null) {
                Text(unterzeile, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (aktiv) {
            Icon(
                Icons.Outlined.Edit,
                stringResource(R.string.kz_aendern),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Uhrzeit und Art eines Abschnitts. Zur Wahl stehen nur die Arten, die der Monteur
 * selbst anlegen darf ([artenZurWahl]); eine Projektnummer gibt es hier nicht, der
 * Server haengt `arbeit`/`material` an das Projekt der Karte.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AbschnittDialog(
    abschnitt: ZeitAbschnitt,
    beiUebernehmen: (ZeitAbschnitt) -> Unit,
    beiEntfernen: () -> Unit,
    beiSchliessen: () -> Unit,
) {
    var von by remember(abschnitt) { mutableStateOf(uhrzeitLesen(abschnitt.beginn) ?: LocalTime.of(7, 0)) }
    var bis by remember(abschnitt) { mutableStateOf(uhrzeitLesen(abschnitt.ende) ?: LocalTime.of(16, 0)) }
    var art by remember(abschnitt) { mutableStateOf(abschnitt.art) }
    val gueltig = von != bis

    AlertDialog(
        onDismissRequest = beiSchliessen,
        title = { Text(stringResource(R.string.kz_dialog_titel)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ZeitKnopf(stringResource(R.string.z_von), von, { von = it }, Modifier.weight(1f))
                    ZeitKnopf(stringResource(R.string.z_bis), bis, { bis = it }, Modifier.weight(1f))
                }
                when {
                    !gueltig -> Text(
                        stringResource(R.string.kz_zeit_ungueltig),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    endetAmFolgetag(von, bis) -> Text(
                        stringResource(R.string.kz_folgetag),
                        color = MaterialTheme.colorScheme.warnung,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    stringResource(R.string.z_art),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    artenZurWahl(abschnitt.art).forEach { a ->
                        FilterChip(
                            selected = a == art,
                            onClick = { art = a },
                            label = { Text(artName(a)) },
                            leadingIcon = { Icon(artSymbol(a), null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                }
                ArtHilfe(art, Modifier.padding(top = 4.dp))
                TextButton(
                    onClick = beiEntfernen,
                    modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.kz_entfernen), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = gueltig,
                onClick = { beiUebernehmen(ZeitAbschnitt(beginn = von.format(UHRZEIT), ende = bis.format(UHRZEIT), art = art)) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.kz_uebernehmen)) }
        },
        dismissButton = {
            TextButton(onClick = beiSchliessen, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.kz_abbrechen))
            }
        },
    )
}
