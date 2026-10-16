package de.chefassistent.app.monteur

import android.app.Application
import de.chefassistent.app.data.AssistentRepository
import de.chefassistent.app.data.Einstellungen
import de.chefassistent.app.monteur.data.MonteurKorb
import de.chefassistent.app.monteur.data.MonteurRepository

/**
 * Anwendung der Monteur-App. Teilt Netz, Token-Speicher und Fehleruebersetzung
 * mit der Buero-App, hat aber eigene Oberflaeche und einen eigenen Ausgangskorb.
 */
class MonteurApp : Application() {

    /** Eine Instanz fuer beide Repositories: dasselbe Token, dieselbe Adresse. */
    val einstellungen: Einstellungen by lazy { Einstellungen(applicationContext) }

    val repository: MonteurRepository by lazy { MonteurRepository(applicationContext, einstellungen) }

    /** Berichte, Zeiten und Antraege, die auf Netz warten. Nur eine Instanz. */
    val korb: MonteurKorb by lazy { MonteurKorb(applicationContext) }

    /** Nur fuer die versteckte Anmeldung mit Buero-Zugang (Passwort + Code) zum Testen. */
    val bueroZugang: AssistentRepository by lazy { AssistentRepository(applicationContext, einstellungen) }
}
