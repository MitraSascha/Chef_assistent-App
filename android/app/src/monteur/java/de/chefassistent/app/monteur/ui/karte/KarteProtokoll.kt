package de.chefassistent.app.monteur.ui.karte

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.chefassistent.app.R
import de.chefassistent.app.monteur.data.Karte
import de.chefassistent.app.monteur.data.Protokoll
import de.chefassistent.app.monteur.data.ProtokollFeld
import de.chefassistent.app.monteur.ui.bericht.UnterschriftDialog
import de.chefassistent.app.ui.theme.warnung

/**
 * Protokoll-Karte (Auftrag 1.25): generisches Formular aus [Protokoll.felder]. Titel mit
 * Vorlagenwechsel, „Es fehlen noch …“, je Feld eine Eingabe nach `t`, Fotos, Textlink
 * „Vorschau“/„PDF“ und die optionale Kundenunterschrift. Keine eigene Ansicht je Vorlage.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ProtokollInhalt(
    karte: Karte,
    viewModel: EingangViewModel,
    fotos: List<String>,
    vorlagen: List<Protokoll>,
    springeZu: Pair<String, Int>?,
    aktiv: Boolean,
    sendet: Boolean,
    beiPdf: (karteId: String) -> Unit,
) {
    val protokoll = karte.protokoll ?: return Text(karte.text)
    val eingabe = viewModel.eingabe
    val bearbeitbar = aktiv && karte.istOffen
    val werte = eingabe.werte
    val fehlend = if (karte.istOffen) fehlendeFelder(protokoll.felder, werte) else emptyList()
    var unterschreiben by remember { mutableStateOf(false) }
    LaunchedEffect(karte.istOffen) { if (karte.istOffen) viewModel.vorlagenLaden() }

    // Je Feld ein Anker, damit nach `409` das erste fehlende Feld ins Bild rueckt.
    val anker = remember(protokoll.vorlage) { protokoll.felder.associate { it.k to BringIntoViewRequester() } }
    LaunchedEffect(springeZu) { springeZu?.first?.let { anker[it]?.bringIntoView() } }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        VorlagenKopf(protokoll, vorlagen, bearbeitbar, viewModel::vorlageWechseln)
        if (protokoll.grundlage.isNotBlank()) {
            Text(protokoll.grundlage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (karte.istOffen) {
            val warn = fehlend.isNotEmpty()
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (warn) MaterialTheme.colorScheme.warnung.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainer,
                border = if (warn) BorderStroke(1.dp, MaterialTheme.colorScheme.warnung) else null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (warn) stringResource(R.string.pr_fehlen, fehlend.joinToString(", ")) else stringResource(R.string.pr_alles_da),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        protokoll.felder.forEach { f ->
            Box(Modifier.bringIntoViewRequester(anker.getValue(f.k))) {
                FeldZeile(
                    feld = f,
                    wert = aktuellerWert(f, werte),
                    fehlt = f.l in fehlend,
                    aktiv = bearbeitbar,
                    beiAenderung = { neu -> eingabe.werte = aenderungSetzen(eingabe.werte, f, neu) },
                )
            }
        }

        FotoReihe(fotos)
        val weitere = karte.fotos - fotos.size
        if (weitere > 0) {
            Text(
                pluralStringResource(R.plurals.bs_fotos_mehr, weitere, weitere),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Kleiner Textlink, kein grosser Knopf (Auftrag 1.25, 3.).
        TextButton(
            onClick = { viewModel.vorschau(beiPdf) },
            enabled = !sendet,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Icon(Icons.Outlined.PictureAsPdf, null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(if (karte.istOffen) R.string.pr_vorschau else R.string.pr_pdf))
        }

        if (karte.istOffen) {
            val u = eingabe.unterschrift
            if (u == null) {
                OutlinedButton(
                    onClick = { unterschreiben = true },
                    enabled = bearbeitbar,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Icon(Icons.Outlined.Draw, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.pr_unterschreiben))
                }
            } else {
                val bild = remember(u) { BitmapFactory.decodeByteArray(u.png, 0, u.png.size)?.asImageBitmap() }
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        bild?.let {
                            Image(it, null, Modifier.width(96.dp).height(56.dp).background(Color.White, RoundedCornerShape(10.dp)))
                        }
                        Text(
                            stringResource(R.string.pr_unterschrift_von, u.name.ifBlank { "–" }),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                        )
                        TextButton(onClick = { unterschreiben = true }, enabled = bearbeitbar) { Text(stringResource(R.string.pr_unterschrift_neu)) }
                        TextButton(onClick = {
                            eingabe.unterschrift = null
                            viewModel.zeichenblock.stricheLoeschen()
                        }, enabled = bearbeitbar) { Text(stringResource(R.string.pr_unterschrift_weg)) }
                    }
                }
            }
        }
    }
    if (unterschreiben) {
        UnterschriftDialog(viewModel.zeichenblock, beiFertig = { unterschreiben = false }, beiAbbruch = { unterschreiben = false })
    }
}

/** Titel fett mit ▾ - antippen zeigt die Vorlagen. Kein eigener Knopf (Auftrag 1.25, 2.). */
@Composable
private fun VorlagenKopf(protokoll: Protokoll, vorlagen: List<Protokoll>, aktiv: Boolean, beiWahl: (String) -> Unit) {
    var offen by remember { mutableStateOf(false) }
    val waehlbar = aktiv && vorlagen.size > 1
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(enabled = waehlbar) { offen = true }.heightIn(min = 48.dp),
        ) {
            Text(
                protokoll.titel.ifBlank { protokoll.vorlage },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (waehlbar) Icon(Icons.Outlined.ArrowDropDown, stringResource(R.string.pr_vorlage_wechseln))
        }
        DropdownMenu(expanded = offen, onDismissRequest = { offen = false }) {
            vorlagen.forEach { v ->
                DropdownMenuItem(
                    text = {
                        Text(
                            v.titel.ifBlank { v.vorlage },
                            fontWeight = if (v.vorlage == protokoll.vorlage) FontWeight.Bold else null,
                        )
                    },
                    onClick = {
                        offen = false
                        beiWahl(v.vorlage)
                    },
                )
            }
        }
    }
}

