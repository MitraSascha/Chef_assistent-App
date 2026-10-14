package de.chefassistent.app.ui.plantafel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.chefassistent.app.R
import de.chefassistent.app.data.Einsatz
import de.chefassistent.app.data.Gewerk
import de.chefassistent.app.data.GuPlan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private val TAG_BREITE = 34.dp
private val NAMEN_BREITE = 112.dp
private val SPUR_HOEHE = 36.dp
private val ZEILEN_RAND = 6.dp
private val KOPF_HOEHE = 44.dp
private val MONAT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMANY)

/** Ein Balken mit seiner Spur innerhalb der Gewerk-Zeile (bei Ueberlappung untereinander). */
private data class Balken(val einsatz: Einsatz, val von: LocalDate, val bis: LocalDate, val spur: Int)

/**
 * Verteilt die Einsaetze eines Gewerks auf Spuren, damit sich ueberlappende
 * Balken nicht verdecken. `bis` ist einschliesslich.
 */
private fun spurenVerteilen(einsaetze: List<Einsatz>): List<Balken> {
    val enden = mutableListOf<LocalDate>()
    return einsaetze
        .mapNotNull { e ->
            val von = datum(e.von) ?: return@mapNotNull null
            val bis = (datum(e.bis) ?: von).let { if (it < von) von else it }
            Triple(e, von, bis)
        }
        .sortedBy { it.second }
        .map { (e, von, bis) ->
            val frei = enden.indexOfFirst { it < von }
            val spur = if (frei >= 0) frei else enden.size.also { enden.add(bis) }
            enden[spur] = bis
            Balken(e, von, bis, spur)
        }
}

/** Sichtbarer Zeitraum: alle Einsaetze mit etwas Rand, mindestens drei Wochen. */
/** Siehe [zeitraum]. */
private const val MAX_TAGE_UM_HEUTE = 366L

internal fun zeitraum(plan: GuPlan, heute: LocalDate = LocalDate.now()): Pair<LocalDate, LocalDate> {
    val tage = plan.einsaetze.flatMap { listOfNotNull(datum(it.von), datum(it.bis)) } +
        listOfNotNull(datum(plan.projekt.von), datum(plan.projekt.bis))
    // Hoechstens ein Jahr um heute: ein Tippfehler im Jahr (2036 statt 2026) machte
    // die Tafel sonst breiter, als Compose zeichnen kann - Absturz bei jedem Oeffnen.
    val frueheste = heute.minusDays(MAX_TAGE_UM_HEUTE)
    val spaeteste = heute.plusDays(MAX_TAGE_UM_HEUTE)
    val anfang = (tage.minOrNull() ?: heute).minusDays(3).coerceIn(frueheste, spaeteste)
    var ende = (tage.maxOrNull() ?: heute).plusDays(7).coerceIn(anfang, spaeteste)
    if (ChronoUnit.DAYS.between(anfang, ende) < 21) ende = anfang.plusDays(21)
    return anfang to ende
}

/**
 * Plantafel: links fest die Gewerke, rechts waagerecht scrollbar die Tage mit
 * den Balken. Abgesagte Einsaetze werden laut `API.md` ausgeblendet.
 */
