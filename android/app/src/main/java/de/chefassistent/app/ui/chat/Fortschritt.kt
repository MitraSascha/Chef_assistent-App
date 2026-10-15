package de.chefassistent.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.chefassistent.app.R
import de.chefassistent.app.data.Ereignis
import de.chefassistent.app.data.Fortschritt
import de.chefassistent.app.ui.markdownEntfernen
import kotlinx.coroutines.delay
import java.util.Locale

// Fortschritt, Denken, Stopp und Zwischenruf (seit v1.20.0, API.md 1d).
//
// Die reine Logik (Zusammenfuehren, Zeilen, Texte) steht oben und ist ohne
// Android testbar, die Bausteine fuer den Chat darunter.

// --- Zustand -----------------------------------------------------------------

/**
 * Chat- oder Sprachanfrage mit Antwort, die gerade laeuft. Nur solange es sie
 * gibt, fragt der Chat jede Sekunde den Fortschritt ab und bietet Stopp und
 * Zwischenruf an.
 */
data class LaufendeAnfrage(
    val anfrageId: String,
    /** Ortszeit beim Absenden, fuer die Uhr in der Fortschrittszeile. */
    val beginn: Long,
    /** Zusammengefuehrter Stand aller Abfragen, `null` bis die erste ankam (oder alter Server). */
    val fortschritt: Fortschritt? = null,
    /** Stopp ist angefordert, die Antwort kommt nach dem laufenden Schritt. */
    val stoppt: Boolean = false,
    /** Ein Zwischenruf ist unterwegs - Knopf gesperrt, damit er nicht doppelt rausgeht. */
    val sendetZwischenruf: Boolean = false,
) {
    /**
     * Stopp und Zwischenruf erst, wenn der Server Fortschritt meldet: dann kann er
     * beides (ab 1.20.0) und die Anfrage ist bei ihm angekommen. Ein alter Server
     * behaelt so den gesperrten Senden-Knopf wie bisher.
     */
    val kannEingreifen: Boolean get() = fortschritt != null && !stoppt
}

/** Was der runde Knopf neben dem Eingabefeld gerade tut. */
enum class SendeKnopf { SENDEN, STOPP, ZWISCHENRUF }

/**
 * Laeuft eine Antwort, wird aus Senden bei leerem Feld Stopp, mit Text der
 * Zwischenruf. Eine zweite normale Nachricht kann so nie parallel rausgehen.
 */
val ChatZustand.sendeKnopf: SendeKnopf
    get() = when {
        laufend?.kannEingreifen != true -> SendeKnopf.SENDEN
        entwurf.isBlank() -> SendeKnopf.STOPP
        else -> SendeKnopf.ZWISCHENRUF
    }

val ChatZustand.sendeKnopfAktiv: Boolean
    get() = when (sendeKnopf) {
        SendeKnopf.SENDEN -> kannSenden
        SendeKnopf.STOPP -> true
        SendeKnopf.ZWISCHENRUF -> !nimmtAuf && laufend?.sendetZwischenruf != true
    }

// --- Texte -------------------------------------------------------------------

const val ZWISCHENRUF_MARKE = "Zwischenruf"
const val ZWISCHENRUF_PLATZHALTER = "Zwischenruf an den Assistenten …"
const val ZWISCHENRUF_ZU_SPAET = "Die Antwort ist schon fertig – bitte als neue Nachricht senden."
const val ABBRECHEN_LAEUFT = "Wird abgebrochen … (der laufende Schritt wird noch beendet)"
const val DETAILS_TITEL = "Details"

// --- Logik -------------------------------------------------------------------

/**
 * Haengt eine neue Abfrage an den bisherigen Stand. Ueberlappen sich die
 * Ereignisse (gleiches `seit` nach einem Funkloch, Antwort ohne `seit`), zaehlt
 * jede `nr` nur einmal. Zaehler und `aktuell` kommen aus der neuen Abfrage.
 */
fun ereignisseZusammenfuehren(alt: Fortschritt?, neu: Fortschritt): Fortschritt {
    val alle = (alt?.ereignisse.orEmpty() + neu.ereignisse)
        .associateBy { it.nr }
        .values
        .sortedBy { it.nr }
    return neu.copy(
        ereignisse = alle,
        naechster = maxOf(alt?.naechster ?: 0, neu.naechster),
        gestartet = neu.gestartet ?: alt?.gestartet,
    )
}

/** Eine Zeile in "Details" bzw. "Verlauf". [fertig] ist das passende `werkzeug_fertig`. */
data class VerlaufZeile(val ereignis: Ereignis, val fertig: Ereignis? = null)

/**
 * `werkzeug_fertig` wird keine eigene Zeile, sondern haengt sich an das erste
 * noch offene Werkzeug gleichen Namens. Findet sich keins (etwa weil der Start
 * fehlt), bleibt es als einfache Zeile stehen, damit nichts verschwindet.
 */
