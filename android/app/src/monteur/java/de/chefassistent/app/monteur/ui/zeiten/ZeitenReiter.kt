package de.chefassistent.app.monteur.ui.zeiten

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.chefassistent.app.R
import de.chefassistent.app.monteur.data.Zeiteintrag
import de.chefassistent.app.ui.komponenten.FehlerSnackbar
import de.chefassistent.app.ui.komponenten.GruppenBlock
import de.chefassistent.app.ui.komponenten.GruppenKarte
import de.chefassistent.app.ui.komponenten.GruppenTitel
import de.chefassistent.app.ui.komponenten.LeerHinweis
import de.chefassistent.app.ui.komponenten.RundesSymbol
import de.chefassistent.app.ui.theme.auswahl
import de.chefassistent.app.ui.theme.gold
import de.chefassistent.app.ui.theme.hauptflaeche
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val KOPF_DATUM: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMANY)
private val KURZ_DATUM: DateTimeFormatter = DateTimeFormatter.ofPattern("EE, d.M.", Locale.GERMANY)
private val MONAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMANY)

/** Inhalt des Reiters „Zeiten“. [innen] haelt Platz fuer Kopf, Mikrofon und untere Leiste frei. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZeitenReiter(viewModel: ZeitenViewModel, innen: PaddingValues) {
    val z by viewModel.zustand.collectAsState()
    val fehlerZustand = remember { SnackbarHostState() }
    val infoZustand = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.meldung.collect { m ->
            if (m.fehler) fehlerZustand.showSnackbar(m.text) else infoZustand.showSnackbar(m.text)
        }
    }

    // Laufende Stempeluhr sekuendlich, sonst reicht die Minute (Banner ab 15 Uhr).
    val laeuft = z.istHeute && z.tag?.stempel != null
    val jetzt by produceState(LocalDateTime.now(), laeuft) {
        while (true) {
            value = LocalDateTime.now()
            delay(if (laeuft) 1_000L else 30_000L)
        }
    }

    var formular by remember { mutableStateOf<FormularZiel?>(null) }
    var loeschFrage by remember { mutableStateOf<Zeiteintrag?>(null) }
    var uebersicht by rememberSaveable { mutableStateOf(false) }
    var tippen by rememberSaveable { mutableStateOf(false) }
    var tippText by rememberSaveable { mutableStateOf("") }

    val eintraege = sichtbareEintraege(z.tag?.eintraege.orEmpty())
    val gesperrt = z.eingereicht
    val projekte = projektNummern(z.termine)
    val banner = z.tag != null && tagBestaetigenZeigen(
        datum = z.datum,
        jetzt = jetzt,
        eingereicht = gesperrt,
        anzahlEintraege = eintraege.size,
        letzterTerminEnde = letzterTerminEnde(z.termine, z.datum),
    )
    val wartend = z.wartend.filter { zeitLesen(it.zeit.beginn)?.toLocalDate() == z.datum }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = z.laedt && z.tag != null,
            onRefresh = viewModel::laden,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innen.calculateTopPadding(),
                    bottom = innen.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "kopf") {
                    TagesKopf(
                        titel = z.datum.format(KOPF_DATUM),
                        istHeute = z.istHeute,
                        beiZurueck = { viewModel.blaettern(-1) },
                        beiVor = { viewModel.blaettern(1) },
                        beiHeute = viewModel::zuHeute,
                    )
                }

                if (gesperrt) {
                    item(key = "gesperrt") {
                        HinweisKarte(Icons.Outlined.Lock, stringResource(R.string.z_eingereicht_hinweis))
                    }
                }

                val tag = z.tag
                if (z.istHeute && tag != null) {
                    item(key = "stempel") {
                        StempelKarte(
                            stempel = tag.stempel,
                            jetzt = jetzt,
                            projekte = projekte,
                            sendet = z.sendet,
                            beiAktion = viewModel::stempeln,
                        )
                    }
                }

                if (banner) {
                    item(key = "banner") {
                        BestaetigenBanner(beiTipp = { uebersicht = true })
                    }
                }

                if (z.versteht || z.vorschlag != null || z.verstehenFehler != null) {
                    item(key = "vorschlag") {
                        VorschlagKarte(
                            versteht = z.versteht,
                            vorschlag = z.vorschlag,
                            fehler = z.verstehenFehler,
                            wiederholbar = z.verstehenWiederholbar,
                            sendet = z.sendet,
                            beiUebernehmen = viewModel::vorschlagUebernehmen,
                            beiVerwerfen = viewModel::verstehenSchliessen,
                            beiNochmal = viewModel::verstehenWiederholen,
                        )
                    }
                }

                if (tag != null) {
                    item(key = "summen") { SummenZeile(tag.arbeit_minuten, tag.pause_minuten) }
                }

                item(key = "titel") { GruppenTitel(stringResource(R.string.z_eintraege)) }

                when {
                    tag == null && z.ladeFehler != null -> item(key = "fehler") { LeerHinweis(z.ladeFehler.orEmpty()) }
                    tag == null -> item(key = "laedt") { LeerHinweis(stringResource(R.string.z_laedt)) }
                    eintraege.isEmpty() && wartend.isEmpty() ->
                        item(key = "leer") { LeerHinweis(stringResource(R.string.z_leer)) }
                    else -> {
                        val anzahl = eintraege.size + wartend.size
                        items(eintraege, key = { "e${it.id}" }) { e ->
                            val index = eintraege.indexOf(e)
                            EintragZeile(
                                eintrag = e,
                                index = index,
                                anzahl = anzahl,
                                bearbeitbar = e.aenderbar && !gesperrt && !z.sendet,
                                beiTipp = { formular = FormularZiel(e) },
                                beiLoeschen = { loeschFrage = e },
                            )
                        }
                        items(wartend.size, key = { "w${wartend[it].zeit.anfrage_id}" }) { i ->
                            WartendeZeile(wartend[i], index = eintraege.size + i, anzahl = anzahl)
                        }
                    }
                }

                item(key = "knoepfe") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) {
                        if (!gesperrt && tag != null) {
                            OutlinedButton(
                                onClick = { formular = FormularZiel(null) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            ) {
                                Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp))
                                Text(stringResource(R.string.z_neu), modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                        OutlinedButton(
                            onClick = { tippen = !tippen },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) {
                            Icon(Icons.Outlined.Keyboard, null, modifier = Modifier.size(18.dp))
                            Text(
                                stringResource(R.string.z_tippen),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                }

                if (tippen) {
                    item(key = "tippfeld") {
                        OutlinedTextField(
                            value = tippText,
                            onValueChange = { tippText = it },
                            label = { Text(stringResource(R.string.z_tippen_feld)) },
                            placeholder = { Text(stringResource(R.string.z_tippen_beispiel)) },
                            trailingIcon = {
                                IconButton(
                                    enabled = tippText.isNotBlank() && !z.versteht,
                                    onClick = {
                                        viewModel.verstehen(null, tippText.trim())
                                        tippText = ""
                                        tippen = false
                                    },
                                ) { Icon(Icons.AutoMirrored.Outlined.Send, stringResource(R.string.z_tippen_senden)) }
                            },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                }

                z.konto?.let { konto ->
                    item(key = "konto") {
                        KontoZeile(konto, monat = z.datum.format(MONAT))
                    }
                }
            }
        }

        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = innen.calculateBottomPadding())) {
            FehlerSnackbar(fehlerZustand)
            SnackbarHost(infoZustand)
        }
    }

    formular?.let { ziel ->
        ZeitFormular(
            eintrag = ziel.eintrag,
            datum = z.datum,
            projekte = projekte,
            sendet = z.sendet,
            beiSchliessen = { formular = null },
            beiSpeichern = { von, bis, art, nr, kommentar ->
                val zu = { formular = null }
                val e = ziel.eintrag
                if (e == null) viewModel.anlegen(von, bis, art, nr, kommentar, zu)
                else viewModel.aendern(e, von, bis, art, nr, kommentar, zu)
            },
            beiLoeschen = ziel.eintrag?.let { e -> { formular = null; loeschFrage = e } },
        )
    }

    loeschFrage?.let { e ->
        LoeschFrage(
            eintrag = e,
            beiJa = {
                loeschFrage = null
                viewModel.loeschen(e)
            },
            beiNein = { loeschFrage = null },
        )
    }

    if (uebersicht && z.tag != null) {
        TagesUebersicht(
            titel = z.datum.format(KURZ_DATUM),
            eintraege = eintraege,
            arbeit = z.tag?.arbeit_minuten ?: 0,
            pause = z.tag?.pause_minuten ?: 0,
            sendet = z.sendet,
            beiEinreichen = { viewModel.einreichen { uebersicht = false } },
            beiSchliessen = { uebersicht = false },
        )
    }
}

/** Welcher Eintrag im Formular steht, `null` = neuer. */
private data class FormularZiel(val eintrag: Zeiteintrag?)

