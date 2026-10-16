package de.chefassistent.app.monteur.ui.karte

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import de.chefassistent.app.monteur.MonteurApp
import de.chefassistent.app.monteur.data.Karte

/** PLATZHALTER - Inhalt der Karte (Auftrag 1.23, 2.), wird ersetzt. */
@Composable
fun BestellungInhalt(karte: Karte, eingabe: KartenEingabe, app: MonteurApp, aktiv: Boolean) {
    Text(karte.text)
}
