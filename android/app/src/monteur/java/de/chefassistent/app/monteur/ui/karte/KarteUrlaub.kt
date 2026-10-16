package de.chefassistent.app.monteur.ui.karte

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.chefassistent.app.R
import de.chefassistent.app.monteur.data.AbwVorschlag
import de.chefassistent.app.monteur.data.Karte
import de.chefassistent.app.monteur.ui.urlaub.Schalter
import de.chefassistent.app.monteur.ui.urlaub.ZeitraumAuswahl
import de.chefassistent.app.monteur.ui.urlaub.artText
import de.chefassistent.app.monteur.ui.urlaub.tageMitEinheit
import de.chefassistent.app.monteur.ui.urlaub.tageSchaetzen
import de.chefassistent.app.monteur.ui.urlaub.zeitraumText

/** Laengster Kommentar, den die Karte annimmt - wie im Antragsformular des Reiters Urlaub. */
private const val KOMMENTAR_MAX = 500

/**
 * Karte „Urlaub“ (Auftrag 1.23, 2.): Art, Zeitraum, halbe Tage, Kommentar. Jede
 * Aenderung schreibt den **vollstaendigen** Antrag nach [KartenEingabe.abwesenheit],
 * weil `…/passt` ihn als Ganzes ersetzt. Die Art bleibt reine Anzeige: aus „Urlaub“
 * wird hier nicht „Krank“ - dafuer ist „Verwerfen“ und neu sagen der klarere Weg.
 */
@Composable
fun UrlaubInhalt(karte: Karte, eingabe: KartenEingabe, aktiv: Boolean) {
    val antrag = eingabe.abwesenheit ?: karte.abwesenheit
    var auswahl by rememberSaveable(karte.id) { mutableStateOf(false) }

    if (antrag == null) {
        Text(
            stringResource(R.string.kz_urlaub_leer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val aendern: (AbwVorschlag) -> Unit = { eingabe.abwesenheit = abwesenheitNormal(it) }
    val halb = stringResource(R.string.u_halber_tag_marke)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.kz_art), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(artText(antrag.art), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            Text(
                stringResource(R.string.kz_zeitraum),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = aktiv) { auswahl = true }
                    .heightIn(min = 48.dp),
            ) {
                Icon(Icons.Outlined.DateRange, null, modifier = Modifier.size(20.dp))
                Text(
                    zeitraumText(antrag.von, antrag.bis, antrag.halber_tag_anfang, antrag.halber_tag_ende, halb),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
                if (aktiv) {
                    Icon(
                        Icons.Outlined.Edit,
                        stringResource(R.string.kz_zeitraum_aendern),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            tageSchaetzen(antrag.von, antrag.bis, antrag.halber_tag_anfang, antrag.halber_tag_ende)?.let { tage ->
                Text(
                    stringResource(R.string.kz_tage_schaetzung, tageMitEinheit(tage)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Schalter(stringResource(R.string.u_halb_anfang), antrag.halber_tag_anfang, aktiv) { an ->
                aendern(antrag.copy(halber_tag_anfang = an))
            }
            Schalter(stringResource(R.string.u_halb_ende), antrag.halber_tag_ende, aktiv) { an ->
                aendern(antrag.copy(halber_tag_ende = an))
            }

            if (aktiv) {
                OutlinedTextField(
                    value = antrag.kommentar,
                    onValueChange = { aendern(antrag.copy(kommentar = it.take(KOMMENTAR_MAX))) },
                    label = { Text(stringResource(R.string.kz_kommentar)) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            } else if (antrag.kommentar.isNotBlank()) {
                Text(antrag.kommentar, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }

    if (aktiv && auswahl) {
        ZeitraumAuswahl(
            von = antrag.von,
            bis = antrag.bis,
            beiFertig = { von, bis -> aendern(antrag.copy(von = von, bis = bis)) },
            beiSchliessen = { auswahl = false },
        )
    }
}

/**
 * Antrag so, wie er bei „Passt“ rausgeht: Datum ohne Uhrzeit (`YYYY-MM-DD`), wie
 * der Vertrag es beim Beantragen verlangt - auch wenn die KI eine Uhrzeit mitlieferte.
 */
fun abwesenheitNormal(antrag: AbwVorschlag): AbwVorschlag =
    antrag.copy(von = antrag.von.trim().take(10), bis = antrag.bis.trim().take(10))
