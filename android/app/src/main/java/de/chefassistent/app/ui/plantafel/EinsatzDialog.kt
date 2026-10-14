package de.chefassistent.app.ui.plantafel

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.chefassistent.app.R
import de.chefassistent.app.data.EINSATZ_STATUS
import de.chefassistent.app.data.Einsatz
import de.chefassistent.app.data.EinsatzAenderung
import de.chefassistent.app.data.EinsatzNeu
import de.chefassistent.app.data.Gewerk
import de.chefassistent.app.data.GewerkSetzen
import de.chefassistent.app.ui.komponenten.GruppenKarte

/** Abgesagt ist ein Status wie die anderen, wird aber auf der Tafel ausgeblendet. */
private val WAEHLBARE_STATUS = EINSATZ_STATUS

/**
 * Details und Bearbeiten eines Einsatzes in einem Sheet. Es geht nur raus, was
 * sich geaendert hat (PATCH). Wandert `von` und haengen andere Einsaetze an
 * diesem (`nach`), fragt die App, ob sie mitwandern sollen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EinsatzSheet(
    einsatz: Einsatz,
    gewerk: Gewerk?,
    alle: List<Einsatz>,
    beschaeftigt: Boolean,
    beiSpeichern: (EinsatzAenderung) -> Unit,
    beiLoeschen: () -> Unit,
    beiSchliessen: () -> Unit,
) {
    val kontext = LocalContext.current
    val farben = MaterialTheme.colorScheme
    var von by remember(einsatz.id) { mutableStateOf(einsatz.von) }
    var bis by remember(einsatz.id) { mutableStateOf(einsatz.bis) }
    var was by remember(einsatz.id) { mutableStateOf(einsatz.was) }
    var bereich by remember(einsatz.id) { mutableStateOf(einsatz.bereich) }
    var status by remember(einsatz.id) { mutableStateOf(einsatz.status) }
    var loeschenFrage by remember { mutableStateOf(false) }
    var mitverschiebenFrage by remember { mutableStateOf<EinsatzAenderung?>(null) }

    val nachfolger = alle.filter { it.nach == einsatz.id && it.status != Einsatz.STATUS_ABGESAGT }
    val vorgaenger = einsatz.nach?.let { id -> alle.firstOrNull { it.id == id } }

    // Nur geaenderte Felder. Wird nur `von` verschoben, bleibt die Dauer laut
    // Vertrag erhalten - dann `bis` nicht mitschicken.
    fun aenderung(): EinsatzAenderung {
        val vonNeu = von.takeIf { it != einsatz.von }
        val bisGeaendert = bis != einsatz.bis
        val altVon = datum(einsatz.von)
        val neuVon = datum(von)
        val dauerBleibt = vonNeu != null && altVon != null && neuVon != null &&
            datum(bis) == datum(einsatz.bis)?.plusDays(java.time.temporal.ChronoUnit.DAYS.between(altVon, neuVon))
        return EinsatzAenderung(
            von = vonNeu,
            bis = bis.takeIf { bisGeaendert && !dauerBleibt },
            was = was.takeIf { it != einsatz.was },
            bereich = bereich.takeIf { it != einsatz.bereich },
            status = status.takeIf { it != einsatz.status },
        )
    }
    val geaendert = aenderung() != EinsatzAenderung()
    val gueltig = (datum(bis) ?: datum(von))?.let { b -> datum(von)?.let { b >= it } } ?: false

    ModalBottomSheet(onDismissRequest = beiSchliessen, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(14.dp)
                        .background(farbeAus(einsatz.farbe, farben.primary), CircleShape),
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(einsatz.gewerk, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "${langDatum(einsatz.von)} – ${langDatum(einsatz.bis)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = farben.onSurfaceVariant,
                    )
                }
            }

            // Firma und Telefon aus dem Gewerk, Telefon antippbar.
            val firma = gewerk?.firma?.ifBlank { null } ?: einsatz.firma.ifBlank { null }
            if (firma != null || !gewerk?.kontakt.isNullOrBlank() || !gewerk?.telefon.isNullOrBlank()) {
                GruppenKarte(farbe = farben.surfaceContainerHigh) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        firma?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                        gewerk?.kontakt?.ifBlank { null }?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, color = farben.onSurfaceVariant)
                        }
                        gewerk?.telefon?.ifBlank { null }?.let { telefon ->
                            AssistChip(
                                onClick = {
                                    // Tablets ohne Telefonie haben keine Waehl-App.
                                    try {
                                        kontext.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(telefon))))
                                    } catch (_: ActivityNotFoundException) {
                                        Toast.makeText(kontext, telefon, Toast.LENGTH_LONG).show()
                                    }
                                },
                                label = { Text(stringResource(R.string.gu_anrufen, telefon)) },
                                leadingIcon = { Icon(Icons.Default.Call, null) },
                            )
                        }
                    }
                }
            }
            vorgaenger?.let { Hinweistext(stringResource(R.string.gu_nach, it.was.ifBlank { it.gewerk })) }

            VonBisFelder(von = von, bis = bis, beiVon = { neu ->
                // Dauer mitnehmen, wie der Server es beim Verschieben auch tut.
                val alt = datum(von)
                val neuDatum = datum(neu)
                val b = datum(bis)
                if (alt != null && neuDatum != null && b != null) {
                    bis = b.plusDays(java.time.temporal.ChronoUnit.DAYS.between(alt, neuDatum)).toString()
                }
                von = neu
            }, beiBis = { bis = it })

            OutlinedTextField(
                value = was, onValueChange = { was = it },
                label = { Text(stringResource(R.string.gu_was)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = bereich, onValueChange = { bereich = it },
                label = { Text(stringResource(R.string.gu_bereich)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            StatusWahl(status) { status = it }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { loeschenFrage = true },
                    enabled = !beschaeftigt,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.gu_loeschen), color = farben.error) }
                Button(
                    onClick = {
                        val a = aenderung()
                        if (a.von != null && nachfolger.isNotEmpty()) mitverschiebenFrage = a else beiSpeichern(a)
                    },
                    enabled = geaendert && gueltig && !beschaeftigt,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.gu_speichern)) }
            }
        }
    }

    if (loeschenFrage) {
        AlertDialog(
            onDismissRequest = { loeschenFrage = false },
            title = { Text(stringResource(R.string.gu_einsatz_loeschen_titel)) },
            text = {
                Text(stringResource(R.string.gu_einsatz_loeschen_frage, einsatz.was.ifBlank { einsatz.gewerk }))
            },
            confirmButton = {
                TextButton(onClick = {
                    loeschenFrage = false
                    beiLoeschen()
                }) { Text(stringResource(R.string.gu_loeschen), color = farben.error) }
            },
            dismissButton = {
                TextButton(onClick = { loeschenFrage = false }) { Text(stringResource(R.string.gu_abbrechen)) }
            },
        )
    }

    mitverschiebenFrage?.let { a ->
        AlertDialog(
            onDismissRequest = { mitverschiebenFrage = null },
            title = { Text(stringResource(R.string.gu_mitverschieben_titel)) },
            text = { Text(stringResource(R.string.gu_mitverschieben_frage, nachfolger.size)) },
            confirmButton = {
                TextButton(onClick = {
                    mitverschiebenFrage = null
                    beiSpeichern(a.copy(folgende_mitverschieben = true))
                }) { Text(stringResource(R.string.gu_mitverschieben_ja)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    mitverschiebenFrage = null
                    beiSpeichern(a)
                }) { Text(stringResource(R.string.gu_mitverschieben_nein)) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusWahl(status: String, beiWahl: (String) -> Unit) {
    Column {
        Text(stringResource(R.string.gu_status), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Ein unbekannter Status vom Server bleibt sichtbar gewaehlt.
            (WAEHLBARE_STATUS + listOf(status)).distinct().forEach { s ->
                FilterChip(selected = s == status, onClick = { beiWahl(s) }, label = { Text(statusText(s)) })
            }
        }
    }
}

/** Neuer Einsatz: Gewerk aus den vorhandenen oder frei, der Server legt Unbekannte an. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun EinsatzNeuSheet(
    gewerke: List<Gewerk>,
    beschaeftigt: Boolean,
    beiAnlegen: (EinsatzNeu) -> Unit,
    beiSchliessen: () -> Unit,
) {
    var gewerk by remember { mutableStateOf("") }
    var von by remember { mutableStateOf<String?>(java.time.LocalDate.now().toString()) }
    var bis by remember { mutableStateOf<String?>(null) }
    var was by remember { mutableStateOf("") }
    var bereich by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(Einsatz.STATUS_GEPLANT) }
    val vonDatum = datum(von)
    val bisDatum = datum(bis)
    val gueltig = gewerk.isNotBlank() && vonDatum != null && (bis == null || (bisDatum != null && bisDatum >= vonDatum))

    ModalBottomSheet(onDismissRequest = beiSchliessen, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
        ) {
            Text(stringResource(R.string.gu_einsatz_neu), style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = gewerk, onValueChange = { gewerk = it },
                label = { Text(stringResource(R.string.gu_gewerk)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            if (gewerke.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    gewerke.forEach { g ->
                        FilterChip(
                            selected = g.name == gewerk,
                            onClick = { gewerk = g.name },
                            label = { Text(g.name) },
                            leadingIcon = {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .background(farbeAus(g.farbe, MaterialTheme.colorScheme.primary), CircleShape),
                                )
                            },
                        )
                    }
                }
            }
            VonBisFelder(von = von, bis = bis, beiVon = { von = it }, beiBis = { bis = it })
            OutlinedTextField(
                value = was, onValueChange = { was = it },
                label = { Text(stringResource(R.string.gu_was)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = bereich, onValueChange = { bereich = it },
                label = { Text(stringResource(R.string.gu_bereich)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            StatusWahl(status) { status = it }
            Button(
                onClick = {
                    beiAnlegen(
                        EinsatzNeu(
                            gewerk = gewerk.trim(),
                            von = von!!,
                            bis = bis,
                            was = was.trim().ifBlank { null },
                            bereich = bereich.trim().ifBlank { null },
                            status = status,
                        ),
                    )
                },
                enabled = gueltig && !beschaeftigt,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.gu_einsatz_neu)) }
        }
    }
}

/** Gewerk bearbeiten: Firma, Kontakt, Telefon, Farbe. Loeschen nur ohne Einsaetze. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun GewerkSheet(
    gewerk: Gewerk,
    hatEinsaetze: Boolean,
    beschaeftigt: Boolean,
    beiSpeichern: (GewerkSetzen) -> Unit,
    beiLoeschen: () -> Unit,
    beiSchliessen: () -> Unit,
) {
    val farben = MaterialTheme.colorScheme
    var name by remember(gewerk.id) { mutableStateOf(gewerk.name) }
    var firma by remember(gewerk.id) { mutableStateOf(gewerk.firma) }
    var kontakt by remember(gewerk.id) { mutableStateOf(gewerk.kontakt) }
    var telefon by remember(gewerk.id) { mutableStateOf(gewerk.telefon) }
    var farbe by remember(gewerk.id) { mutableStateOf(gewerk.farbe.lowercase()) }
    var loeschenFrage by remember { mutableStateOf(false) }

    val aenderung = GewerkSetzen(
        name = gewerk.name,
        firma = firma.takeIf { it != gewerk.firma },
        kontakt = kontakt.takeIf { it != gewerk.kontakt },
        telefon = telefon.takeIf { it != gewerk.telefon },
        farbe = farbe.takeIf { !it.equals(gewerk.farbe, ignoreCase = true) },
        neuer_name = name.trim().takeIf { it.isNotBlank() && it != gewerk.name },
    )
    val geaendert = aenderung != GewerkSetzen(name = gewerk.name)

    ModalBottomSheet(onDismissRequest = beiSchliessen, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
        ) {
            Text(stringResource(R.string.gu_gewerk_bearbeiten), style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text(stringResource(R.string.gu_gewerk)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = firma, onValueChange = { firma = it },
                label = { Text(stringResource(R.string.gu_firma)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = kontakt, onValueChange = { kontakt = it },
                label = { Text(stringResource(R.string.gu_kontakt)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = telefon, onValueChange = { telefon = it },
                label = { Text(stringResource(R.string.gu_telefon)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.gu_farbe), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                (GEWERK_FARBEN + listOf(gewerk.farbe.lowercase())).distinct().forEach { hex ->
                    val c = farbeAus(hex, farben.primary)
                    val gewaehlt = hex.equals(farbe, ignoreCase = true)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .background(c, CircleShape)
                            .then(if (gewaehlt) Modifier.border(3.dp, farben.onSurface, CircleShape) else Modifier)
                            .clickable { farbe = hex },
                    ) {
                        if (gewaehlt) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = if (c.luminance() > 0.5f) Color.Black else Color.White,
                            )
                        }
                    }
                }
            }
            if (hatEinsaetze) Hinweistext(stringResource(R.string.gu_gewerk_hat_einsaetze))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { loeschenFrage = true },
                    enabled = !hatEinsaetze && !beschaeftigt,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.gu_loeschen)) }
                Button(
                    onClick = { beiSpeichern(aenderung) },
                    enabled = geaendert && !beschaeftigt,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.gu_speichern)) }
            }
        }
    }

    if (loeschenFrage) {
        AlertDialog(
            onDismissRequest = { loeschenFrage = false },
            title = { Text(stringResource(R.string.gu_gewerk_loeschen_titel)) },
            text = { Text(stringResource(R.string.gu_gewerk_loeschen_frage, gewerk.name)) },
            confirmButton = {
                TextButton(onClick = {
                    loeschenFrage = false
                    beiLoeschen()
                }) { Text(stringResource(R.string.gu_loeschen), color = farben.error) }
            },
            dismissButton = {
                TextButton(onClick = { loeschenFrage = false }) { Text(stringResource(R.string.gu_abbrechen)) }
            },
        )
    }
}
