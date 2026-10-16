package de.chefassistent.app.monteur.ui.bericht

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.chefassistent.app.R
import de.chefassistent.app.monteur.data.ArtikelWahl
import de.chefassistent.app.monteur.data.Material
import de.chefassistent.app.monteur.data.MonteurBericht
import de.chefassistent.app.monteur.ui.AufnahmeSteuerung
import de.chefassistent.app.monteur.ui.MikrofonKnopf
import de.chefassistent.app.monteur.ui.rememberAufnahme
import de.chefassistent.app.ui.komponenten.GruppenKarte
import de.chefassistent.app.ui.komponenten.GruppenTitel
import de.chefassistent.app.ui.theme.gold

/**
 * Bestaetigungskarte (Auftrag 1.22, 3.): alles auf einer scrollbaren Seite,
 * unten fest Mikrofon, „Kunde unterschreibt“ und „Passt – nach HERO“ - die drei
 * Dinge, die der Monteur am Ende wirklich tut.
 */
@Composable
internal fun BerichtKarte(viewModel: BerichtViewModel, zustand: BerichtZustand) {
    val bericht = zustand.bericht ?: return
    val e = bericht.entwurf
    // Eine Aufnahme fuer alle Mikrofone der Karte: es kann ohnehin nur eine laufen.
    val aufnahme = rememberAufnahme()
    var bearbeiten by rememberSaveable { mutableStateOf(false) }
    var unterschreiben by rememberSaveable { mutableStateOf(false) }
    var ohneUnterschrift by rememberSaveable { mutableStateOf(false) }
    var verwerfen by rememberSaveable { mutableStateOf(false) }
    val gesperrt = zustand.gesperrt

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Kopf(bericht, viewModel.start.titel, zustand.automatisch)

            GruppenTitel(stringResource(R.string.b_taetigkeit))
            GruppenKarte(Modifier.clickable(enabled = !gesperrt) { bearbeiten = true }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Text(
                        e.taetigkeit.ifBlank { stringResource(R.string.b_taetigkeit_leer) },
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (e.taetigkeit.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.b_bearbeiten),
                        modifier = Modifier.padding(start = 8.dp).size(20.dp),
                    )
                }
            }

            if (e.anstehend.isNotEmpty()) {
                GruppenTitel(stringResource(R.string.b_anstehend))
                GruppenKarte {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        e.anstehend.forEach { Text("• $it", style = MaterialTheme.typography.bodyLarge) }
                    }
                }
            }

            GruppenTitel(stringResource(R.string.b_stunden))
            GruppenKarte {
                StundenZeile(stringResource(R.string.b_monteur), e.monteur_stunden, !gesperrt) { viewModel.stunden(false, it) }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                StundenZeile(stringResource(R.string.b_helfer), e.helfer_stunden, !gesperrt) { viewModel.stunden(true, it) }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                SchalterZeile(stringResource(R.string.b_anfahrt), e.anfahrt, !gesperrt, viewModel::anfahrtSetzen)
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                SchalterZeile(stringResource(R.string.b_kleinmaterial), e.kleinmaterial, !gesperrt, viewModel::kleinmaterialSetzen)
            }

            GruppenTitel(stringResource(R.string.b_material))
            if (e.material.isEmpty()) {
                GruppenKarte {
                    Text(
                        stringResource(R.string.b_material_leer),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    e.material.forEachIndexed { i, m ->
                        MaterialZeile(m, !gesperrt, aufnahme, viewModel, i)
                    }
                }
            }
            // Material von Hand, mit Suche im Artikelstamm (seit 1.22.0).
            var materialDialog by rememberSaveable { mutableStateOf(false) }
            OutlinedButton(
                onClick = { materialDialog = true },
                enabled = !gesperrt,
                shape = KNOPF_FORM,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.b_material_plus))
            }
            if (materialDialog) {
                MaterialDialog(
                    viewModel = viewModel,
                    beiFertig = { neu ->
                        materialDialog = false
                        viewModel.materialHinzufuegen(neu)
                    },
                    beiAbbruch = {
                        materialDialog = false
                        viewModel.artikelSucheBeenden()
                    },
                )
            }

            if (e.zeiten.isNotEmpty()) {
                GruppenTitel(stringResource(R.string.b_zeiten))
                val namen = artNamen()
                GruppenKarte {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        e.zeiten.forEach { z ->
                            Text(zeitZeile(z) { art -> artName(art, namen) }, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            if (e.hinweise_buero.isNotEmpty() || e.rueckfragen.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = KARTEN_FORM,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (e.rueckfragen.isNotEmpty()) {
                            Text(stringResource(R.string.b_rueckfragen), style = MaterialTheme.typography.titleSmall)
                            e.rueckfragen.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                        }
                        if (e.hinweise_buero.isNotEmpty()) {
                            if (e.rueckfragen.isNotEmpty()) Spacer(Modifier.height(6.dp))
                            Text(stringResource(R.string.b_buero), style = MaterialTheme.typography.titleSmall)
                            e.hinweise_buero.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            }

            // Ergaenzen oder Rueckfrage beantworten - getippt. Steht direkt unter den
            // Rueckfragen, damit man die Frage beim Antworten sieht.
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(if (e.rueckfragen.isNotEmpty()) R.string.b_antworten_titel else R.string.b_ergaenzen_titel),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
            OutlinedTextField(
                value = zustand.ergaenzung,
                onValueChange = viewModel::ergaenzungAendern,
                placeholder = { Text(stringResource(R.string.b_ergaenzen_platzhalter)) },
                enabled = !gesperrt,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = viewModel::ergaenzungSenden,
                enabled = !gesperrt && zustand.ergaenzung.isNotBlank(),
                shape = KNOPF_FORM,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.b_ergaenzen_senden)) }

            zustand.unterschrift?.let { u ->
                Spacer(Modifier.height(16.dp))
                UnterschriftVorschau(u, gesperrt, beiNeu = { unterschreiben = true }, beiEntfernen = viewModel::unterschriftEntfernen)
            }

            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { verwerfen = true },
                enabled = !gesperrt,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.b_verwerfen), color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
        }

        Unten(
            zustand = zustand,
            aufnahme = aufnahme,
            beiDiktat = viewModel::diktatSenden,
            beiDiktatNochmal = viewModel::offenesDiktatSenden,
            beiDiktatVerwerfen = viewModel::offenesDiktatVerwerfen,
            beiUnterschrift = { unterschreiben = true },
            beiPasst = { if (zustand.unterschrift == null) ohneUnterschrift = true else viewModel.abschliessen() },
        )
    }

    if (bearbeiten) {
        TaetigkeitDialog(
            text = e.taetigkeit,
            beiFertig = {
                bearbeiten = false
                viewModel.taetigkeitAendern(it)
            },
            beiAbbruch = { bearbeiten = false },
        )
    }
    if (unterschreiben) {
        UnterschriftDialog(viewModel, beiFertig = { unterschreiben = false }, beiAbbruch = { unterschreiben = false })
    }
    if (ohneUnterschrift) {
        AlertDialog(
            onDismissRequest = { ohneUnterschrift = false },
            title = { Text(stringResource(R.string.b_ohne_unterschrift_titel)) },
            text = { Text(stringResource(R.string.b_ohne_unterschrift_text)) },
            confirmButton = {
                TextButton(onClick = {
                    ohneUnterschrift = false
                    viewModel.abschliessen()
                }) { Text(stringResource(R.string.b_ohne_unterschrift_ja)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    ohneUnterschrift = false
                    unterschreiben = true
                }) { Text(stringResource(R.string.b_unterschreibt)) }
            },
        )
    }
    if (verwerfen) {
        AlertDialog(
            onDismissRequest = { verwerfen = false },
            title = { Text(stringResource(R.string.b_verwerfen_titel)) },
            text = { Text(stringResource(R.string.b_verwerfen_text)) },
            confirmButton = {
                TextButton(onClick = {
                    verwerfen = false
                    viewModel.verwerfen()
                }) { Text(stringResource(R.string.b_verwerfen_ja), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { verwerfen = false }) { Text(stringResource(R.string.b_abbrechen)) } },
        )
    }
}

