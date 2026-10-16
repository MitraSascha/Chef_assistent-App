package de.chefassistent.app.monteur.ui.bericht

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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

/**
 * Womit der Bericht-Bildschirm startet (Route `bericht?…` in MonteurActivity).
 * - [berichtId]: vorhandenen Bericht laden (Entwurf weiterbearbeiten bzw. fertigen zeigen).
 * - [aufnahme]: Pfad einer frischen Aufnahme vom Mikrofon auf „Heute“ - sofort senden.
 * - sonst: Eingabe (Mikrofon oder Text) fuer [terminId] bzw. [projektNr], ohne beides findet der Server den Termin.
 */
data class BerichtStart(
    val berichtId: String? = null,
    val terminId: String? = null,
    val projektNr: String? = null,
    /** Fuer die Anzeige, z. B. „SAN-1243 · Böse“. */
    val titel: String? = null,
    val aufnahme: String? = null,
)

/** PLATZHALTER - wird durch die echte Umsetzung ersetzt (Auftrag 1.22, Abschnitt 3). */
class BerichtViewModel(val app: MonteurApp, val start: BerichtStart) : ViewModel() {
    val abgemeldet: StateFlow<Boolean> = MutableStateFlow(false)

    companion object {
        fun factory(app: MonteurApp, start: BerichtStart) = viewModelFactory {
            initializer { BerichtViewModel(app, start) }
        }
    }
}

@Composable
fun BerichtScreen(viewModel: BerichtViewModel, beiZurueck: () -> Unit, beiAbmeldung: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Bericht folgt") }
}
