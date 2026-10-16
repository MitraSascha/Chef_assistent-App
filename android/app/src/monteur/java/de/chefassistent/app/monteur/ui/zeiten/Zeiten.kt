package de.chefassistent.app.monteur.ui.zeiten

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.chefassistent.app.monteur.MonteurApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** PLATZHALTER - wird durch die echte Umsetzung ersetzt (Auftrag 1.22, Abschnitt 4). */
class ZeitenViewModel(val app: MonteurApp) : ViewModel() {
    val abgemeldet: StateFlow<Boolean> = MutableStateFlow(false)

    /** Vom schwebenden Mikrofon des Reiters: Aufnahme -> `POST /v1/monteur/verstehen` -> Vorschlaege. */
    fun verstehen(aufnahme: File?, text: String?) {
        aufnahme?.delete()
    }

    companion object {
        fun factory(app: MonteurApp) = viewModelFactory { initializer { ZeitenViewModel(app) } }
    }
}

/** Inhalt des Reiters „Zeiten“. [innen] haelt Platz fuer Kopf, Mikrofon und untere Leiste frei. */
@Composable
fun ZeitenReiter(viewModel: ZeitenViewModel, innen: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(innen), contentAlignment = Alignment.Center) { Text("Zeiten folgen") }
}