@Composable
private fun TagesKopf(
    titel: String,
    istHeute: Boolean,
    beiZurueck: () -> Unit,
    beiVor: () -> Unit,
    beiHeute: () -> Unit,
) {
    // Rechts 48 dp frei: dort liegt das ⋮-Menue der Activity.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(end = 48.dp, top = 4.dp, bottom = 4.dp),
    ) {
        IconButton(onClick = beiZurueck) { Icon(Icons.Outlined.ChevronLeft, stringResource(R.string.z_tag_zurueck)) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                titel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!istHeute) {
                TextButton(onClick = beiHeute, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(stringResource(R.string.z_heute))
                }
            }
        }
        IconButton(onClick = beiVor) { Icon(Icons.Outlined.ChevronRight, stringResource(R.string.z_tag_vor)) }
    }
}

@Composable
private fun HinweisKarte(symbol: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    GruppenKarte(modifier = Modifier.padding(vertical = 6.dp), farbe = MaterialTheme.colorScheme.auswahl) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Icon(symbol, null, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
private fun BestaetigenBanner(beiTipp: () -> Unit) {
    Surface(
        onClick = beiTipp,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.auswahl,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 56.dp).padding(16.dp)) {
            // Gold nur als Flaeche, nie als Schrift.
            RundesSymbol(Icons.Outlined.TaskAlt, farbe = MaterialTheme.colorScheme.gold, symbolFarbe = Color.Black)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(stringResource(R.string.z_bestaetigen), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.z_bestaetigen_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Outlined.ChevronRight, null)
        }
    }
}

