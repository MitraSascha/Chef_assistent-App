package de.chefassistent.app.ui.angebote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.chefassistent.app.R
import de.chefassistent.app.data.ANTWORT_PASST
import de.chefassistent.app.data.Angebot
import de.chefassistent.app.data.AngebotBeantwortet
import de.chefassistent.app.data.AngebotGruppe
import de.chefassistent.app.data.AngebotPosition
import de.chefassistent.app.data.AngebotPunkt
import de.chefassistent.app.data.PunktPosition
import de.chefassistent.app.ui.dateiTeilen
import de.chefassistent.app.ui.komponenten.GrosseKopfleiste
import de.chefassistent.app.ui.komponenten.GruppenBlock
import de.chefassistent.app.ui.komponenten.GruppenKarte
import de.chefassistent.app.ui.komponenten.GruppenTitel
import de.chefassistent.app.ui.komponenten.LeerHinweis
import de.chefassistent.app.ui.komponenten.gruppenAnordnung
import de.chefassistent.app.ui.komponenten.listenAbstand
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Ab 6 Punkten nur die ersten 5, der Rest hinter "weitere anzeigen" (`API.md` 4e). */
internal const val PUNKTE_SICHTBAR = 5

/** So lange bleibt eine angesprungene Position hervorgehoben. */
private const val HERVORHEBEN_MS = 2500L

/**
 * Vorschau eines Angebots (`GET /v1/angebote/{id}`, `API.md` 4e): Kopf mit Summen,
 * oben die Entscheidungen (`punkte`, seit v1.15.0), darunter die Gruppen mit ihren
 * Positionen. Die alten Listen `fragen`, `markiert` und `hinweise` zeigt die App
 * nicht mehr. Geaendert wird weiterhin im Chat; hier wird nur geantwortet.
 */
