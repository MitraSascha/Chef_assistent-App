package de.chefassistent.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.chefassistent.app.R
import de.chefassistent.app.data.ModellAuswahl
import de.chefassistent.app.data.ModellEintrag
import de.chefassistent.app.data.ModellWahl
import de.chefassistent.app.data.MODELL_AUTO
import de.chefassistent.app.ui.theme.warnung

// --- Reine Logik (testbar) ---------------------------------------------------

/**
 * Zeile unter einer Antwort (Auftrag 1.20 A5): `gpt-5.6-sol · mittel`, bei einem
 * teuren Modell `gpt-5.6-astra (teuer) · hoch`, nach einem automatischen Wechsel
 * zusaetzlich ` · gewechselt`. `null` bei alten Nachrichten ohne [modell].
 */
fun modellZeile(
    modell: String?,
    denkaufwandName: String?,
    gewechselt: Boolean,
    auswahl: ModellAuswahl?,
): String? {
    val wert = modell?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val name = auswahl?.modell(wert)?.name ?: wert
    val teile = buildList {
        add(if (auswahl?.istTeuer(wert) == true) "$name (teuer)" else name)
        denkaufwandName?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
        if (gewechselt) add("gewechselt")
    }
    return teile.joinToString(" · ")
}

fun modellZeile(eintrag: AssistentEintrag, auswahl: ModellAuswahl?): String? =
    modellZeile(eintrag.modell, eintrag.denkaufwandName, eintrag.modellGewechselt, auswahl)

/** Anzeigename eines Modells, `null` fuer "auto" (dann "Automatisch" aus den Ressourcen). */
fun modellName(wert: String, auswahl: ModellAuswahl?): String? =
    if (wert == MODELL_AUTO) null else auswahl?.modell(wert)?.name ?: wert

/** Anzeigename einer Denkstufe, `null` fuer "auto". */
fun denkaufwandName(wert: String, auswahl: ModellAuswahl?): String? =
    if (wert == MODELL_AUTO) null else auswahl?.denkstufen?.firstOrNull { it.wert == wert }?.name ?: wert

// --- Oberflaeche --------------------------------------------------------------

/**
 * Zwei Auswahlfelder ueber dem Eingabefeld: Modell und Denkaufwand, je Gespraech.
 * Mit [auswahl] `null` (alter Server ohne `GET /v1/modelle`) erscheint nichts.
 *
 * Ein teures Modell wird erst nach einer Rueckfrage gewaehlt und bleibt danach
 * in Warnfarbe sichtbar, samt "⚠ teuer pro Anfrage" - damit es niemand
 * versehentlich stehen laesst.
 */
@Composable
fun ModellLeiste(
    auswahl: ModellAuswahl?,
    modell: String,
    denkaufwand: String,
    aktiv: Boolean,
    beiModell: (String) -> Unit,
    beiDenkaufwand: (String) -> Unit,
) {
    if (auswahl == null) return
    var infoOffen by remember { mutableStateOf(false) }
    var teuerInfoOffen by remember { mutableStateOf(false) }
    var rueckfrage by remember { mutableStateOf<ModellEintrag?>(null) }
    val teuer = auswahl.modell(modell)?.takeIf { it.teuer }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ModellFeld(
                auswahl = auswahl,
                wert = modell,
                aktiv = aktiv,
                beiWahl = { neu ->
                    val eintrag = auswahl.modell(neu)
                    when {
                        neu == modell -> Unit
                        // Nicht erneut fragen, wenn es schon eingestellt ist (oben abgefangen).
                        eintrag?.teuer == true -> rueckfrage = eintrag
                        else -> beiModell(neu)
                    }
                },
            )
            DenkaufwandFeld(
                auswahl = auswahl,
                wert = denkaufwand,
                aktiv = aktiv,
                beiWahl = { neu -> if (neu != denkaufwand) beiDenkaufwand(neu) },
            )
            IconButton(onClick = { infoOffen = true }) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.modell_info),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (teuer != null) {
            // Eigene Zeile, damit sie auch auf schmalen Handys nie aus dem Bild rutscht.
            Text(
                text = stringResource(R.string.modell_teuer_warnung),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.warnung,
                modifier = Modifier
                    .heightIn(min = 32.dp)
                    .clickable(enabled = teuer.hinweis != null) { teuerInfoOffen = true }
                    .padding(vertical = 6.dp),
            )
        }
    }

    if (infoOffen) {
        HinweisDialog(
            titel = stringResource(R.string.modell_info_titel),
            text = auswahl.automatisch,
            beiSchliessen = { infoOffen = false },
        )
    }
    if (teuerInfoOffen && teuer?.hinweis != null) {
        HinweisDialog(
            titel = stringResource(R.string.modell_teuer_titel),
            text = teuer.hinweis,
            beiSchliessen = { teuerInfoOffen = false },
        )
    }
    rueckfrage?.let { eintrag ->
        AlertDialog(
            onDismissRequest = { rueckfrage = null },
            title = { Text(stringResource(R.string.modell_teuer_titel)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.modell_teuer_frage, eintrag.name))
                    eintrag.hinweis?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.warnung,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    rueckfrage = null
                    beiModell(eintrag.wert)
                }) { Text(stringResource(R.string.modell_ja)) }
            },
            dismissButton = {
                TextButton(onClick = { rueckfrage = null }) { Text(stringResource(R.string.modell_nein)) }
            },
        )
    }
}

