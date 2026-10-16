package de.chefassistent.app.monteur.ui.zeiten

import de.chefassistent.app.monteur.data.ART_ARBEIT
import de.chefassistent.app.monteur.data.MonteurTag
import de.chefassistent.app.monteur.data.TagTermin
import de.chefassistent.app.monteur.data.ZEIT_ARTEN
import de.chefassistent.app.monteur.data.ZeitAbschnitt
import de.chefassistent.app.monteur.data.ZeitNeu
import de.chefassistent.app.monteur.data.Zeiteintrag
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.abs

/*
 * Reine Logik des Reiters „Zeiten“ - ohne Android, damit sie in Unit-Tests laeuft.
 * Zeiten: der Server liefert Ortszeit ohne Zone (`YYYY-MM-DDTHH:MM`), an manchen
 * Stellen aber auch mit Offset. Beides wird in die Geraetezone gebracht; geschickt
 * wird immer Ortszeit ohne Zone (Vertrag `ZeitNeu`).
 */

/** Ab dieser Uhrzeit erinnert der Banner „Tag bestätigen“ (Auftrag 1.22, Abschnitt 4). */
val TAG_BESTAETIGEN_AB: LocalTime = LocalTime.of(15, 0)

/** Ortszeit-Format, das der Server bei `POST /v1/monteur/zeiten` erwartet. */
private val ORTSZEIT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

val UHRZEIT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Minuten als „7:30 h“. Mit [vorzeichen] (Stundenkonto) „+1:20 h“ bzw. „−0:45 h“
 * mit echtem Minuszeichen, damit es neben dem Plus gleich breit wirkt.
 */
fun minutenText(minuten: Int, vorzeichen: Boolean = false): String {
    val betrag = abs(minuten.toLong())
    val text = "%d:%02d h".format(betrag / 60, betrag % 60)
    return when {
        !vorzeichen -> if (minuten < 0) "−$text" else text
        minuten < 0 -> "−$text"
        else -> "+$text"
    }
}

/** Laufende Dauer der Stempeluhr als „1:05:09“ (sekuendlich aktualisiert). */
fun dauerText(beginn: LocalDateTime, jetzt: LocalDateTime): String {
    val sekunden = Duration.between(beginn, jetzt).seconds.coerceAtLeast(0)
    return "%d:%02d:%02d".format(sekunden / 3600, (sekunden / 60) % 60, sekunden % 60)
}

/**
 * Zeitangabe des Servers lesen: Ortszeit ohne Zone (`2026-10-20T07:30`, auch mit
 * Sekunden oder Leerzeichen) bleibt, wie sie ist; mit Offset oder `Z` wird sie in
 * [zone] umgerechnet. `null`, wenn nichts Lesbares drinsteht.
 */
