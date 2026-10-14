package de.chefassistent.app.ui.kalender

import de.chefassistent.app.data.KalenderTermin
import de.chefassistent.app.data.PERSON_ALLE
import de.chefassistent.app.data.PERSON_ICH
import de.chefassistent.app.data.Person
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

// Reine Logik des Kalenders (`API.md` 5a), ohne Compose - damit testbar.

private val UHRZEIT = DateTimeFormatter.ofPattern("HH:mm")
private val TAG_MONAT = DateTimeFormatter.ofPattern("d.M.")

/** Angezeigter Zeitraum: der Tag selbst oder Montag bis Sonntag der Woche. */
internal fun zeitraum(anker: LocalDate, woche: Boolean): Pair<LocalDate, LocalDate> =
    if (woche) {
        val montag = anker.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        montag to montag.plusDays(6)
    } else {
        anker to anker
    }

/** Zeitpunkt in Geraetezeit. Ganztaegig kommt nur ein Datum, dann Mitternacht. */
internal fun zeitpunkt(iso: String, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime? =
    runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(zone).toLocalDateTime() }.getOrNull()
        ?: runCatching { LocalDate.parse(iso).atStartOfDay() }.getOrNull()

/**
 * Die Tage, die ein Termin beruehrt. Endet er genau um Mitternacht, zaehlt der
 * Folgetag nicht mit. Unlesbares Ende = nur der erste Tag.
 *
 * Mit [von]/[bis] nur die Tage in diesem Ausschnitt: ein Termin ueber Monate
 * ("Baustelle Juli bis Dezember") soll in jeder Woche stehen, die er beruehrt -
 * frueher wurde ab dem Beginn gekappt, und er fehlte spaeter kommentarlos.
 */
internal fun tage(
    termin: KalenderTermin,
    zone: ZoneId = ZoneId.systemDefault(),
    von: LocalDate? = null,
    bis: LocalDate? = null,
): List<LocalDate> {
    val beginn = zeitpunkt(termin.beginn, zone) ?: return emptyList()
    var ende = zeitpunkt(termin.ende, zone)?.takeIf { !it.isBefore(beginn) } ?: beginn
    if (!termin.ganztag && ende.toLocalTime() == LocalTime.MIDNIGHT && ende.isAfter(beginn)) ende = ende.minusMinutes(1)
    val erster = maxOf(beginn.toLocalDate(), von ?: LocalDate.MIN)
    // Schutz gegen kaputte Daten: mehr als der groesste erlaubte Zeitraum ergibt keinen Sinn.
    val letzter = minOf(ende.toLocalDate(), bis ?: LocalDate.MAX, erster.plusDays(62))
    return generateSequence(erster) { it.plusDays(1) }.takeWhile { !it.isAfter(letzter) }.toList()
}

/** Ein Termin in der Tagesliste; mehrtaegige stehen an jedem Tag, den sie beruehren. */
internal data class TagesTermin(val tag: LocalDate, val termin: KalenderTermin) {
    val schluessel: String get() = "${termin.id}@$tag"
}

/**
 * Alle Tage von [von] bis [bis] mit ihren Terminen, auch leere. Doppelte IDs
 * werden zusammengelegt: ob der Server mehrtaegige Termine je Tag wiederholt
 * oder nur einmal schickt, ist dann egal.
 */
internal fun nachTagen(
    termine: List<KalenderTermin>,
    von: LocalDate,
    bis: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): Map<LocalDate, List<TagesTermin>> {
    val tageListe = generateSequence(von) { it.plusDays(1) }.takeWhile { !it.isAfter(bis) }.toList()
    val ergebnis = tageListe.associateWith { mutableListOf<TagesTermin>() }
    termine.distinctBy { it.id }.forEach { termin ->
        tage(termin, zone, von, bis).forEach { tag -> ergebnis[tag]?.add(TagesTermin(tag, termin)) }
    }
    // Ganztaegige oben, dann nach Beginn.
    return ergebnis.mapValues { (_, liste) ->
        liste.sortedWith(compareBy({ !it.termin.ganztag }, { zeitpunkt(it.termin.beginn, zone) }))
    }
}

/** Mehrtaegig = Beginn und Ende an verschiedenen Tagen (nach [tage]). */
internal fun istMehrtaegig(termin: KalenderTermin, zone: ZoneId = ZoneId.systemDefault()): Boolean =
    tage(termin, zone).size > 1

/** "14:30" bzw. bei mehrtaegigen "5.10. 02:01" (`API.md` 5a). */
internal fun zeitText(iso: String, mitDatum: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
    val zeit = zeitpunkt(iso, zone) ?: return ""
    return if (mitDatum) "${zeit.format(TAG_MONAT)} ${zeit.format(UHRZEIT)}" else zeit.format(UHRZEIT)
}

/** "Patrick van Dalen" -> "Patrick". */
internal fun vorname(name: String): String = name.trim().substringBefore(' ').ifBlank { name }

/** Filterschluessel einer Person: man selbst ist immer `ich`, damit auch die eigenen Termine kommen. */
internal fun Person.filter(): String = if (ich) PERSON_ICH else schluessel

/**
 * Gewaehlte Filterschluessel in fester Reihenfolge (ich, App-Nutzer, Monteure),
 * daraus die Farbe: Position in dieser Liste. Bei `alle` zaehlen alle Personen.
 */
internal fun farbReihenfolge(auswahl: List<String>, personen: List<Person>): List<String> {
    val reihenfolge = listOf(PERSON_ICH) + personen.filter { !it.ich }.map { it.schluessel }
    if (PERSON_ALLE in auswahl) return reihenfolge
    return reihenfolge.filter { it in auswahl } + auswahl.filter { it !in reihenfolge && it != PERSON_ALLE }
}

/**
 * Wem gehoert der Termin unter den gewaehlten Personen? Erste Uebereinstimmung
 * in [reihenfolge], `null` = niemand davon (dann neutral). Verglichen wird ueber
 * `hero_id` oder `app_benutzer`. Eigene Termine ohne HERO-Zuordnung gehoeren zu `ich`.
 */
internal fun farbIndex(termin: KalenderTermin, reihenfolge: List<String>, personen: List<Person>): Int? {
    reihenfolge.forEachIndexed { i, schluessel ->
        val person = if (schluessel == PERSON_ICH) personen.firstOrNull { it.ich } else personen.firstOrNull { it.schluessel == schluessel }
        val passt = termin.personen.any { tp ->
            (person != null && tp.hero_id != null && tp.hero_id == person.hero_id) ||
                (person?.app_benutzer != null && tp.app_benutzer == person.app_benutzer)
        }
        if (passt) return i
        if (schluessel == PERSON_ICH && person == null && termin.quelle == "eigen") return i
    }
    return null
}

/** Neue Auswahl nach Tipp auf einen Chip. Nie leer, `alle` schliesst den Rest aus. */
internal fun auswahlUmschalten(auswahl: List<String>, schluessel: String): List<String> {
    if (schluessel == PERSON_ALLE) return if (PERSON_ALLE in auswahl) listOf(PERSON_ICH) else listOf(PERSON_ALLE)
    val ohneAlle = auswahl - PERSON_ALLE
    val neu = if (schluessel in ohneAlle) ohneAlle - schluessel else ohneAlle + schluessel
    return neu.ifEmpty { listOf(PERSON_ICH) }
}
