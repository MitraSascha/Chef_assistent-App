package de.chefassistent.app.monteur.ui.karte

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.chefassistent.app.R
import de.chefassistent.app.data.ApiFehler
import de.chefassistent.app.monteur.MonteurApp
import de.chefassistent.app.monteur.data.Artikel
import de.chefassistent.app.monteur.data.ArtikelWahl
import de.chefassistent.app.monteur.data.Karte
import de.chefassistent.app.monteur.data.MonteurRepository
import de.chefassistent.app.monteur.ui.bericht.ArtikelSuche
import de.chefassistent.app.monteur.ui.bericht.KARTEN_FORM
import de.chefassistent.app.monteur.ui.bericht.MaterialDialog
import de.chefassistent.app.monteur.ui.bericht.mengeSchritt
import de.chefassistent.app.monteur.ui.bericht.zahlText
import de.chefassistent.app.ui.theme.gold
import de.chefassistent.app.ui.theme.link
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Karte „Material bestellen“ (Auftrag 1.23, 2.): dieselbe Materialliste wie im
 * Bericht, aber **lokal**. Fuer Bestellungen gibt es keinen PATCH, alles geht erst
 * mit „Passt“ raus. Deshalb haelt die Karte ihren Stand selbst ([BestellZeile])
 * und schreibt bei jeder Aenderung die vollstaendige Liste nach
 * [KartenEingabe.material] - die Huelle schickt sie dann mit.
 *
 * Erledigte Karten zeigen die Liste, wie der Server sie zurueckgibt, ohne
 * lokale Aenderungen (die sind dann schon eingearbeitet).
 */
@Composable
fun BestellungInhalt(karte: Karte, eingabe: KartenEingabe, app: MonteurApp, aktiv: Boolean) {
    val bestellung = karte.bestellung
    val material = bestellung?.material.orEmpty()
    // Neu aufsetzen, wenn eine andere Karte oder eine neue Fassung derselben Karte kommt.
    var zeilen by rememberSaveable(karte.id, material, stateSaver = ZEILEN_SAVER) {
        mutableStateOf(bestellStart(material))
    }
    val sichtbar = if (karte.istOffen) zeilen else bestellStart(material)

    // Eine Quelle: der Bildschirmstand. Auch nach Drehen oder Prozessende stimmt
    // so die Eingabe der Huelle mit dem ueberein, was der Monteur sieht.
    LaunchedEffect(karte.id, zeilen, material, karte.istOffen) {
        if (karte.istOffen) eingabe.material = bestellAenderungen(material, zeilen)
    }

    val scope = rememberCoroutineScope()
    val suche = remember(app) { BestellSuche(app.repository, scope) }
    var dialog by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.bs_material),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        val anzeige = bestellAnzeige(material, sichtbar)
        if (anzeige.isEmpty()) {
            Text(
                stringResource(R.string.bs_leer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        anzeige.forEachIndexed { pos, a ->
            BestellPosition(
                a = a,
                aktiv = aktiv && karte.istOffen,
                beiSchritt = { richtung ->
                    zeilen = mengeSetzen(zeilen, pos, mengeSchritt(zeilen[pos].menge, richtung))
                },
                beiWahl = { artikel -> zeilen = wahlSetzen(zeilen, pos, artikel) },
                beiEntfernen = { zeilen = zeileEntfernen(zeilen, pos) },
            )
        }
        if (aktiv && karte.istOffen) {
            OutlinedButton(
                onClick = { dialog = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.b_material_plus))
            }
        }
        bestellung?.hinweis?.takeIf { it.isNotBlank() }?.let { hinweis ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.bs_hinweis, hinweis), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }

    if (dialog) {
        MaterialDialog(
            suche = suche.stand,
            beiSuche = suche::suchen,
            beiSucheBeenden = suche::beenden,
            beiFertig = { neu, artikel ->
                dialog = false
                suche.beenden()
                zeilen = zeileAnhaengen(zeilen, neu, artikel)
            },
            beiAbbruch = {
                dialog = false
                suche.beenden()
            },
        )
    }
}