/**
 * Projekt, Kunde, Datum. Hat der Server den Termin selbst gesucht, steht das
 * gold umrandet da - ein falsch zugeordneter Bericht landet sonst unbemerkt
 * im falschen HERO-Projekt.
 */
@Composable
private fun Kopf(bericht: MonteurBericht, titel: String?, automatisch: Boolean) {
    val projekt = bericht.projekt?.nr?.takeIf { it.isNotBlank() }
    val kunde = kundeAusTitel(titel)
    val zeile = listOfNotNull(projekt, kunde.ifBlank { null }, bericht.datum.takeIf { it.isNotBlank() }?.let(::datumText))
        .joinToString(" · ")
        .ifBlank { stringResource(R.string.b_projekt_unbekannt) }
    if (automatisch) {
        val gold = MaterialTheme.colorScheme.gold
        Surface(
            shape = KARTEN_FORM,
            color = gold.copy(alpha = 0.16f),
            border = BorderStroke(2.dp, gold),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.b_automatisch, zeile),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(stringResource(R.string.b_automatisch_pruefen), style = MaterialTheme.typography.bodyMedium)
            }
        }
    } else {
        GruppenKarte {
            Text(zeile, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun StundenZeile(name: String, wert: Double, aktiv: Boolean, beiSchritt: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Stepper(stringResource(R.string.b_stunden_wert, zahlText(wert)), aktiv, beiSchritt)
    }
}

/** − Wert +, je 48 dp Tippflaeche. */
@Composable
private fun Stepper(wert: String, aktiv: Boolean, beiSchritt: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { beiSchritt(-1) }, enabled = aktiv, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.b_weniger))
        }
        Text(
            wert,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 80.dp),
        )
        FilledTonalIconButton(onClick = { beiSchritt(1) }, enabled = aktiv, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.b_mehr))
        }
    }
}

