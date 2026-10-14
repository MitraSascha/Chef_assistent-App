package de.chefassistent.app.ui.antwort

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Zeichnet einen `diagramm`-Block (`API.md` 1a). Farben, Masse und Bewegung
 * folgen den Vorgaben dort, damit App und Weboberflaeche gleich aussehen.
 *
 * Die Animation laeuft einmal, wenn das Diagramm zum ersten Mal zu einem
 * Viertel sichtbar ist. Der Merker ist `rememberSaveable`, damit Drehen und
 * Zurueckscrollen sie nicht erneut ausloesen.
 */
@Composable
fun DiagrammAnsicht(
    daten: DiagrammDaten,
    hintergrund: Color,
    modifier: Modifier = Modifier,
    hoehe: Dp = 220.dp,
    beiVollbild: (() -> Unit)? = null,
) {
    val dunkel = hintergrund.luminance() < 0.5f
    val farben = if (dunkel) FARBEN_DUNKEL else FARBEN_HELL

    var gelaufen by rememberSaveable(daten) { mutableStateOf(false) }
    val gesamt = remember(daten) { animationsDauer(daten) }
    val zeit = remember(daten) { Animatable(if (gelaufen) gesamt else 0f) }
    var sichtbar by remember { mutableStateOf(false) }
    LaunchedEffect(sichtbar) {
        if (sichtbar && !gelaufen) {
            zeit.animateTo(gesamt, tween(gesamt.roundToInt(), easing = LinearEasing))
            gelaufen = true
        }
    }

    // Auswahl per Tipp (x-Stelle bzw. Tortenstueck) und Hervorhebung per Legende.
    var auswahl by remember(daten) { mutableStateOf<Int?>(null) }
    var letzteAuswahl by remember(daten) { mutableStateOf(0) }
    var hervor by remember(daten) { mutableStateOf<Int?>(null) }
    var letztesHervor by remember(daten) { mutableStateOf(0) }
    val auswahlDimm by animateFloatAsState(if (auswahl != null) 1f else 0f, tween(200), label = "auswahl")
    val hervorDimm by animateFloatAsState(if (hervor != null) 1f else 0f, tween(200), label = "hervor")

    val zustand = Zeichenzustand(
        zeit = zeit.value,
        auswahl = auswahl ?: letzteAuswahl,
        auswahlDimm = auswahlDimm,
        hervor = hervor ?: letztesHervor,
        hervorDimm = hervorDimm,
    )

    Column(
        modifier = modifier.onGloballyPositioned { k ->
            val h = k.size.height
            if (!sichtbar && h > 0 && k.boundsInWindow().height / h >= 0.25f) sichtbar = true
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = daten.titel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(vertical = 6.dp),
            )
            if (beiVollbild != null) VollbildKnopf(beiVollbild)
        }

        val tippen: (Int?) -> Unit = { i ->
            auswahl = if (i == null || i == auswahl) null else i
            if (i != null) letzteAuswahl = i
        }
        if (daten.art == ART_TORTE) {
            Torte(daten, farben, hintergrund, zustand, hoehe, tippen)
        } else {
            AchsenDiagramm(daten, farben, hintergrund, zustand, hoehe, tippen)
        }

        if (daten.reihen.size >= 2 || daten.art == ART_TORTE) {
            Legende(daten, farben, hervor) { i ->
                hervor = if (i == hervor) null else i
                letztesHervor = i
            }
        }
    }
}

private data class Zeichenzustand(
    val zeit: Float,
    val auswahl: Int,
    val auswahlDimm: Float,
    val hervor: Int,
    val hervorDimm: Float,
) {
    /** Deckkraft eines Elements an Stelle [stelle] der Reihe [reihe]. */
    fun deckkraft(stelle: Int?, reihe: Int?): Float {
        var a = 1f
        if (stelle != null && stelle != auswahl) a *= 1f - 0.7f * auswahlDimm
        if (reihe != null && reihe != hervor) a *= 1f - 0.8f * hervorDimm
        return a
    }
}

// --- Bewegung ------------------------------------------------------------------

/** "Schnell los, weich aus", wie in der Weboberflaeche. */
private val KURVE = CubicBezierEasing(.22f, 1f, .36f, 1f)

