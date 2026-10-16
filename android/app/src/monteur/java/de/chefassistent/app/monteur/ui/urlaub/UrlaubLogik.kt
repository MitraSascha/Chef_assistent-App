package de.chefassistent.app.monteur.ui.urlaub

import androidx.annotation.StringRes
import de.chefassistent.app.R
import de.chefassistent.app.monteur.data.AbwVorschlag
import de.chefassistent.app.monteur.data.AbwesenheitNeu
import de.chefassistent.app.monteur.data.KRANK_ARTEN
import de.chefassistent.app.monteur.data.URLAUB_ARTEN
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

/*
 * Reine Logik des Reiters „Urlaub“: ohne Android, damit sie im Unit-Test laeuft.
 * Die Oberflaeche in [UrlaubReiter] rechnet nichts selbst.
 */

/** Status aus dem Vertrag (`Abwesenheit.status`). */
const val STATUS_ENTWURF = "entwurf"
const val STATUS_BEANTRAGT = "beantragt"
const val STATUS_GENEHMIGT = "genehmigt"
const val STATUS_ABGELEHNT = "abgelehnt"

/**
 * Deutscher Name einer Art, `null` = unbekannt (die App zeigt dann den Rohwert).
 * Neue Arten des Servers sollen sichtbar bleiben statt zu verschwinden.
 */
@StringRes
fun artName(art: String): Int? = when (art) {
    "urlaub" -> R.string.u_art_urlaub
    "ueberstunden" -> R.string.u_art_ueberstunden
    "sonderurlaub" -> R.string.u_art_sonderurlaub
    "unbezahlt" -> R.string.u_art_unbezahlt
    "krank" -> R.string.u_art_krank
    "kind_krank" -> R.string.u_art_kind_krank
    else -> null
}

/** Statusname, `null` = unbekannt (Rohwert anzeigen). */
@StringRes
fun statusName(status: String): Int? = when (status) {
    STATUS_ENTWURF -> R.string.u_status_entwurf
    STATUS_BEANTRAGT -> R.string.u_status_beantragt
    STATUS_GENEHMIGT -> R.string.u_status_genehmigt
    STATUS_ABGELEHNT -> R.string.u_status_abgelehnt
    else -> null
}

/** Ob die Krank-Variante des Formulars gilt: Krank/Kind krank gibt es nur ueber „Krank melden“. */
fun istKrankArt(art: String): Boolean = art in KRANK_ARTEN

/**
 * Nur unentschiedene Antraege lassen sich zurueckziehen (`DELETE` sonst 409).
 * Deshalb sind genehmigte und abgelehnte gar nicht erst wischbar.
 */
fun istZurueckziehbar(status: String): Boolean = status == STATUS_BEANTRAGT || status == STATUS_ENTWURF

/**
 * Tage mit Komma und ohne unnoetige Nullen: `2.5` -> „2,5“, `3.0` -> „3“.
 * `null` -> „–“, weil HERO den Stand nicht immer kennt (z. B. ohne Jahresurlaub).
 */
fun tageText(tage: Double?, leer: String = "–"): String {
    if (tage == null || tage.isNaN() || tage.isInfinite()) return leer
    val zahl = BigDecimal.valueOf(tage).stripTrailingZeros()
    val text = if (zahl.scale() <= 0) zahl.setScale(0).toPlainString() else zahl.toPlainString()
    return text.replace('.', ',')
}

/** `true` = Einzahl („1 Tag“), sonst „2,5 Tage“, „0,5 Tage“. */
fun istEinTag(tage: Double): Boolean = tage == 1.0

private val WOCHENTAGE = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

/** ISO-Datum `YYYY-MM-DD` (auch mit Uhrzeit dahinter) lesen, sonst `null`. */
fun datumLesen(iso: String): LocalDate? = try {
    LocalDate.parse(iso.take(10))
} catch (_: DateTimeParseException) {
    null
}

/** „Do 22.10.“; mit [mitJahr] „Do 22.10.2026“. Feste Kuerzel statt Locale, damit es auf jedem Geraet gleich aussieht. */
fun tagText(datum: LocalDate, mitJahr: Boolean = false): String {
    val wt = WOCHENTAGE[datum.dayOfWeek.value - 1]
    val tm = "%02d.%02d.".format(datum.dayOfMonth, datum.monthValue)
    return if (mitJahr) "$wt $tm${datum.year}" else "$wt $tm"
}

/**
 * „Do 22.10. – Fr 23.10.“, ein Tag allein „Do 22.10.“. Halbe Tage bekommen [halb]
 * („Do 22.10. ½ – Fr 23.10.“). Geht der Zeitraum ueber den Jahreswechsel, steht
 * das Jahr dabei, sonst waere „Mo 29.12. – Fr 02.01.“ nicht eindeutig.
 * Unlesbare Daten kommen roh zurueck, damit nie ein leerer Text dasteht.
 */
fun zeitraumText(
    von: String,
    bis: String,
    halbAnfang: Boolean = false,
    halbEnde: Boolean = false,
    halb: String = "½",
): String {
    val v = datumLesen(von)
    val b = datumLesen(bis)
    if (v == null || b == null) return if (von == bis) von else "$von – $bis"
    val mitJahr = v.year != b.year
    if (v == b) {
        val text = tagText(v, mitJahr)
        return if (halbAnfang || halbEnde) "$text $halb" else text
    }
    val links = tagText(v, mitJahr) + if (halbAnfang) " $halb" else ""
    val rechts = tagText(b, mitJahr) + if (halbEnde) " $halb" else ""
    return "$links – $rechts"
}

