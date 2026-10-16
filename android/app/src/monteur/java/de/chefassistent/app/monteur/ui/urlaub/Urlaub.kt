package de.chefassistent.app.monteur.ui.urlaub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.chefassistent.app.R
import de.chefassistent.app.monteur.data.Abwesenheit
import de.chefassistent.app.monteur.data.AbwesenheitNeu
import de.chefassistent.app.monteur.data.Urlaubsstand
import de.chefassistent.app.ui.komponenten.GruppenBlock
import de.chefassistent.app.ui.komponenten.GruppenKarte
import de.chefassistent.app.ui.komponenten.GruppenTitel
import de.chefassistent.app.ui.komponenten.LeerHinweis
import de.chefassistent.app.ui.theme.gold
import de.chefassistent.app.ui.theme.hauptflaeche

/*
 * Oberflaeche des Reiters „Urlaub“. Die Logik steht in [UrlaubViewModel] und
 * [UrlaubLogik], hier wird nur gezeichnet.
 */

/** Gruen fuer „genehmigt“: Material 3 hat keine Erfolgsfarbe, `primary` ist hier Navy. */
private val Gruen = Color(0xFF2E7D32)

/** Dunkle Schrift auf Gold (Gold nie als Schrift auf Weiss, nur als Flaeche). */
private val SchriftAufGold = Color(0xFF1F1A12)

/** Platz rechts oben fuer das ⋮-Menue der Activity. */
private val MENUE_PLATZ = 48.dp

private class MeldungAnzeige(
    override val message: String,
    val fehler: Boolean,
) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = SnackbarDuration.Short
}