@Composable
private fun SummenZeile(arbeit: Int, pause: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, start = 4.dp, end = 4.dp)) {
        Summe(stringResource(R.string.z_summe_arbeit), minutenText(arbeit), Modifier.weight(1f))
        Summe(stringResource(R.string.z_summe_pause), minutenText(pause), Modifier.weight(1f))
    }
}

@Composable
private fun Summe(titel: String, wert: String, modifier: Modifier) {
    Column(modifier) {
        Text(titel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(wert, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EintragZeile(
    eintrag: Zeiteintrag,
    index: Int,
    anzahl: Int,
    bearbeitbar: Boolean,
    beiTipp: () -> Unit,
    beiLoeschen: () -> Unit,
) {
    val zustand = rememberSwipeToDismissBoxState()
    // Wischen loescht nicht sofort: erst die Rueckfrage, die Zeile springt zurueck.
    LaunchedEffect(zustand.currentValue) {
        if (zustand.currentValue == SwipeToDismissBoxValue.EndToStart) {
            beiLoeschen()
            zustand.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }
    SwipeToDismissBox(
        state = zustand,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = bearbeitbar,
        gesturesEnabled = bearbeitbar,
        backgroundContent = {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier.fillMaxSize().padding(end = 24.dp),
            ) { Icon(Icons.Outlined.Delete, stringResource(R.string.z_loeschen), tint = MaterialTheme.colorScheme.error) }
        },
    ) {
        GruppenBlock(index = index, anzahl = anzahl, einzug = 72.dp) {
            EintragInhalt(
                art = eintrag.art,
                spanne = spanneText(eintrag.beginn, eintrag.ende),
                minuten = eintrag.minuten,
                projekt = eintrag.projekt_nr,
                kommentar = eintrag.kommentar,
                gesperrt = !eintrag.aenderbar,
                wartet = false,
                modifier = Modifier.clickable(enabled = bearbeitbar, onClick = beiTipp),
            )
        }
    }
}

@Composable
private fun WartendeZeile(w: WartendeZeit, index: Int, anzahl: Int) {
    GruppenBlock(index = index, anzahl = anzahl, einzug = 72.dp) {
        val b = zeitLesen(w.zeit.beginn)
        val e = zeitLesen(w.zeit.ende)
        val minuten = if (b != null && e != null) java.time.Duration.between(b, e).toMinutes().toInt() else 0
        EintragInhalt(
            art = w.zeit.art,
            spanne = spanneText(w.zeit.beginn, w.zeit.ende),
            minuten = minuten,
            projekt = w.zeit.projekt_nr,
            kommentar = w.zeit.kommentar,
            gesperrt = false,
            wartet = true,
            modifier = Modifier,
        )
    }
}

@Composable
private fun EintragInhalt(
    art: String,
    spanne: String,
    minuten: Int,
    projekt: String?,
    kommentar: String,
    gesperrt: Boolean,
    wartet: Boolean,
    modifier: Modifier,
) {
    val blass = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        RundesSymbol(
            artSymbol(art),
            farbe = if (wartet) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.hauptflaeche,
        )
        Column(Modifier.weight(1f).padding(start = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(spanne, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(minutenText(minuten), style = MaterialTheme.typography.bodyMedium, color = blass)
            }
            val zeile = listOfNotNull(artName(art), projekt?.takeIf { it.isNotBlank() }).joinToString(" · ")
            Text(zeile, style = MaterialTheme.typography.bodyMedium, color = blass)
            if (kommentar.isNotBlank()) {
                Text(kommentar, style = MaterialTheme.typography.bodySmall, color = blass, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (wartet) {
                Text(stringResource(R.string.z_wartet), style = MaterialTheme.typography.labelSmall, color = blass)
            }
        }
        when {
            wartet -> Icon(Icons.Outlined.CloudUpload, stringResource(R.string.z_wartet), tint = blass)
            gesperrt -> Icon(Icons.Outlined.Lock, stringResource(R.string.z_status_eingereicht), tint = blass)
        }
    }
}

@Composable
private fun KontoZeile(konto: de.chefassistent.app.monteur.data.Stundenkonto, monat: String) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp, start = 4.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.z_konto, monat),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                minutenText(konto.saldo_minuten, vorzeichen = true),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (konto.saldo_minuten < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            stringResource(
                R.string.z_konto_details,
                minutenText(konto.soll_minuten),
                minutenText(konto.bestaetigt_minuten),
                minutenText(konto.eingereicht_minuten),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LoeschFrage(eintrag: Zeiteintrag, beiJa: () -> Unit, beiNein: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = beiNein,
        title = { Text(stringResource(R.string.z_loeschen_frage)) },
        text = {
            Text(
                stringResource(
                    R.string.z_loeschen_text,
                    artName(eintrag.art),
                    spanneText(eintrag.beginn, eintrag.ende),
                )
            )
        },
        confirmButton = {
            TextButton(onClick = beiJa) { Text(stringResource(R.string.z_loeschen), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = beiNein) { Text(stringResource(R.string.z_abbrechen)) } },
    )
}

/** Navy-Knopf fuer die Hauptaktion. */
@Composable
internal fun navyFarben() = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.hauptflaeche,
    contentColor = Color.White,
)
