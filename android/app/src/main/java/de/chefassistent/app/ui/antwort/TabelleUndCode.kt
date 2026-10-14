package de.chefassistent.app.ui.antwort

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.chefassistent.app.R

/**
 * Tabelle in Compose statt in Markwon. Markwon verteilt die Spalten gleichmaessig
 * auf die Blasenbreite, ab vier Spalten brach jede Zelle um. Hier bekommt jede
 * Spalte ihre natuerliche Breite (gedeckelt), und die Tabelle scrollt seitlich.
 * Ein Tipp oeffnet sie im Vollbild.
 */
@Composable
fun TabellenAnsicht(
    ganzeTabelle: Abschnitt.Tabelle,
    modifier: Modifier = Modifier,
    vollbild: Boolean = false,
    beiVollbild: (() -> Unit)? = null,
) {
    // In der Blase hoechstens [MAX_ZEILEN_BLASE] Zeilen: jede Zelle wird zum
    // Vermessen gesetzt, bei hunderten Zeilen ruckelte der Chat. Alles im Vollbild.
    val tabelle = remember(ganzeTabelle, vollbild) {
        if (vollbild || ganzeTabelle.zeilen.size <= MAX_ZEILEN_BLASE) ganzeTabelle
        else ganzeTabelle.copy(zeilen = ganzeTabelle.zeilen.take(MAX_ZEILEN_BLASE))
    }
    val weitere = ganzeTabelle.zeilen.size - tabelle.zeilen.size
    val messer = rememberTextMeasurer()
    val dichte = LocalDensity.current
    val stil = MaterialTheme.typography.bodyMedium
    val kopfStil = stil.copy(fontWeight = FontWeight.SemiBold)
    val linkFarbe = MaterialTheme.colorScheme.primary
    val codeHintergrund = MaterialTheme.colorScheme.surfaceContainerHighest
    val maxSpalte = if (vollbild) 360.dp else 220.dp
    val innen = 12.dp

    val kopf = remember(tabelle, linkFarbe) { tabelle.kopf.map { inline(it, linkFarbe, codeHintergrund) } }
    val koerper = remember(tabelle, linkFarbe) {
        tabelle.zeilen.map { z -> z.map { inline(it, linkFarbe, codeHintergrund) } }
    }
    val breiten: List<Dp> = remember(tabelle, vollbild, stil) {
        val grenze = with(dichte) { maxSpalte.roundToPx() }
        tabelle.kopf.indices.map { s ->
            val texte = listOf(kopf[s] to kopfStil) + koerper.map { it[s] to stil }
            val breitest = texte.maxOf { (t, st) ->
                messer.measure(t, st, maxLines = 1, constraints = Constraints(maxWidth = Constraints.Infinity)).size.width
            }
            with(dichte) { breitest.coerceAtMost(grenze).toDp() } + innen * 2 + 1.dp
        }
    }
    // Rechtsbuendig, wenn die Trennzeile es sagt oder die Spalte aus Zahlen besteht.
    val ausrichtung = remember(tabelle) {
        tabelle.kopf.indices.map { s ->
            when (tabelle.ausrichtung[s]) {
                Ausrichtung.ENDE -> TextAlign.End
                Ausrichtung.MITTE -> TextAlign.Center
                Ausrichtung.START -> TextAlign.Start
                Ausrichtung.OFFEN -> {
                    val werte = tabelle.zeilen.map { it[s] }.filter { it.isNotBlank() }
                    if (werte.isNotEmpty() && werte.all(::istZahl)) TextAlign.End else TextAlign.Start
                }
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = Color.Transparent,
        modifier = modifier.then(if (beiVollbild != null) Modifier.clickable(onClick = beiVollbild) else Modifier),
    ) {
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Column {
                Row(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    kopf.forEachIndexed { s, zelle ->
                        Zelle(zelle, kopfStil, breiten[s], ausrichtung[s], innen)
                    }
                }
                koerper.forEach { zeile ->
                    HorizontalDivider(
                        modifier = Modifier.width(breiten.fold(0.dp) { a, b -> a + b }),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Row {
                        zeile.forEachIndexed { s, zelle -> Zelle(zelle, stil, breiten[s], ausrichtung[s], innen) }
                    }
                }
                if (weitere > 0) {
                    Text(
                        text = pluralStringResource(R.plurals.tabelle_weitere_zeilen, weitere, weitere),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = innen, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** Siehe [TabellenAnsicht]. */
private const val MAX_ZEILEN_BLASE = 50

@Composable
private fun Zelle(text: AnnotatedString, stil: TextStyle, breite: Dp, ausrichtung: TextAlign, innen: Dp) {
    Text(
        text = text,
        style = stil,
        textAlign = ausrichtung,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.width(breite).padding(horizontal = innen, vertical = 8.dp),
    )
}

/**
 * Codeblock mit Kopierknopf. Pfade, Befehle und Konfiguration will man fast
 * immer weitergeben - markieren auf dem Handy ist dafuer zu fummelig.
 */
@Composable
fun CodeBlock(code: Abschnitt.Code, modifier: Modifier = Modifier) {
    val kontext = LocalContext.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp),
            ) {
                Text(
                    text = code.sprache,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { kopieren(kontext, code.inhalt) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.ContentCopy,
                        stringResource(R.string.antwort_kopieren),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SelectionContainer {
                Text(
                    text = code.inhalt,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = false,
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                )
            }
        }
    }
}

/** Vollbild fuer Tabelle oder Diagramm, mit Schliessen-Knopf. Laesst sich drehen. */
@Composable
fun Vollbild(titel: String, beiSchliessen: () -> Unit, inhalt: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = beiSchliessen,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp, end = 16.dp),
                ) {
                    IconButton(onClick = beiSchliessen) {
                        Icon(Icons.Default.Close, stringResource(R.string.schliessen))
                    }
                    Text(
                        text = titel,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    inhalt()
                }
            }
        }
    }
}

/** Kleiner Knopf "Vollbild" fuer Kopfzeilen. */
@Composable
fun VollbildKnopf(beiKlick: () -> Unit) {
    IconButton(onClick = beiKlick, modifier = Modifier.size(36.dp)) {
        Icon(
            Icons.Default.OpenInFull,
            stringResource(R.string.antwort_vollbild),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

fun kopieren(kontext: Context, text: String) {
    val ablage = kontext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    ablage.setPrimaryClip(ClipData.newPlainText("Chef-Assistent", text))
    // Ab Android 13 zeigt das System selbst eine Bestaetigung.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(kontext, R.string.antwort_kopiert, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Inline-Markdown einer Tabellenzelle: **fett**, *kursiv*, `Code`, ~~durch~~ und
 * [Text](url). Mehr kommt in Zellen nicht vor; was nicht passt, bleibt Text.
 */
internal fun inline(text: String, linkFarbe: Color, codeHintergrund: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val rest = text.substring(i)
        val treffer = INLINE.find(rest)
        if (treffer == null || treffer.range.first != 0) {
            val bis = treffer?.range?.first ?: rest.length
            append(rest.substring(0, bis))
            i += bis
            continue
        }
        val g = treffer.groups
        when {
            g[1] != null -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(g[1]!!.value) }
            g[2] != null -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(g[2]!!.value) }
            g[3] != null -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeHintergrund)) {
                append(g[3]!!.value)
            }
            g[4] != null -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(g[4]!!.value) }
            g[5] != null -> withLink(
                LinkAnnotation.Url(g[6]!!.value, TextLinkStyles(SpanStyle(color = linkFarbe, textDecoration = TextDecoration.Underline)))
            ) { append(g[5]!!.value) }
            g[7] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[7]!!.value) }
        }
        i += treffer.value.length
    }
}

private val INLINE = Regex("""\*\*(.+?)\*\*|__(.+?)__|`([^`]+)`|~~(.+?)~~|\[([^]]+)]\(([^)\s]+)\)|\*([^*\s][^*]*?)\*""")