@Composable
fun AngebotScreen(
    viewModel: AngeboteViewModel,
    angebotId: String,
    beiZurueck: () -> Unit,
    beiAbmeldung: () -> Unit,
    /** Schickt den Text als neue Nachricht in den Chat. */
    beiEinarbeiten: (String) -> Unit,
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val kontext = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val listenZustand = rememberLazyListState()
    val bereich = rememberCoroutineScope()
    var angesprungen by remember { mutableStateOf<String?>(null) }
    var allePunkte by rememberSaveable { mutableStateOf(false) }
    var menueOffen by remember { mutableStateOf(false) }

    LifecycleResumeEffect(angebotId) {
        viewModel.angebotLaden(angebotId)
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
    LaunchedEffect(angesprungen) {
        if (angesprungen != null) {
            delay(HERVORHEBEN_MS)
            angesprungen = null
        }
    }

    val angebot = zustand.angebot
    val eintraege = remember(angebot, allePunkte) { angebot?.let { eintraegeAus(it, allePunkte) }.orEmpty() }
    val uglTitel = stringResource(R.string.angebot_ugl_teilen)
    val titel = angebot?.titel?.ifBlank { null } ?: stringResource(R.string.angebote_ohne_titel)
    val einarbeitenText = stringResource(R.string.punkt_einarbeiten_text, angebot?.titel?.ifBlank { null } ?: angebotId)
    // Fehler- und Ladezeilen stehen vor den Eintraegen und verschieben deren Index.
    val vorlauf = listOfNotNull(
        if (zustand.laeuft && angebot == null) Unit else null,
        zustand.nichtEingerichtet,
        zustand.nichtGefunden,
        zustand.nichtErreichbar,
    ).size

    fun springen(positionId: String) {
        val index = eintraege.indexOfFirst { it is Eintrag.Position && it.position.id == positionId }
        if (index < 0) return
        angesprungen = positionId
        bereich.launch { listenZustand.animateScrollToItem(vorlauf + index) }
    }

    GrosseKopfleiste(
        titel = titel,
        titelZeilen = 1,
        untertitel = angebot?.let { kopfzeile(it) },
        beiZurueck = beiZurueck,
        snackbar = snackbar,
        aktionen = {
            IconButton(onClick = { viewModel.angebotLaden(angebotId) }) {
                Icon(Icons.Default.Refresh, stringResource(R.string.angebote_neu_laden))
            }
            if (angebot != null) {
                Box {
                    IconButton(onClick = { menueOffen = true }) {
                        if (zustand.laedtUgl) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        } else {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.angebot_mehr))
                        }
                    }
                    DropdownMenu(expanded = menueOffen, onDismissRequest = { menueOffen = false }) {
                        val vorschau = angebot.vorschau?.takeIf { it.isNotBlank() }
                        if (vorschau != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.angebot_web)) },
                                leadingIcon = { Icon(Icons.Default.OpenInBrowser, null) },
                                onClick = {
                                    menueOffen = false
                                    runCatching { uriHandler.openUri(vorschau) }
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.angebot_ugl)) },
                            leadingIcon = { Icon(Icons.Default.Share, null) },
                            enabled = !zustand.laedtUgl,
                            onClick = {
                                menueOffen = false
                                viewModel.uglLaden { datei -> dateiTeilen(kontext, datei, uglTitel) }
                            },
                        )
                    }
                }
            }
        },
    ) { innen ->
        LazyColumn(
            state = listenZustand,
            modifier = Modifier.fillMaxSize(),
            contentPadding = listenAbstand(innen),
            verticalArrangement = gruppenAnordnung,
        ) {
            if (zustand.laeuft && angebot == null) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
            }
            zustand.nichtEingerichtet?.let { text -> item { LeerHinweis(text) } }
            zustand.nichtGefunden?.let { text -> item { LeerHinweis(text) } }
            zustand.nichtErreichbar?.let { text ->
                item { NochmalHinweis(text) { viewModel.angebotLaden(angebotId) } }
            }
            items(eintraege, key = { it.schluessel }) { eintrag ->
                when (eintrag) {
                    is Eintrag.Summen -> SummenKarte(eintrag.angebot)
                    is Eintrag.Entscheidungen -> GruppenTitel("${stringResource(R.string.angebot_entscheidungen)} (${eintrag.anzahl})")
                    is Eintrag.Punkt -> PunktKarte(
                        punkt = eintrag.punkt,
                        sendet = zustand.antwortet == eintrag.punkt.id,
                        gesperrt = zustand.antwortet != null,
                        beiPosition = ::springen,
                        beiAntwort = { antwort -> viewModel.antworten(eintrag.punkt.id, antwort) },
                    )
                    is Eintrag.MehrPunkte -> TextButton(
                        onClick = { allePunkte = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(pluralStringResource(R.plurals.punkte_weitere, eintrag.anzahl, eintrag.anzahl)) }
                    is Eintrag.Beantwortet -> BeantwortetKarte(eintrag.liste) { beiEinarbeiten(einarbeitenText) }
                    is Eintrag.GruppenKopf -> GruppenTitel(
                        text = listOf(eintrag.gruppe.nr, eintrag.gruppe.titel).filter { it.isNotBlank() }.joinToString("  "),
                        farbe = if (eintrag.gruppe.ebene <= 1) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = if (eintrag.gruppe.ebene > 1) 16.dp else 0.dp),
                    )
                    is Eintrag.Position -> PositionKarte(eintrag, hervorgehoben = eintrag.position.id == angesprungen)
                }
            }
        }
    }
}

// --- Aufbau der Liste --------------------------------------------------------