/**
 * Eine Position. Mit offener Rueckfrage gold markiert (Rand und Flaeche, nie
 * goldene Schrift), darunter die Frage, die Vorschlaege als Chips und „Frei
 * lassen“. Ist sie geloest, verschwindet die Markierung, die Chips bleiben
 * zum Umentscheiden stehen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BestellPosition(
    a: BestellAnzeige,
    aktiv: Boolean,
    beiSchritt: (Int) -> Unit,
    beiWahl: (Artikel?) -> Unit,
    beiEntfernen: () -> Unit,
) {
    val gold = MaterialTheme.colorScheme.gold
    val offen = a.rueckfrage != null
    Surface(
        shape = KARTEN_FORM,
        color = if (offen) gold.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainer,
        border = if (offen) BorderStroke(2.dp, gold) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).padding(top = 8.dp)) {
                    Text(a.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    val unter = buildList {
                        add(if (a.artikelNr != null) stringResource(R.string.b_artikel_nr, a.artikelNr) else stringResource(R.string.b_frei))
                        a.diktat?.let { add(stringResource(R.string.bs_diktiert, it)) }
                    }.joinToString(" · ")
                    Text(unter, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (a.neu || a.geaendert) {
                        Text(
                            stringResource(if (a.neu) R.string.bs_neu else R.string.bs_geaendert),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.link,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                if (aktiv) {
                    IconButton(onClick = beiEntfernen) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.bs_entfernen))
                    }
                }
            }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                MengenStepper("${zahlText(a.menge)} ${a.einheit}".trim(), aktiv, beiSchritt)
            }
            a.rueckfrage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp, end = 12.dp),
                )
            }
            if (a.vorschlaege.isNotEmpty() && (offen || a.wahl != ArtikelWahl.Behalten)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp, end = 12.dp),
                ) {
                    a.vorschlaege.forEach { v ->
                        FilterChip(
                            selected = (a.wahl as? ArtikelWahl.Waehlen)?.nr == v.nr,
                            onClick = { beiWahl(v) },
                            enabled = aktiv,
                            label = { Text(v.name) },
                            modifier = Modifier.heightIn(min = 48.dp),
                        )
                    }
                    FilterChip(
                        selected = a.wahl == ArtikelWahl.Frei,
                        onClick = { beiWahl(null) },
                        enabled = aktiv,
                        label = { Text(stringResource(R.string.b_frei_lassen)) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            } else if (offen) {
                // Rueckfrage ohne Vorschlaege: nur „Frei lassen“ loest sie.
                FilterChip(
                    selected = false,
                    onClick = { beiWahl(null) },
                    enabled = aktiv,
                    label = { Text(stringResource(R.string.b_frei_lassen)) },
                    modifier = Modifier.padding(top = 6.dp).heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun MengenStepper(wert: String, aktiv: Boolean, beiSchritt: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { beiSchritt(-1) }, enabled = aktiv, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.bs_weniger))
        }
        Text(
            wert,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 80.dp),
        )
        FilledTonalIconButton(onClick = { beiSchritt(1) }, enabled = aktiv, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.bs_mehr))
        }
    }
}

/**
 * Artikelsuche fuer „+ Material“ ohne eigenes ViewModel: ab 2 Zeichen, 300 ms
 * Pause, vorige Anfrage abbrechen - wie im Bericht (Auftrag 1.22). Ein 401 zeigt
 * nur den Text: Die Huelle merkt die Abmeldung spaetestens bei „Passt“, und die
 * lokalen Aenderungen sollen dabei nicht verloren gehen.
 */
private class BestellSuche(private val repository: MonteurRepository, private val scope: CoroutineScope) {
    var stand by mutableStateOf(ArtikelSuche())
        private set

    private var job: Job? = null

    fun suchen(eingabe: String) {
        job?.cancel()
        val q = eingabe.trim()
        if (q.length < 2) {
            stand = ArtikelSuche()
            return
        }
        stand = stand.copy(laeuft = true, fehler = null)
        job = scope.launch {
            delay(SUCHE_PAUSE_MS)
            stand = try {
                ArtikelSuche(suchtext = q, treffer = repository.artikelSuchen(q))
            } catch (fehler: ApiFehler) {
                ArtikelSuche(suchtext = q, fehler = fehler.anzeigetext)
            }
        }
    }

    fun beenden() {
        job?.cancel()
        stand = ArtikelSuche()
    }
}

private const val SUCHE_PAUSE_MS = 300L

private val ZEILEN_JSON = Json { ignoreUnknownKeys = true }

/** Lokaler Stand als JSON im Bundle - klein, eine Bestellung hat selten mehr als zehn Positionen. */
private val ZEILEN_SAVER = Saver<List<BestellZeile>, String>(
    save = { ZEILEN_JSON.encodeToString(it) },
    restore = { ZEILEN_JSON.decodeFromString<List<BestellZeile>>(it) },
)
