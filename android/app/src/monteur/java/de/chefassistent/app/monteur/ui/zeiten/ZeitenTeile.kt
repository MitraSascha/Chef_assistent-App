package de.chefassistent.app.monteur.ui.zeiten

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Warehouse
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.chefassistent.app.R
import de.chefassistent.app.monteur.data.ART_ARBEIT
import de.chefassistent.app.monteur.data.ART_LAGER
import de.chefassistent.app.monteur.data.Stempel
import de.chefassistent.app.monteur.data.ZEIT_ARTEN
import de.chefassistent.app.monteur.data.ZEIT_ARTEN_ANLEGEN
import de.chefassistent.app.monteur.data.Zeiteintrag
import de.chefassistent.app.ui.komponenten.GruppenKarte
import de.chefassistent.app.ui.theme.warnung
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val VORSCHLAG_DATUM: DateTimeFormatter = DateTimeFormatter.ofPattern("EE, d.M.", Locale.GERMANY)

// --- Arten -----------------------------------------------------------------------

fun artSymbol(art: String): ImageVector = when (art) {
    "arbeit" -> Icons.Outlined.Build
    "fahrt" -> Icons.Outlined.DirectionsCar
    "pause" -> Icons.Outlined.Coffee
    "material" -> Icons.Outlined.Inventory2
    ART_LAGER -> Icons.Outlined.Warehouse
    "buero" -> Icons.Outlined.Business
    "vor_ort" -> Icons.Outlined.Place
    else -> Icons.Outlined.MoreHoriz
}

/** Anzeigename; Unbekanntes (neue Arten vom Server) roh statt gar nicht. */
@Composable
fun artName(art: String): String = when (art) {
    "arbeit" -> stringResource(R.string.z_art_arbeit)
    "fahrt" -> stringResource(R.string.z_art_fahrt)
    "pause" -> stringResource(R.string.z_art_pause)
    "material" -> stringResource(R.string.z_art_material)
    ART_LAGER -> stringResource(R.string.z_art_lager)
    "buero" -> stringResource(R.string.z_art_buero)
    "vor_ort" -> stringResource(R.string.z_art_vor_ort)
    "sonstige" -> stringResource(R.string.z_art_sonstige)
    else -> art
}