/** Flache Liste, damit "antippen springt zur Position" einen festen Index hat. */
internal sealed class Eintrag(val schluessel: String) {
    class Summen(val angebot: Angebot) : Eintrag("summen")
    class Entscheidungen(val anzahl: Int) : Eintrag("entscheidungen")
    class Punkt(val punkt: AngebotPunkt, nr: Int) : Eintrag("punkt-$nr-${punkt.id}")
    class MehrPunkte(val anzahl: Int) : Eintrag("punkte-mehr")
    class Beantwortet(val liste: List<AngebotBeantwortet>) : Eintrag("beantwortet")
    class GruppenKopf(val gruppe: AngebotGruppe, nr: Int) : Eintrag("gruppe-$nr")
    class Position(val position: AngebotPosition, val ebene: Int, val index: Int, val anzahl: Int, nr: Int) :
        Eintrag("pos-$nr-${position.id}")
}

internal fun eintraegeAus(angebot: Angebot, allePunkte: Boolean = false): List<Eintrag> = buildList {
    add(Eintrag.Summen(angebot))

    val punkte = angebot.punkte
    if (punkte.isNotEmpty()) {
        add(Eintrag.Entscheidungen(punkte.size))
        val sichtbar = if (allePunkte || punkte.size <= PUNKTE_SICHTBAR) punkte else punkte.take(PUNKTE_SICHTBAR)
        sichtbar.forEachIndexed { i, punkt -> add(Eintrag.Punkt(punkt, i)) }
        if (sichtbar.size < punkte.size) add(Eintrag.MehrPunkte(punkte.size - sichtbar.size))
    }
    if (angebot.beantwortet.isNotEmpty()) add(Eintrag.Beantwortet(angebot.beantwortet))

    var laufend = 0
    angebot.gruppen.forEachIndexed { g, gruppe ->
        add(Eintrag.GruppenKopf(gruppe, g))
        gruppe.positionen.forEachIndexed { i, position ->
            add(Eintrag.Position(position, gruppe.ebene, i, gruppe.positionen.size, laufend++))
        }
    }
}

// --- Kopf --------------------------------------------------------------------

@Composable
private fun kopfzeile(angebot: Angebot): String = listOfNotNull(
    angebot.version?.let { stringResource(R.string.angebot_version, it) },
    angebot.bearbeiter?.takeIf { it.isNotBlank() },
    zeitpunkt(angebot.geaendert),
).joinToString(" · ")

/** Material-EK und Pauschalen-VK nebeneinander. */
@Composable
private fun SummenKarte(angebot: Angebot) {
    GruppenKarte(modifier = Modifier.padding(bottom = 8.dp)) {
        Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Summe(stringResource(R.string.angebot_ek_summe), betrag(angebot.ek_summe), Modifier.weight(1f))
            Summe(stringResource(R.string.angebot_pauschalen_summe), betrag(angebot.pauschalen_summe), Modifier.weight(1f))
        }
    }
}

