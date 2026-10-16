package de.chefassistent.app.monteur.ui.karte

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BeachAccess
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.chefassistent.app.R
import de.chefassistent.app.monteur.MonteurApp
import de.chefassistent.app.monteur.data.AnleitungTreffer
import de.chefassistent.app.monteur.data.Karte
import de.chefassistent.app.monteur.ui.rememberAufnahme
import de.chefassistent.app.ui.komponenten.FehlerSnackbar
import de.chefassistent.app.ui.theme.erfolg
import de.chefassistent.app.ui.theme.gold
import de.chefassistent.app.ui.theme.hauptflaeche
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Der Bildschirm zum Eingang (Auftrag 1.23): Eingabe (Fotos, Text, Mikrofon), „KI denkt
 * mit …“, und die Karte mit genau zwei Knoepfen - „Passt“ und „Verwerfen“. Bei einer
 * Frage steht statt „Passt“ „Fertig“; weiterreden ist dort eine Nachfrage.
 * Berichte zeigt nicht dieser Bildschirm, sondern die Bestaetigungskarte aus 1.22.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KarteScreen(
    viewModel: EingangViewModel,
    app: MonteurApp,
    beiZurueck: () -> Unit,
    beiAnleitung: (AnleitungTreffer) -> Unit,
    beiProtokollPdf: (karteId: String) -> Unit = {},
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val aufnahme = rememberAufnahme()
    var verwerfenFrage by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) { viewModel.meldungen.collect { snackbar.showSnackbar(it) } }

    val karte = zustand.karte
    val schliessen: () -> Unit = {
        viewModel.schliessen()
        beiZurueck()
    }

    if (verwerfenFrage) {
        AlertDialog(
            onDismissRequest = { verwerfenFrage = false },
            text = { Text(stringResource(R.string.k_verwerfen_frage)) },
            confirmButton = {
                TextButton(onClick = {
                    verwerfenFrage = false
                    viewModel.verwerfen(beiZurueck)
                }) { Text(stringResource(R.string.k_verwerfen), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { verwerfenFrage = false }) { Text(stringResource(R.string.m_abbrechen)) } },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { FehlerSnackbar(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = {
                    IconButton(onClick = beiZurueck) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.m_zurueck)) }
                },
                title = {
                    Text(
                        if (karte != null && zustand.phase == EingangPhase.KARTE) stringResource(ueberschrift(karte.art))
                        else stringResource(R.string.k_titel)
                    )
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    when (zustand.phase) {
                        EingangPhase.EINGABE -> EingangKnoepfe(
                            aufnahme = aufnahme,
                            beiAufnahme = { viewModel.senden(aufnahme = it) },
                            // Bekanntes Projekt: sofort ablegen, das Foto erscheint oben in der Leiste (1.29.0).
                            beiFotos = { d -> zustand.kontext.projektId?.let { viewModel.fotosAblegen(it, d) } ?: viewModel.fotosHinzu(d) },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        )
                        EingangPhase.KARTE -> if (karte != null) KartenKnoepfe(
                            karte = karte,
                            sendet = zustand.sendet,
                            aufnahme = aufnahme,
                            beiPasst = viewModel::passt,
                            beiVerwerfen = { verwerfenFrage = true },
                            beiFertig = schliessen,
                            beiAufnahme = { viewModel.senden(aufnahme = it) },
                            beiFotos = { viewModel.fotosHinzu(it) },
                        )
                        else -> Unit
                    }
                }
            }
        },
    ) { innen ->
        Box(Modifier.fillMaxSize().padding(innen)) {
            when (zustand.phase) {
                EingangPhase.EINGABE -> {
                    // „gerade aufgenommen“ zum Projekt dieser Eingabe (1.29.0) - antippen = markieren.
                    val alle by app.gerade.fotos.collectAsStateWithLifecycle()
                    val auswahl by viewModel.auswahl.collectAsStateWithLifecycle()
                    val gerade = app.gerade.fuerProjekt(zustand.kontext.projektId, alle)
                    Eingabe(zustand, viewModel, gerade, auswahl.gemeint(gerade).map { it.id }.toSet())
                }
                EingangPhase.LAEUFT -> Laeuft(zustand.beginn)
                EingangPhase.WARTET -> Wartet(schliessen)
                EingangPhase.KARTE -> if (karte != null) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        KartenKopf(karte, zustand.passtFehler)
                        when (karte.art) {
                            Karte.ART_FRAGE -> {
                                FrageInhalt(karte, zustand.verlauf, beiAnleitung)
                                FotoAblageZeile(karte, viewModel.eingabe, aktiv = !zustand.sendet)
                            }
                            Karte.ART_FOTO -> FotoInhalt(karte, viewModel.eingabe, zustand.kartenFotos, aktiv = karte.istOffen && !zustand.sendet)
                            Karte.ART_BESTELLUNG -> BestellungInhalt(karte, viewModel.eingabe, app, aktiv = karte.istOffen && !zustand.sendet)
                            Karte.ART_MELDUNG -> MeldungInhalt(karte, viewModel.eingabe, zustand.kartenFotos, aktiv = karte.istOffen && !zustand.sendet)
                            Karte.ART_ZEITEN -> ZeitenInhalt(karte, viewModel.eingabe, aktiv = karte.istOffen && !zustand.sendet)
                            Karte.ART_URLAUB -> UrlaubInhalt(karte, viewModel.eingabe, aktiv = karte.istOffen && !zustand.sendet)
                            Karte.ART_PROTOKOLL -> ProtokollInhalt(
                                karte = karte,
                                viewModel = viewModel,
                                fotos = zustand.kartenFotos,
                                vorlagen = zustand.vorlagen,
                                springeZu = zustand.springeZu,
                                aktiv = karte.istOffen && !zustand.sendet,
                                sendet = zustand.sendet,
                                beiPdf = beiProtokollPdf,
                            )
                            else -> Text(karte.text)
                        }
                        if (karte.istErledigt && karte.art != Karte.ART_FRAGE) Ergebnis(karte)
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

/** Eingabe: gesammelte Fotos, Textfeld, Senden. Das Mikrofon steht unten. */
@Composable
private fun Eingabe(
    zustand: EingangZustand,
    viewModel: EingangViewModel,
    gerade: List<de.chefassistent.app.monteur.data.GeradeFoto> = emptyList(),
    gemeint: Set<String> = emptySet(),
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        zustand.kontext.titel?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        GeradeLeiste(gerade, gemeint, viewModel::fotoUmschalten)
        if (zustand.projektFotos.isNotEmpty()) {
            Text(stringResource(R.string.k_frage_zum_foto), style = MaterialTheme.typography.labelLarge)
        }
        val anzahl = zustand.fotos.size + zustand.projektFotos.size
        if (anzahl > 0) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Fotos aus der Projektmappe: gehen als foto_ids, gezeigt wird die kleine Vorschau.
                zustand.projektFotos.forEach { r -> FotoVorschau(r.vorschau, beiEntfernen = { viewModel.projektFotoEntfernen(r.uuid) }) }
                zustand.fotos.forEach { pfad -> FotoVorschau(pfad, beiEntfernen = { viewModel.fotoEntfernen(pfad) }) }
            }
            Text(
                stringResource(R.string.k_fotos_hinweis, anzahl, EingangViewModel.MAX_FOTOS),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedTextField(
            value = zustand.text,
            onValueChange = viewModel::textAendern,
            label = { Text(stringResource(R.string.k_tippen)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        GalerieHinweis()
        Button(
            onClick = { viewModel.senden() },
            enabled = zustand.text.isNotBlank() || zustand.fotos.isNotEmpty() || zustand.projektFotos.isNotEmpty(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.hauptflaeche, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text(stringResource(R.string.k_senden)) }
    }
}

/**
 * Einmalig beim ersten Oeffnen der Eingabe: „Lange drücken: aus der Galerie“ (Auftrag 1.28, 2.).
 * Danach nie wieder - die Galerie hat keinen eigenen Knopf.
 */
@Composable
private fun GalerieHinweis() {
    val kontext = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { kontext.getSharedPreferences("monteur_hinweise", android.content.Context.MODE_PRIVATE) }
    val zeigen = remember { !prefs.getBoolean("galerie_hinweis", false) }
    LaunchedEffect(Unit) { prefs.edit().putBoolean("galerie_hinweis", true).apply() }
    if (zeigen) {
        Text(
            stringResource(R.string.k_galerie_hinweis),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FotoVorschau(pfad: String?, beiEntfernen: (() -> Unit)? = null) {
    val bild by produceState<android.graphics.Bitmap?>(null, pfad) {
        if (pfad == null) return@produceState
        value = withContext(Dispatchers.Default) {
            runCatching {
                de.chefassistent.app.data.FotoAufbereitung.vorschau(File(pfad), 320) ?: BitmapFactory.decodeFile(pfad)
            }.getOrNull()
        }
    }
    Box(Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        bild?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        if (beiEntfernen != null) {
            IconButton(onClick = beiEntfernen, modifier = Modifier.align(Alignment.TopEnd).size(36.dp)) {
                Icon(
                    Icons.Outlined.Close,
                    stringResource(R.string.k_foto_entfernen),
                    tint = Color.White,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50)).padding(2.dp),
                )
            }
        }
    }
}

/** Lokale Fotos der Karte als Vorschau (Meldung). Oeffentlich fuer die Karteninhalte. */
@Composable
fun FotoReihe(fotos: List<String>) {
    if (fotos.isEmpty()) return
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        fotos.forEach { FotoVorschau(it) }
    }
}

@Composable
private fun Laeuft(beginn: Long) {
    var jetzt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(beginn) {
        while (true) {
            delay(1000)
            jetzt = System.currentTimeMillis()
        }
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.gold)
        Text(stringResource(R.string.k_denkt), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 20.dp))
        Text(
            stringResource(R.string.k_denkt_hinweis, ((jetzt - beginn) / 1000).coerceAtLeast(0)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 20.dp))
    }
}

@Composable
private fun Wartet(beiFertig: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.CloudUpload, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.k_wartet), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
        Text(
            stringResource(R.string.k_wartet_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        TextButton(onClick = beiFertig, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.m_ok)) }
    }
}

/**
 * Kopf jeder Karte: Art-Symbol und Ueberschrift, das erkannte **Projekt** deutlich
 * (damit auffaellt, wenn der Server das falsche erkannt hat), der erkannte Text klein
 * und grau, die Rueckfrage gold markiert, ein Fehler vom „Passt“ rot.
 */
@Composable
private fun KartenKopf(karte: Karte, passtFehler: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(symbol(karte.art), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(ueberschrift(karte.art)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
    karte.projekt?.let { p ->
        Text(
            listOf(p.nr, p.kunde).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
    if (karte.text.isNotBlank()) {
        Text(
            "„${karte.text}“",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    karte.rueckfrage?.takeIf { it.isNotBlank() }?.let {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.fillMaxWidth().border(BorderStroke(2.dp, MaterialTheme.colorScheme.gold), RoundedCornerShape(12.dp)),
        ) { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(14.dp)) }
    }
    passtFehler?.let {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
            Text(it, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(14.dp))
        }
    }
}

/** Nach „Passt“: gruener Haken, was passiert ist, und `ergebnis.hinweise` als Liste. */
@Composable
private fun Ergebnis(karte: Karte) {
    val text = when (karte.art) {
        Karte.ART_BESTELLUNG -> karte.ergebnisText("hero_nr")?.let { stringResource(R.string.k_erg_bestellung, it) }
            ?: karte.ergebnisText("hero")
        Karte.ART_MELDUNG -> stringResource(R.string.k_erg_meldung)
        Karte.ART_ZEITEN -> karte.ergebnisText("gebucht")?.let { stringResource(R.string.k_erg_zeiten, it) }
        Karte.ART_URLAUB -> stringResource(R.string.k_erg_urlaub)
        Karte.ART_PROTOKOLL -> karte.ergebnisText("hero") ?: stringResource(R.string.k_erg_protokoll)
        Karte.ART_FOTO -> stringResource(R.string.k_erg_foto)
        else -> karte.ergebnisText("hero")
    } ?: stringResource(R.string.k_erledigt)
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.erfolg, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(10.dp))
                Text(text, style = MaterialTheme.typography.titleMedium)
            }
            if (karte.art == Karte.ART_PROTOKOLL) {
                karte.ergebnisText("datei")?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            karte.hinweise.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

/** Unten auf der Karte: „Passt“ + „Verwerfen“; bei Fragen „Fertig“ und Mikrofon fuer Nachfragen. */
@Composable
private fun KartenKnoepfe(
    karte: Karte,
    sendet: Boolean,
    aufnahme: de.chefassistent.app.monteur.ui.AufnahmeSteuerung,
    beiPasst: () -> Unit,
    beiVerwerfen: () -> Unit,
    beiFertig: () -> Unit,
    beiAufnahme: (File) -> Unit,
    beiFotos: (List<File>) -> Unit,
) {
    val navy = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.hauptflaeche, contentColor = Color.White)
    when {
        karte.art == Karte.ART_FRAGE -> {
            EingangKnoepfe(
                aufnahme = aufnahme,
                beiAufnahme = beiAufnahme,
                beiFotos = beiFotos,
                groesse = 60.dp,
                beschriftung = stringResource(R.string.k_nachfragen),
                hinweis = null,
                modifier = Modifier.fillMaxWidth(),
            )
            val ablegen = karte.foto != null && karte.foto.abgelegt.isEmpty()
            if (ablegen) {
                // Frage mit Fotos (1.28): Ablegen ist der Hauptknopf, „Fertig“ schliesst ohne.
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = beiFertig,
                        enabled = !sendet,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) { Text(stringResource(R.string.k_fertig)) }
                    Button(
                        onClick = beiPasst,
                        enabled = !sendet,
                        shape = RoundedCornerShape(10.dp),
                        colors = navy,
                        modifier = Modifier.weight(2f).heightIn(min = 52.dp),
                    ) {
                        if (sendet) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text(stringResource(R.string.k_passt_foto), maxLines = 1)
                    }
                }
            } else {
                Button(onClick = beiFertig, shape = RoundedCornerShape(10.dp), colors = navy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 52.dp)) {
                    Text(stringResource(R.string.k_fertig))
                }
            }
        }
        karte.istOffen -> {
            Button(
                onClick = beiPasst,
                enabled = !sendet,
                shape = RoundedCornerShape(10.dp),
                colors = navy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                if (sendet) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(stringResource(R.string.k_passt), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = beiVerwerfen, enabled = !sendet, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.k_verwerfen), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        else -> Button(onClick = beiFertig, shape = RoundedCornerShape(10.dp), colors = navy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(R.string.k_fertig))
        }
    }
}

private fun ueberschrift(art: String): Int = when (art) {
    Karte.ART_BERICHT -> R.string.k_art_bericht
    Karte.ART_FRAGE -> R.string.k_art_frage
    Karte.ART_BESTELLUNG -> R.string.k_art_bestellung
    Karte.ART_MELDUNG -> R.string.k_art_meldung
    Karte.ART_ZEITEN -> R.string.k_art_zeiten
    Karte.ART_URLAUB -> R.string.k_art_urlaub
    Karte.ART_PROTOKOLL -> R.string.k_art_protokoll
    Karte.ART_FOTO -> R.string.k_art_foto
    else -> R.string.k_titel
}

private fun symbol(art: String): ImageVector = when (art) {
    Karte.ART_BERICHT -> Icons.Outlined.Description
    Karte.ART_FRAGE -> Icons.Outlined.HelpOutline
    Karte.ART_BESTELLUNG -> Icons.Outlined.Inventory2
    Karte.ART_MELDUNG -> Icons.Outlined.Sms
    Karte.ART_ZEITEN -> Icons.Outlined.Schedule
    Karte.ART_URLAUB -> Icons.Outlined.BeachAccess
    Karte.ART_PROTOKOLL -> Icons.Outlined.FactCheck
    Karte.ART_FOTO -> Icons.Outlined.PhotoCamera
    else -> Icons.Outlined.Description
}
