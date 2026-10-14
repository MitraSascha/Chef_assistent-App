package de.chefassistent.app.ui.angebote

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DEUTSCH = DecimalFormatSymbols(Locale.GERMANY)
private val ZEIT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

/** Betrag aus dem Vertrag (`"9195.69"`) -> `9.195,69 €`. Unlesbares bleibt roh stehen. */
internal fun betrag(text: String?): String {
    val wert = zahl(text) ?: return text.orEmpty()
    val format = DecimalFormat("#,##0.00", DEUTSCH)
    return format.format(wert.setScale(2, RoundingMode.HALF_UP)) + " €"
}

/** Menge (`"2.50"`) -> `2,5`, ohne ueberfluessige Nullen. */
internal fun menge(text: String?): String {
    val wert = zahl(text) ?: return text.orEmpty()
    val format = DecimalFormat("#,##0.###", DEUTSCH)
    return format.format(wert)
}

/** Betraege und Mengen kommen als String mit Punkt - nie ueber `Double` rechnen. */
internal fun zahl(text: String?): BigDecimal? = text?.trim()?.takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()

/**
 * `geaendert` -> `TT.MM.JJJJ HH:MM`. Mit Offset wird in die Geraetezone
 * umgerechnet. Ohne Offset (so im Beispiel aus `API.md`) ist es schon Ortszeit.
 */
internal fun zeitpunkt(text: String?, zone: ZoneId = ZoneId.systemDefault()): String? {
    if (text.isNullOrBlank()) return null
    runCatching { return OffsetDateTime.parse(text).atZoneSameInstant(zone).format(ZEIT) }
    runCatching { return LocalDateTime.parse(text).format(ZEIT) }
    return text
}
