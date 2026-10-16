package de.chefassistent.app.data

/**
 * Was der [VersandArbeiter] von der Anwendung braucht. Die Buero-App
 * (`ChefAssistentApp`) stellt es bereit; die Monteur-App hat eigene Koerbe und
 * startet diesen Arbeiter nie.
 */
interface Koerbe {
    val repository: AssistentRepository
    val ausgangskorb: Ausgangskorb
    val medienkorb: Medienkorb
}