@Composable
private fun ModellFeld(auswahl: ModellAuswahl, wert: String, aktiv: Boolean, beiWahl: (String) -> Unit) {
    var offen by remember { mutableStateOf(false) }
    val teuer = auswahl.istTeuer(wert)
    val anzeige = modellName(wert, auswahl) ?: stringResource(R.string.modell_automatisch)
    val beschreibung = stringResource(R.string.modell_feld, anzeige)
    Box {
        AuswahlChip(
            text = anzeige,
            beschreibung = beschreibung,
            symbol = { Icon(Icons.Outlined.Memory, null, Modifier.size(FilterChipDefaults.IconSize)) },
            gewaehlt = wert != MODELL_AUTO,
            teuer = teuer,
            aktiv = aktiv,
            beiKlick = { offen = true },
        )
        DropdownMenu(expanded = offen, onDismissRequest = { offen = false }, shape = RoundedCornerShape(20.dp)) {
            val waehlen: (String) -> Unit = { neu ->
                offen = false
                beiWahl(neu)
            }
            AuswahlEintrag(stringResource(R.string.modell_automatisch), wert == MODELL_AUTO) { waehlen(MODELL_AUTO) }
            auswahl.modelle.filterNot { it.teuer }.forEach { m ->
                AuswahlEintrag(m.name, wert == m.wert, untertitel = m.hinweis) { waehlen(m.wert) }
            }
            val teure = auswahl.modelle.filter { it.teuer }
            if (teure.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    text = stringResource(R.string.modell_abschnitt_teuer),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.warnung,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                teure.forEach { m ->
                    AuswahlEintrag(m.name, wert == m.wert, untertitel = m.hinweis, teuer = true) { waehlen(m.wert) }
                }
            }
        }
    }
}

@Composable
private fun DenkaufwandFeld(auswahl: ModellAuswahl, wert: String, aktiv: Boolean, beiWahl: (String) -> Unit) {
    var offen by remember { mutableStateOf(false) }
    val anzeige = denkaufwandName(wert, auswahl) ?: stringResource(R.string.modell_automatisch)
    val beschreibung = stringResource(R.string.denkaufwand_feld, anzeige)
    Box {
        AuswahlChip(
            text = anzeige,
            beschreibung = beschreibung,
            symbol = { Icon(Icons.Outlined.Psychology, null, Modifier.size(FilterChipDefaults.IconSize)) },
            gewaehlt = wert != MODELL_AUTO,
            teuer = false,
            aktiv = aktiv,
            beiKlick = { offen = true },
        )
        DropdownMenu(expanded = offen, onDismissRequest = { offen = false }, shape = RoundedCornerShape(20.dp)) {
            val waehlen: (String) -> Unit = { neu ->
                offen = false
                beiWahl(neu)
            }
            AuswahlEintrag(stringResource(R.string.modell_automatisch), wert == MODELL_AUTO) { waehlen(MODELL_AUTO) }
            auswahl.denkstufen.forEach { stufe ->
                AuswahlEintrag(stufe.name, wert == stufe.wert) { waehlen(stufe.wert) }
            }
        }
    }
}

/**
 * Chip mit Pfeil. Etwas anderes als "Automatisch" bekommt einen Rahmen in der
 * Akzentfarbe, ein teures Modell Rahmen und Schrift in Warnfarbe. Die Tippflaeche
 * ist durch Material mindestens 48 dp hoch.
 */
@Composable
private fun AuswahlChip(
    text: String,
    beschreibung: String,
    symbol: @Composable () -> Unit,
    gewaehlt: Boolean,
    teuer: Boolean,
    aktiv: Boolean,
    beiKlick: () -> Unit,
) {
    val akzent = if (teuer) MaterialTheme.colorScheme.warnung else MaterialTheme.colorScheme.primary
    FilterChip(
        selected = gewaehlt,
        onClick = beiKlick,
        enabled = aktiv,
        shape = CircleShape,
        modifier = Modifier.semantics { contentDescription = beschreibung },
        label = { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = symbol,
        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.size(FilterChipDefaults.IconSize)) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = if (teuer) akzent.copy(alpha = 0.14f) else Color.Transparent,
            selectedLabelColor = akzent,
            selectedLeadingIconColor = akzent,
            selectedTrailingIconColor = akzent,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = aktiv,
            selected = gewaehlt,
            selectedBorderColor = akzent,
            selectedBorderWidth = 1.5.dp,
        ),
    )
}

@Composable
private fun AuswahlEintrag(
    name: String,
    gewaehlt: Boolean,
    untertitel: String? = null,
    teuer: Boolean = false,
    beiKlick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, fontWeight = if (gewaehlt) FontWeight.SemiBold else null)
                    if (teuer) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.modell_teuer_marke),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.warnung,
                        )
                    }
                }
                untertitel?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        trailingIcon = if (gewaehlt) {
            { Icon(Icons.Default.Check, null) }
        } else {
            null
        },
        onClick = beiKlick,
    )
}

@Composable
private fun HinweisDialog(titel: String, text: String, beiSchliessen: () -> Unit) {
    AlertDialog(
        onDismissRequest = beiSchliessen,
        title = { Text(titel) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = beiSchliessen) { Text(stringResource(R.string.modell_ok)) } },
    )
}

/** Kleine graue Zeile unter einer Antwort: womit sie erzeugt wurde. */
@Composable
fun ModellZeileText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