private fun phase(zeit: Float, start: Float, dauer: Float): Float =
    KURVE.transform(((zeit - start) / dauer).coerceIn(0f, 1f))

private fun balkenVerzug(n: Int): Float = min(60f, 500f / n)

private fun animationsDauer(d: DiagrammDaten): Float = when (d.art) {
    ART_BALKEN -> 120f + (d.x.size - 1) * balkenVerzug(d.x.size) + 700f
    ART_LINIE -> (d.reihen.size - 1) * 120f + 900f + 450f + if (d.reihen.size == 1) 600f else 0f
    else -> 1000f
}

// --- Achsendiagramm (Balken, Linie) -----------------------------------------------

/** Was Zeichnen und Antippen gemeinsam brauchen. */
private class AchsenMasse(
    val links: Float,
    val oben: Float,
    val rechts: Float,
    val unten: Float,
    val n: Int,
    val linie: Boolean,
) {
    val breite get() = rechts - links
    val hoehe get() = unten - oben

    /** Mitte der Gruppe bzw. Punkt an Stelle [i]. */
    fun mitte(i: Int): Float =
        if (linie) links + if (n == 1) breite / 2 else i * breite / (n - 1) else links + (i + 0.5f) * breite / n

    fun stelleBei(x: Float): Int =
        if (linie) ((x - links) / (breite / max(1, n - 1))).roundToInt().coerceIn(0, n - 1)
        else ((x - links) / (breite / n)).toInt().coerceIn(0, n - 1)
}

