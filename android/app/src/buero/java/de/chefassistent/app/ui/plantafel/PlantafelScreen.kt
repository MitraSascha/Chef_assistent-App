package de.chefassistent.app.ui.plantafel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.chefassistent.app.R
import de.chefassistent.app.data.Einsatz
import de.chefassistent.app.data.Gewerk
import de.chefassistent.app.data.GuWarnung
import de.chefassistent.app.ui.dateiOeffnen
import de.chefassistent.app.ui.komponenten.EINZUG_MIT_SYMBOL
import de.chefassistent.app.ui.komponenten.GrosseKopfleiste
import de.chefassistent.app.ui.komponenten.GruppenBlock
import de.chefassistent.app.ui.komponenten.GruppenKarte
import de.chefassistent.app.ui.komponenten.GruppenTitel
import de.chefassistent.app.ui.komponenten.LeerHinweis
import de.chefassistent.app.ui.komponenten.gruppenAnordnung
import de.chefassistent.app.ui.komponenten.listenAbstand

/**
 * Plantafel eines GU-Projekts (`GET /v1/bauablauf/{id}`): Warnungen oben,
 * darunter die Zeitleiste und die Gewerke. Antippen oeffnet ein Sheet zum
 * Bearbeiten, nach jeder Aenderung wird neu geladen.
 */
