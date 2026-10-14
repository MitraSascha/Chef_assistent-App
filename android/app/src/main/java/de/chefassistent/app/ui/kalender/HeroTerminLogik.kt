package de.chefassistent.app.ui.kalender

import de.chefassistent.app.data.HeroTerminErgebnis
import de.chefassistent.app.data.HeroTerminNeu
import de.chefassistent.app.data.HeroTerminVerschieben
import de.chefassistent.app.data.KalenderTermin
import de.chefassistent.app.data.PERSON_ICH
import de.chefassistent.app.data.Personen
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// HERO-Termine anlegen und verschieben (`API.md` 5b, seit v1.18.0), ohne Compose - damit testbar.

/** Ortszeit ohne Zone, wie der Server sie erwartet. */
private val ORTSZEIT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

/** Warum "Pruefen" noch nicht geht. `null` = alles da. */
enum class FormularLuecke { PERSONEN, TITEL, KATEGORIE, ENDE_VOR_BEGINN, ZU_LANG }

/**
 * Das Formular fuer "＋ HERO-Termin" bzw. "Verschieben". Beim Verschieben ist
 * [verschieben] gesetzt, Personen, Titel, Kategorie, Projekt und Notiz bleiben
 * dann unbenutzt.
 *
 * [geprueft] haelt die Antwort auf `nur_pruefen=true`. Jede Aenderung am Formular
 * loescht sie ([aendern]), damit nie etwas anderes eingetragen wird als geprueft.
 */