@Composable
private fun SchalterZeile(name: String, an: Boolean, aktiv: Boolean, beiAenderung: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(enabled = aktiv) { beiAenderung(!an) }.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = an, onCheckedChange = beiAenderung, enabled = aktiv)
    }
}

/**
 * Eine Materialposition. Mit `rueckfrage` gold markiert (Rand und Flaeche, nie
 * goldene Schrift), darunter die Frage, die Vorschlaege als Chips, „Frei lassen“
 * und ein kleines Mikrofon zum Antworten per Sprache.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MaterialZeile(m: Material, aktiv: Boolean, aufnahme: AufnahmeSteuerung, viewModel: BerichtViewModel, index: Int) {
    val gold = MaterialTheme.colorScheme.gold
    val offen = m.rueckfrage != null
    Surface(
        shape = KARTEN_FORM,
        color = if (offen) gold.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainer,
        border = if (offen) BorderStroke(2.dp, gold) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(top = 8.dp)) {
                    Text(materialName(m), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    val unter = when {
                        m.artikel != null -> stringResource(R.string.b_artikel_nr, m.artikel.nr) +
                            (if (m.text.isNotBlank() && m.text != m.artikel.name) " · „${m.text}“" else "")
                        else -> stringResource(R.string.b_frei)
                    }
                    Text(unter, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { viewModel.materialEntfernen(index) }, enabled = aktiv) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.b_entfernen))
                }
            }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Stepper("${zahlText(m.menge)} ${materialEinheit(m)}".trim(), aktiv) { viewModel.menge(index, it) }
            }
            if (m.rueckfrage != null) {
                Text(
                    m.rueckfrage,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp, end = 12.dp),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp, end = 12.dp),
                ) {
                    m.vorschlaege.forEach { v ->
                        FilterChip(
                            selected = m.artikel?.nr == v.nr,
                            onClick = { viewModel.artikelWaehlen(index, ArtikelWahl.Waehlen(v.nr)) },
                            enabled = aktiv,
                            label = { Text(v.name) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                    FilterChip(
                        selected = false,
                        onClick = { viewModel.artikelWaehlen(index, ArtikelWahl.Frei) },
                        enabled = aktiv,
                        label = { Text(stringResource(R.string.b_frei_lassen)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
                MikrofonKnopf(
                    steuerung = aufnahme,
                    beiFertig = viewModel::diktatSenden,
                    aktiv = aktiv,
                    groesse = 48.dp,
                    beschriftung = stringResource(R.string.b_per_sprache),
                    modifier = Modifier.padding(top = 8.dp).align(Alignment.Start),
                )
            }
        }
    }
}

@Composable
private fun UnterschriftVorschau(u: Unterschrift, gesperrt: Boolean, beiNeu: () -> Unit, beiEntfernen: () -> Unit) {
    val bild = remember(u) { BitmapFactory.decodeByteArray(u.png, 0, u.png.size)?.asImageBitmap() }
    GruppenKarte {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            bild?.let {
                Image(
                    it,
                    contentDescription = null,
                    modifier = Modifier.width(96.dp).height(56.dp).background(androidx.compose.ui.graphics.Color.White, KNOPF_FORM),
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(
                if (u.name.isBlank()) stringResource(R.string.b_unterschrieben_ohne_name) else stringResource(R.string.b_unterschrieben, u.name),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = beiNeu, enabled = !gesperrt) { Text(stringResource(R.string.b_unterschrift_neu)) }
            IconButton(onClick = beiEntfernen, enabled = !gesperrt) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.b_loeschen))
            }
        }
    }
}

/** Fester Fuss: Mikrofon „Ergaenzen/Korrigieren“, Unterschrift, „Passt – nach HERO“. */
@Composable
private fun Unten(
    zustand: BerichtZustand,
    aufnahme: AufnahmeSteuerung,
    beiDiktat: (java.io.File) -> Unit,
    beiDiktatNochmal: () -> Unit,
    beiDiktatVerwerfen: () -> Unit,
    beiUnterschrift: () -> Unit,
    beiPasst: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            if (zustand.diktatLaeuft) {
                Text(stringResource(R.string.b_diktat_laeuft), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 6.dp))
            } else if (zustand.offenesDiktat != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.b_diktat_liegt), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = beiDiktatNochmal, enabled = !zustand.gesperrt) { Text(stringResource(R.string.b_nochmal)) }
                    IconButton(onClick = beiDiktatVerwerfen) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.b_aufnahme_verwerfen))
                    }
                }
            } else if (zustand.sendet) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                MikrofonKnopf(
                    steuerung = aufnahme,
                    beiFertig = beiDiktat,
                    aktiv = !zustand.gesperrt,
                    groesse = 60.dp,
                    beschriftung = stringResource(R.string.b_ergaenzen),
                    modifier = Modifier.width(104.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = beiUnterschrift,
                        enabled = !zustand.gesperrt && !aufnahme.nimmtAuf,
                        shape = KNOPF_FORM,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Outlined.Draw, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.b_unterschreibt))
                    }
                    Button(
                        onClick = beiPasst,
                        enabled = !zustand.gesperrt && !aufnahme.nimmtAuf,
                        colors = navyKnopf(),
                        shape = KNOPF_FORM,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) { Text(stringResource(R.string.b_passt), fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun TaetigkeitDialog(text: String, beiFertig: (String) -> Unit, beiAbbruch: () -> Unit) {
    var neu by rememberSaveable { mutableStateOf(text) }
    AlertDialog(
        onDismissRequest = beiAbbruch,
        title = { Text(stringResource(R.string.b_taetigkeit)) },
        text = {
            OutlinedTextField(
                value = neu,
                onValueChange = { neu = it },
                minLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { beiFertig(neu) }) { Text(stringResource(R.string.b_speichern)) } },
        dismissButton = { TextButton(onClick = beiAbbruch) { Text(stringResource(R.string.b_abbrechen)) } },
    )
}

/** Vertragswerte der Zeit-Art auf Deutsch. */
@Composable
private fun artNamen(): Map<String, String> = mapOf(
    "arbeit" to stringResource(R.string.b_art_arbeit),
    "fahrt" to stringResource(R.string.b_art_fahrt),
    "pause" to stringResource(R.string.b_art_pause),
    "material" to stringResource(R.string.b_art_material),
    "buero" to stringResource(R.string.b_art_buero),
    "vor_ort" to stringResource(R.string.b_art_vor_ort),
)

/** Unbekannte Arten so, wie sie kamen - ein neuer Wert vom Server darf nichts kaputtmachen. */
private fun artName(art: String, namen: Map<String, String>): String =
    namen[art] ?: art.replace('_', ' ').replaceFirstChar { it.uppercase() }