@Composable
private fun ArtAuswahl(gewaehlt: String, beiWahl: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        ZEIT_ARTEN_ANLEGEN.forEach { art ->
            FilterChip(
                selected = art == gewaehlt,
                onClick = { beiWahl(art) },
                label = { Text(artName(art)) },
                leadingIcon = { Icon(artSymbol(art), null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

/** Projektnummern der Termine als Chips, dazu „Ohne Projekt“. `""` = ohne. */
@Composable
private fun ProjektAuswahl(
    projekte: List<Pair<String, String>>,
    gewaehlt: String,
    beiWahl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (projekte.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        FilterChip(
            selected = gewaehlt.isBlank(),
            onClick = { beiWahl("") },
            label = { Text(stringResource(R.string.z_ohne_projekt)) },
            modifier = Modifier.heightIn(min = 48.dp),
        )
        projekte.forEach { (nr, kunde) ->
            FilterChip(
                selected = nr == gewaehlt,
                onClick = { beiWahl(nr) },
                label = { Text(if (kunde.isBlank()) nr else "$nr · $kunde", maxLines = 1) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

// --- Stempeluhr ----------------------------------------------------------------------

/**
 * Oben im Reiter, nur heute. Laeuft keine Uhr: Art (und Projekt) waehlen, „Start“.
 * Laeuft sie: Dauer sekuendlich, „Wechsel“ bucht den laufenden Abschnitt und
 * startet die gewaehlte Art, „Stopp“ bucht ihn. Die Uhrzeit nimmt der Server.
 */
@Composable
fun StempelKarte(
    stempel: Stempel?,
    jetzt: LocalDateTime,
    projekte: List<Pair<String, String>>,
    sendet: Boolean,
    beiAktion: (aktion: String, art: String, projektNr: String?) -> Unit,
) {
    var art by rememberSaveable(stempel?.art) { mutableStateOf(stempel?.art?.takeIf { it in ZEIT_ARTEN_ANLEGEN } ?: ART_ARBEIT) }
    var projekt by rememberSaveable { mutableStateOf(projekte.singleOrNull()?.first.orEmpty()) }
    val beginn = zeitLesen(stempel?.beginn)

    GruppenKarte(modifier = Modifier.padding(vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            if (stempel == null) {
                Text(stringResource(R.string.z_stempel), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(artSymbol(stempel.art), null, modifier = Modifier.size(20.dp))
                    Text(
                        stringResource(
                            R.string.z_stempel_laeuft,
                            artName(stempel.art),
                            beginn?.format(UHRZEIT) ?: stempel.beginn,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Text(
                    beginn?.let { dauerText(it, jetzt) } ?: "–",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (stempel.kommentar.isNotBlank()) {
                    Text(stempel.kommentar, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    stringResource(R.string.z_stempel_wechsel_hinweis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            ArtAuswahl(art, { art = it }, Modifier.padding(top = 8.dp))
            // Lager, Fahrt, Pause haben kein Projekt - die Chips wuerden nur verleiten.
            if (mitProjekt(art)) ProjektAuswahl(projekte, projekt, { projekt = it }, Modifier.padding(top = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                if (stempel == null) {
                    Button(
                        onClick = { beiAktion(ZeitenViewModel.AKTION_START, art, projektFuerArt(art, projekt)) },
                        enabled = !sendet,
                        colors = navyFarben(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.z_stempel_start), modifier = Modifier.padding(start = 6.dp))
                    }
                } else {
                    Button(
                        onClick = { beiAktion(ZeitenViewModel.AKTION_WECHSEL, art, projektFuerArt(art, projekt)) },
                        enabled = !sendet,
                        colors = navyFarben(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Outlined.SwapHoriz, null, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.z_stempel_wechsel), modifier = Modifier.padding(start = 6.dp))
                    }
                    OutlinedButton(
                        onClick = { beiAktion(ZeitenViewModel.AKTION_STOPP, stempel.art.takeIf { it in ZEIT_ARTEN } ?: ART_ARBEIT, null) },
                        enabled = !sendet,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Outlined.Stop, null, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.z_stempel_stopp), modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

// --- Verstehen -------------------------------------------------------------------------

@Composable
fun VorschlagKarte(
    versteht: Boolean,
    vorschlag: Vorschlag?,
    fehler: String?,
    wiederholbar: Boolean,
    sendet: Boolean,
    beiUebernehmen: () -> Unit,
    beiVerwerfen: () -> Unit,
    beiNochmal: () -> Unit,
) {
    GruppenKarte(modifier = Modifier.padding(vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            when {
                versteht -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.z_versteht), modifier = Modifier.padding(start = 12.dp))
                }

                fehler != null -> {
                    Text(fehler, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        TextButton(onClick = beiVerwerfen) { Text(stringResource(R.string.z_verwerfen)) }
                        if (wiederholbar) TextButton(onClick = beiNochmal) { Text(stringResource(R.string.z_nochmal)) }
                    }
                }

                vorschlag != null -> VorschlagInhalt(vorschlag, sendet, beiUebernehmen, beiVerwerfen)
            }
        }
    }
}

@Composable
private fun VorschlagInhalt(vorschlag: Vorschlag, sendet: Boolean, beiUebernehmen: () -> Unit, beiVerwerfen: () -> Unit) {
    val v = vorschlag.verstanden
    val tag = runCatching { LocalDate.parse(vorschlag.datum) }.getOrNull()
    Text(
        stringResource(R.string.z_vorschlag, tag?.format(VORSCHLAG_DATUM) ?: vorschlag.datum),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    if (v.text.isNotBlank()) {
        Text(
            "„${v.text}“",
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    vorschlag.offen.forEach { (abschnitt, _) ->
        val art = artFuerBuchung(abschnitt.art)
        val lesbar = vorschlagZuZeit(abschnitt, vorschlag.datum, "x") != null
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Icon(artSymbol(art), null, modifier = Modifier.size(20.dp))
            Text(
                "${abschnitt.beginn}–${abschnitt.ende}  ${artName(art)}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 12.dp).weight(1f),
            )
            if (!lesbar) {
                Text(stringResource(R.string.z_vorschlag_unlesbar), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
    v.rueckfrage?.takeIf { it.isNotBlank() }?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.warnung,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    if (v.abwesenheit != null) {
        Text(
            stringResource(R.string.z_urlaub_hinweis),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    if (vorschlag.offen.isEmpty() && v.abwesenheit == null && v.rueckfrage.isNullOrBlank()) {
        Text(
            stringResource(R.string.z_vorschlag_leer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        TextButton(onClick = beiVerwerfen, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(if (vorschlag.offen.isEmpty()) R.string.z_schliessen else R.string.z_verwerfen))
        }
        if (vorschlag.offen.isNotEmpty()) {
            Button(
                onClick = beiUebernehmen,
                enabled = !sendet,
                colors = navyFarben(),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.z_uebernehmen)) }
        }
    }
}

// --- Formular ----------------------------------------------------------------------------

/**
 * Neuer oder geaenderter Abschnitt am angezeigten Tag. Bis vor Von heisst: endet
 * am Folgetag (Notdienst) - das sagt ein Hinweis, damit es nicht aus Versehen passiert.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ZeitFormular(
    eintrag: Zeiteintrag?,
    datum: LocalDate,
    projekte: List<Pair<String, String>>,
    sendet: Boolean,
    beiSchliessen: () -> Unit,
    beiSpeichern: (von: LocalTime, bis: LocalTime, art: String, projektNr: String, kommentar: String) -> Unit,
    beiLoeschen: (() -> Unit)?,
) {
    val vorgabeVon = remember(eintrag) {
        eintrag?.let { zeitLesen(it.beginn)?.toLocalTime() } ?: LocalTime.of(7, 0)
    }
    val vorgabeBis = remember(eintrag) {
        eintrag?.let { zeitLesen(it.ende)?.toLocalTime() } ?: LocalTime.of(16, 0)
    }
    var von by remember(eintrag) { mutableStateOf(vorgabeVon) }
    var bis by remember(eintrag) { mutableStateOf(vorgabeBis) }
    var art by remember(eintrag) { mutableStateOf(eintrag?.art?.takeIf { it in ZEIT_ARTEN } ?: ART_ARBEIT) }
    var projekt by remember(eintrag) { mutableStateOf(eintrag?.projekt_nr.orEmpty()) }
    var kommentar by remember(eintrag) { mutableStateOf(eintrag?.kommentar.orEmpty()) }
    val gueltig = zeitraum(datum, von, bis) != null

    ModalBottomSheet(onDismissRequest = beiSchliessen, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                stringResource(if (eintrag == null) R.string.z_neu_titel else R.string.z_aendern_titel),
                style = MaterialTheme.typography.titleLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 16.dp)) {
                ZeitKnopf(stringResource(R.string.z_von), von, { von = it }, Modifier.weight(1f))
                ZeitKnopf(stringResource(R.string.z_bis), bis, { bis = it }, Modifier.weight(1f))
            }
            when {
                !gueltig -> Text(stringResource(R.string.z_zeit_ungueltig), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                endetAmFolgetag(von, bis) -> Text(
                    stringResource(R.string.z_folgetag),
                    color = MaterialTheme.colorScheme.warnung,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Text(stringResource(R.string.z_art), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                artenZurWahl(eintrag?.art).forEach { a ->
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

            // Lager, Fahrt, Pause: kein Projekt, also auch kein Feld dafuer.
            if (mitProjekt(art)) {
                OutlinedTextField(
                    value = projekt,
                    onValueChange = { projekt = it.take(40) },
                    label = { Text(stringResource(R.string.z_projekt)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                ProjektAuswahl(projekte, projekt, { projekt = it }, Modifier.padding(top = 4.dp))
            }

            OutlinedTextField(
                value = kommentar,
                onValueChange = { kommentar = it.take(500) },
                label = { Text(stringResource(R.string.z_kommentar)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                if (beiLoeschen != null) {
                    TextButton(onClick = beiLoeschen, enabled = !sendet, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.z_loeschen), color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = beiSchliessen, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.z_abbrechen))
                }
                Button(
                    onClick = { beiSpeichern(von, bis, art, projektFuerArt(art, projekt), kommentar) },
                    enabled = gueltig && !sendet,
                    colors = navyFarben(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.z_speichern)) }
            }
        }
    }
}

/**
 * Ein Satz je Art, klein und grau, dazu der Hinweis zum Heimweg (Auftrag 1.23, 6.).
 * Die Regeln des Betriebs sind nicht selbsterklaerend - ohne den Satz landet die
 * Ladezeit am Morgen als „Arbeit“ beim ersten Kunden.
 */
@Composable
fun ArtHilfe(art: String, modifier: Modifier = Modifier) {
    val satz = when (art) {
        ART_LAGER -> R.string.z_hilfe_lager
        "fahrt" -> R.string.z_hilfe_fahrt
        "arbeit" -> R.string.z_hilfe_arbeit
        "material" -> R.string.z_hilfe_material
        "pause" -> R.string.z_hilfe_pause
        else -> null
    }
    Column(modifier) {
        if (satz != null) {
            Text(stringResource(satz), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            stringResource(R.string.z_hilfe_heimweg),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ZeitKnopf(beschriftung: String, wert: LocalTime, beiWahl: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    var offen by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(beschriftung, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(
            onClick = { offen = true },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Icon(Icons.Outlined.Schedule, null, modifier = Modifier.size(18.dp))
            Text(wert.format(UHRZEIT), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
        }
    }
    if (offen) {
        val auswahl = rememberTimePickerState(initialHour = wert.hour, initialMinute = wert.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { offen = false },
            confirmButton = {
                TextButton(onClick = {
                    beiWahl(LocalTime.of(auswahl.hour, auswahl.minute))
                    offen = false
                }) { Text(stringResource(R.string.z_ok)) }
            },
            dismissButton = { TextButton(onClick = { offen = false }) { Text(stringResource(R.string.z_abbrechen)) } },
            text = { TimePicker(state = auswahl) },
        )
    }
}

// --- Tag bestaetigen ------------------------------------------------------------------------

/** Tagesuebersicht vor dem Einreichen: was rausgeht, Summen, dann gesperrt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagesUebersicht(
    titel: String,
    eintraege: List<Zeiteintrag>,
    arbeit: Int,
    pause: Int,
    sendet: Boolean,
    beiEinreichen: () -> Unit,
    beiSchliessen: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = beiSchliessen, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(stringResource(R.string.z_uebersicht_titel, titel), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            if (eintraege.isEmpty()) {
                Text(stringResource(R.string.z_uebersicht_leer), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            eintraege.forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 40.dp)) {
                    Icon(artSymbol(e.art), null, modifier = Modifier.size(20.dp))
                    Text(
                        spanneText(e.beginn, e.ende),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                    Text(
                        listOfNotNull(artName(e.art), e.projekt_nr?.takeIf { it.isNotBlank() }).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp).weight(1f),
                    )
                    if (!e.aenderbar) Icon(Icons.Outlined.Lock, stringResource(R.string.z_status_eingereicht), modifier = Modifier.size(18.dp))
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(
                stringResource(R.string.z_uebersicht_summen, minutenText(arbeit), minutenText(pause)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.z_uebersicht_hinweis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                TextButton(onClick = beiSchliessen, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.z_abbrechen))
                }
                Button(
                    onClick = beiEinreichen,
                    enabled = eintraege.isNotEmpty() && !sendet,
                    colors = navyFarben(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.z_einreichen)) }
            }
        }
    }
}
