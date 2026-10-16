package de.chefassistent.app.monteur.ui.projekt

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.chefassistent.app.monteur.MonteurApp

/** PLATZHALTER - Projektmappe (Auftrag 1.22, Abschnitt 2a). */
class ProjektViewModel(val app: MonteurApp, val projektId: Int) : ViewModel() {
    companion object {
        fun factory(app: MonteurApp, projektId: Int) = viewModelFactory { initializer { ProjektViewModel(app, projektId) } }
    }
}

@Composable
fun ProjektScreen(
    viewModel: ProjektViewModel,
    entwurfId: String?,
    beiZurueck: () -> Unit,
    beiAbmeldung: () -> Unit,
    beiDatei: (uuid: String, name: String) -> Unit,
    beiBericht: (berichtId: String?, projektNr: String?) -> Unit,
) {
    Text("Projektmappe folgt")
}
