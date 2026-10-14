package de.chefassistent.app.ui.antwort

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Inhalt eines ```` ```diagramm ````-Blocks (`API.md` 1a, seit v1.14.0).
 *
 * Der Vertrag beschreibt das Format nur im Text, `openapi.json` kennt es nicht -
 * `antwort` ist dort ein String. Deshalb liegt es hier in der Oberflaeche und
 * nicht in `Vertrag.kt`. Die Feldnamen sind trotzdem die aus `API.md`.
 */
@Serializable
data class DiagrammDaten(
    /** `balken`, `linie` oder `torte`, siehe [DIAGRAMM_ARTEN]. */
    val art: String,
    val titel: String = "",
    val einheit: String? = null,
    val x: List<String>,
    val reihen: List<DiagrammReihe>,
    /** Nur `balken` mit mindestens zwei Reihen. */
    val gestapelt: Boolean = false,
) {
    val istGestapelt: Boolean get() = art == ART_BALKEN && gestapelt && reihen.size >= 2
}

@Serializable
data class DiagrammReihe(
    val name: String = "",
    /** Genau so lang wie `x`, `null` = Wert fehlt. */
    val werte: List<Double?>,
)

const val ART_BALKEN = "balken"
const val ART_LINIE = "linie"
const val ART_TORTE = "torte"

/** Die Arten aus `API.md` 1a. Was nicht dabei ist, wird nicht gezeichnet. */
val DIAGRAMM_ARTEN = listOf(ART_BALKEN, ART_LINIE, ART_TORTE)

private val json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

/**
 * Liest und prueft einen Block. Der Server prueft laut `API.md` schon selbst
 * und entfernt Ungueltiges - die Grenzen hier sind die zweite Sicherung, damit
 * ein kaputter Block nicht die Zeichnung abstuerzen laesst. `null` heisst:
 * nichts zeichnen, die Tabelle darunter traegt die Zahlen.
 */
fun diagrammLesen(roh: String): DiagrammDaten? {
    val daten = runCatching { json.decodeFromString<DiagrammDaten>(roh.trim()) }.getOrNull() ?: return null
    return daten.takeIf(::istGueltig)
}

internal fun istGueltig(d: DiagrammDaten): Boolean {
    val n = d.x.size
    if (d.reihen.isEmpty() || d.reihen.any { it.werte.size != n }) return false
    // Auch riesige Werte weg: Summen nahe 1e308 laufen beim Stapeln ueber, die
    // Skala wird NaN und die Zeichnung leer. Die Tabelle darunter traegt die Zahlen.
    if (d.reihen.any { r -> r.werte.any { it != null && (!it.isFinite() || kotlin.math.abs(it) > MAX_WERT) } }) return false
    return when (d.art) {
        ART_BALKEN -> n in 1..24 && d.reihen.size in 1..6 &&
            (!d.istGestapelt || d.reihen.all { r -> r.werte.all { (it ?: 0.0) >= 0.0 } })
        ART_LINIE -> n in 2..60 && d.reihen.size in 1..6
        ART_TORTE -> d.reihen.size == 1 && n in 2..6 && d.reihen[0].werte.all { it != null && it > 0.0 }
        else -> false
    }
}

/** y-Achse: beginnt bei 0 (bzw. reicht darunter bei negativen Werten), runde Schritte. */
data class Skala(val unten: Double, val oben: Double, val schritt: Double) {
    val striche: List<Double>
        get() {
            val anzahl = ((oben - unten) / schritt).toInt()
            return (0..anzahl).map { unten + it * schritt }
        }

    /** 0 = unten, 1 = oben. */
    fun anteil(wert: Double): Float = ((wert - unten) / (oben - unten)).toFloat()
}

fun skala(min: Double, max: Double, ziel: Int = 4): Skala {
    val lo = minOf(0.0, min)
    var hi = maxOf(0.0, max)
    if (hi - lo < 1e-12) hi = lo + 1.0
    val schritt = runderSchritt((hi - lo) / ziel)
    // Die Toleranz zeigt jeweils nach innen, sonst rutscht eine glatte 0 eine Stufe tiefer.
    return Skala(floor(lo / schritt + 1e-9) * schritt, ceil(hi / schritt - 1e-9) * schritt, schritt)
}

/** 1, 2, 2,5 oder 5 mal einer Zehnerpotenz. */
internal fun runderSchritt(roh: Double): Double {
    val potenz = 10.0.pow(floor(log10(roh)))
    val f = roh / potenz
    val rund = when {
        f <= 1.0 -> 1.0
        f <= 2.0 -> 2.0
        f <= 2.5 -> 2.5
        f <= 5.0 -> 5.0
        else -> 10.0
    }
    return rund * potenz
}

/** Grenzen der y-Achse aus allen Werten, bei gestapelten Balken aus den Summen. */
fun DiagrammDaten.wertebereich(): Pair<Double, Double> {
    if (istGestapelt) {
        val summen = x.indices.map { i -> reihen.sumOf { it.werte[i] ?: 0.0 } }
        return 0.0 to (summen.maxOrNull() ?: 0.0)
    }
    val alle = reihen.flatMap { it.werte }.filterNotNull()
    return (alle.minOrNull() ?: 0.0) to (alle.maxOrNull() ?: 0.0)
}

private val DEUTSCH = DecimalFormatSymbols(Locale.GERMANY)

/** Voller Wert fuer Infobox, Legende und Summe: "1.820,25". */
fun zahlLang(wert: Double): String =
    DecimalFormat("#,##0.##", DEUTSCH).apply { roundingMode = RoundingMode.HALF_UP }.format(wert)

/** Achsenbeschriftung: "25 Tsd.", "1,2 Mio.", kleine Werte wie [zahlLang]. */
fun zahlKurz(wert: Double): String {
    val betrag = abs(wert)
    val format = DecimalFormat("#,##0.#", DEUTSCH).apply { roundingMode = RoundingMode.HALF_UP }
    return when {
        betrag >= 1_000_000_000 -> "${format.format(wert / 1_000_000_000)} Mrd."
        betrag >= 1_000_000 -> "${format.format(wert / 1_000_000)} Mio."
        betrag >= 10_000 -> "${format.format(wert / 1_000)} Tsd."
        else -> zahlLang(wert)
    }
}

/** Wert mit Einheit: "1.820,25 €", "12 h". */
fun mitEinheit(text: String, einheit: String?): String =
    if (einheit.isNullOrBlank()) text else "$text $einheit"

/** Groesster Betrag, den ein Diagramm zeichnet - weit ueber jedem echten Umsatz. */
private const val MAX_WERT = 1e15
