package de.chefassistent.app.monteur.ui.einrichtung

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.chefassistent.app.monteur.MonteurApp

/** PLATZHALTER - Einrichtung per QR-Code (Auftrag 1.22, Abschnitt 1). */
class EinrichtungViewModel(val app: MonteurApp) : ViewModel() {
    companion object {
        fun factory(app: MonteurApp) = viewModelFactory { initializer { EinrichtungViewModel(app) } }
    }
}

@Composable
fun EinrichtungScreen(viewModel: EinrichtungViewModel, beiFertig: () -> Unit) {
    Text("Einrichtung folgt")
}
