package de.chefassistent.app.ui.aufgaben

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.chefassistent.app.R
import de.chefassistent.app.data.Aufgabe
import de.chefassistent.app.data.AufgabenUebersicht
import de.chefassistent.app.ui.dateiOeffnen
import de.chefassistent.app.ui.komponenten.GrosseKopfleiste
import de.chefassistent.app.ui.komponenten.GruppenBlock
import de.chefassistent.app.ui.komponenten.GruppenTitel
import de.chefassistent.app.ui.komponenten.LeerHinweis
import de.chefassistent.app.ui.komponenten.gruppenAnordnung
import de.chefassistent.app.ui.komponenten.listenAbstand
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val KURZDATUM = DateTimeFormatter.ofPattern("dd.MM.")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AufgabenScreen(
    viewModel: AufgabenViewModel,
    /** `null` = Reiter der unteren Leiste, kein Zurueck-Pfeil (seit v1.21.0). */
    beiZurueck: (() -> Unit)?,
    beiAbmeldung: () -> Unit,
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val kontext = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var streichenFrage by remember { mutableStateOf<Aufgabe?>(null) }
    var fristFuer by remember { mutableStateOf<Aufgabe?>(null) }

    // Bei jeder Rueckkehr neu laden: im Chat kann inzwischen etwas abgehakt
    // oder angelegt worden sein, und die IDs koennen sich geaendert haben.
    LifecycleResumeEffect(Unit) {
        viewModel.laden()
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

    // Eine gestrichene Aufgabe verschwindet aus jeder Gruppe und laesst sich in
    // der App nicht mehr zuruecknehmen - deshalb die Rueckfrage.
    streichenFrage?.let { aufgabe ->
        AlertDialog(
            onDismissRequest = { streichenFrage = null },
            title = { Text(stringResource(R.string.aufgabe_streichen_titel)) },
            text = { Text(stringResource(R.string.aufgabe_streichen_frage, aufgabe.text)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.streichen(aufgabe)
                    streichenFrage = null
                }) {
                    Text(stringResource(R.string.aufgabe_weg_damit), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { streichenFrage = null }) {
                    Text(stringResource(R.string.loeschen_abbrechen))
                }
            },
        )
    }

    fristFuer?.let { aufgabe ->
        FristDialog(
            aufgabe = aufgabe,
            beiFertig = { bis ->
                viewModel.fristSetzen(aufgabe, bis)
                fristFuer = null
            },
            beiAbbruch = { fristFuer = null },
        )
    }

    val uebersicht = zustand.uebersicht
    val gruppen = uebersicht?.gruppen?.filter { it.second.isNotEmpty() }.orEmpty()

    GrosseKopfleiste(
        titel = stringResource(R.string.aufgaben_titel),
        // Reiter der unteren Leiste: schmaler Kopf wie Chat und Kalender (seit v1.21.0).
        kompakt = true,
        untertitel = uebersicht?.let {
            if (zustand.person == null) {
                pluralStringResource(R.plurals.aufgaben_offen_anzahl, it.offen, it.offen)
            } else {
                it.person
            }
        },
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
            verticalArrangement = gruppenAnordnung,
        ) {
            if (zustand.laeuft) {
                item(key = "laedt") { LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) }
            }

            if (zustand.personen.isNotEmpty()) {
                item(key = "personen") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 4.dp),
                    ) {
                        FilterChip(
                            selected = zustand.person == null,
                            onClick = { viewModel.personWaehlen(null) },
                            label = { Text(stringResource(R.string.aufgaben_ich)) },
                            shape = CircleShape,
                        )
                        zustand.personen.forEach { name ->
                            FilterChip(
                                selected = zustand.person == name,
                                onClick = { viewModel.personWaehlen(name) },
                                label = { Text(name) },
                                shape = CircleShape,
                            )
                        }
                    }
                }
            }

            if (uebersicht != null && gruppen.isEmpty()) {
                item(key = "leer") { LeerHinweis(stringResource(R.string.aufgaben_leer), Modifier.padding(top = 8.dp)) }
            }

            gruppen.forEach { (gruppe, aufgaben) ->
                item(key = "kopf_$gruppe") {
                    GruppenTitel(
                        text = gruppenTitel(gruppe),
                        farbe = if (gruppe == AufgabenUebersicht.GRUPPE_UEBERFAELLIG) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                itemsIndexed(aufgaben, key = { _, a -> "${gruppe}_${a.id}" }) { index, aufgabe ->
                    GruppenBlock(index = index, anzahl = aufgaben.size, einzug = 60.dp) {
                        AufgabeZeile(
                            aufgabe = aufgabe,
                            gruppe = gruppe,
                            person = uebersicht!!.person,
                            beschaeftigt = zustand.aktion == aufgabe.id,
                            gesperrt = zustand.aktion != null,
                            laedtNotiz = zustand.laedtDatei == aufgabe.notiz,
                            beiHaken = { viewModel.abhaken(aufgabe, it) },
                            beiNotiz = {
                                viewModel.dateiOeffnen(aufgabe.notiz) { dateiOeffnen(kontext, it) }
                            },
                            beiBleibt = { viewModel.bleibt(aufgabe) },
                            beiWeg = { streichenFrage = aufgabe },
                            beiFrist = { fristFuer = aufgabe },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AufgabeZeile(
    aufgabe: Aufgabe,
    gruppe: String,
    person: String,
    beschaeftigt: Boolean,
    gesperrt: Boolean,
    laedtNotiz: Boolean,
    beiHaken: (Boolean) -> Unit,
    beiNotiz: () -> Unit,
    beiBleibt: () -> Unit,
    beiWeg: () -> Unit,
    beiFrist: () -> Unit,
) {
    val farben = MaterialTheme.colorScheme
    val erledigt = aufgabe.istErledigt
    Box {
        Row(modifier = Modifier.padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 10.dp)) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (beschaeftigt) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Checkbox(checked = erledigt, enabled = !gesperrt, onCheckedChange = beiHaken)
                }
            }
            Column(
                modifier = Modifier.weight(1f).padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = aufgabe.text,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (erledigt) TextDecoration.LineThrough else null,
                    color = if (erledigt) farben.onSurfaceVariant else farben.onSurface,
                )

                val angaben = buildList {
                    aufgabe.bis?.let { add(stringResource(R.string.aufgabe_bis, kurzdatum(it))) }
                    if (gruppe == AufgabenUebersicht.GRUPPE_AN_ANDERE) {
                        aufgabe.fuer?.let { add(stringResource(R.string.aufgabe_fuer, it)) }
                    } else if (!aufgabe.von.equals(person, ignoreCase = true)) {
                        add(stringResource(R.string.aufgabe_von, aufgabe.von))
                    }
                    if (erledigt) aufgabe.erledigt_von?.let { add(stringResource(R.string.aufgabe_erledigt_von, it)) }
                }
                if (angaben.isNotEmpty()) {
                    Text(
                        text = angaben.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (gruppe == AufgabenUebersicht.GRUPPE_UEBERFAELLIG) farben.error else farben.onSurfaceVariant,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = aufgabe.notiz.substringAfterLast('/').removeSuffix(".md"),
                        style = MaterialTheme.typography.labelMedium,
                        color = farben.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).clickable(onClick = beiNotiz),
                    )
                    if (laedtNotiz) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(start = 6.dp).size(12.dp),
                            strokeWidth = 1.5.dp,
                        )
                    }
                }

                if (!erledigt) {
                    if (aufgabe.nachfragen) {
                        Text(
                            text = stringResource(R.string.aufgabe_noch_aktuell),
                            style = MaterialTheme.typography.labelLarge,
                            color = farben.tertiary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (aufgabe.nachfragen) {
                            OutlinedButton(onClick = beiBleibt, enabled = !gesperrt) {
                                Text(stringResource(R.string.aufgabe_bleibt))
                            }
                            TextButton(onClick = beiWeg, enabled = !gesperrt) {
                                Text(stringResource(R.string.aufgabe_weg_damit), color = farben.error)
                            }
                        }
                        TextButton(onClick = beiFrist, enabled = !gesperrt) {
                            Text(stringResource(R.string.aufgabe_neue_frist))
                        }
                    }
                }
            }
        }
    }
}