@Composable
private fun Summe(beschriftung: String, wert: String, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(beschriftung, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(wert, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

// --- Entscheidungen ----------------------------------------------------------

/**
 * Ein Punkt als Karte: farbige Kopfzeile mit Art und Ort, Text fett, die
 * betroffenen Positionen als Kacheln, dann Optionen, eigene Antwort und
 * "Passt so". Der Entwurf im Feld bleibt stehen, wenn das Senden scheitert.
 */
@Composable
private fun PunktKarte(
    punkt: AngebotPunkt,
    sendet: Boolean,
    gesperrt: Boolean,
    beiPosition: (String) -> Unit,
    beiAntwort: (String) -> Unit,
) {
    val farbe = punktFarbe(punkt.art)
    var eigene by rememberSaveable(punkt.id) { mutableStateOf("") }
    GruppenKarte(modifier = Modifier.padding(bottom = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(farbe.copy(alpha = 0.14f))
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Punkt(farbe)
            Text(
                text = punktArt(punkt.art),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = farbe,
            )
            if (punkt.ort.isNotBlank()) {
                Text(
                    text = punkt.ort,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (sendet) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = punkt.text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            punkt.positionen.forEach { p -> PositionsKachel(p) { beiPosition(p.id) } }
            punkt.optionen.forEach { option ->
                OutlinedButton(
                    onClick = { beiAntwort(option) },
                    enabled = !gesperrt,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(option, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth()) }
            }
            if (punkt.frei) {
                OutlinedTextField(
                    value = eigene,
                    onValueChange = { eigene = it },
                    enabled = !gesperrt,
                    placeholder = { Text(stringResource(R.string.punkt_eigene_antwort)) },
                    trailingIcon = {
                        IconButton(
                            onClick = { beiAntwort(eigene) },
                            enabled = !gesperrt && eigene.isNotBlank(),
                        ) { Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.punkt_senden)) }
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            TextButton(
                onClick = { beiAntwort(ANTWORT_PASST) },
                enabled = !gesperrt,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(if (punkt.art == "hinweis") R.string.punkt_ausblenden else R.string.punkt_passt))
            }
        }
    }
}

/** Betroffene Position in der Karte: Nr. klein, "Menge × Name", Preis rechts. Tippen springt hin. */
@Composable
private fun PositionsKachel(p: PunktPosition, beiKlick: () -> Unit) {
    val farben = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = farben.surfaceContainerHighest,
        onClick = beiKlick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = listOf(p.nr, p.art_nr).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = farben.onSurfaceVariant,
                )
                Text("${menge(p.menge)} × ${p.name}", style = MaterialTheme.typography.bodyMedium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(betrag(p.gesamt), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                // `pauschale`: `gesamt` ist VK, sonst EK (`API.md` 4e).
                Text(
                    text = stringResource(if (p.pauschale) R.string.angebot_vk else R.string.angebot_ek),
                    style = MaterialTheme.typography.labelSmall,
                    color = farben.onSurfaceVariant,
                )
            }
        }
    }
}

/** `beantwortet`, eingeklappt, mit "Antworten einarbeiten". */
@Composable
private fun BeantwortetKarte(liste: List<AngebotBeantwortet>, beiEinarbeiten: () -> Unit) {
    var offen by rememberSaveable { mutableStateOf(false) }
    val farben = MaterialTheme.colorScheme
    GruppenKarte(modifier = Modifier.padding(bottom = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { offen = !offen }
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Text(
                text = pluralStringResource(R.plurals.punkte_beantwortet, liste.size, liste.size),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(if (offen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
        }
        if (offen) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
            ) {
                liste.forEach { b ->
                    Column {
                        Text(b.text, style = MaterialTheme.typography.bodyMedium, color = farben.onSurfaceVariant)
                        Text(
                            text = "→ " + if (b.antwort == ANTWORT_PASST) stringResource(R.string.punkt_passt) else b.antwort,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        FilledTonalButton(
            onClick = beiEinarbeiten,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
        ) { Text(stringResource(R.string.punkt_einarbeiten)) }
    }
}

@Composable
private fun punktArt(art: String): String = when (art) {
    "frage" -> stringResource(R.string.punkt_frage)
    "pruefen" -> stringResource(R.string.punkt_pruefen)
    "alternative" -> stringResource(R.string.punkt_alternative)
    "hinweis" -> stringResource(R.string.punkt_hinweis)
    else -> art.replaceFirstChar { it.uppercase() }
}

/** `frage`/`pruefen` rot, `alternative` gelb, `hinweis` und Unbekanntes grau. */
@Composable
private fun punktFarbe(art: String): Color = when (art) {
    "frage", "pruefen" -> MaterialTheme.colorScheme.error
    "alternative" -> markierungsFarbe("alternative")
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

// --- Positionen --------------------------------------------------------------

/**
 * Position als Karte: Nr. und Menge links, Name mit Umbruch in der Mitte,
 * Gesamtpreis rechts mit Einzelpreis klein darunter. Rechenwege (`info`)
 * eingeklappt als "Rechenweg ▸", andere Markierungen als farbige Zeile.
 */
@Composable
private fun PositionKarte(eintrag: Eintrag.Position, hervorgehoben: Boolean) {
    val p = eintrag.position
    val farben = MaterialTheme.colorScheme
    val markierung = p.markierung
    val istRechenweg = markierung?.art == "info"
    var rechenwegOffen by rememberSaveable(p.id) { mutableStateOf(false) }
    val hintergrund = when {
        hervorgehoben -> farben.primaryContainer
        markierung != null && !istRechenweg -> markierungsFarbe(markierung.art).copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    GruppenBlock(
        index = eintrag.index,
        anzahl = eintrag.anzahl,
        modifier = Modifier.padding(start = if (eintrag.ebene > 1) 16.dp else 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(hintergrund)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.width(56.dp)) {
                    Text(p.nr, style = MaterialTheme.typography.labelSmall, color = farben.onSurfaceVariant)
                    Text("${menge(p.menge)} ×", style = MaterialTheme.typography.labelLarge)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.bodyLarge)
                    if (p.art.isNotBlank()) {
                        Text(p.art, style = MaterialTheme.typography.labelSmall, color = farben.onSurfaceVariant)
                    }
                    if (p.bemerkung.isNotBlank()) {
                        Text(p.bemerkung, style = MaterialTheme.typography.bodySmall, color = farben.onSurfaceVariant)
                    }
                }
                // Pauschalen tragen einen Verkaufspreis (`API.md` 4e), alles andere den Einkauf.
                val einzel = if (p.pauschale) p.vk ?: p.ek else p.ek
                val gesamt = if (p.pauschale) p.vk_gesamt ?: p.vk ?: p.ek_gesamt else p.ek_gesamt
                Column(horizontalAlignment = Alignment.End) {
                    Text(betrag(gesamt), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    // Bei Menge 1 waere der Einzelpreis nur eine Wiederholung und kostet der Bezeichnung Platz.
                    val preisart = stringResource(if (p.pauschale) R.string.angebot_vk else R.string.angebot_ek)
                    Text(
                        text = if (zahl(p.menge)?.compareTo(java.math.BigDecimal.ONE) == 0) preisart
                        else stringResource(R.string.angebot_je, betrag(einzel), preisart),
                        style = MaterialTheme.typography.labelSmall,
                        color = farben.onSurfaceVariant,
                    )
                }
            }
            if (markierung != null && markierung.text.isNotBlank()) {
                if (istRechenweg) {
                    Text(
                        text = stringResource(R.string.angebot_rechenweg) + if (rechenwegOffen) " ▾" else " ▸",
                        style = MaterialTheme.typography.labelLarge,
                        color = farben.primary,
                        modifier = Modifier
                            .padding(start = 68.dp, top = 4.dp)
                            .clickable { rechenwegOffen = !rechenwegOffen }
                            .padding(vertical = 4.dp),
                    )
                    if (rechenwegOffen) {
                        Text(
                            text = markierung.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = farben.onSurfaceVariant,
                            modifier = Modifier.padding(start = 68.dp),
                        )
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(start = 68.dp, top = 4.dp),
                    ) {
                        Punkt(markierungsFarbe(markierung.art))
                        Text(markierung.text, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun Punkt(farbe: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(8.dp).background(farbe, CircleShape))
}

/** `pruefen` rot, `alternative` gelb, `info` blau (`API.md` 4e), Unbekanntes neutral. */
@Composable
internal fun markierungsFarbe(art: String): Color = when (art) {
    "pruefen" -> MaterialTheme.colorScheme.error
    "alternative" -> Color(0xFFE0A800)
    "info" -> Color(0xFF3E7BFA)
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** 502: Angebotsgenerator nicht erreichbar - Text vom Server und "Nochmal versuchen". */
@Composable
internal fun NochmalHinweis(text: String, beiNochmal: () -> Unit) {
    GruppenKarte(modifier = Modifier.padding(bottom = 8.dp)) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = beiNochmal, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.angebote_nochmal))
            }
        }
    }
}