/**
 * Jahr fuer `DELETE …/{id}?jahr=`: der Vertrag verlangt das **Jahr des Beginns**,
 * nicht das gerade angezeigte. Ein Antrag 28.12.–03.01. haengt am alten Jahr.
 */
fun jahrZumZurueckziehen(von: String, angezeigt: Int): Int = datumLesen(von)?.year ?: angezeigt

/**
 * Geschaetzte Arbeitstage fuer die Vorschlagskarte („…, 2 Tage?“). Der Vorschlag
 * aus `verstehen` traegt keine Tage; der Server rechnet sie erst beim Antrag
 * (dann mit Feiertagen). Hier zaehlen nur Mo–Fr, halbe Tage je 0,5 weniger.
 */
fun tageSchaetzen(von: String, bis: String, halbAnfang: Boolean, halbEnde: Boolean): Double? {
    val v = datumLesen(von) ?: return null
    val b = datumLesen(bis) ?: return null
    if (b.isBefore(v)) return null
    var tage = 0.0
    var d = v
    while (!d.isAfter(b)) {
        if (d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY) tage += 1.0
        d = d.plusDays(1)
    }
    if (v == b) return if ((halbAnfang || halbEnde) && tage > 0) 0.5 else tage
    if (halbAnfang && istWerktag(v)) tage -= 0.5
    if (halbEnde && istWerktag(b)) tage -= 0.5
    return tage.coerceAtLeast(0.0)
}

private fun istWerktag(d: LocalDate) = d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY

/** Vorschlag aus `POST /v1/monteur/verstehen` als Antrag mit eigener [anfrageId]. */
fun vorschlagZuAntrag(vorschlag: AbwVorschlag, anfrageId: String): AbwesenheitNeu = AbwesenheitNeu(
    art = vorschlag.art,
    von = vorschlag.von.take(10),
    bis = vorschlag.bis.take(10),
    halber_tag_anfang = vorschlag.halber_tag_anfang,
    halber_tag_ende = vorschlag.halber_tag_ende,
    kommentar = vorschlag.kommentar,
    anfrage_id = anfrageId,
)

/** Datumsauswahl rechnet in UTC-Mitternacht; so verschiebt keine Zeitzone den Tag. */
fun millisZuDatum(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()

fun datumZuMillis(iso: String): Long? = datumLesen(iso)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()

/**
 * Eingaben des Antragsformulars. [anfrageId] bleibt fuer die ganze Lebensdauer
 * des Formulars gleich: wird nach einem Netzfehler erneut gesendet, legt der
 * Server keinen zweiten Antrag an.
 */
data class AntragFormular(
    val krank: Boolean,
    val art: String,
    val von: String,
    val bis: String,
    val halbAnfang: Boolean = false,
    val halbEnde: Boolean = false,
    val kommentar: String = "",
    val anfrageId: String,
    val sendet: Boolean = false,
    /** `detail` des Servers (422/409) oder anderer Fehler, die Eingaben bleiben stehen. */
    val fehler: String? = null,
) {
    /** Arten zur Auswahl: Krank-Arten nur in der Krank-Variante. */
    val arten: List<String> get() = if (krank) KRANK_ARTEN else URLAUB_ARTEN

    val gueltig: Boolean
        get() {
            val v = datumLesen(von) ?: return false
            val b = datumLesen(bis) ?: return false
            return !b.isBefore(v) && art.isNotBlank()
        }

    fun zuAntrag(): AbwesenheitNeu = AbwesenheitNeu(
        art = art,
        von = von,
        bis = bis,
        halber_tag_anfang = halbAnfang,
        halber_tag_ende = halbEnde,
        kommentar = kommentar.trim(),
        anfrage_id = anfrageId,
    )

    companion object {
        /** Urlaub: Zeitraum leer, der Monteur waehlt ihn bewusst (kein versehentliches „heute“). */
        fun urlaub(anfrageId: String): AntragFormular =
            AntragFormular(krank = false, art = URLAUB_ARTEN.first(), von = "", bis = "", anfrageId = anfrageId)

        /** Krank: heute bis heute vorbelegt, das ist der Regelfall. */
        fun krank(anfrageId: String, heute: LocalDate): AntragFormular = AntragFormular(
            krank = true,
            art = KRANK_ARTEN.first(),
            von = heute.toString(),
            bis = heute.toString(),
            anfrageId = anfrageId,
        )

        /** „Ändern“ an einem Vorschlag: vorbelegt, Krank-Variante je nach Art. */
        fun ausVorschlag(vorschlag: AbwVorschlag, anfrageId: String): AntragFormular = AntragFormular(
            krank = istKrankArt(vorschlag.art),
            art = vorschlag.art,
            von = vorschlag.von.take(10),
            bis = vorschlag.bis.take(10),
            halbAnfang = vorschlag.halber_tag_anfang,
            halbEnde = vorschlag.halber_tag_ende,
            kommentar = vorschlag.kommentar,
            anfrageId = anfrageId,
        )
    }
}
