package de.chefassistent.app.ui

import android.text.SpannableStringBuilder
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.text.util.Linkify
import android.widget.TextView
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import de.chefassistent.app.ui.theme.link
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.linkify.LinkifyPlugin

/**
 * Zeigt das Markdown aus `antwort` an.
 *
 * Compose bringt keinen Markdown-Renderer mit, deshalb ein [TextView] unter
 * Markwon. Die Farben kommen von aussen, damit der Text dem Compose-Theme folgt.
 *
 * Markwon in der Grundausstattung kann **keine Tabellen** und verlinkt **keine
 * nackten URLs** - beides kam in einer Formatprobe des Assistenten tatsaechlich
 * vor und wurde als roher Text angezeigt. Deshalb die drei Zusatzmodule:
 *
 * - [TablePlugin]: `| Spalte | Spalte |` wird eine Tabelle statt einer Strichwueste
 * - [LinkifyPlugin]: `https://…` ohne Klammern wird anklickbar
 * - [StrikethroughPlugin]: `~~durchgestrichen~~`
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    farbe: Color = MaterialTheme.colorScheme.onSurface,
    linkFarbe: Color = MaterialTheme.colorScheme.link,
) {
    val kontext = LocalContext.current
    val markwon = remember(kontext) {
        Markwon.builder(kontext)
            .usePlugin(TablePlugin.create(kontext))
            .usePlugin(StrikethroughPlugin.create())
            // Bewusst ohne Linkify.PHONE_NUMBERS: die Erkennung haelt deutsche
            // Datumsangaben wie "23.09.2026" fuer Telefonnummern und macht daraus
            // Links, die die Telefon-App oeffnen. Datumsangaben kommen in den
            // Antworten weit haeufiger vor als Rufnummern.
            .usePlugin(LinkifyPlugin.create(Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES))
            .build()
    }
    val groesse = LocalTextStyle.current.fontSize.value

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                setTextIsSelectable(true)
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { ansicht ->
            ansicht.setTextColor(farbe.toArgb())
            ansicht.textSize = if (groesse > 0f) groesse else 16f
            // Bewusst eine andere Farbe als der Fliesstext: vorher waren Links
            // nur an der Unterstreichung zu erkennen.
            ansicht.setLinkTextColor(linkFarbe.toArgb())
            val gerendert = SpannableStringBuilder(markwon.toMarkdown(markdown))
            dateinamenEntlinken(gerendert)
            markwon.setParsedMarkdown(ansicht, gerendert)
        },
    )
}

/**
 * Endungen, die zugleich Top-Level-Domains sind. Linkify macht aus
 * "2026-09-24 App-Test Fotos.md" sonst einen Link auf `http://Fotos.md` (Moldau) -
 * am Geraet am 2026-09-24 so gesehen. Dateinamen kommen in den Antworten
 * staendig vor, nackte Domains mit diesen Endungen praktisch nie.
 */
private val DATEIENDUNGEN_ALS_DOMAIN = setOf("md", "zip", "mov", "sh", "py", "pl", "rs")

/**
 * Nimmt Linkify-Links von Dateinamen wieder weg. Betroffen sind nur Links ohne
 * `http`/`www` im sichtbaren Text - echte Adressen und `[Text](url)` bleiben.
 */
internal fun dateinamenEntlinken(text: SpannableStringBuilder) {
    text.getSpans(0, text.length, URLSpan::class.java).forEach { link ->
        val sichtbar = text.subSequence(text.getSpanStart(link), text.getSpanEnd(link)).toString()
        val ohneSchema = !sichtbar.startsWith("http", ignoreCase = true) &&
            !sichtbar.startsWith("www.", ignoreCase = true)
        val vonLinkify = link.url.equals("http://$sichtbar", ignoreCase = true)
        val endung = sichtbar.substringAfterLast('.', "").lowercase()
        if (ohneSchema && vonLinkify && endung in DATEIENDUNGEN_ALS_DOMAIN) {
            text.removeSpan(link)
        }
    }
}

/**
 * Entfernt die Markdown-Zeichen fuer die Sprachausgabe - vorgelesen werden soll
 * der Satz, nicht "Sternchen Sternchen".
 *
 * Tabellen bekommen eine Sonderbehandlung: die Trennzeile faellt weg, und aus
 * den Strichen werden Kommas. Sonst liest die Stimme "senkrechter Strich"
 * zwischen jeder Zelle vor.
 */
fun markdownEntfernen(markdown: String): String =
    markdown
        // Codebloecke und Diagramme, mit ``` oder ~~~ - der Zerleger kennt beide.
        .replace(Regex("""(```|~~~)[\s\S]*?\1"""), " ")
        // Trennzeile samt ihrem Zeilenumbruch entfernen. Bewusst [ \t] statt \s:
        // \s schliesst den Zeilenumbruch ein und wuerde die Tabellenzeilen
        // zusammenkleben.
        .replace(Regex("""^[ \t]*\|[ \t:|-]*\|[ \t]*\r?\n?""", RegexOption.MULTILINE), "")
        .replace(Regex("""^[ \t]*\|(.*)\|[ \t]*$""", RegexOption.MULTILINE)) { treffer ->
            treffer.groupValues[1].split('|').joinToString(", ") { it.trim() }
        }
        .replace(Regex("""`([^`]*)`"""), "$1")
        .replace(Regex("""~~([^~]*)~~"""), "$1")
        .replace(Regex("""\*\*([^*]*)\*\*"""), "$1")
        .replace(Regex("""\*([^*]*)\*"""), "$1")
        .replace(Regex("""^\s{0,3}#{1,6}\s*""", RegexOption.MULTILINE), "")
        // [ \t] statt \s, aus demselben Grund wie oben: sonst verschluckt der
        // Ausdruck Leerzeilen davor und klebt Absaetze zusammen.
        .replace(Regex("""^[ \t]*>[ \t]?""", RegexOption.MULTILINE), "")
        .replace(Regex("""^[ \t]*[-*+][ \t]+""", RegexOption.MULTILINE), "")
        .replace(Regex("""\[([^]]*)]\([^)]*\)"""), "$1")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()
