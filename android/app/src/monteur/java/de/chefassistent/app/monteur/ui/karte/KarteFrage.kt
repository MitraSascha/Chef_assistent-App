package de.chefassistent.app.monteur.ui.karte

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import de.chefassistent.app.monteur.data.AnleitungTreffer
import de.chefassistent.app.monteur.data.Karte

/** PLATZHALTER - Inhalt der Karte (Auftrag 1.23, 2.), wird ersetzt. */
@Composable
fun FrageInhalt(karte: Karte, verlauf: List<Karte>, beiAnleitung: (AnleitungTreffer) -> Unit) {
    Text(karte.text)
}