data class HeroFormular(
    val verschieben: KalenderTermin? = null,
    val personen: List<String> = emptyList(),
    val titel: String = "",
    val kategorieId: Long? = null,
    val datum: LocalDate,
    val von: LocalTime,
    /** Letzter Tag; bei eintaegigen Terminen gleich [datum]. */
    val bisDatum: LocalDate,
    val bis: LocalTime,
    val ganztag: Boolean = false,
    val projekt: String = "",
    val notiz: String = "",
    val geprueft: HeroTerminErgebnis? = null,
    val laeuft: Boolean = false,
    /** `detail` des Servers, im Formular gezeigt. */
    val fehler: String? = null,
) {
    val beginn: LocalDateTime get() = datum.atTime(von)
    val ende: LocalDateTime get() = bisDatum.atTime(bis)

    /** Mehrtaegig ohne ganztaegig: dann braucht das Formular ein eigenes "Bis Datum". */
    val mehrtaegig: Boolean get() = bisDatum != datum

    val luecke: FormularLuecke?
        get() = when {
            verschieben == null && personen.isEmpty() -> FormularLuecke.PERSONEN
            verschieben == null && titel.isBlank() -> FormularLuecke.TITEL
            verschieben == null && kategorieId == null -> FormularLuecke.KATEGORIE
            ganztag && bisDatum.isBefore(datum) -> FormularLuecke.ENDE_VOR_BEGINN
            ganztag && ChronoUnit.DAYS.between(datum, bisDatum) + 1 > HeroTerminNeu.GANZTAG_TAGE_MAX -> FormularLuecke.ZU_LANG
            !ganztag && !ende.isAfter(beginn) -> FormularLuecke.ENDE_VOR_BEGINN
            else -> null
        }

    /** Aenderung durch den Nutzer: Pruefung und Fehler verfallen. */
    fun aendern(neu: HeroFormular.() -> HeroFormular): HeroFormular = neu().copy(geprueft = null, fehler = null)

    /** Neues Datum; das Ende wandert mit, die Dauer in Tagen bleibt. */
    fun mitDatum(tag: LocalDate): HeroFormular =
        copy(datum = tag, bisDatum = bisDatum.plusDays(ChronoUnit.DAYS.between(datum, tag)))

    /** Neuer Beginn; das Ende wandert mit, die Dauer bleibt (10:00 -> 10:00-11:00). */
    fun mitVon(zeit: LocalTime): HeroFormular {
        val neuesEnde = ende.plus(ChronoUnit.MINUTES.between(beginn, datum.atTime(zeit)), ChronoUnit.MINUTES)
        return copy(von = zeit, bisDatum = neuesEnde.toLocalDate(), bis = neuesEnde.toLocalTime())
    }

    /** Neues Ende am selben Tag wie bisher. */
    fun mitBis(zeit: LocalTime): HeroFormular = copy(bis = zeit)

    fun mitBisDatum(tag: LocalDate): HeroFormular = copy(bisDatum = tag)

    /** Ganztaegig ein und aus. Aus: wieder ein Tag, eine Stunde ab [von]. */
    fun mitGanztag(an: Boolean): HeroFormular =
        if (an) copy(ganztag = true) else copy(ganztag = false, bisDatum = datum, bis = von.plusHours(1)).let {
            // Kurz vor Mitternacht: eine Stunde ginge in den Folgetag.
            if (it.bis.isBefore(it.von)) it.copy(bisDatum = datum.plusDays(1)) else it
        }

    fun personUmschalten(schluessel: String): HeroFormular =
        copy(personen = if (schluessel in personen) personen - schluessel else personen + schluessel)

    fun anfrageNeu(): HeroTerminNeu = HeroTerminNeu(
        personen = personen,
        titel = titel.trim(),
        beginn = if (ganztag) datum.toString() else beginn.format(ORTSZEIT),
        ende = if (ganztag) bisDatum.toString() else ende.format(ORTSZEIT),
        ganztag = ganztag,
        kategorie_id = requireNotNull(kategorieId),
        projekt = projekt.trim().ifBlank { null },
        notiz = notiz.trim().ifBlank { null },
    )

    /** Das Ende geht immer mit: was auf dem Bildschirm steht, soll auch in HERO stehen. */
    fun anfrageVerschieben(): HeroTerminVerschieben = HeroTerminVerschieben(
        beginn = if (ganztag) datum.toString() else beginn.format(ORTSZEIT),
        ende = if (ganztag) bisDatum.toString() else ende.format(ORTSZEIT),
        ganztag = ganztag,
    )

    companion object {
        /**
         * Neuer Termin: [tag] (Tagesansicht) bzw. heute, ab der naechsten vollen
         * Stunde fuer eine Stunde. Vorbelegt mit `ich`, wenn man in HERO zugeordnet ist.
         */
        fun neu(tag: LocalDate, personen: Personen?, jetzt: LocalDateTime = LocalDateTime.now()): HeroFormular {
            val stunde = if (tag == jetzt.toLocalDate()) (jetzt.hour + 1).coerceIn(7, 22) else 8
            val von = LocalTime.of(stunde, 0)
            return HeroFormular(
                personen = if (personen?.ich != null) listOf(PERSON_ICH) else emptyList(),
                datum = tag,
                von = von,
                bisDatum = tag,
                bis = von.plusHours(1),
            )
        }

        /** Verschieben: vorbelegt mit der bisherigen Zeit in Geraetezeit. */
        fun verschieben(termin: KalenderTermin, zone: ZoneId = ZoneId.systemDefault()): HeroFormular? {
            val beginn = zeitpunkt(termin.beginn, zone) ?: return null
            val tageListe = tage(termin, zone)
            val ende = zeitpunkt(termin.ende, zone)?.takeIf { it.isAfter(beginn) } ?: beginn.plusHours(1)
            return HeroFormular(
                verschieben = termin,
                datum = beginn.toLocalDate(),
                von = beginn.toLocalTime(),
                bisDatum = if (termin.ganztag) tageListe.lastOrNull() ?: beginn.toLocalDate() else ende.toLocalDate(),
                bis = ende.toLocalTime(),
                ganztag = termin.ganztag,
            )
        }
    }
}

/** "20.10. 08:00–09:00" bzw. bei verschiedenen Tagen "20.10. 08:00 – 21.10. 09:00". Fuer "bisher …" und Ueberschneidungen. */
internal fun zeitspanne(beginn: String, ende: String, zone: ZoneId = ZoneId.systemDefault()): String {
    val b = ortszeit(beginn, zone) ?: return beginn
    val e = ortszeit(ende, zone) ?: return zeitText(beginn, true, zone).ifBlank { beginn }
    val tagB = b.format(DateTimeFormatter.ofPattern("d.M."))
    val uhrB = b.format(DateTimeFormatter.ofPattern("HH:mm"))
    val uhrE = e.format(DateTimeFormatter.ofPattern("HH:mm"))
    return if (b.toLocalDate() == e.toLocalDate()) {
        "$tagB $uhrB–$uhrE"
    } else {
        "$tagB $uhrB – ${e.format(DateTimeFormatter.ofPattern("d.M."))} $uhrE"
    }
}

/** Wie [zeitpunkt], nimmt aber auch Ortszeit ohne Zone (`Ueberschneidung` kommt so). */
internal fun ortszeit(iso: String, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime? =
    zeitpunkt(iso, zone) ?: runCatching { LocalDateTime.parse(iso) }.getOrNull()
