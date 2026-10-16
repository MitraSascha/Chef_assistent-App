package de.chefassistent.app.monteur

import android.app.Application
import de.chefassistent.app.data.AssistentRepository
import de.chefassistent.app.data.Einstellungen

/**
 * Anwendung der Monteur-App. Teilt Netz, Token-Speicher und Datenschicht mit
 * der Buero-App, hat aber eigene Oberflaeche und einen eigenen Ausgangskorb.
 */
class MonteurApp : Application() {

    val repository: AssistentRepository by lazy {
        AssistentRepository(
            context = applicationContext,
            einstellungen = Einstellungen(applicationContext),
        )
    }
}
