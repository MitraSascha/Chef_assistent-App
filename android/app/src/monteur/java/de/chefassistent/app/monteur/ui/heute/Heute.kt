package de.chefassistent.app.monteur.ui.heute

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.chefassistent.app.monteur.MonteurApp
import de.chefassistent.app.monteur.ui.bericht.BerichtStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** PLATZHALTER - Heute (Auftrag 1.22, Abschnitt 2). */
class HeuteViewModel(val app: MonteurApp) : ViewModel() {
    val abgemeldet: StateFlow<Boolean> = MutableStateFlow(false)

    companion object {
        fun factory(app: MonteurApp) = viewModelFactory { initializer { HeuteViewModel(app) } }
    }
}

@Composable
fun HeuteReiter(
    viewModel: HeuteViewModel,
    innen: PaddingValues,
    beiTermin: (pid: Int, termin: String?, titel: String?, entwurf: String?) -> Unit,
    beiBericht: (BerichtStart) -> Unit,
) {
    Text("Heute folgt")
}
