package de.chefassistent.app.monteur.ui.bericht

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.chefassistent.app.monteur.ui.MikrofonKnopf
import de.chefassistent.app.monteur.ui.rememberAufnahme
import de.chefassistent.app.R
import de.chefassistent.app.ui.komponenten.FehlerSnackbar
import de.chefassistent.app.ui.komponenten.GruppenKarte
import de.chefassistent.app.ui.theme.erfolg
import de.chefassistent.app.ui.theme.hauptflaeche
import kotlinx.coroutines.delay

/**
 * Womit der Bericht-Bildschirm startet (Route `bericht?…` in MonteurActivity).
 * - [berichtId]: vorhandenen Bericht laden (Entwurf weiterbearbeiten bzw. fertigen zeigen).
 * - [aufnahme]: Pfad einer frischen Aufnahme vom Mikrofon auf „Heute“ - sofort senden.
 * - sonst: Eingabe (Mikrofon oder Text) fuer [terminId] bzw. [projektNr], ohne beides findet der Server den Termin.
 */
data class BerichtStart(
    val berichtId: String? = null,
    val terminId: String? = null,
    val projektNr: String? = null,
    /** Fuer die Anzeige, z. B. „SAN-1243 · Böse“. */
    val titel: String? = null,
    val aufnahme: String? = null,
    /**
     * Seit 1.23.0: Karte aus dem Eingang, zu der dieser Bericht gehoert. Dann laufen
     * „Passt“, Verwerfen und Ergaenzen ueber `/v1/monteur/eingang` statt ueber die
     * Bericht-Endpunkte aus 1.22.
     */
    val karteId: String? = null,
)

/**
 * Bericht (Auftrag 1.22, 3.): so wenig Tippen wie moeglich. Sprechen oder
 * tippen → KI schreibt den Entwurf → auf einer Karte pruefen → „Passt – nach HERO“.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BerichtScreen(viewModel: BerichtViewModel, beiZurueck: () -> Unit, beiAbmeldung: () -> Unit) {
    val zustand by viewModel.zustand.collectAsState()
    val abgemeldet by viewModel.abgemeldet.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(abgemeldet) { if (abgemeldet) beiAbmeldung() }
    LaunchedEffect(zustand.geschlossen) { if (zustand.geschlossen) beiZurueck() }
    LaunchedEffect(viewModel) { viewModel.meldungen.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.b_titel))
                        viewModel.start.titel?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = beiZurueck) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.b_zurueck))
                    }
                },
            )
        },
        snackbarHost = { FehlerSnackbar(snackbar) },
    ) { innen ->
        Box(Modifier.padding(innen).fillMaxSize()) {
            when (zustand.phase) {
                Phase.LADEN -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                Phase.LADEFEHLER -> LadeFehler(zustand.ladeFehler.orEmpty(), viewModel::neuLaden)
                Phase.EINGABE -> Eingabe(viewModel, zustand)
                Phase.LAEUFT -> Laeuft()
                Phase.WARTET -> Wartet(
                    titel = stringResource(R.string.b_wartet),
                    text = stringResource(R.string.b_wartet_text),
                    beiFertig = beiZurueck,
                )
                Phase.KARTE -> BerichtKarte(viewModel, zustand)
                Phase.ABSCHLUSS_WARTET -> Wartet(
                    titel = stringResource(R.string.b_abschluss_wartet),
                    text = stringResource(R.string.b_abschluss_wartet_text),
                    beiFertig = beiZurueck,
                )
                Phase.FERTIG -> Fertig(zustand, beiZurueck)
            }
        }
    }

    zustand.hinweisDialog?.let { text ->
        AlertDialog(
            onDismissRequest = viewModel::hinweisGelesen,
            title = { Text(stringResource(R.string.b_hinweis_titel)) },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = viewModel::hinweisGelesen) { Text(stringResource(R.string.b_ok)) } },
        )
    }
}

@Composable
private fun LadeFehler(text: String, beiNochmal: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = beiNochmal, shape = KNOPF_FORM) { Text(stringResource(R.string.b_nochmal)) }
    }
}

/** Grosses Mikrofon, darunter alternativ ein Textfeld. */
@Composable
private fun Eingabe(viewModel: BerichtViewModel, zustand: BerichtZustand) {
    val aufnahme = rememberAufnahme()
    val farben = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (viewModel.start.titel == null && viewModel.start.terminId == null && viewModel.start.projektNr == null) {
            Text(
                stringResource(R.string.b_ohne_termin),
                style = MaterialTheme.typography.bodyMedium,
                color = farben.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        zustand.eingabeFehler?.let { fehler ->
            Spacer(Modifier.height(12.dp))
            Surface(color = farben.errorContainer, contentColor = farben.onErrorContainer, shape = KARTEN_FORM) {
                Text(fehler, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth().padding(16.dp))
            }
        }
        zustand.offeneAufnahme?.let {
            Spacer(Modifier.height(12.dp))
            GruppenKarte {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.b_aufnahme_liegt), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = viewModel::offeneAufnahmeSenden,
                        colors = navyKnopf(),
                        shape = KNOPF_FORM,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.b_aufnahme_nochmal)) }
                    TextButton(onClick = viewModel::offeneAufnahmeVerwerfen, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.b_aufnahme_verwerfen))
                    }
                }
            }
        }
        Spacer(Modifier.height(40.dp))
        MikrofonKnopf(
            steuerung = aufnahme,
            beiFertig = viewModel::aufnahmeSenden,
            groesse = 112.dp,
            beschriftung = stringResource(R.string.b_mikro_frage),
            hinweis = stringResource(R.string.b_mikro_hinweis),
        )
        Spacer(Modifier.height(40.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f))
            Text(
                stringResource(R.string.b_oder_tippen),
                style = MaterialTheme.typography.labelMedium,
                color = farben.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            HorizontalDivider(Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = viewModel.text,
            onValueChange = viewModel::textAendern,
            label = { Text(stringResource(R.string.b_text_feld)) },
            minLines = 3,
            enabled = !aufnahme.nimmtAuf,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = viewModel::textSenden,
            enabled = viewModel.text.isNotBlank() && !aufnahme.nimmtAuf,
            colors = navyKnopf(),
            shape = KNOPF_FORM,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.b_senden)) }
    }
}