/** Inhalt des Reiters „Urlaub“. [innen] haelt Platz fuer Kopf, Mikrofon und untere Leiste frei. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlaubReiter(viewModel: UrlaubViewModel, innen: PaddingValues) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.meldungen.collect { snackbar.showSnackbar(MeldungAnzeige(it.text, it.fehler)) }
    }

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = zustand.laedt && zustand.daten != null,
            onRefresh = { viewModel.laden() },
            modifier = Modifier.fillMaxSize(),
        ) {
            UrlaubListe(zustand, viewModel, innen)
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = innen.calculateBottomPadding())
                .padding(horizontal = 16.dp),
        ) { daten ->
            val fehler = (daten.visuals as? MeldungAnzeige)?.fehler == true
            Snackbar(
                containerColor = if (fehler) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.inverseSurface,
                contentColor = if (fehler) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.inverseOnSurface,
                shape = RoundedCornerShape(12.dp),
            ) { Text(daten.visuals.message) }
        }
    }

    zustand.formular?.let { f ->
        AntragSheet(
            formular = f,
            beiAenderung = viewModel::formularAendern,
            beiSenden = viewModel::formularSenden,
            beiSchliessen = viewModel::formularSchliessen,
        )
    }

    zustand.zurueckziehen?.let { e ->
        AlertDialog(
            onDismissRequest = viewModel::zurueckziehenAbbrechen,
            title = { Text(stringResource(R.string.u_zurueckziehen_frage)) },
            text = {
                Text(stringResource(R.string.u_zurueckziehen_text, artText(e.art), zeitraumText(e.von, e.bis, e.halber_tag_anfang, e.halber_tag_ende)))
            },
            confirmButton = {
                TextButton(onClick = viewModel::zurueckziehen) {
                    Text(stringResource(R.string.u_zurueckziehen), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::zurueckziehenAbbrechen) { Text(stringResource(R.string.u_abbrechen)) }
            },
        )
    }
}

@Composable
private fun UrlaubListe(zustand: UrlaubZustand, viewModel: UrlaubViewModel, innen: PaddingValues) {
    val eintraege = zustand.daten?.eintraege.orEmpty()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = innen.calculateTopPadding(),
            bottom = innen.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(key = "kopf") {
            Kopf(jahr = zustand.jahr, beiWechsel = viewModel::jahrWechseln)
        }
        item(key = "stand") {
            StandKarte(zustand.daten?.stand, laedt = zustand.laedt && zustand.daten == null)
            Spacer(Modifier.size(12.dp))
        }
        item(key = "knoepfe") {
            Knoepfe(
                versteht = zustand.versteht,
                beiUrlaub = viewModel::urlaubBeantragen,
                beiKrank = viewModel::krankMelden,
                beiText = { viewModel.verstehen(null, it) },
            )
        }
        zustand.offeneAufnahme?.let {
            item(key = "aufnahme") {
                OffeneAufnahme(
                    fehler = zustand.offeneAufnahmeFehler.orEmpty(),
                    beiNochmal = viewModel::aufnahmeNochmal,
                    beiVerwerfen = viewModel::aufnahmeVerwerfen,
                )
            }
        }
        zustand.ergebnis?.let { e ->
            item(key = "vorschlag") {
                VorschlagKarte(
                    ergebnis = e,
                    beiBeantragen = viewModel::vorschlagBeantragen,
                    beiAendern = viewModel::vorschlagAendern,
                    beiVerwerfen = viewModel::ergebnisVerwerfen,
                )
            }
        }

        val anzahl = zustand.wartend.size + eintraege.size
        item(key = "titel") {
            GruppenTitel(stringResource(R.string.u_eintraege, anzahl), modifier = Modifier.padding(top = 4.dp))
        }
        if (anzahl == 0 && !zustand.laedt) {
            item(key = "leer") { LeerHinweis(stringResource(R.string.u_leer)) }
        }
        itemsIndexed(zustand.wartend, key = { _, a -> "w-" + a.anfrage_id }) { index, antrag ->
            GruppenBlock(index = index, anzahl = anzahl) { WartendZeile(antrag) }
        }
        itemsIndexed(eintraege, key = { _, e -> "e-" + e.id }) { index, eintrag ->
            val position = zustand.wartend.size + index
            if (istZurueckziehbar(eintrag.status)) {
                WischbarerEintrag(eintrag, beiWischen = { viewModel.zurueckziehenFragen(eintrag) }) {
                    GruppenBlock(index = position, anzahl = anzahl) { EintragZeile(eintrag) }
                }
            } else {
                GruppenBlock(index = position, anzahl = anzahl) { EintragZeile(eintrag) }
            }
        }
        if (eintraege.any { istZurueckziehbar(it.status) }) {
            item(key = "wischen") {
                Text(
                    stringResource(R.string.u_wischen_hinweis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
                )
            }
        }
        item(key = "fuss") {
            Text(
                stringResource(R.string.u_fusszeile),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun Kopf(jahr: Int, beiWechsel: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(end = MENUE_PLATZ).heightIn(min = 56.dp),
    ) {
        Text(
            stringResource(R.string.u_titel),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 4.dp).weight(1f),
        )
        IconButton(onClick = { beiWechsel(-1) }) {
            Icon(Icons.Outlined.ChevronLeft, stringResource(R.string.u_jahr_zurueck), tint = LocalContentColor.current)
        }
        Text("$jahr", style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { beiWechsel(1) }) {
            Icon(Icons.Outlined.ChevronRight, stringResource(R.string.u_jahr_vor), tint = LocalContentColor.current)
        }
    }
}

@Composable
private fun StandKarte(stand: Urlaubsstand?, laedt: Boolean) {
    val leer = stringResource(R.string.u_leer_wert)
    GruppenKarte {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                if (laedt) {
                    CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
                } else {
                    Text(
                        tageText(stand?.verfuegbar, leer),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    stringResource(R.string.u_verfuegbar),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp, bottom = 6.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                StandWert(tageText(stand?.genehmigt, leer), stringResource(R.string.u_genehmigt_kurz), Modifier.weight(1f))
                StandWert(tageText(stand?.beantragt, leer), stringResource(R.string.u_beantragt_kurz), Modifier.weight(1f))
                StandWert(tageText(stand?.jahresurlaub, leer), stringResource(R.string.u_jahresurlaub), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StandWert(wert: String, name: String, modifier: Modifier) {
    Column(modifier) {
        Text(wert, style = MaterialTheme.typography.titleMedium)
        Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Knoepfe(versteht: Boolean, beiUrlaub: () -> Unit, beiKrank: () -> Unit, beiText: (String) -> Unit) {
    var tippen by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = beiUrlaub,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.hauptflaeche,
                    contentColor = Color.White,
                ),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Icon(Icons.Outlined.DateRange, null, tint = LocalContentColor.current, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.u_beantragen_knopf))
            }
            OutlinedButton(
                onClick = beiKrank,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Icon(Icons.Outlined.MedicalServices, null, tint = LocalContentColor.current, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.u_krank_knopf))
            }
        }
        if (versteht) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.u_versteht), style = MaterialTheme.typography.bodyMedium)
            }
        } else if (!tippen) {
            TextButton(onClick = { tippen = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.Keyboard, null, tint = LocalContentColor.current, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.u_tippen))
            }
        }
        if (tippen && !versteht) {
            val absenden = {
                if (text.isNotBlank()) {
                    beiText(text)
                    text = ""
                    tippen = false
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.u_tippen_feld)) },
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { absenden() }),
                trailingIcon = {
                    IconButton(onClick = absenden, enabled = text.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Outlined.Send, stringResource(R.string.u_tippen_senden), tint = LocalContentColor.current)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        Spacer(Modifier.size(8.dp))
    }
}

@Composable
private fun OffeneAufnahme(fehler: String, beiNochmal: () -> Unit, beiVerwerfen: () -> Unit) {
    GruppenKarte(modifier = Modifier.padding(bottom = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.WifiOff, null, tint = LocalContentColor.current, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.u_aufnahme_offen, fehler), style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                TextButton(onClick = beiVerwerfen) { Text(stringResource(R.string.u_schliessen)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = beiNochmal,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.hauptflaeche,
                        contentColor = Color.White,
                    ),
                ) { Text(stringResource(R.string.u_nochmal)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VorschlagKarte(
    ergebnis: VerstehenErgebnis,
    beiBeantragen: () -> Unit,
    beiAendern: () -> Unit,
    beiVerwerfen: () -> Unit,
) {
    GruppenKarte(modifier = Modifier.padding(bottom = 8.dp)) {
        Row(Modifier.padding(16.dp)) {
            // Goldener Streifen als Kennzeichen „Vorschlag, noch nicht gebucht“.
            Box(
                Modifier
                    .width(4.dp)
                    .heightIn(min = 48.dp)
                    .background(MaterialTheme.colorScheme.gold, RoundedCornerShape(2.dp)),
            )
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                if (ergebnis.gesagt.isNotBlank()) {
                    Text(
                        stringResource(R.string.u_vorschlag_gesagt, ergebnis.gesagt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(6.dp))
                }
                val v = ergebnis.vorschlag
                when {
                    v != null -> {
                        val tage = tageSchaetzen(v.von, v.bis, v.halber_tag_anfang, v.halber_tag_ende)
                        val zeitraum = zeitraumText(v.von, v.bis, v.halber_tag_anfang, v.halber_tag_ende)
                        Text(
                            if (tage == null) stringResource(R.string.u_vorschlag_frage_ohne_tage, artText(v.art), zeitraum)
                            else stringResource(R.string.u_vorschlag_frage, artText(v.art), zeitraum, tageMitEinheit(tage)),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (v.kommentar.isNotBlank()) {
                            Text(v.kommentar, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    ergebnis.nurZeiten -> Text(stringResource(R.string.u_zeiten_hinweis), style = MaterialTheme.typography.bodyLarge)
                    ergebnis.nichts -> Text(stringResource(R.string.u_nicht_verstanden), style = MaterialTheme.typography.bodyLarge)
                }
                ergebnis.rueckfrage?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                ) {
                    TextButton(onClick = beiVerwerfen, enabled = !ergebnis.sendet) {
                        Text(stringResource(if (v != null) R.string.u_vorschlag_verwerfen else R.string.u_schliessen))
                    }
                    if (v != null) {
                        OutlinedButton(
                            onClick = beiAendern,
                            enabled = !ergebnis.sendet,
                            shape = RoundedCornerShape(10.dp),
                        ) { Text(stringResource(R.string.u_vorschlag_aendern)) }
                        Button(
                            onClick = beiBeantragen,
                            enabled = !ergebnis.sendet,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.hauptflaeche,
                                contentColor = Color.White,
                            ),
                        ) {
                            if (ergebnis.sendet) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(stringResource(R.string.u_vorschlag_beantragen))
                        }
                    }
                }
            }
        }
    }
}

/** Nur Antraege mit Status beantragt/entwurf; Wischen fragt erst nach (kein sofortiges DELETE). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WischbarerEintrag(eintrag: Abwesenheit, beiWischen: () -> Unit, inhalt: @Composable () -> Unit) {
    val zustand = rememberSwipeToDismissBoxState()
    LaunchedEffect(zustand.currentValue) {
        if (zustand.currentValue != SwipeToDismissBoxValue.Settled) {
            beiWischen()
            zustand.reset()
        }
    }
    SwipeToDismissBox(
        state = zustand,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp),
            ) {
                Text(
                    stringResource(R.string.u_zurueckziehen),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Outlined.Undo, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) { inhalt() }
}

@Composable
private fun EintragZeile(eintrag: Abwesenheit) {
    val halb = stringResource(R.string.u_halber_tag_marke)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(artText(eintrag.art), style = MaterialTheme.typography.titleMedium)
            Text(
                zeitraumText(eintrag.von, eintrag.bis, eintrag.halber_tag_anfang, eintrag.halber_tag_ende, halb) +
                    (eintrag.tage?.let { " · " + tageMitEinheit(it) } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (eintrag.kommentar.isNotBlank()) {
                Text(
                    eintrag.kommentar,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        StatusChip(eintrag.status)
    }
}

@Composable
private fun WartendZeile(antrag: AbwesenheitNeu) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(artText(antrag.art), style = MaterialTheme.typography.titleMedium)
            Text(
                zeitraumText(antrag.von, antrag.bis, antrag.halber_tag_anfang, antrag.halber_tag_ende, stringResource(R.string.u_halber_tag_marke)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Chip(
            text = stringResource(R.string.u_status_wartet),
            flaeche = MaterialTheme.colorScheme.surfaceVariant,
            schrift = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** beantragt = Gold (Flaeche, dunkle Schrift), genehmigt = gruen, abgelehnt = Fehlerfarbe, sonst grau. */
