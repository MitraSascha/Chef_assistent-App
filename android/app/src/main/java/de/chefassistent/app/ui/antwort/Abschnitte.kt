package de.chefassistent.app.ui.antwort

/**
 * Eine Antwort des Assistenten, zerlegt in Stuecke, die jeweils anders
 * dargestellt werden.
 *
 * Markwon kann Fliesstext gut, aber eine Tabelle quetscht es in die Blasenbreite,
 * einen Codeblock bekommt man nicht kopiert, und Diagramme kennt es gar nicht.
 * Deshalb geht nur [Text] an Markwon, der Rest wird in Compose gezeichnet.
 */
sealed interface Abschnitt {
    data class Text(val markdown: String) : Abschnitt

    data class Tabelle(
        val kopf: List<String>,
        val ausrichtung: List<Ausrichtung>,
        val zeilen: List<List<String>>,
    ) : Abschnitt

    data class Code(val sprache: String, val inhalt: String) : Abschnitt

    /** `API.md` 1a, seit v1.14.0. */
    data class Diagramm(val daten: DiagrammDaten) : Abschnitt
}

enum class Ausrichtung { START, MITTE, ENDE, OFFEN }

/**
 * Zerlegt Markdown in [Abschnitt]e. Bewusst zeilenweise und schlicht: der
 * Assistent schreibt "einfaches Markdown", und alles, was hier nicht erkannt
 * wird, landet unveraendert als Text bei Markwon - schlimmstenfalls sieht es
 * also aus wie bisher.
 *
 * Ein `diagramm`-Block, der sich nicht lesen laesst, faellt weg. Die Zahlen
 * stehen laut Vertrag immer noch einmal als Tabelle darunter.
 */
fun abschnitte(markdown: String): List<Abschnitt> {
    val zeilen = markdown.replace("\r\n", "\n").split('\n')
    val ergebnis = mutableListOf<Abschnitt>()
    val text = StringBuilder()

    fun textAbschliessen() {
        if (text.isNotBlank()) ergebnis += Abschnitt.Text(text.toString().trim('\n'))
        text.clear()
    }

    var i = 0
    while (i < zeilen.size) {
        val zeile = zeilen[i]
        val zaun = ZAUN.matchEntire(zeile)
        if (zaun != null) {
            val zeichen = zaun.groupValues[2]
            val sprache = zaun.groupValues[3].trim().substringBefore(' ').lowercase()
            val inhalt = mutableListOf<String>()
            var j = i + 1
            while (j < zeilen.size && !istZaunEnde(zeilen[j], zeichen)) {
                inhalt += zeilen[j]
                j++
            }
            textAbschliessen()
            val roh = inhalt.joinToString("\n")
            if (sprache == SPRACHE_DIAGRAMM) {
                diagrammLesen(roh)?.let { ergebnis += Abschnitt.Diagramm(it) }
            } else {
                ergebnis += Abschnitt.Code(sprache, roh.trimEnd())
            }
            i = j + 1
            continue
        }

        // Trennzeile mit mindestens einem Strich und gleich vielen Spalten wie der
        // Kopf - sonst wurde ein Satz mit "|" vor einer Linie "---" zur Tabelle.
        if (i + 1 < zeilen.size && '|' in zeile && '|' in zeilen[i + 1] && istTrennzeile(zeilen[i + 1]) &&
            zellen(zeilen[i + 1]).size == zellen(zeile).size
        ) {
            val kopf = zellen(zeile)
            val ausrichtung = zellen(zeilen[i + 1]).map(::ausrichtungAus)
            val koerper = mutableListOf<List<String>>()
            var j = i + 2
            while (j < zeilen.size && '|' in zeilen[j] && zeilen[j].isNotBlank()) {
                koerper += zellen(zeilen[j])
                j++
            }
            textAbschliessen()
            val spalten = kopf.size
            ergebnis += Abschnitt.Tabelle(
                kopf = kopf,
                ausrichtung = List(spalten) { ausrichtung.getOrElse(it) { Ausrichtung.OFFEN } },
                // Der Vertrag verspricht nichts ueber gleich lange Zeilen - auffuellen bzw. kappen.
                zeilen = koerper.map { z -> List(spalten) { z.getOrElse(it) { "" } } },
            )
            i = j
            continue
        }

        text.append(zeile).append('\n')
        i++
    }
    textAbschliessen()
    return ergebnis
}

