package de.chefassistent.app.ui.kalender

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import de.chefassistent.app.R
import de.chefassistent.app.data.HeroTerminNeu
import de.chefassistent.app.data.KalenderTermin
import de.chefassistent.app.data.PERSON_ICH
import de.chefassistent.app.data.Personen
import de.chefassistent.app.data.TerminKategorie
import de.chefassistent.app.ui.komponenten.GruppenKarte
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DATUM = DateTimeFormatter.ofPattern("EE, d.M.yyyy", java.util.Locale.GERMANY)
private val UHR = DateTimeFormatter.ofPattern("HH:mm")

/**
 * "＋ HERO-Termin" bzw. "Verschieben" (`API.md` 5b). Immer zwei Schritte:
 * "Pruefen" zeigt Beschreibung und Ueberschneidungen, erst dann wird eingetragen.
 * Jede Aenderung danach verlangt eine neue Pruefung.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HeroTerminSheet(
    formular: HeroFormular,
    personen: Personen?,
    kategorien: List<TerminKategorie>,
    beiAenderung: (HeroFormular.() -> HeroFormular) -> Unit,
    beiAbschicken: () -> Unit,
    beiSchliessen: () -> Unit,
) {
    val f = formular
    val farben = MaterialTheme.colorScheme
    val alt = f.verschieben
    ModalBottomSheet(
        onDismissRequest = beiSchliessen,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                stringResource(if (alt == null) R.string.hero_titel_neu else R.string.hero_titel_verschieben),
                style = MaterialTheme.typography.titleLarge,
            )
            if (alt != null) {
                Text(alt.titel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.hero_bisher, bisherText(alt)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = farben.onSurfaceVariant,
                )
            } else {
                PersonenWahl(personen, f.personen) { schluessel -> beiAenderung { personUmschalten(schluessel) } }
                OutlinedTextField(
                    value = f.titel,
                    onValueChange = { text -> beiAenderung { copy(titel = text.take(HeroTerminNeu.TITEL_MAX)) } },
                    label = { Text(stringResource(R.string.hero_titel)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                KategorieWahl(kategorien, f.kategorieId) { id -> beiAenderung { copy(kategorieId = id) } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.hero_ganztaegig), modifier = Modifier.weight(1f))
                    Switch(checked = f.ganztag, onCheckedChange = { an -> beiAenderung { mitGanztag(an) } })
                }
            }

            // Datum, bei ganztaegig bzw. mehrtaegig auch "Bis Datum".
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DatumKnopf(
                    beschriftung = stringResource(R.string.hero_datum),
                    wert = f.datum,
                    beiWahl = { tag -> beiAenderung { mitDatum(tag) } },
                    modifier = Modifier.weight(1f),
                )
                if (f.ganztag || f.mehrtaegig) {
                    DatumKnopf(
                        beschriftung = stringResource(R.string.hero_bis_datum),
                        wert = f.bisDatum,
                        beiWahl = { tag -> beiAenderung { mitBisDatum(tag) } },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (!f.ganztag) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZeitKnopf(
                        beschriftung = stringResource(R.string.hero_von),
                        wert = f.von,
                        beiWahl = { zeit -> beiAenderung { mitVon(zeit) } },
                        modifier = Modifier.weight(1f),
                    )
                    ZeitKnopf(
                        beschriftung = stringResource(R.string.hero_bis),
                        wert = f.bis,
                        beiWahl = { zeit -> beiAenderung { mitBis(zeit) } },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (alt == null) {
                OutlinedTextField(
                    value = f.projekt,
                    onValueChange = { text -> beiAenderung { copy(projekt = text) } },
                    label = { Text(stringResource(R.string.hero_projekt)) },
                    placeholder = { Text(stringResource(R.string.hero_projekt_beispiel)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = f.notiz,
                    onValueChange = { text -> beiAenderung { copy(notiz = text.take(HeroTerminNeu.NOTIZ_MAX)) } },
                    label = { Text(stringResource(R.string.hero_notiz)) },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            f.geprueft?.let { pruefung ->
                GruppenKarte {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        pruefung.beschreibung?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodyLarge)
                        }
                        if (pruefung.ueberschneidungen.isEmpty()) {
                            Text(
                                stringResource(R.string.hero_keine_ueberschneidungen),
                                style = MaterialTheme.typography.bodyMedium,
                                color = farben.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                stringResource(R.string.hero_ueberschneidungen),
                                style = MaterialTheme.typography.titleSmall,
                                color = farben.error,
                            )
                            pruefung.ueberschneidungen.forEach { u ->
                                Text(
                                    "⚠ ${u.wer}: ${u.titel}, ${zeitspanne(u.beginn, u.ende)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = farben.error,
                                )
                            }
                        }
                        pruefung.hinweis?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
                        }
                    }
                }
            }

            f.fehler?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = farben.error) }
            f.luecke?.let { luecke ->
                Text(lueckeText(luecke), style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = beiSchliessen) { Text(stringResource(R.string.hero_abbrechen)) }
                Spacer(Modifier.weight(1f))
                Button(onClick = beiAbschicken, enabled = !f.laeuft && f.luecke == null) {
                    if (f.laeuft) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        stringResource(
                            when {
                                f.geprueft == null -> R.string.hero_pruefen
                                alt == null -> R.string.hero_eintragen
                                else -> R.string.hero_verschieben_knopf
                            },
                        ),
                    )
                }
            }
        }
    }
}

/** "Mo, 20.10.2026 08:00–09:00" bzw. bei ganztaegigen nur die Tage. */
private fun bisherText(termin: KalenderTermin): String {
    val f = HeroFormular.verschieben(termin) ?: return termin.beginn
    return if (f.ganztag) {
        if (f.mehrtaegig) "${f.datum.format(DATUM)} – ${f.bisDatum.format(DATUM)}" else f.datum.format(DATUM)
    } else if (f.mehrtaegig) {
        "${f.datum.format(DATUM)} ${f.von.format(UHR)} – ${f.bisDatum.format(DATUM)} ${f.bis.format(UHR)}"
    } else {
        "${f.datum.format(DATUM)} ${f.von.format(UHR)}–${f.bis.format(UHR)}"
    }
}