/** „KI schreibt den Bericht …“ mit mitlaufender Zeit, damit niemand nach 15 s abbricht. */
@Composable
private fun Laeuft() {
    var sekunden by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            sekunden++
        }
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(Modifier.size(56.dp))
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.b_laeuft), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.b_laeuft_dauer, sekunden),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
private fun Wartet(titel: String, text: String, beiFertig: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(16.dp))
        Text(titel, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = beiFertig, colors = navyKnopf(), shape = KNOPF_FORM, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.b_fertig_knopf))
        }
    }
}

/** Gruener Haken, BER-Nummer gross, darunter, was nicht ganz geklappt hat. */
@Composable
private fun Fertig(zustand: BerichtZustand, beiFertig: () -> Unit) {
    val bericht = zustand.bericht
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = gruen(), modifier = Modifier.size(96.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.b_fertig), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        bericht?.hero_nr?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        }
        val hinweise = bericht?.hinweise.orEmpty()
        if (hinweise.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = KARTEN_FORM,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.b_fertig_hinweise), style = MaterialTheme.typography.titleSmall)
                    hinweise.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp)) }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = beiFertig,
            colors = navyKnopf(),
            shape = KNOPF_FORM,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text(stringResource(R.string.b_fertig_knopf)) }
    }
}

// --- Gemeinsame Gestaltung des Bericht-Bildschirms --------------------------------------

internal val KARTEN_FORM = RoundedCornerShape(12.dp)
internal val KNOPF_FORM = RoundedCornerShape(10.dp)

/** Gefuellter Knopf in Navy, Text weiss (Gestaltung 1.21). */
@Composable
internal fun navyKnopf() = ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.hauptflaeche,
    contentColor = Color.White,
)

/** Gruen fuer „fertig“ aus dem Theme ([erfolg]). */
@Composable
internal fun gruen(): Color = MaterialTheme.colorScheme.erfolg
