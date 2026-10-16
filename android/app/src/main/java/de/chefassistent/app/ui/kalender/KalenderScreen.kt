package de.chefassistent.app.ui.kalender

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.chefassistent.app.R
import de.chefassistent.app.data.KalenderTermin
import de.chefassistent.app.data.PERSON_ALLE
import de.chefassistent.app.data.PERSON_ICH
import de.chefassistent.app.data.Person
import de.chefassistent.app.ui.antwort.FARBEN_DUNKEL
import de.chefassistent.app.ui.antwort.FARBEN_HELL
import de.chefassistent.app.ui.komponenten.GrosseKopfleiste
import de.chefassistent.app.ui.komponenten.GruppenBlock
import de.chefassistent.app.ui.komponenten.GruppenKarte
import de.chefassistent.app.ui.komponenten.GruppenTitel
import de.chefassistent.app.ui.komponenten.SEITENRAND
import de.chefassistent.app.ui.komponenten.gruppenAnordnung
import de.chefassistent.app.ui.komponenten.listenAbstand
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

private val DATUM_LANG = DateTimeFormatter.ofPattern("d.M.yyyy")
private val DATUM_KURZ = DateTimeFormatter.ofPattern("d.M.")

/** Zeilen der Liste, flach, damit "zu heute scrollen" einen Index findet. */
private sealed class Zeile(val schluessel: String) {
    object OhneSpiegel : Zeile("ohne-spiegel")
    class Tag(val tag: LocalDate) : Zeile("tag-$tag")
    class Leer(val tag: LocalDate) : Zeile("leer-$tag")
    class Termin(val eintrag: TagesTermin, val index: Int, val anzahl: Int) : Zeile("t-${eintrag.schluessel}")
}