@Composable
private fun StatusChip(status: String) {
    val name = statusName(status)?.let { stringResource(it) } ?: status
    val (flaeche, schrift) = when (status) {
        STATUS_BEANTRAGT -> MaterialTheme.colorScheme.gold to SchriftAufGold
        STATUS_GENEHMIGT -> Gruen to Color.White
        STATUS_ABGELEHNT -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Chip(name, flaeche, schrift)
}

@Composable
private fun Chip(text: String, flaeche: Color, schrift: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = flaeche, contentColor = schrift) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

// --- Antragsformular ------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AntragSheet(
    formular: AntragFormular,
    beiAenderung: ((AntragFormular) -> AntragFormular) -> Unit,
    beiSenden: () -> Unit,
    beiSchliessen: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var auswahl by remember { mutableStateOf(false) }
    val halb = stringResource(R.string.u_halber_tag_marke)

    ModalBottomSheet(onDismissRequest = beiSchliessen, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(
                stringResource(if (formular.krank) R.string.u_formular_krank else R.string.u_formular_urlaub),
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.size(16.dp))

            Text(stringResource(R.string.u_zeitraum), style = MaterialTheme.typography.labelLarge)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clickable(enabled = !formular.sendet) { auswahl = true },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.heightIn(min = 52.dp).padding(horizontal = 16.dp),
                ) {
                    Icon(Icons.Outlined.DateRange, null, tint = LocalContentColor.current, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (formular.von.isBlank()) stringResource(R.string.u_zeitraum_waehlen)
                        else zeitraumText(formular.von, formular.bis, formular.halbAnfang, formular.halbEnde, halb),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            Spacer(Modifier.size(16.dp))
            Text(stringResource(R.string.u_art), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                val arten = if (formular.art in formular.arten) formular.arten else formular.arten + formular.art
                arten.forEach { art ->
                    FilterChip(
                        selected = formular.art == art,
                        onClick = { beiAenderung { it.copy(art = art) } },
                        label = { Text(artText(art)) },
                        enabled = !formular.sendet,
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }

            Spacer(Modifier.size(8.dp))
            Schalter(stringResource(R.string.u_halb_anfang), formular.halbAnfang, !formular.sendet) { an ->
                beiAenderung { it.copy(halbAnfang = an) }
            }
            Schalter(stringResource(R.string.u_halb_ende), formular.halbEnde, !formular.sendet) { an ->
                beiAenderung { it.copy(halbEnde = an) }
            }

            OutlinedTextField(
                value = formular.kommentar,
                onValueChange = { t -> beiAenderung { it.copy(kommentar = t) } },
                label = { Text(stringResource(R.string.u_kommentar)) },
                enabled = !formular.sendet,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            formular.fehler?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Button(
                onClick = beiSenden,
                enabled = formular.gueltig && !formular.sendet,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.hauptflaeche,
                    contentColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                if (formular.sendet) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
                    Spacer(Modifier.width(10.dp))
                }
                Text(stringResource(if (formular.krank) R.string.u_senden_krank else R.string.u_senden_urlaub))
            }
        }
    }

    if (auswahl) {
        ZeitraumAuswahl(
            von = formular.von,
            bis = formular.bis,
            beiFertig = { von, bis -> beiAenderung { it.copy(von = von, bis = bis) } },
            beiSchliessen = { auswahl = false },
        )
    }
}

@Composable
private fun Schalter(text: String, an: Boolean, aktiv: Boolean, beiWechsel: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = aktiv) { beiWechsel(!an) },
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = an, onCheckedChange = beiWechsel, enabled = aktiv)
    }
}