/** Eine Zeile je Feld; fehlende Pflichtfelder im Warnton umrandet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeldZeile(feld: ProtokollFeld, wert: String?, fehlt: Boolean, aktiv: Boolean, beiAenderung: (String?) -> Unit) {
    val warn = MaterialTheme.colorScheme.warnung
    val beschriftung = if (feld.p) "${feld.l} *" else feld.l
    when (val art = feldArt(feld)) {
        FeldArt.TEXT, FeldArt.MEHRZEILIG, FeldArt.ZAHL -> OutlinedTextField(
            value = wert.orEmpty(),
            onValueChange = { neu ->
                beiAenderung(if (art == FeldArt.ZAHL) zahlEingabe(neu) else neu.take(PROTOKOLL_MAX_ZEICHEN))
            },
            enabled = aktiv,
            label = { Text(beschriftung) },
            suffix = feld.e?.takeIf { it.isNotBlank() }?.let { e -> { Text(e) } },
            singleLine = art != FeldArt.MEHRZEILIG,
            minLines = if (art == FeldArt.MEHRZEILIG) 3 else 1,
            keyboardOptions = if (art == FeldArt.ZAHL) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
            isError = fehlt,
            colors = if (fehlt) androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                errorBorderColor = warn,
                errorLabelColor = warn,
                errorCursorColor = warn,
            ) else androidx.compose.material3.OutlinedTextFieldDefaults.colors(),
            modifier = Modifier.fillMaxWidth(),
        )
        FeldArt.JANEIN, FeldArt.AUSWAHL -> {
            val optionen = if (art == FeldArt.JANEIN) listOf(stringResource(R.string.pr_ja), stringResource(R.string.pr_nein)) else feld.o.orEmpty()
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (fehlt) warn.copy(alpha = 0.12f) else Color.Transparent,
                border = BorderStroke(1.dp, if (fehlt) warn else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(beschriftung, style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        optionen.forEach { o ->
                            FilterChip(
                                selected = wert != null && wert.equals(o, ignoreCase = true),
                                onClick = { beiAenderung(umschalten(wert, o)) },
                                enabled = aktiv,
                                label = { Text(o) },
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