/**
 * Text fuer "Kopieren" und "Teilen": ohne Diagrammdaten und ohne Sternchen, aber
 * mit Listen und Tabellen, damit es in einer Mail oder in WhatsApp lesbar bleibt.
 */
fun textZumKopieren(markdown: String): String =
    markdown
        .replace(Regex("""(?m)^[ \t]*(```|~~~)[ \t]*diagramm\b[\s\S]*?^[ \t]*\1[ \t]*$\n?"""), "")
        .replace(Regex("""\[([^]]*)]\(([^)]*)\)"""), "$1 ($2)")
        .replace(Regex("""\*\*([^*]+)\*\*"""), "$1")
        .replace(Regex("""__([^_]+)__"""), "$1")
        .replace(Regex("""~~([^~]+)~~"""), "$1")
        .replace(Regex("""(?<!`)`([^`\n]+)`(?!`)"""), "$1")
        .replace(Regex("""(?m)^#{1,6}[ \t]+"""), "")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()

private const val SPRACHE_DIAGRAMM = "diagramm"

/** Oeffnender Zaun: bis zu 3 Leerzeichen, dann ``` oder ~~~ (auch laenger), dann die Sprache. */
private val ZAUN = Regex("""^( {0,3})(`{3,}|~{3,})(.*)$""")

private fun istZaunEnde(zeile: String, oeffnend: String): Boolean {
    val t = zeile.trim()
    return t.length >= oeffnend.length && t.all { it == oeffnend[0] }
}

private val TRENNZEILE = Regex("""^[ \t]*\|?[ \t]*:?-+:?[ \t]*(\|[ \t]*:?-+:?[ \t]*)*\|?[ \t]*$""")

private fun istTrennzeile(zeile: String): Boolean = '-' in zeile && TRENNZEILE.matches(zeile)

/** Zellen einer Tabellenzeile. `\|` bleibt ein Strich im Text. */
internal fun zellen(zeile: String): List<String> {
    var t = zeile.trim()
    if (t.startsWith("|")) t = t.substring(1)
    if (t.endsWith("|") && !t.endsWith("\\|")) t = t.dropLast(1)
    val teile = mutableListOf<String>()
    val aktuell = StringBuilder()
    var k = 0
    while (k < t.length) {
        val c = t[k]
        if (c == '\\' && k + 1 < t.length && t[k + 1] == '|') {
            aktuell.append('|')
            k += 2
            continue
        }
        if (c == '|') {
            teile += aktuell.toString().trim()
            aktuell.clear()
        } else {
            aktuell.append(c)
        }
        k++
    }
    teile += aktuell.toString().trim()
    return teile
}

private fun ausrichtungAus(trenner: String): Ausrichtung {
    val links = trenner.startsWith(":")
    val rechts = trenner.endsWith(":")
    return when {
        links && rechts -> Ausrichtung.MITTE
        rechts -> Ausrichtung.ENDE
        links -> Ausrichtung.START
        else -> Ausrichtung.OFFEN
    }
}

/** "1.820,25 €", "-12 %", "3,5 h" - solche Zellen stehen rechtsbuendig, damit die Stellen untereinander stehen. */
private val ZAHL_ZELLE = Regex("""^[-+−]?[ \t]*[€$]?[ \t]*\d[\d.,  ']*[ \t]*(%|€|h|Std\.?|m²|m|kg|Stk\.?|T€|Tsd\.?|Mio\.?)?$""")

internal fun istZahl(zelle: String): Boolean = ZAHL_ZELLE.matches(entferneInline(zelle).trim())

/** Nur fuer die Erkennung: Sternchen und Backticks weg. */
private fun entferneInline(text: String): String = text.replace("**", "").replace("`", "").replace("__", "")