@Composable
private fun AchsenDiagramm(
    daten: DiagrammDaten,
    farben: List<Color>,
    hintergrund: Color,
    z: Zeichenzustand,
    hoehe: Dp,
    beiTipp: (Int?) -> Unit,
) {
    val messer = rememberTextMeasurer()
    val dichte = LocalDensity.current
    val beschriftung = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val raster = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val nulllinie = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    val hilfslinie = MaterialTheme.colorScheme.onSurfaceVariant

    val (min, max) = remember(daten) { daten.wertebereich() }
    val skala = remember(daten) { skala(min, max) }
    val yTexte = remember(daten, beschriftung) {
        skala.striche.map { messer.measure(zahlKurz(it), beschriftung, maxLines = 1) }
    }
    val xTexte = remember(daten, beschriftung) {
        val grenze = with(dichte) { 96.dp.roundToPx() }
        daten.x.map {
            messer.measure(
                it, beschriftung, maxLines = 1, overflow = TextOverflow.Ellipsis,
                constraints = Constraints(maxWidth = grenze),
            )
        }
    }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val breitePx = with(dichte) { maxWidth.toPx() }
        val hoehePx = with(dichte) { hoehe.toPx() }
        val masse = remember(daten, breitePx, hoehePx) {
            with(dichte) {
                val links = (yTexte.maxOf { it.size.width }) + 8.dp.toPx()
                val untenText = (xTexte.maxOfOrNull { it.size.height } ?: 0) + 6.dp.toPx()
                AchsenMasse(
                    links = links,
                    oben = 8.dp.toPx(),
                    rechts = breitePx - if (daten.art == ART_LINIE) 8.dp.toPx() else 0f,
                    unten = hoehePx - untenText,
                    n = daten.x.size,
                    linie = daten.art == ART_LINIE,
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(hoehe)
                .pointerInput(daten, masse) {
                    detectTapGestures { pos ->
                        beiTipp(if (pos.x < masse.links - 8.dp.toPx() || pos.y > masse.unten) null else masse.stelleBei(pos.x))
                    }
                },
        ) {
            val rasterAlpha = phase(z.zeit, 0f, 400f)
            val y = { v: Double -> masse.unten - skala.anteil(v) * masse.hoehe }

            // Raster und y-Beschriftung
            skala.striche.forEachIndexed { k, wert ->
                val yy = y(wert)
                drawLine(
                    color = if (wert == 0.0 && skala.unten < 0) nulllinie else raster,
                    start = Offset(masse.links, yy),
                    end = Offset(size.width, yy),
                    strokeWidth = Stroke.HairlineWidth,
                    alpha = rasterAlpha,
                )
                val t = yTexte[k]
                drawText(t, topLeft = Offset(masse.links - 8.dp.toPx() - t.size.width, yy - t.size.height / 2f), alpha = rasterAlpha)
            }

            // x-Beschriftung: nur jede n-te, wenn sie nicht nebeneinander passen. Nie drehen.
            val abstand = if (masse.linie) masse.breite / max(1, masse.n - 1) else masse.breite / masse.n
            val breitest = xTexte.maxOf { it.size.width } + 8.dp.toPx()
            val jede = max(1, ceil(breitest / abstand).toInt())
            xTexte.forEachIndexed { i, t ->
                if (i % jede != 0) return@forEachIndexed
                val mitte = masse.mitte(i)
                val x = (mitte - t.size.width / 2f).coerceIn(0f, size.width - t.size.width)
                drawText(t, topLeft = Offset(x, masse.unten + 6.dp.toPx()), alpha = rasterAlpha)
            }

            if (daten.art == ART_BALKEN) {
                balkenZeichnen(daten, farben, hintergrund, z, masse, y)
            } else {
                linienZeichnen(daten, farben, hintergrund, z, masse, y, hilfslinie)
            }
        }

        // Infobox ueber der angetippten Stelle
        if (z.auswahlDimm > 0f) {
            val i = z.auswahl
            Infobox(
                titel = daten.x[i],
                zeilen = daten.reihen.mapIndexed { s, r ->
                    Triple(farben[s % farben.size], r.name, r.werte[i]?.let { mitEinheit(zahlLang(it), daten.einheit) } ?: "–")
                },
                anker = masse.mitte(i),
                sichtbar = z.auswahlDimm,
            )
        }
    }
}

private fun DrawScope.balkenZeichnen(
    d: DiagrammDaten,
    farben: List<Color>,
    hintergrund: Color,
    z: Zeichenzustand,
    m: AchsenMasse,
    y: (Double) -> Float,
) {
    val n = d.x.size
    val gruppe = m.breite / n
    val luecke = 2.dp.toPx()
    val k = if (d.istGestapelt) 1 else d.reihen.size
    val saeule = min(24.dp.toPx(), (gruppe * 0.72f - (k - 1) * luecke) / k).coerceAtLeast(2.dp.toPx())
    val ganz = k * saeule + (k - 1) * luecke
    val y0 = y(0.0)
    val verzug = balkenVerzug(n)

    for (i in 0 until n) {
        val p = phase(z.zeit, 120f + i * verzug, 700f)
        if (p <= 0f) continue
        val start = m.links + i * gruppe + (gruppe - ganz) / 2f
        val skaliert = { v: Double -> y0 + (y(v) - y0) * p }

        if (d.istGestapelt) {
            var summe = 0.0
            val oberste = d.reihen.indices.lastOrNull { (d.reihen[it].werte[i] ?: 0.0) > 0.0 }
            d.reihen.forEachIndexed { s, r ->
                val v = r.werte[i] ?: return@forEachIndexed
                if (v <= 0.0) return@forEachIndexed
                val unten = skaliert(summe)
                summe += v
                val oben = skaliert(summe)
                // 2 dp Luecke zum Teil darunter, in der Hintergrundfarbe - also einfach freilassen.
                val untenMitLuecke = if (unten < y0 - 0.5f) unten - luecke else unten
                saeuleZeichnen(
                    start, oben, saeule, untenMitLuecke, s == oberste,
                    farben[s % farben.size].copy(alpha = z.deckkraft(i, s)),
                )
            }
        } else {
            d.reihen.forEachIndexed { s, r ->
                val v = r.werte[i] ?: return@forEachIndexed
                val x = start + s * (saeule + luecke)
                saeuleZeichnen(x, skaliert(v), saeule, y0, true, farben[s % farben.size].copy(alpha = z.deckkraft(i, s)))
            }
        }
    }
}

/** Eine Saeule von [y0] bis [yWert]. Das Ende weg von der Nulllinie ist 4 dp abgerundet. */
private fun DrawScope.saeuleZeichnen(x: Float, yWert: Float, breite: Float, y0: Float, rund: Boolean, farbe: Color) {
    val oben = min(yWert, y0)
    val unten = max(yWert, y0)
    if (unten - oben < 0.5f) return
    val r = if (rund) min(4.dp.toPx(), min(breite / 2f, unten - oben)) else 0f
    val ecke = CornerRadius(r, r)
    val positiv = yWert <= y0
    val form = RoundRect(
        left = x, top = oben, right = x + breite, bottom = unten,
        topLeftCornerRadius = if (positiv) ecke else CornerRadius.Zero,
        topRightCornerRadius = if (positiv) ecke else CornerRadius.Zero,
        bottomLeftCornerRadius = if (positiv) CornerRadius.Zero else ecke,
        bottomRightCornerRadius = if (positiv) CornerRadius.Zero else ecke,
    )
    drawPath(Path().apply { addRoundRect(form) }, farbe)
}

private fun DrawScope.linienZeichnen(
    d: DiagrammDaten,
    farben: List<Color>,
    hintergrund: Color,
    z: Zeichenzustand,
    m: AchsenMasse,
    y: (Double) -> Float,
    hilfslinie: Color,
) {
    val dicke = 2.dp.toPx()
    val radius = 4.dp.toPx()
    val ring = 2.dp.toPx()
    val y0 = y(0.0).coerceIn(m.oben, m.unten)

    // Senkrechte Hilfslinie an der angetippten Stelle
    if (z.auswahlDimm > 0f) {
        val x = m.mitte(z.auswahl)
        drawLine(hilfslinie, Offset(x, m.oben), Offset(x, m.unten), strokeWidth = 1.dp.toPx(), alpha = 0.6f * z.auswahlDimm)
    }

    d.reihen.forEachIndexed { s, r ->
        val start = s * 120f
        val p = phase(z.zeit, start, 900f)
        if (p <= 0f) return@forEachIndexed
        val farbe = farben[s % farben.size]
        val deckkraft = z.deckkraft(null, s)

        val linie = Path()
        var offen = false
        r.werte.forEachIndexed { i, v ->
            if (v == null) {
                offen = false
            } else {
                val punkt = Offset(m.mitte(i), y(v))
                if (offen) linie.lineTo(punkt.x, punkt.y) else linie.moveTo(punkt.x, punkt.y)
                offen = true
            }
        }

        // Zarte Flaeche nur bei einer einzigen Reihe, nach dem Endpunkt.
        if (d.reihen.size == 1) {
            val flaeche = phase(z.zeit, start + 900f + 450f, 600f)
            if (flaeche > 0f) {
                abschnitteOhneLuecke(r.werte).forEach { (von, bis) ->
                    val f = Path().apply {
                        moveTo(m.mitte(von), y0)
                        for (i in von..bis) lineTo(m.mitte(i), y(r.werte[i]!!))
                        lineTo(m.mitte(bis), y0)
                        close()
                    }
                    drawPath(f, farbe.copy(alpha = 0.10f * flaeche * deckkraft))
                }
            }
        }

        clipRect(left = 0f, top = 0f, right = m.links + m.breite * p + dicke, bottom = size.height) {
            drawPath(
                linie, farbe.copy(alpha = deckkraft),
                style = Stroke(width = dicke, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        // Endpunkt springt auf: 0 -> 1,5 -> 1
        val letzter = r.werte.indexOfLast { it != null }
        if (letzter >= 0) {
            val u = ((z.zeit - start - 900f) / 450f).coerceIn(0f, 1f)
            val skal = if (u < 0.6f) 1.5f * KURVE.transform(u / 0.6f) else 1.5f - 0.5f * KURVE.transform((u - 0.6f) / 0.4f)
            if (skal > 0f) punktZeichnen(Offset(m.mitte(letzter), y(r.werte[letzter]!!)), radius * skal, ring * skal, farbe.copy(alpha = deckkraft), hintergrund)
        }

        // Punkte auf jeder Reihe an der angetippten Stelle
        if (z.auswahlDimm > 0f) {
            r.werte.getOrNull(z.auswahl)?.let { v ->
                punktZeichnen(Offset(m.mitte(z.auswahl), y(v)), radius, ring, farbe.copy(alpha = z.auswahlDimm * deckkraft), hintergrund.copy(alpha = z.auswahlDimm))
            }
        }
    }
}

private fun DrawScope.punktZeichnen(mitte: Offset, radius: Float, ring: Float, farbe: Color, hintergrund: Color) {
    drawCircle(hintergrund, radius + ring, mitte)
    drawCircle(farbe, radius, mitte)
}

/** Zusammenhaengende Stuecke ohne `null`, als (von, bis). */
private fun abschnitteOhneLuecke(werte: List<Double?>): List<Pair<Int, Int>> {
    val stuecke = mutableListOf<Pair<Int, Int>>()
    var von = -1
    werte.forEachIndexed { i, v ->
        if (v != null && von < 0) von = i
        if (v == null && von >= 0) {
            stuecke += von to i - 1
            von = -1
        }
    }
    if (von >= 0) stuecke += von to werte.lastIndex
    return stuecke
}

// --- Torte (Ring) -----------------------------------------------------------------

@Composable
private fun Torte(
    daten: DiagrammDaten,
    farben: List<Color>,
    hintergrund: Color,
    z: Zeichenzustand,
    hoehe: Dp,
    beiTipp: (Int?) -> Unit,
) {
    val messer = rememberTextMeasurer()
    val werte = daten.reihen[0].werte.map { it ?: 0.0 }
    val summe = werte.sum()
    val summenStil = MaterialTheme.typography.titleMedium.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
    )
    val ringHoehe = min(hoehe.value, 200f).dp

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val dichte = LocalDensity.current
        val breitePx = with(dichte) { maxWidth.toPx() }
        val hoehePx = with(dichte) { ringHoehe.toPx() }
        val mitte = Offset(breitePx / 2f, hoehePx / 2f)
        val aussen = min(breitePx, hoehePx) / 2f - with(dichte) { 8.dp.toPx() }
        val staerke = aussen * 0.32f

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(ringHoehe)
                .pointerInput(daten, breitePx) {
                    detectTapGestures { pos ->
                        val abstand = hypot(pos.x - mitte.x, pos.y - mitte.y)
                        if (abstand < aussen - staerke - 8.dp.toPx() || abstand > aussen + 8.dp.toPx()) {
                            beiTipp(null)
                            return@detectTapGestures
                        }
                        // Winkel ab 12 Uhr im Uhrzeigersinn, 0..1
                        var w = (atan2(pos.y - mitte.y, pos.x - mitte.x) + PI / 2) / (2 * PI)
                        if (w < 0) w += 1.0
                        var bis = 0.0
                        val stueck = werte.indexOfFirst { bis += it / summe; w <= bis }
                        beiTipp(stueck.takeIf { it >= 0 })
                    }
                },
        ) {
            val p = phase(z.zeit, 0f, 1000f)
            val luecke = Math.toDegrees((2.dp.toPx() / (aussen - staerke / 2f)).toDouble()).toFloat()
            val radius = aussen - staerke / 2f
            var winkel = -90f
            val ende = -90f + 360f * p
            werte.forEachIndexed { i, v ->
                val bogen = (v / summe * 360f).toFloat()
                val sichtbarBis = min(winkel + bogen, ende)
                if (sichtbarBis > winkel) {
                    val schub = if (i == z.auswahl) 5.dp.toPx() * z.auswahlDimm else 0f
                    val mittelWinkel = Math.toRadians((winkel + bogen / 2f).toDouble())
                    val versatz = Offset((cos(mittelWinkel) * schub).toFloat(), (sin(mittelWinkel) * schub).toFloat())
                    val zeichnen = (sichtbarBis - winkel - luecke).coerceAtLeast(0.5f)
                    drawArc(
                        color = farben[i % farben.size].copy(alpha = z.deckkraft(i, i)),
                        startAngle = winkel + luecke / 2f,
                        sweepAngle = zeichnen,
                        useCenter = false,
                        topLeft = Offset(mitte.x - radius, mitte.y - radius) + versatz,
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = staerke),
                    )
                }
                winkel += bogen
            }
            // Summe in der Mitte zaehlt mit hoch.
            val text = messer.measure(mitEinheit(zahlLang(summe * p), daten.einheit), summenStil, maxLines = 1)
            drawText(text, topLeft = Offset(mitte.x - text.size.width / 2f, mitte.y - text.size.height / 2f))
        }

        if (z.auswahlDimm > 0f) {
            val i = z.auswahl
            Infobox(
                titel = daten.x[i],
                zeilen = listOf(
                    Triple(
                        farben[i % farben.size],
                        daten.reihen[0].name,
                        stueckText(werte[i], summe, daten.einheit),
                    )
                ),
                anker = mitte.x,
                sichtbar = z.auswahlDimm,
            )
        }
    }
}