fun verlaufZeilen(ereignisse: List<Ereignis>): List<VerlaufZeile> {
    val zeilen = mutableListOf<VerlaufZeile>()
    for (e in ereignisse.sortedBy { it.nr }) {
        if (e.art == Ereignis.ART_WERKZEUG_FERTIG) {
            val i = zeilen.indexOfFirst {
                it.ereignis.art == Ereignis.ART_WERKZEUG && it.fertig == null &&
                    (e.werkzeug == null || it.ereignis.werkzeug == e.werkzeug)
            }
            if (i >= 0) {
                zeilen[i] = zeilen[i].copy(fertig = e)
                continue
            }
        }
        zeilen += VerlaufZeile(e)
    }
    return zeilen
}

/** "0:42", "12:05" - Minuten ohne fuehrende Null. */
fun zeitText(sekunden: Double): String {
    val s = sekunden.coerceAtLeast(0.0).toLong()
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

/** Dauer eines Werkzeugs: "0,4 s", "12 s", "1:05 min". */
fun dauerText(ms: Long): String {
    val s = ms.coerceAtLeast(0) / 1000.0
    return when {
        s < 10 -> String.format(Locale.GERMANY, "%.1f s", s)
        s < 60 -> "${s.toLong()} s"
        else -> "${zeitText(s)} min"
    }
}

fun werkzeugeText(anzahl: Int): String = if (anzahl == 1) "1 Werkzeug" else "$anzahl Werkzeuge"

/** "Arbeitet · Schritt 3 · 5 Werkzeuge · 0:42". Schritt und Werkzeuge erst, wenn es welche gibt. */
fun arbeitetZeile(schritte: Int, werkzeuge: Int, sekunden: Long): String = buildList {
    add("Arbeitet")
    if (schritte > 0) add("Schritt $schritte")
    if (werkzeuge > 0) add(werkzeugeText(werkzeuge))
    add(zeitText(sekunden.toDouble()))
}.joinToString(" · ")

/** Unter einer fertigen Antwort nur, wenn Werkzeuge oder Denken dabei waren (API.md 1d). */
fun zeigtVerlauf(fortschritt: Fortschritt?): Boolean =
    fortschritt != null && (fortschritt.werkzeuge > 0 || fortschritt.ereignisse.any { it.art == Ereignis.ART_DENKEN })

/** "Verlauf: 7 Werkzeuge · 1:12 min". Ohne Werkzeuge (nur Denken) steht dort "Denken". */
fun verlaufTitel(fortschritt: Fortschritt): String {
    val was = if (fortschritt.werkzeuge > 0) werkzeugeText(fortschritt.werkzeuge) else "Denken"
    return "Verlauf: $was · ${zeitText(fortschritt.dauer_s)} min"
}

// --- Oberflaeche ---------------------------------------------------------------

/**
 * Ersetzt "Assistent arbeitet … N s", sobald der Server Fortschritt meldet:
 * Zaehlerzeile mit lokaler Uhr, darunter kursiv `aktuell`, darunter
 * eingeklappt die Ereignisse.
 */
@Composable
fun FortschrittBlase(laufend: LaufendeAnfrage) {
    val fortschritt = laufend.fortschritt ?: return
    var jetzt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(laufend.anfrageId) {
        while (true) {
            delay(1000)
            jetzt = System.currentTimeMillis()
        }
    }
    // Lokal weiterzaehlen, aber nie hinter dem zuruecklaufen, was der Server sagt.
    val sekunden = maxOf((jetzt - laufend.beginn) / 1000, fortschritt.dauer_s.toLong())

    Card(
        shape = RoundedCornerShape(6.dp, 22.dp, 22.dp, 22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    text = arbeitetZeile(fortschritt.schritte, fortschritt.werkzeuge, sekunden),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            if (laufend.stoppt) {
                Text(
                    text = ABBRECHEN_LAEUFT,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 28.dp),
                )
            } else {
                fortschritt.aktuell?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 28.dp),
                    )
                }
            }
            if (fortschritt.ereignisse.isNotEmpty()) {
                Aufklapper(titel = DETAILS_TITEL, schluessel = "details-${laufend.anfrageId}") {
                    EreignisListe(fortschritt.ereignisse)
                }
            }
        }
    }
}

/** Unter einer fertigen Antwort: eingeklappt "Verlauf: N Werkzeuge · m:ss min". */
@Composable
fun VerlaufAufklapper(fortschritt: Fortschritt?, schluessel: String) {
    if (fortschritt == null || !zeigtVerlauf(fortschritt)) return
    Aufklapper(titel = verlaufTitel(fortschritt), schluessel = "verlauf-$schluessel") {
        EreignisListe(fortschritt.ereignisse)
    }
}