@Composable
internal fun Zeitleiste(
    plan: GuPlan,
    markiert: Set<Int>,
    beiEinsatz: (Einsatz) -> Unit,
    beiGewerk: (Gewerk) -> Unit,
    modifier: Modifier = Modifier,
) {
    val farben = MaterialTheme.colorScheme
    val heute = remember { LocalDate.now() }
    val (anfang, ende) = remember(plan) { zeitraum(plan, heute) }
    val tage = remember(anfang, ende) {
        generateSequence(anfang) { it.plusDays(1) }.takeWhile { it <= ende }.toList()
    }
    val sichtbar = plan.einsaetze.filter { it.status != Einsatz.STATUS_ABGESAGT }
    // Gewerke in Server-Reihenfolge, dahinter Einsaetze mit unbekanntem Gewerk.
    val zeilen: List<Pair<Gewerk?, List<Balken>>> = remember(plan) {
        val bekannt = plan.gewerke.map { g -> g to spurenVerteilen(sichtbar.filter { it.gewerk_id == g.id }) }
        val ids = plan.gewerke.map { it.id }.toSet()
        val rest = sichtbar.filter { it.gewerk_id !in ids }
        bekannt + if (rest.isEmpty()) emptyList() else listOf(null to spurenVerteilen(rest))
    }
    val scroll = rememberScrollState()
    val dichte = LocalDensity.current

    // Beim Oeffnen auf heute (bzw. den Anfang) springen, heute als zweite Spalte.
    LaunchedEffect(anfang) {
        val index = ChronoUnit.DAYS.between(anfang, heute).toInt()
        if (index in tage.indices) scroll.scrollTo(with(dichte) { (TAG_BREITE * (index - 1).coerceAtLeast(0)).roundToPx() })
    }

    fun zeilenHoehe(balken: List<Balken>): Dp =
        SPUR_HOEHE * ((balken.maxOfOrNull { it.spur } ?: 0) + 1) + ZEILEN_RAND * 2

    Row(modifier = modifier.fillMaxWidth()) {
        // Feste Namensspalte.
        Column(modifier = Modifier.width(NAMEN_BREITE)) {
            Box(Modifier.height(KOPF_HOEHE))
            zeilen.forEach { (gewerk, balken) ->
                HorizontalDivider(color = farben.outlineVariant)
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(zeilenHoehe(balken))
                        .then(if (gewerk != null) Modifier.clickable { beiGewerk(gewerk) } else Modifier)
                        .padding(start = 16.dp, end = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .width(4.dp)
                                .height(24.dp)
                                .background(farbeAus(gewerk?.farbe, farben.outline), RoundedCornerShape(2.dp)),
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = gewerk?.name ?: "?",
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!gewerk?.firma.isNullOrBlank()) {
                                Text(
                                    text = gewerk!!.firma,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = farben.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Tage, waagerecht scrollbar.
        Box(modifier = Modifier.weight(1f).horizontalScroll(scroll)) {
            val breite = TAG_BREITE * tage.size
            Column(
                modifier = Modifier
                    .width(breite)
                    .drawBehind {
                        // Wochenenden leicht abgesetzt, Wochenanfang mit Linie.
                        val tagPx = TAG_BREITE.toPx()
                        tage.forEachIndexed { i, tag ->
                            val x = i * tagPx
                            if (tag.dayOfWeek == DayOfWeek.SATURDAY || tag.dayOfWeek == DayOfWeek.SUNDAY) {
                                drawRect(
                                    color = farben.onSurface.copy(alpha = 0.05f),
                                    topLeft = Offset(x, 0f),
                                    size = androidx.compose.ui.geometry.Size(tagPx, size.height),
                                )
                            }
                            if (tag.dayOfWeek == DayOfWeek.MONDAY) {
                                drawLine(farben.outlineVariant, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                            }
                        }
                        val heuteIndex = ChronoUnit.DAYS.between(anfang, heute)
                        if (heuteIndex in tage.indices.map { it.toLong() }) {
                            val x = heuteIndex * tagPx + tagPx / 2
                            drawLine(farben.error, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
                        }
                    },
            ) {
                TageKopf(tage, heute)
                zeilen.forEach { (_, balken) ->
                    HorizontalDivider(color = farben.outlineVariant)
                    Box(modifier = Modifier.fillMaxWidth().height(zeilenHoehe(balken))) {
                        // Auf den sichtbaren Zeitraum gekappt: ein Balken ueber Jahre
                        // waere sonst breiter, als Compose zeichnen kann.
                        balken.filter { it.bis >= anfang && it.von <= ende }.forEach { b ->
                            val von = maxOf(b.von, anfang)
                            val bis = minOf(b.bis, ende)
                            BalkenFeld(
                                balken = b,
                                x = TAG_BREITE * ChronoUnit.DAYS.between(anfang, von).toInt(),
                                breite = TAG_BREITE * (ChronoUnit.DAYS.between(von, bis).toInt() + 1),
                                markiert = b.einsatz.id in markiert,
                                beiKlick = { beiEinsatz(b.einsatz) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TageKopf(tage: List<LocalDate>, heute: LocalDate) {
    val farben = MaterialTheme.colorScheme
    Box(modifier = Modifier.height(KOPF_HOEHE)) {
        // Monatsname ueber dem ersten Tag des Monats (und ganz vorn).
        tage.forEachIndexed { i, tag ->
            if (i == 0 || tag.dayOfMonth == 1) {
                Text(
                    text = tag.format(MONAT),
                    style = MaterialTheme.typography.labelSmall,
                    color = farben.primary,
                    maxLines = 1,
                    modifier = Modifier.offset(x = TAG_BREITE * i + 4.dp),
                )
            }
        }
        Row(modifier = Modifier.align(Alignment.BottomStart)) {
            tage.forEach { tag ->
                val istHeute = tag == heute
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(TAG_BREITE),
                ) {
                    Text(
                        text = tag.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.GERMANY),
                        style = MaterialTheme.typography.labelSmall,
                        color = farben.onSurfaceVariant,
                    )
                    Text(
                        text = tag.dayOfMonth.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (istHeute) FontWeight.Bold else FontWeight.Normal,
                        color = if (istHeute) farben.error else farben.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun BalkenFeld(balken: Balken, x: Dp, breite: Dp, markiert: Boolean, beiKlick: () -> Unit) {
    val farben = MaterialTheme.colorScheme
    val e = balken.einsatz
    val grund = farbeAus(e.farbe, farben.primary)
    val form = RoundedCornerShape(10.dp)
    // geplant: blass und gestreift · bestaetigt: voll · fertig: gedaempft mit Haken.
    val fuellung = when (e.status) {
        Einsatz.STATUS_GEPLANT -> grund.copy(alpha = 0.35f)
        Einsatz.STATUS_FERTIG -> grund.copy(alpha = 0.45f)
        else -> grund
    }
    val schrift = when {
        e.status == Einsatz.STATUS_GEPLANT || e.status == Einsatz.STATUS_FERTIG -> farben.onSurface
        grund.luminance() > 0.5f -> Color.Black
        else -> Color.White
    }
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .offset(x = x + 2.dp, y = ZEILEN_RAND + SPUR_HOEHE * balken.spur + 2.dp)
            .width(breite - 4.dp)
            .height(SPUR_HOEHE - 4.dp)
            .clip(form)
            .background(fuellung)
            .drawBehind {
                if (e.status == Einsatz.STATUS_GEPLANT) {
                    val abstand = 8.dp.toPx()
                    var start = -size.height
                    while (start < size.width) {
                        drawLine(
                            grund.copy(alpha = 0.55f),
                            Offset(start, size.height),
                            Offset(start + size.height, 0f),
                            1.5.dp.toPx(),
                        )
                        start += abstand
                    }
                }
            }
            .then(if (markiert) Modifier.border(2.dp, farben.error, form) else Modifier)
            .clickable(onClick = beiKlick)
            .padding(horizontal = 8.dp),
    ) {
        Text(
            text = (if (e.status == Einsatz.STATUS_FERTIG) "✓ " else "") + e.was.ifBlank { e.gewerk },
            style = MaterialTheme.typography.labelMedium,
            color = schrift,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Legende unter der Tafel. */
@Composable
internal fun Legende() {
    val farben = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        listOf(
            0.35f to stringResource(R.string.gu_legende_geplant),
            1f to stringResource(R.string.gu_legende_bestaetigt),
            0.45f to "✓ " + stringResource(R.string.gu_legende_fertig),
        ).forEach { (alpha, text) ->
            Box(
                Modifier
                    .width(18.dp)
                    .height(10.dp)
                    .background(farben.primary.copy(alpha = alpha), RoundedCornerShape(3.dp)),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = farben.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, end = 16.dp),
            )
        }
        Box(Modifier.width(2.dp).height(12.dp).background(farben.error))
        Text(
            text = stringResource(R.string.gu_heute),
            style = MaterialTheme.typography.bodySmall,
            color = farben.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