/**
 * Kalender mit HERO-Terminen und eigenen (`API.md` 5a, seit v1.16.0): Kopf mit
 * ‹ Heute › und Tag/Woche, Personen-Chips, darunter eine Tagesliste. Wischen
 * blaettert, Ziehen aktualisiert. Seit v1.18.0 "＋ HERO-Termin" im Kopf und
 * "Verschieben" an HERO-Terminen, beides nur bei `hero_schreiben` (`API.md` 5b).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KalenderScreen(
    viewModel: KalenderViewModel,
    /** `null` = Reiter der unteren Leiste, kein Zurueck-Pfeil (seit v1.21.0). */
    beiZurueck: (() -> Unit)?,
    beiAbmeldung: () -> Unit,
    beiOrdner: (String) -> Unit,
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val listenZustand = rememberLazyListState()
    var offen by rememberSaveable { mutableStateOf(setOf<String>()) }
    val heute = LocalDate.now()

    LaunchedEffect(zustand.abgemeldet) {
        if (zustand.abgemeldet) beiAbmeldung()
    }
    LaunchedEffect(zustand.fehler) {
        zustand.fehler?.let {
            snackbar.showSnackbar(it)
            viewModel.fehlerVerwerfen()
        }
    }
    val eingetragen = stringResource(R.string.hero_eingetragen)
    val verschoben = stringResource(R.string.hero_verschoben)
    LaunchedEffect(zustand.meldung) {
        zustand.meldung?.let {
            viewModel.meldungVerwerfen()
            snackbar.showSnackbar(if (it == Meldung.VERSCHOBEN) verschoben else eingetragen)
        }
    }

    val personen = zustand.personen?.personen.orEmpty()
    val reihenfolge = remember(zustand.auswahl, personen) { farbReihenfolge(zustand.auswahl, personen) }
    val palette = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) FARBEN_DUNKEL else FARBEN_HELL

    // Nur Termine zeigen, die zum angezeigten Zeitraum gehoeren - beim Blaettern
    // steht sonst kurz die alte Woche unter den neuen Tagen.
    val antwort = zustand.antwort?.takeIf { it.von == zustand.von.toString() && it.bis == zustand.bis.toString() }
    // Schreibrecht haengt am Server, nicht am Zeitraum: die letzte Antwort reicht.
    val heroSchreiben = zustand.antwort?.hero_schreiben == true
    val zeilen = remember(antwort, zustand.von, zustand.bis) {
        buildList {
            if (antwort?.spiegel == false) add(Zeile.OhneSpiegel)
            if (antwort != null) {
                nachTagen(antwort.termine, zustand.von, zustand.bis).forEach { (tag, termine) ->
                    add(Zeile.Tag(tag))
                    if (termine.isEmpty()) add(Zeile.Leer(tag))
                    termine.forEachIndexed { i, t -> add(Zeile.Termin(t, i, termine.size)) }
                }
            }
        }
    }

    // Beim Oeffnen und nach dem Blaettern zu "heute" bzw. zum Anfang.
    LaunchedEffect(antwort?.von, antwort?.bis) {
        if (antwort == null) return@LaunchedEffect
        val ziel = zeilen.indexOfFirst { it is Zeile.Tag && it.tag == heute }
        listenZustand.scrollToItem(if (ziel >= 0 && zustand.woche) ziel else 0)
    }

    val schwelle = with(LocalDensity.current) { 96.dp.toPx() }

    GrosseKopfleiste(
        titel = stringResource(R.string.kalender_titel),
        untertitel = zeitraumText(zustand.von, zustand.bis),
        beiZurueck = beiZurueck,
        snackbar = snackbar,
        aktionen = {
            if (heroSchreiben) {
                TextButton(onClick = viewModel::heroTerminNeu) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.kalender_hero_neu))
                }
            }
        },
    ) { innen ->
        Column(modifier = Modifier.fillMaxSize().padding(top = innen.calculateTopPadding())) {
            // Kopf und Chips bleiben stehen, nur die Tage scrollen - sonst verschwindet
            // beides, sobald die Liste zu "heute" springt.
            Column(modifier = Modifier.padding(horizontal = SEITENRAND)) {
                Kopf(
                    woche = zustand.woche,
                    laeuft = zustand.laeuft && antwort == null,
                    beiSchritt = viewModel::schritt,
                    beiHeute = viewModel::heute,
                    beiAnsicht = viewModel::ansichtWaehlen,
                )
                PersonenChips(
                    personen = personen,
                    auswahl = zustand.auswahl,
                    reihenfolge = reihenfolge,
                    palette = palette,
                    monteureOffen = zustand.monteureOffen,
                    beiPerson = viewModel::personUmschalten,
                    beiMonteure = viewModel::monteureUmschalten,
                )
            }
            PullToRefreshBox(
                isRefreshing = zustand.laeuft && zustand.antwort != null,
                onRefresh = viewModel::aktualisieren,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(zustand.woche) {
                        var weg = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { weg = 0f },
                            onDragEnd = {
                                if (abs(weg) > schwelle) viewModel.schritt(if (weg < 0) 1 else -1)
                            },
                        ) { _, dx -> weg += dx }
                    },
            ) {
                LazyColumn(
                    state = listenZustand,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = listenAbstand(PaddingValues(bottom = innen.calculateBottomPadding())),
                    verticalArrangement = gruppenAnordnung,
                ) {
                    items(zeilen, key = { it.schluessel }) { zeile ->
                        when (zeile) {
                            Zeile.OhneSpiegel -> GruppenKarte(modifier = Modifier.padding(bottom = 4.dp)) {
                                Text(
                                    text = stringResource(R.string.kalender_ohne_spiegel),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                )
                            }
                            is Zeile.Tag -> TagesKopf(zeile.tag, zeile.tag == heute)
                            is Zeile.Leer -> Text(
                                text = stringResource(R.string.kalender_keine_termine),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 20.dp, bottom = 4.dp),
                            )
                            is Zeile.Termin -> {
                                val schluessel = zeile.eintrag.schluessel
                                val farbe = farbIndex(zeile.eintrag.termin, reihenfolge, personen)?.let { palette[it % palette.size] }
                                TerminKarte(
                                    eintrag = zeile.eintrag,
                                    index = zeile.index,
                                    anzahl = zeile.anzahl,
                                    personenFarbe = farbe,
                                    aufgeklappt = schluessel in offen,
                                    beiKlick = { offen = if (schluessel in offen) offen - schluessel else offen + schluessel },
                                    beiOrdner = beiOrdner,
                                    beiVerschieben = if (heroSchreiben && zeile.eintrag.termin.verschiebbar) {
                                        { viewModel.heroTerminVerschieben(zeile.eintrag.termin) }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    zustand.formular?.let { formular ->
        HeroTerminSheet(
            formular = formular,
            personen = zustand.personen,
            kategorien = zustand.kategorien,
            beiAenderung = viewModel::formularAendern,
            beiAbschicken = viewModel::formularAbschicken,
            beiSchliessen = viewModel::formularSchliessen,
        )
    }
}

@Composable
private fun zeitraumText(von: LocalDate, bis: LocalDate): String =
    if (von == bis) {
        "${wochentag(von)} ${von.format(DATUM_LANG)}"
    } else {
        "${von.format(DATUM_KURZ)} – ${bis.format(DATUM_LANG)}"
    }

private fun wochentag(tag: LocalDate): String =
    tag.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.GERMANY).removeSuffix(".")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Kopf(
    woche: Boolean,
    laeuft: Boolean,
    beiSchritt: (Int) -> Unit,
    beiHeute: () -> Unit,
    beiAnsicht: (Boolean) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { beiSchritt(-1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.kalender_zurueck))
            }
            TextButton(onClick = beiHeute) { Text(stringResource(R.string.kalender_heute)) }
            IconButton(onClick = { beiSchritt(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.kalender_vor))
            }
            Spacer(Modifier.weight(1f))
            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = !woche,
                    onClick = { beiAnsicht(false) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    icon = {},
                ) { Text(stringResource(R.string.kalender_tag)) }
                SegmentedButton(
                    selected = woche,
                    onClick = { beiAnsicht(true) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = {},
                ) { Text(stringResource(R.string.kalender_woche)) }
            }
        }
        if (laeuft) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
    }
}

/**
 * Ich, die anderen App-Nutzer mit Vornamen, "Monteure ▾" (klappt die uebrigen
 * auf) und "Alle". Gewaehlte Chips tragen den Punkt in ihrer Farbe.
 */
@Composable
private fun PersonenChips(
    personen: List<Person>,
    auswahl: List<String>,
    reihenfolge: List<String>,
    palette: List<Color>,
    monteureOffen: Boolean,
    beiPerson: (String) -> Unit,
    beiMonteure: () -> Unit,
) {
    val alle = PERSON_ALLE in auswahl
    val appNutzer = personen.filter { !it.ich && it.app_benutzer != null }
    val monteure = personen.filter { !it.ich && it.app_benutzer == null }

    @Composable
    fun Chip(text: String, schluessel: String) {
        val gewaehlt = alle || schluessel in auswahl
        val index = reihenfolge.indexOf(schluessel)
        FilterChip(
            selected = gewaehlt,
            onClick = { beiPerson(schluessel) },
            label = { Text(text) },
            leadingIcon = if (gewaehlt && index >= 0) {
                { Box(Modifier.size(10.dp).background(palette[index % palette.size], CircleShape)) }
            } else {
                null
            },
            shape = CircleShape,
        )
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    ) {
        Chip(stringResource(R.string.kalender_ich), PERSON_ICH)
        appNutzer.forEach { Chip(vorname(it.name), it.filter()) }
        if (monteure.isNotEmpty()) {
            val gewaehlteMonteure = monteure.count { it.schluessel in auswahl }
            FilterChip(
                selected = gewaehlteMonteure > 0 && !alle,
                onClick = beiMonteure,
                label = {
                    Text(
                        stringResource(R.string.kalender_monteure) +
                            if (gewaehlteMonteure > 0 && !alle) " ($gewaehlteMonteure)" else "",
                    )
                },
                trailingIcon = { Icon(if (monteureOffen) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown, null) },
                shape = CircleShape,
            )
        }
        FilterChip(
            selected = alle,
            onClick = { beiPerson(PERSON_ALLE) },
            label = { Text(stringResource(R.string.kalender_alle)) },
            shape = CircleShape,
        )
        if (monteureOffen) monteure.forEach { Chip(it.name, it.filter()) }
    }
}

@Composable
private fun TagesKopf(tag: LocalDate, istHeute: Boolean) {
    val text = "${wochentag(tag)} ${tag.format(DATUM_KURZ)}"
    GruppenTitel(
        text = if (istHeute) stringResource(R.string.kalender_tag_heute, text) else text,
        farbe = if (istHeute) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Links der Rand in der Personenfarbe, dann die Zeit mit dem Kategorie-Strich,
 * rechts Titel, "Kategorie · Projektnr Kunde" und die Vornamen. Antippen klappt
 * Adresse, alle Personen, Notiz und den Projektordner auf.
 */
@Composable
private fun TerminKarte(
    eintrag: TagesTermin,
    index: Int,
    anzahl: Int,
    personenFarbe: Color?,
    aufgeklappt: Boolean,
    beiKlick: () -> Unit,
    beiOrdner: (String) -> Unit,
    /** `null` = nicht verschiebbar (kein Schreibrecht, `nur_lesen` oder eigener Termin). */
    beiVerschieben: (() -> Unit)?,
) {
    val t = eintrag.termin
    val farben = MaterialTheme.colorScheme
    val kontext = LocalContext.current
    val mehrtaegig = istMehrtaegig(t)
    GruppenBlock(index = index, anzahl = anzahl, einzug = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clickable(onClick = beiKlick),
        ) {
            Box(
                Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(personenFarbe ?: farben.outlineVariant),
            )
            Column(modifier = Modifier.width(if (mehrtaegig) 92.dp else 68.dp).padding(start = 14.dp, top = 12.dp)) {
                kategorieFarbe(t.farbe)?.let { strich ->
                    Box(
                        Modifier
                            .padding(bottom = 4.dp)
                            .size(width = 22.dp, height = 3.dp)
                            .background(strich, RoundedCornerShape(2.dp)),
                    )
                }
                when {
                    t.ganztag && !mehrtaegig -> Text(
                        stringResource(R.string.kalender_ganztaegig),
                        style = MaterialTheme.typography.labelMedium,
                        color = farben.onSurfaceVariant,
                    )
                    else -> {
                        Text(
                            zeitText(t.beginn, mehrtaegig).ifBlank { stringResource(R.string.kalender_ganztaegig) },
                            style = if (mehrtaegig) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            (if (mehrtaegig) "– " else "") + zeitText(t.ende, mehrtaegig),
                            style = MaterialTheme.typography.bodySmall,
                            color = farben.onSurfaceVariant,
                        )
                    }
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            ) {
                Text(t.titel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                val projekt = t.projekt
                val unterzeile = listOfNotNull(
                    t.kategorie.takeIf { it.isNotBlank() },
                    projekt?.let { listOf(it.nr, it.kunde).filter { s -> s.isNotBlank() }.joinToString(" ") }?.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (unterzeile.isNotBlank()) {
                    Text(unterzeile, style = MaterialTheme.typography.bodyMedium, color = farben.onSurfaceVariant)
                }
                if (t.personen.isNotEmpty() && !aufgeklappt) {
                    Text(
                        t.personen.joinToString(", ") { vorname(it.name) },
                        style = MaterialTheme.typography.labelMedium,
                        color = farben.onSurfaceVariant,
                    )
                }

                if (aufgeklappt) {
                    val adresse = projekt?.adresse?.takeIf { it.isNotBlank() } ?: t.ort?.takeIf { it.isNotBlank() }
                    if (adresse != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .clickable { karteOeffnen(kontext, adresse) },
                        ) {
                            Icon(Icons.Default.Place, null, tint = farben.primary, modifier = Modifier.size(18.dp))
                            Text(adresse, style = MaterialTheme.typography.bodyMedium, color = farben.primary)
                        }
                    }
                    if (t.personen.isNotEmpty()) {
                        Text(
                            t.personen.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    t.notiz?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
                    }
                    projekt?.pfad?.takeIf { it.isNotBlank() }?.let { pfad ->
                        OutlinedButton(onClick = { beiOrdner(pfad) }, modifier = Modifier.padding(top = 6.dp)) {
                            Icon(Icons.Default.Folder, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.kalender_projektordner))
                        }
                    }
                    if (beiVerschieben != null) {
                        OutlinedButton(onClick = beiVerschieben, modifier = Modifier.padding(top = 2.dp)) {
                            Icon(Icons.Default.Schedule, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.kalender_verschieben))
                        }
                    } else {
                        // Nach `quelle`, nicht nach `nur_lesen`: seit 1.18.0 sind HERO-Termine oft `nur_lesen: false`.
                        Text(
                            stringResource(if (t.istHero) R.string.kalender_aus_hero else R.string.kalender_eigen),
                            style = MaterialTheme.typography.labelSmall,
                            color = farben.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** `#RRGGBB` -> Farbe, Unlesbares faellt weg. */
internal fun kategorieFarbe(hex: String?): Color? =
    hex?.removePrefix("#")?.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { Color(0xFF000000 or it) }

private fun karteOeffnen(kontext: android.content.Context, adresse: String) {
    try {
        kontext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(adresse))))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(kontext, R.string.kalender_keine_karten_app, Toast.LENGTH_SHORT).show()
    }
}
