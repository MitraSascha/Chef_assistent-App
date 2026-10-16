package de.chefassistent.app.monteur.ui.karte

import de.chefassistent.app.monteur.data.FELD_AUSWAHL
import de.chefassistent.app.monteur.data.FELD_JANEIN
import de.chefassistent.app.monteur.data.FELD_TEXT
import de.chefassistent.app.monteur.data.FELD_ZAHL
import de.chefassistent.app.monteur.data.ProtokollFeld
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Freitext hoechstens so lang (Vertrag). */
const val PROTOKOLL_MAX_ZEICHEN = 500

/** Diese Textfelder sind mehrzeilig (Auftrag 1.25, 1.). */
private val MEHRZEILIG = setOf("bemerkung", "maengel")

/** Wie die Karte das Feld zeichnet - Unbekanntes als Text. */
enum class FeldArt { TEXT, MEHRZEILIG, ZAHL, JANEIN, AUSWAHL }

fun feldArt(f: ProtokollFeld): FeldArt = when (f.t) {
    FELD_ZAHL -> FeldArt.ZAHL
    FELD_JANEIN -> FeldArt.JANEIN
    FELD_AUSWAHL -> if (f.o.isNullOrEmpty()) FeldArt.TEXT else FeldArt.AUSWAHL
    FELD_TEXT -> if (f.k in MEHRZEILIG) FeldArt.MEHRZEILIG else FeldArt.TEXT
    else -> FeldArt.TEXT
}

/** Leerer Text gilt als leer, damit „ “ kein Pflichtfeld fuellt. */
private fun leer(wert: String?) = wert.isNullOrBlank()

/** Aktueller Wert: lokal geaendert oder vom Server. */
fun aktuellerWert(f: ProtokollFeld, aenderungen: Map<String, String?>): String? =
    if (aenderungen.containsKey(f.k)) aenderungen[f.k] else f.wert

/** Beschriftungen der leeren Pflichtfelder, nach jeder Eingabe lokal neu geprueft. */
fun fehlendeFelder(felder: List<ProtokollFeld>, aenderungen: Map<String, String?>): List<String> =
    felder.filter { it.p && leer(aktuellerWert(it, aenderungen)) }.map { it.l }

/**
 * Neuer Wert fuer [f]: gleicht er dem vom Server, faellt die Aenderung weg - so geht
 * bei „Passt“ nur wirklich Geaendertes raus.
 */
fun aenderungSetzen(aenderungen: Map<String, String?>, f: ProtokollFeld, neu: String?): Map<String, String?> {
    val wert = neu?.takeIf { it.isNotBlank() }
    val server = f.wert?.takeIf { it.isNotBlank() }
    return if (wert == server) aenderungen - f.k else aenderungen + (f.k to wert)
}

/** `werte` fuer „Passt“: `null` leert ein Feld. Nichts geaendert → `null`. */
fun werteJson(aenderungen: Map<String, String?>): JsonObject? =
    aenderungen.takeIf { it.isNotEmpty() }
        ?.let { m -> JsonObject(m.mapValues { (_, v) -> v?.let(::JsonPrimitive) ?: JsonNull }) }

/** Ja/Nein und Auswahl: nochmal tippen = leer. */
fun umschalten(aktuell: String?, gewaehlt: String): String? =
    if (aktuell != null && aktuell.equals(gewaehlt, ignoreCase = true)) null else gewaehlt

/**
 * Zahlenfeld: Ziffern, ein Komma bzw. Punkt (wird Komma), Minus vorn. Was der Server
 * nicht versteht, normalisiert er ohnehin - hier geht es nur um saubere Eingabe.
 */
fun zahlEingabe(text: String): String {
    val sb = StringBuilder()
    var komma = false
    text.forEachIndexed { i, c ->
        when {
            c.isDigit() -> sb.append(c)
            (c == ',' || c == '.') && !komma -> {
                komma = true
                sb.append(',')
            }
            c == '-' && i == 0 -> sb.append(c)
        }
    }
    return sb.toString().take(20)
}