/** Kalender zum Waehlen der Frist, dazu "Frist entfernen", wenn schon eine gesetzt ist. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FristDialog(aufgabe: Aufgabe, beiFertig: (String?) -> Unit, beiAbbruch: () -> Unit) {
    val vorher = aufgabe.bis?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    // Der DatePicker rechnet in UTC-Mitternacht - Umrechnung in beide Richtungen ueber UTC.
    val auswahl = rememberDatePickerState(
        initialSelectedDateMillis = (vorher ?: LocalDate.now())
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = beiAbbruch,
        confirmButton = {
            TextButton(
                enabled = auswahl.selectedDateMillis != null,
                onClick = {
                    auswahl.selectedDateMillis?.let {
                        beiFertig(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                },
            ) { Text(stringResource(R.string.aufgabe_frist_setzen)) }
        },
        dismissButton = {
            Row {
                if (vorher != null) {
                    TextButton(onClick = { beiFertig(null) }) {
                        Text(stringResource(R.string.aufgabe_frist_entfernen))
                    }
                }
                TextButton(onClick = beiAbbruch) { Text(stringResource(R.string.loeschen_abbrechen)) }
            }
        },
    ) {
        DatePicker(state = auswahl)
    }
}

@Composable
private fun gruppenTitel(gruppe: String): String = when (gruppe) {
    AufgabenUebersicht.GRUPPE_UEBERFAELLIG -> stringResource(R.string.aufgaben_ueberfaellig)
    AufgabenUebersicht.GRUPPE_DIESE_WOCHE -> stringResource(R.string.aufgaben_diese_woche)
    AufgabenUebersicht.GRUPPE_SPAETER -> stringResource(R.string.aufgaben_spaeter)
    AufgabenUebersicht.GRUPPE_OHNE_DATUM -> stringResource(R.string.aufgaben_ohne_datum)
    AufgabenUebersicht.GRUPPE_AN_ANDERE -> stringResource(R.string.aufgaben_an_andere)
    AufgabenUebersicht.GRUPPE_HEUTE_ERLEDIGT -> stringResource(R.string.aufgaben_heute_erledigt)
    else -> gruppe
}

/** `YYYY-MM-DD` -> `TT.MM.`, unbekanntes Format roh. */
internal fun kurzdatum(iso: String): String =
    runCatching { LocalDate.parse(iso).format(KURZDATUM) }.getOrDefault(iso)