@Composable
private fun Aufklapper(titel: String, schluessel: String, inhalt: @Composable () -> Unit) {
    var offen by rememberSaveable(schluessel) { mutableStateOf(false) }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 40.dp)
                .clickable { offen = !offen }
                .padding(end = 8.dp),
        ) {
            Icon(
                imageVector = if (offen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = titel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        if (offen) {
            Column(modifier = Modifier.padding(bottom = 8.dp)) { inhalt() }
        }
    }
}

/**
 * Die Ereignisse als Liste. Denken steht nur hier, grau und kursiv - nie in der
 * Antwort, nie vorgelesen, nie kopiert (die Blase nimmt nur `antwort`).
 */
@Composable
private fun EreignisListe(ereignisse: List<Ereignis>) {
    val zeilen = remember(ereignisse) { verlaufZeilen(ereignisse) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        zeilen.forEach { EreignisZeile(it) }
    }
}

@Composable
private fun EreignisZeile(zeile: VerlaufZeile) {
    val e = zeile.ereignis
    val grau = MaterialTheme.colorScheme.onSurfaceVariant
    val fehler = e.art == Ereignis.ART_FEHLER
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = zeitText(e.sekunde),
            style = MaterialTheme.typography.labelSmall,
            color = grau,
            modifier = Modifier.width(40.dp).padding(top = 2.dp),
        )
        Icon(
            imageVector = symbol(e.art),
            contentDescription = null,
            modifier = Modifier.size(14.dp).padding(top = 2.dp),
            tint = if (fehler) MaterialTheme.colorScheme.error else grau,
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            when (e.art) {
                Ereignis.ART_DENKEN -> Text(
                    text = markdownEntfernen(e.text),
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = grau,
                )

                Ereignis.ART_ZWISCHENRUF -> Text(
                    text = "$ZWISCHENRUF_MARKE: ${e.text}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )

                else -> Text(
                    text = e.text.ifBlank { e.werkzeug ?: e.art },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (fehler) MaterialTheme.colorScheme.error else LocalContentColor.current,
                )
            }
            zeile.fertig?.let { f ->
                if (f.ok == false) {
                    Text(
                        text = f.text.ifBlank { "Fehlgeschlagen" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        text = "✓" + (f.dauer_ms?.let { " " + dauerText(it) } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = grau,
                    )
                }
            }
        }
    }
}

private fun symbol(art: String): ImageVector = when (art) {
    Ereignis.ART_SCHRITT -> Icons.Default.AutoAwesome
    Ereignis.ART_WERKZEUG, Ereignis.ART_WERKZEUG_FERTIG -> Icons.Default.Build
    Ereignis.ART_DENKEN -> Icons.Default.Psychology
    Ereignis.ART_WECHSEL -> Icons.Default.SwapHoriz
    Ereignis.ART_ZWISCHENRUF -> Icons.Default.Campaign
    Ereignis.ART_ABBRUCH, Ereignis.ART_ABGEBROCHEN -> Icons.Default.Stop
    Ereignis.ART_FEHLER -> Icons.Default.ErrorOutline
    Ereignis.ART_FERTIG -> Icons.Default.CheckCircle
    else -> Icons.Default.Circle
}

/**
 * Der Knopf rechts neben dem Eingabefeld. Normal der runde Senden-Knopf, waehrend
 * einer Antwort beschriftet: "Stopp" im Fehlerton, "Zwischenruf" in der
 * Akzentfarbe. Die Enter-Taste der Tastatur loest keinen davon aus (das Feld
 * hat keine Sende-Aktion), ein leeres Feld kann also nie versehentlich stoppen.
 */
@Composable
fun SendenKnopf(
    knopf: SendeKnopf,
    aktiv: Boolean,
    beiSenden: () -> Unit,
    beiStopp: () -> Unit,
    beiZwischenruf: () -> Unit,
) {
    // Alle drei rund und gleich gross: ein breiter Knopf mit Text drueckte das
    // Eingabefeld auf eine schmale Spalte zusammen (am S25 gesehen). Was der Knopf
    // tut, sagen Farbe, Symbol und der Platzhalter "Zwischenruf an den Assistenten …".
    val form = Modifier.size(56.dp)
    when (knopf) {
        SendeKnopf.SENDEN -> FilledIconButton(onClick = beiSenden, enabled = aktiv, modifier = form) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_senden))
        }

        SendeKnopf.STOPP -> FilledIconButton(
            onClick = beiStopp,
            enabled = aktiv,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
            modifier = form,
        ) {
            Icon(Icons.Default.Stop, contentDescription = "Stopp")
        }

        SendeKnopf.ZWISCHENRUF -> FilledIconButton(onClick = beiZwischenruf, enabled = aktiv, modifier = form) {
            Icon(Icons.Default.Campaign, contentDescription = ZWISCHENRUF_MARKE)
        }
    }
}

/** Kleine Marke ueber dem Text einer Zwischenruf-Blase. */
@Composable
fun ZwischenrufMarke(farbe: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(14.dp), tint = farbe)
        Text(
            text = ZWISCHENRUF_MARKE,
            style = MaterialTheme.typography.labelSmall,
            color = farbe,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** Gestrichelter Rand in der Form der Blase, fuer Zwischenrufe. */
fun Modifier.gestrichelterRand(farbe: Color, form: Shape, breite: Dp = 1.5.dp): Modifier = drawWithContent {
    drawContent()
    val umriss = form.createOutline(size, layoutDirection, this)
    drawOutline(
        outline = umriss,
        color = farbe,
        style = Stroke(
            width = breite.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
        ),
    )
}