@Composable
fun PlantafelScreen(
    viewModel: PlantafelViewModel,
    projektId: Int,
    beiZurueck: () -> Unit,
    beiAbmeldung: () -> Unit,
    beiOrdner: (String) -> Unit,
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val kontext = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var einsatzOffen by remember { mutableStateOf<Int?>(null) }
    var gewerkOffen by remember { mutableStateOf<Int?>(null) }
    var neuOffen by remember { mutableStateOf(false) }

    LifecycleResumeEffect(projektId) {
        viewModel.planLaden(projektId)
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

    val plan = zustand.plan
    val projekt = plan?.projekt

    // Sheets arbeiten mit der aktuellen Fassung aus dem Plan - nach dem Neuladen
    // zeigen sie also schon die Serverwerte.
    einsatzOffen?.let { id ->
        val einsatz = plan?.einsaetze?.firstOrNull { it.id == id }
        if (einsatz == null) {
            einsatzOffen = null
        } else {
            EinsatzSheet(
                einsatz = einsatz,
                gewerk = plan.gewerke.firstOrNull { it.id == einsatz.gewerk_id },
                alle = plan.einsaetze,
                beschaeftigt = zustand.beschaeftigt,
                beiSpeichern = { aenderung ->
                    viewModel.einsatzAendern(einsatz.id, aenderung) { einsatzOffen = null }
                },
                beiLoeschen = {
                    viewModel.einsatzLoeschen(einsatz.id) { einsatzOffen = null }
                },
                beiSchliessen = { einsatzOffen = null },
            )
        }
    }
    gewerkOffen?.let { id ->
        val gewerk = plan?.gewerke?.firstOrNull { it.id == id }
        if (gewerk == null) {
            gewerkOffen = null
        } else {
            GewerkSheet(
                gewerk = gewerk,
                hatEinsaetze = plan.einsaetze.any { it.gewerk_id == gewerk.id },
                beschaeftigt = zustand.beschaeftigt,
                beiSpeichern = {
                    viewModel.gewerkSetzen(it) { gewerkOffen = null }
                },
                beiLoeschen = {
                    viewModel.gewerkLoeschen(gewerk.id) { gewerkOffen = null }
                },
                beiSchliessen = { gewerkOffen = null },
            )
        }
    }
    if (neuOffen && plan != null) {
        EinsatzNeuSheet(
            gewerke = plan.gewerke,
            beschaeftigt = zustand.beschaeftigt,
            beiAnlegen = {
                viewModel.einsatzAnlegen(it) { neuOffen = false }
            },
            beiSchliessen = { neuOffen = false },
        )
    }

    GrosseKopfleiste(
        titel = projekt?.name ?: stringResource(R.string.gu_titel),
        untertitel = projekt?.let { p ->
            if (p.von != null) "${kurzDatum(p.von)} – ${kurzDatum(p.bis ?: p.von)}" else null
        },
        beiZurueck = beiZurueck,
        snackbar = snackbar,
        aktionen = {
            if (projekt != null) {
                IconButton(onClick = { beiOrdner(projekt.ordner) }) {
                    Icon(Icons.Default.FolderOpen, stringResource(R.string.gu_ordner))
                }
                IconButton(
                    onClick = { viewModel.pdf { dateiOeffnen(kontext, it) } },
                    enabled = !zustand.laedtPdf,
                ) {
                    if (zustand.laedtPdf) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.PictureAsPdf, stringResource(R.string.gu_pdf))
                    }
                }
            }
            IconButton(onClick = { viewModel.planLaden(projektId) }) {
                Icon(Icons.Default.Refresh, stringResource(R.string.gu_neu_laden))
            }
        },
        knopf = {
            if (plan != null) {
                ExtendedFloatingActionButton(
                    onClick = { neuOffen = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text(stringResource(R.string.gu_einsatz)) },
                    expanded = !zustand.beschaeftigt,
                )
            }
        },
    ) { innen ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = listenAbstand(innen),
            verticalArrangement = gruppenAnordnung,
        ) {
            if (zustand.laeuft || zustand.beschaeftigt) {
                item(key = "laden") {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
            }
            if (plan == null) return@LazyColumn

            if (plan.warnungen.isNotEmpty()) {
                item(key = "warn_titel") { GruppenTitel(stringResource(R.string.gu_warnungen)) }
                itemsIndexed(plan.warnungen, key = { i, _ -> "warn_$i" }) { i, warnung ->
                    WarnungZeile(
                        warnung = warnung,
                        index = i,
                        anzahl = plan.warnungen.size,
                        // Antippen oeffnet den ersten betroffenen Einsatz.
                        beiKlick = warnung.einsaetze.firstOrNull()?.let { id -> { einsatzOffen = id } },
                    )
                }
            }

            val sichtbar = plan.einsaetze.filter { it.status != Einsatz.STATUS_ABGESAGT }
            if (sichtbar.isEmpty()) {
                item(key = "leer") {
                    Spacer(Modifier.height(8.dp))
                    LeerHinweis(stringResource(R.string.gu_tafel_leer))
                }
            } else {
                item(key = "tafel") {
                    Spacer(Modifier.height(12.dp))
                    GruppenKarte {
                        Zeitleiste(
                            plan = plan,
                            markiert = plan.warnungen.flatMap { it.einsaetze }.toSet(),
                            beiEinsatz = { einsatzOffen = it.id },
                            beiGewerk = { gewerkOffen = it.id },
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Legende()
                    }
                }
            }

            if (plan.gewerke.isNotEmpty()) {
                item(key = "gewerke_titel") { GruppenTitel(stringResource(R.string.gu_gewerke)) }
                itemsIndexed(plan.gewerke, key = { _, g -> "g_${g.id}" }) { i, gewerk ->
                    GewerkZeile(
                        gewerk = gewerk,
                        einsaetze = plan.einsaetze.count { it.gewerk_id == gewerk.id && it.status != Einsatz.STATUS_ABGESAGT },
                        index = i,
                        anzahl = plan.gewerke.size,
                        beiKlick = { gewerkOffen = gewerk.id },
                    )
                }
            }
            // Platz fuer den schwebenden Knopf.
            item(key = "ende") { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun WarnungZeile(warnung: GuWarnung, index: Int, anzahl: Int, beiKlick: (() -> Unit)?) {
    val farben = MaterialTheme.colorScheme
    GruppenBlock(index = index, anzahl = anzahl, farbe = farben.secondaryContainer, einzug = 56.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (beiKlick != null) Modifier.clickable(onClick = beiKlick) else Modifier)
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Icon(Icons.Default.WarningAmber, null, tint = farben.onSecondaryContainer)
            Text(
                text = warnung.text,
                style = MaterialTheme.typography.bodyMedium,
                color = farben.onSecondaryContainer,
                modifier = Modifier.padding(start = 14.dp),
            )
        }
    }
}

@Composable
private fun GewerkZeile(gewerk: Gewerk, einsaetze: Int, index: Int, anzahl: Int, beiKlick: () -> Unit) {
    val farben = MaterialTheme.colorScheme
    GruppenBlock(index = index, anzahl = anzahl, einzug = EINZUG_MIT_SYMBOL) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = beiKlick)
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            // Farbpunkt statt Symbol: die Farbe ist die des Balkens auf der Tafel.
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp)) {
                Box(
                    Modifier
                        .size(20.dp)
                        .background(farbeAus(gewerk.farbe, farben.primary), CircleShape),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(gewerk.name, style = MaterialTheme.typography.bodyLarge)
                val zeile = listOf(gewerk.firma, gewerk.kontakt, gewerk.telefon).filter { it.isNotBlank() }
                    .joinToString(" · ")
                Text(
                    text = zeile.ifBlank { pluralStringResource(R.plurals.gu_einsaetze_anzahl, einsaetze, einsaetze) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = farben.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = farben.onSurfaceVariant)
        }
    }
}
