package de.chefassistent.app.monteur.ui.karte

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.chefassistent.app.R
import de.chefassistent.app.monteur.data.Karte

/**
 * Karte „Info ans Buero“ (Auftrag 1.23, 2.): Art als Chip (nur Anzeige), der von
 * der KI formulierte Text **bearbeitbar** - genau so landet er im HERO-
 * Projektverlauf, darum soll der Monteur ihn vor „Passt“ korrigieren koennen.
 * Nur ein geaenderter Text geht als [KartenEingabe.text] mit.
 *
 * [fotos] sind die lokal aufgenommenen Bilder; der Server meldet nur die Anzahl
 * ([Karte.fotos]), was darueber hinausgeht, steht als „+ N Fotos“ da.
 */
@Composable
fun MeldungInhalt(karte: Karte, eingabe: KartenEingabe, fotos: List<String>, aktiv: Boolean) {
    val meldung = karte.meldung
    val original = meldung?.text.orEmpty()
    var text by rememberSaveable(karte.id, original) { mutableStateOf(eingabe.text ?: original) }
    val bearbeitbar = aktiv && karte.istOffen
    // Der Bildschirmstand ist die Quelle - so stimmt die Eingabe auch nach Prozessende.
    LaunchedEffect(karte.id, text, original, karte.istOffen) {
        if (karte.istOffen) eingabe.text = meldungText(original, text)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        meldung?.let { m ->
            // Nur Anzeige, darum kein Chip-Knopf: ein deaktivierter Chip saehe ausgegraut aus.
            val art = meldungArtText(m.art)?.let { stringResource(it) } ?: m.art
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Text(
                    art,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        OutlinedTextField(
            // Erledigte Karten zeigen, was der Server gespeichert hat.
            value = if (karte.istOffen) text else original,
            onValueChange = { neu -> text = neu.take(MELDUNG_MAX_ZEICHEN) },
            enabled = bearbeitbar,
            label = { Text(stringResource(R.string.bs_text)) },
            supportingText = if (bearbeitbar) {
                { Text(stringResource(R.string.bs_zeichen, text.length, MELDUNG_MAX_ZEICHEN)) }
            } else null,
            minLines = 3,
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        FotoReihe(fotos)
        val weitere = karte.fotos - fotos.size
        if (weitere > 0) {
            Text(
                pluralStringResource(R.plurals.bs_fotos_mehr, weitere, weitere),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val projekt = projektText(karte.projekt)
        Text(
            if (projekt.isEmpty()) stringResource(R.string.bs_verlauf_ohne_projekt)
            else stringResource(R.string.bs_verlauf, projekt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