@Composable
private fun lueckeText(luecke: FormularLuecke): String = stringResource(
    when (luecke) {
        FormularLuecke.PERSONEN -> R.string.hero_luecke_personen
        FormularLuecke.TITEL -> R.string.hero_luecke_titel
        FormularLuecke.KATEGORIE -> R.string.hero_luecke_kategorie
        FormularLuecke.ENDE_VOR_BEGINN -> R.string.hero_luecke_ende
        FormularLuecke.ZU_LANG -> R.string.hero_luecke_zu_lang
    },
)

/** "Für": ich, die anderen App-Nutzer, "Monteure ▾" klappt die uebrigen auf. Mehrfachauswahl. */
@Composable
private fun PersonenWahl(personen: Personen?, gewaehlt: List<String>, beiPerson: (String) -> Unit) {
    val liste = personen?.personen.orEmpty()
    val appNutzer = liste.filter { !it.ich && it.app_benutzer != null }
    val monteure = liste.filter { !it.ich && it.app_benutzer == null }
    // Aufgeklappt, sobald ein Monteur gewaehlt ist - sonst waere die Wahl unsichtbar.
    var monteureOffen by rememberSaveable { mutableStateOf(monteure.any { it.schluessel in gewaehlt }) }

    @Composable
    fun Chip(text: String, schluessel: String) {
        FilterChip(
            selected = schluessel in gewaehlt,
            onClick = { beiPerson(schluessel) },
            label = { Text(text) },
            shape = CircleShape,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.hero_fuer), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (personen?.ich != null) Chip(stringResource(R.string.kalender_ich), PERSON_ICH)
            appNutzer.forEach { Chip(vorname(it.name), it.filter()) }
            if (monteure.isNotEmpty()) {
                val anzahl = monteure.count { it.schluessel in gewaehlt }
                FilterChip(
                    selected = anzahl > 0,
                    onClick = { monteureOffen = !monteureOffen },
                    label = { Text(stringResource(R.string.kalender_monteure) + if (anzahl > 0) " ($anzahl)" else "") },
                    trailingIcon = { Icon(if (monteureOffen) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown, null) },
                    shape = CircleShape,
                )
            }
            if (monteureOffen) monteure.forEach { Chip(it.name, it.filter()) }
        }
    }
}

/** Pflichtfeld ohne Vorauswahl, die Kategoriefarbe als Punkt. */
@Composable
private fun KategorieWahl(kategorien: List<TerminKategorie>, gewaehlt: Long?, beiWahl: (Long) -> Unit) {
    var offen by remember { mutableStateOf(false) }
    val aktuell = kategorien.firstOrNull { it.id == gewaehlt }
    Box {
        OutlinedButton(onClick = { offen = true }, modifier = Modifier.fillMaxWidth()) {
            aktuell?.let { FarbPunkt(it.farbe) }
            Text(
                aktuell?.name ?: stringResource(R.string.hero_kategorie_waehlen),
                modifier = Modifier.weight(1f).padding(start = if (aktuell != null) 8.dp else 0.dp),
            )
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
            kategorien.forEach { k ->
                DropdownMenuItem(
                    text = { Text(k.name) },
                    leadingIcon = { FarbPunkt(k.farbe) },
                    onClick = {
                        beiWahl(k.id)
                        offen = false
                    },
                )
            }
        }
    }
}

@Composable
private fun FarbPunkt(hex: String?) {
    val farbe = kategorieFarbe(hex) ?: MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.size(12.dp).background(farbe, CircleShape))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatumKnopf(beschriftung: String, wert: LocalDate, beiWahl: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var offen by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(beschriftung, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { offen = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(18.dp))
            Text(wert.format(DATUM), modifier = Modifier.padding(start = 8.dp))
        }
    }
    if (offen) {
        // Der DatePicker rechnet in UTC-Mitternacht - Umrechnung in beide Richtungen ueber UTC.
        val auswahl = rememberDatePickerState(
            initialSelectedDateMillis = wert.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { offen = false },
            confirmButton = {
                TextButton(
                    enabled = auswahl.selectedDateMillis != null,
                    onClick = {
                        auswahl.selectedDateMillis?.let { beiWahl(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                        offen = false
                    },
                ) { Text(stringResource(R.string.hero_ok)) }
            },
            dismissButton = { TextButton(onClick = { offen = false }) { Text(stringResource(R.string.hero_abbrechen)) } },
        ) {
            DatePicker(state = auswahl)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZeitKnopf(beschriftung: String, wert: LocalTime, beiWahl: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    var offen by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(beschriftung, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { offen = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Schedule, null, modifier = Modifier.size(18.dp))
            Text(wert.format(UHR), modifier = Modifier.padding(start = 8.dp))
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
                }) { Text(stringResource(R.string.hero_ok)) }
            },
            dismissButton = { TextButton(onClick = { offen = false }) { Text(stringResource(R.string.hero_abbrechen)) } },
            text = { TimePicker(state = auswahl) },
        )
    }
}