private fun prozent(wert: Double, summe: Double): String = "${zahlLang(Math.round(wert / summe * 1000) / 10.0)} %"

/** "1.820 € · 45 %". Ist die Einheit schon Prozent, stuende dieselbe Zahl sonst zweimal da. */
private fun stueckText(wert: Double, summe: Double, einheit: String?): String {
    val text = mitEinheit(zahlLang(wert), einheit)
    return if (einheit?.trim() == "%") text else "$text · ${prozent(wert, summe)}"
}

// --- Infobox und Legende ------------------------------------------------------------

/**
 * Infobox oben im Diagramm, waagerecht ueber [anker], am Rand festgehalten.
 * Blendet ein und rueckt dabei 4 dp nach oben.
 */
@Composable
private fun Infobox(titel: String, zeilen: List<Triple<Color, String, String>>, anker: Float, sichtbar: Float) {
    val einblenden = remember(titel) { Animatable(0f) }
    LaunchedEffect(titel) { einblenden.animateTo(1f, tween(150)) }
    val dichte = LocalDensity.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 4.dp,
        modifier = Modifier
            .layout { messbar, grenzen ->
                val platz = messbar.measure(grenzen.copy(minWidth = 0))
                val x = (anker - platz.width / 2f).roundToInt().coerceIn(0, max(0, grenzen.maxWidth - platz.width))
                layout(grenzen.maxWidth, platz.height) { platz.place(x, 0) }
            }
            .graphicsLayer {
                val a = einblenden.value * sichtbar
                alpha = a
                translationY = (1f - a) * with(dichte) { 4.dp.toPx() }
            },
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(titel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            zeilen.forEach { (farbe, name, wert) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Quadrat(farbe)
                    if (name.isNotBlank()) {
                        Text(
                            "$name:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    Text(wert, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

/**
 * Legende: nur bei mehreren Reihen bzw. bei der Torte (dort mit Wert und
 * Prozent). Text immer in Textfarbe, die Farbe traegt das Quadrat davor.
 */
@Composable
private fun Legende(daten: DiagrammDaten, farben: List<Color>, hervor: Int?, beiTipp: (Int) -> Unit) {
    val torte = daten.art == ART_TORTE
    val eintraege = if (torte) {
        val werte = daten.reihen[0].werte.map { it ?: 0.0 }
        val summe = werte.sum()
        daten.x.mapIndexed { i, x -> "$x  ${stueckText(werte[i], summe, daten.einheit)}" }
    } else {
        daten.reihen.map { it.name }
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 6.dp),
    ) {
        eintraege.forEachIndexed { i, text ->
            val gedimmt = hervor != null && hervor != i
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { beiTipp(i) }
                    .graphicsLayer { alpha = if (gedimmt) 0.4f else 1f }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Quadrat(farben[i % farben.size])
                Text(
                    text,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun Quadrat(farbe: Color) {
    Canvas(Modifier.size(10.dp)) {
        drawRoundRect(farbe, cornerRadius = CornerRadius(2.dp.toPx()))
    }
}

/** Reihenfarben aus `API.md` 1a, geprueft auf Farbsehschwaeche. Reihenfolge ist fest. */
/** Feste Reihenfolge aus `API.md` 1a, auch fuer die Personen im Kalender. */
internal val FARBEN_HELL = listOf(
    Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A),
    Color(0xFFEDA100), Color(0xFFE87BA4), Color(0xFF008300),
)
internal val FARBEN_DUNKEL = listOf(
    Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70),
    Color(0xFFC98500), Color(0xFFD55181), Color(0xFF008300),
)