fun zeitLesen(text: String?, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime? {
    val t = text?.trim()?.replace(' ', 'T')?.takeIf { it.isNotEmpty() } ?: return null
    try {
        return OffsetDateTime.parse(t).atZoneSameInstant(zone).toLocalDateTime()
    } catch (_: DateTimeParseException) {
    }
    return try {
        LocalDateTime.parse(t)
    } catch (_: DateTimeParseException) {
        null
    }
}

/** `HH:MM` (auch `7:30`) lesen, `null` bei Unsinn. */
fun uhrzeitLesen(text: String?): LocalTime? {
    val teile = text?.trim()?.split(':') ?: return null
    if (teile.size !in 2..3) return null
    val stunde = teile[0].toIntOrNull() ?: return null
    val minute = teile[1].toIntOrNull() ?: return null
    if (stunde !in 0..23 || minute !in 0..59) return null
    return LocalTime.of(stunde, minute)
}

fun ortszeit(zeit: LocalDateTime): String = zeit.format(ORTSZEIT)

/**
 * Beginn und Ende als Ortszeit am Tag [datum]. Liegt [bis] vor [von], endet der
 * Abschnitt am Folgetag (Notdienst bis nach Mitternacht). Gleiche Uhrzeiten oder
 * unlesbare Angaben ergeben `null` - so etwas soll gar nicht erst rausgehen.
 */
fun zeitraum(datum: LocalDate, von: LocalTime, bis: LocalTime): Pair<String, String>? {
    if (von == bis) return null
    val beginn = datum.atTime(von)
    val ende = if (bis.isAfter(von)) datum.atTime(bis) else datum.plusDays(1).atTime(bis)
    return ortszeit(beginn) to ortszeit(ende)
}

/** Wie [zeitraum], aber aus `YYYY-MM-DD` und `HH:MM`, wie sie in `Verstanden` stehen. */
fun zeitraum(datum: String, von: String, bis: String): Pair<String, String>? {
    val tag = runCatching { LocalDate.parse(datum.trim()) }.getOrNull() ?: return null
    return zeitraum(tag, uhrzeitLesen(von) ?: return null, uhrzeitLesen(bis) ?: return null)
}

/** Ob ein Abschnitt am Folgetag endet (fuer den Hinweis im Formular). */
fun endetAmFolgetag(von: LocalTime, bis: LocalTime): Boolean = bis.isBefore(von)

/**
 * Vorschlaege aus `POST /v1/monteur/verstehen` zum Buchen. [ids] liefert je
 * Vorschlag dieselbe `anfrage_id` wie beim ersten Versuch - so legt ein erneutes
 * „Übernehmen“ nach einem halben Erfolg keinen zweiten Eintrag an. Unbekannte
 * Arten werden „arbeit“: der Vertrag nimmt beim Anlegen nur die bekannten.
 */
fun vorschlagZuZeit(abschnitt: ZeitAbschnitt, datum: String, anfrageId: String): ZeitNeu? {
    val (beginn, ende) = zeitraum(datum, abschnitt.beginn, abschnitt.ende) ?: return null
    return ZeitNeu(
        beginn = beginn,
        ende = ende,
        art = artFuerBuchung(abschnitt.art),
        anfrage_id = anfrageId,
    )
}

fun artFuerBuchung(art: String): String = art.takeIf { it in ZEIT_ARTEN } ?: ART_ARBEIT

/**
 * Wann der Banner „Tag bestätigen“ erscheint (Auftrag 1.22, Abschnitt 4):
 * - eingereichte Tage nie, kuenftige Tage nie,
 * - heute ab 15 Uhr oder sobald der letzte Termin des Tages vorbei ist,
 * - vergangene Tage, sobald dort etwas eingetragen ist - sonst liesse sich ein
 *   vergessener Tag gar nicht mehr einreichen.
 */
fun tagBestaetigenZeigen(
    datum: LocalDate,
    jetzt: LocalDateTime,
    eingereicht: Boolean,
    anzahlEintraege: Int,
    letzterTerminEnde: LocalDateTime?,
): Boolean {
    if (eingereicht) return false
    val heute = jetzt.toLocalDate()
    return when {
        datum.isAfter(heute) -> false
        datum.isBefore(heute) -> anzahlEintraege > 0
        !jetzt.toLocalTime().isBefore(TAG_BESTAETIGEN_AB) -> true
        letzterTerminEnde != null -> !jetzt.isBefore(letzterTerminEnde)
        else -> false
    }
}

/** Ende des letzten Termins mit Uhrzeit an [datum]; ganztaegige zaehlen nicht. */
fun letzterTerminEnde(termine: List<TagTermin>, datum: LocalDate, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime? =
    termine.asSequence()
        .filter { !it.ganztag }
        .mapNotNull { zeitLesen(it.ende, zone) }
        .filter { it.toLocalDate() == datum }
        .maxOrNull()

/** Termine des Tages [datum] aus `GET /v1/monteur/tage`. */
fun termineAm(tage: List<MonteurTag>, datum: LocalDate): List<TagTermin> =
    tage.filter { it.datum == datum.toString() }.flatMap { it.termine }

/** Projektnummern der Termine, fuer die Vorschlagschips; ohne Doppelte, in Terminfolge. */
fun projektNummern(termine: List<TagTermin>): List<Pair<String, String>> =
    termine.mapNotNull { t -> t.projekt?.takeIf { it.nr.isNotBlank() }?.let { it.nr to it.kunde.ifBlank { t.titel } } }
        .distinctBy { it.first }

/** Was angezeigt wird: geloeschte Eintraege nicht, nach Beginn sortiert. */
fun sichtbareEintraege(eintraege: List<Zeiteintrag>): List<Zeiteintrag> =
    eintraege.filter { it.status != STATUS_GELOESCHT }
        .sortedBy { zeitLesen(it.beginn) ?: LocalDateTime.MAX }

const val STATUS_GELOESCHT = "geloescht"

/** „07:30–16:00“, Abschnitte ueber Mitternacht ebenso; Unlesbares roh. */
fun spanneText(beginn: String, ende: String): String {
    val b = zeitLesen(beginn)?.format(UHRZEIT) ?: beginn
    val e = zeitLesen(ende)?.format(UHRZEIT) ?: ende
    return "$b–$e"
}

/**
 * Zeitraum fuer das Stundenkonto des Monats von [datum]: Monatsanfang bis heute,
 * fuer vergangene Monate bis Monatsende. Sonst zaehlte das Soll kuenftiger Tage
 * schon mit und der Saldo stuende den ganzen Monat im Minus.
 */
fun kontoZeitraum(datum: LocalDate, heute: LocalDate): Pair<LocalDate, LocalDate> {
    val anfang = datum.withDayOfMonth(1)
    val ende = datum.withDayOfMonth(datum.lengthOfMonth())
    val bis = if (!heute.isBefore(anfang) && !heute.isAfter(ende)) heute else ende
    return anfang to bis
}
