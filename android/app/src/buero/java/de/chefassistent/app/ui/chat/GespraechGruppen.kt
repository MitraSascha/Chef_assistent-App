package de.chefassistent.app.ui.chat

import de.chefassistent.app.data.GespraechKurz
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Gruppen der Gespraechsliste (seit v1.21.0, API.md 1e). Die Gruppen bildet die
 * App selbst, nach `geaendert` in Ortszeit:
 * Angeheftet · Heute · Gestern · Letzte 7 Tage · Letzte 30 Tage · je Monat.
 */
data class GespraechGruppe(
    /** Stabil ueber Tage hinweg, zum Merken des Klappzustands: `angeheftet`, `heute`, `2026-09` … */
    val schluessel: String,
    val art: GruppenArt,
    /** Nur bei [GruppenArt.MONAT]: „September 2026“. */
    val monat: String? = null,
    val gespraeche: List<GespraechKurz>,
) {
    /** Monate starten eingeklappt, alle anderen offen. */
    val standardOffen: Boolean get() = art != GruppenArt.MONAT
}

enum class GruppenArt { ANGEHEFTET, HEUTE, GESTERN, SIEBEN_TAGE, DREISSIG_TAGE, MONAT, TREFFER }

private val DEUTSCH = Locale.GERMANY
private val UHRZEIT = DateTimeFormatter.ofPattern("HH:mm", DEUTSCH)

private fun ortszeit(iso: String, zone: ZoneId): ZonedDateTime? =
    runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(zone) }.getOrNull()

/**
 * Die Reihenfolge innerhalb einer Gruppe bleibt die des Servers (angeheftete
 * zuerst, dann `geaendert` absteigend). Unlesbare Zeiten landen bei „Heute“,
 * damit kein Gespraech verschwindet.
 */
fun gespraecheGruppieren(
    liste: List<GespraechKurz>,
    heute: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): List<GespraechGruppe> {
    val gruppen = linkedMapOf<String, Pair<GespraechGruppe, MutableList<GespraechKurz>>>()
    fun hinein(schluessel: String, art: GruppenArt, monat: String? = null, g: GespraechKurz) {
        gruppen.getOrPut(schluessel) {
            GespraechGruppe(schluessel, art, monat, emptyList()) to mutableListOf()
        }.second.add(g)
    }

    // Angeheftete zuerst, unabhaengig davon, wo der Server sie einsortiert.
    liste.filter { it.istAngeheftet }.forEach { hinein("angeheftet", GruppenArt.ANGEHEFTET, g = it) }

    // Die Zeitgruppen in fester Folge, Monate absteigend.
    val rest = liste.filterNot { it.istAngeheftet }
        .map { it to (ortszeit(it.geaendert, zone)?.toLocalDate() ?: heute) }
    val ordnung = listOf(GruppenArt.HEUTE, GruppenArt.GESTERN, GruppenArt.SIEBEN_TAGE, GruppenArt.DREISSIG_TAGE)
    for (art in ordnung) {
        rest.filter { zeitArt(it.second, heute) == art }.forEach { hinein(art.name.lowercase(), art, g = it.first) }
    }
    rest.filter { zeitArt(it.second, heute) == GruppenArt.MONAT }
        .sortedByDescending { it.second.withDayOfMonth(1) }
        .forEach { (g, tag) ->
            val schluessel = "%04d-%02d".format(tag.year, tag.monthValue)
            hinein(schluessel, GruppenArt.MONAT, monatsName(tag), g)
        }

    return gruppen.values.map { (gruppe, eintraege) -> gruppe.copy(gespraeche = eintraege) }
}

private fun zeitArt(tag: LocalDate, heute: LocalDate): GruppenArt {
    val tage = ChronoUnit.DAYS.between(tag, heute)
    return when {
        tage <= 0 -> GruppenArt.HEUTE
        tage == 1L -> GruppenArt.GESTERN
        tage <= 7 -> GruppenArt.SIEBEN_TAGE
        tage <= 30 -> GruppenArt.DREISSIG_TAGE
        else -> GruppenArt.MONAT
    }
}

/** „September 2026“. */
fun monatsName(tag: LocalDate): String =
    tag.month.getDisplayName(TextStyle.FULL_STANDALONE, DEUTSCH)
        .replaceFirstChar { it.uppercase(DEUTSCH) } + " " + tag.year

/**
 * Zweite Zeile unter dem Titel: Uhrzeit (heute/gestern), „Mo 12.10.“ (7 Tage),
 * sonst „28.9.“, vor dem laufenden Jahr mit Jahreszahl.
 */
fun zeitZeile(
    iso: String,
    heute: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): String? {
    val zeit = ortszeit(iso, zone) ?: return null
    val tag = zeit.toLocalDate()
    val tage = ChronoUnit.DAYS.between(tag, heute)
    return when {
        tage <= 1 -> zeit.format(UHRZEIT)
        tage <= 7 -> {
            val wochentag = tag.dayOfWeek.getDisplayName(TextStyle.SHORT, DEUTSCH).removeSuffix(".")
            "$wochentag ${tag.dayOfMonth}.${tag.monthValue}."
        }
        tag.year == heute.year -> "${tag.dayOfMonth}.${tag.monthValue}."
        else -> "${tag.dayOfMonth}.${tag.monthValue}.${tag.year}"
    }
}
