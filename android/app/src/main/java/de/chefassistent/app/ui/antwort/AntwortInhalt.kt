package de.chefassistent.app.ui.antwort

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.chefassistent.app.R
import de.chefassistent.app.ui.MarkdownText

/**
 * Antwort des Assistenten: Fliesstext ueber Markwon, Tabellen, Codebloecke und
 * Diagramme in Compose. [hintergrund] ist die Farbe der Blase - die Luecken
 * zwischen Saeulen und der Ring um Linienpunkte werden darin gezeichnet.
 */
@Composable
fun AntwortInhalt(markdown: String, hintergrund: Color, modifier: Modifier = Modifier) {
    val teile = remember(markdown) { abschnitte(markdown) }
    var vollbild by remember { mutableStateOf<Abschnitt?>(null) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        teile.forEach { teil ->
            when (teil) {
                is Abschnitt.Text -> MarkdownText(
                    markdown = teil.markdown,
                    farbe = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )

                is Abschnitt.Tabelle -> TabellenAnsicht(teil, beiVollbild = { vollbild = teil })

                is Abschnitt.Code -> CodeBlock(teil)

                is Abschnitt.Diagramm -> DiagrammAnsicht(
                    daten = teil.daten,
                    hintergrund = hintergrund,
                    modifier = Modifier.fillMaxWidth(),
                    beiVollbild = { vollbild = teil },
                )
            }
        }
    }

    when (val v = vollbild) {
        is Abschnitt.Tabelle -> Vollbild(stringResource(R.string.antwort_tabelle), { vollbild = null }) {
            TabellenAnsicht(v, vollbild = true)
        }

        is Abschnitt.Diagramm -> Vollbild(v.daten.titel, { vollbild = null }) {
            // Im Vollbild ohne Titelzeile (steht schon oben) und deutlich hoeher.
            DiagrammAnsicht(
                daten = v.daten.copy(titel = ""),
                hintergrund = MaterialTheme.colorScheme.surface,
                hoehe = 360.dp,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

        else -> Unit
    }
}