/** Datumsbereich. Ein einzelner Tag ist erlaubt: ohne Ende gilt Beginn = Ende. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZeitraumAuswahl(
    von: String,
    bis: String,
    beiFertig: (String, String) -> Unit,
    beiSchliessen: () -> Unit,
) {
    val zustand = rememberDateRangePickerState(
        initialSelectedStartDateMillis = datumZuMillis(von),
        initialSelectedEndDateMillis = datumZuMillis(bis),
    )
    DatePickerDialog(
        onDismissRequest = beiSchliessen,
        confirmButton = {
            TextButton(
                enabled = zustand.selectedStartDateMillis != null,
                onClick = {
                    val start = zustand.selectedStartDateMillis
                    if (start != null) {
                        val ende = zustand.selectedEndDateMillis ?: start
                        beiFertig(millisZuDatum(start), millisZuDatum(ende))
                    }
                    beiSchliessen()
                },
            ) { Text(stringResource(R.string.u_uebernehmen)) }
        },
        dismissButton = {
            TextButton(onClick = beiSchliessen) { Text(stringResource(R.string.u_abbrechen)) }
        },
    ) {
        DateRangePicker(state = zustand, modifier = Modifier.weight(1f))
    }
}

// --- Texte ------------------------------------------------------------------------------

@Composable
private fun artText(art: String): String = artName(art)?.let { stringResource(it) } ?: art

@Composable
private fun tageMitEinheit(tage: Double): String =
    stringResource(if (istEinTag(tage)) R.string.u_tag_eins else R.string.u_tage, tageText(tage))
