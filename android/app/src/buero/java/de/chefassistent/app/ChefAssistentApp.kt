package de.chefassistent.app

import android.app.Application
import de.chefassistent.app.data.AssistentRepository
import de.chefassistent.app.data.Ausgangskorb
import de.chefassistent.app.data.Einstellungen
import de.chefassistent.app.data.Koerbe
import de.chefassistent.app.data.Medienkorb

/**
 * Haelt die wenigen langlebigen Objekte der App.
 *
 * Bewusst ohne DI-Bibliothek: es gibt genau zwei Abhaengigkeiten, und die
 * Oberflaeche kennt ohnehin nur [AssistentRepository].
 */
class ChefAssistentApp : Application(), Koerbe {

    override val repository: AssistentRepository by lazy {
        AssistentRepository(
            context = applicationContext,
            einstellungen = Einstellungen(applicationContext),
        )
    }

    /** Sprachnotizen, die auf Netz warten. */
    override val ausgangskorb: Ausgangskorb by lazy { Ausgangskorb(applicationContext) }

    /** Fotos und Videos, die auf den Versand warten. */
    override val medienkorb: Medienkorb by lazy { Medienkorb(applicationContext) }
}
