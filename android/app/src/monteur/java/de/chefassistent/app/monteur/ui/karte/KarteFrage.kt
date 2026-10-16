package de.chefassistent.app.monteur.ui.karte

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.chefassistent.app.R
import de.chefassistent.app.monteur.data.AnleitungTreffer
import de.chefassistent.app.monteur.data.Antwort
import de.chefassistent.app.monteur.data.Artikel
import de.chefassistent.app.monteur.data.Karte
import de.chefassistent.app.monteur.data.Quelle
import de.chefassistent.app.ui.MarkdownText
import de.chefassistent.app.ui.komponenten.TypMarke
import de.chefassistent.app.ui.markdownEntfernen
import de.chefassistent.app.ui.theme.link
import java.net.URI

/**
 * Antwortkarte `frage` (Auftrag 1.23, 2.): Antwort aus Anleitungsbibliothek,
 * Katalog und Internet.
 *
 * Oben die frueheren Runden derselben Frage (Nachfragen mit `bezug_id`) als
 * kurzer, eingeklappter Verlauf - der Monteur soll sehen, worauf die neue Antwort
 * aufbaut, aber nicht durch drei volle Antworten scrollen muessen. Darunter die
 * aktuelle Antwort voll und gross: auf der Baustelle liest man mit Handschuhen
 * und Abstand. Die Frage selbst zeigt schon der Kopf der Kartenhuelle.
 */
@Composable
fun FrageInhalt(karte: Karte, verlauf: List<Karte>, beiAnleitung: (AnleitungTreffer) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (verlauf.isNotEmpty()) {
            verlauf.forEach { frueher -> FrageVerlaufEintrag(frueher, beiAnleitung) }
            HorizontalDivider()
        }
        val antwort = karte.antwort
        if (antwort == null || antwort.text.isBlank() && antwort.istOhneAnhang()) {
            Text(
                stringResource(R.string.f_keine_antwort),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FrageAntwort(antwort, beiAnleitung, gross = true)
        }
    }
}

/** Text plus Quellen, Anleitungen und Artikel; leere Listen fallen weg. */
@Composable
private fun FrageAntwort(antwort: Antwort, beiAnleitung: (AnleitungTreffer) -> Unit, gross: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (antwort.text.isNotBlank()) {
            // MarkdownText nimmt die Groesse aus LocalTextStyle.
            val stil = if (gross) {
                MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp)
            } else {
                MaterialTheme.typography.bodyMedium
            }
            CompositionLocalProvider(LocalTextStyle provides stil) {
                MarkdownText(antwort.text, Modifier.fillMaxWidth())
            }
        }
        if (antwort.quellen.isNotEmpty()) {
            FrageAbschnitt(stringResource(R.string.f_quellen)) {
                antwort.quellen.forEach { FrageQuelle(it) }
            }
        }
        if (antwort.anleitungen.isNotEmpty()) {
            FrageAbschnitt(stringResource(R.string.f_anleitungen)) {
                antwort.anleitungen.forEach { FrageAnleitung(it, beiAnleitung) }
            }
        }
        if (antwort.artikel.isNotEmpty()) {
            FrageAbschnitt(stringResource(R.string.f_artikel)) { FrageArtikelChips(antwort.artikel) }
        }
    }
}

@Composable
private fun FrageAbschnitt(titel: String, inhalt: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            titel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        inhalt()
    }
}

/** Eine fruehere Runde: Frage klein und grau, Antwort gekuerzt, antippen klappt auf. */
@Composable
private fun FrageVerlaufEintrag(karte: Karte, beiAnleitung: (AnleitungTreffer) -> Unit) {
    var offen by rememberSaveable(karte.id) { mutableStateOf(false) }
    val antwort = karte.antwort
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { offen = !offen }
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (karte.text.isNotBlank()) {
                Text(
                    stringResource(R.string.f_du, karte.text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (offen) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                antwort == null -> Text(
                    stringResource(R.string.f_keine_antwort),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                offen -> {
                    FrageAntwort(antwort, beiAnleitung, gross = false)
                    Text(
                        stringResource(R.string.f_zuklappen),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.link,
                    )
                }
                else -> {
                    Text(
                        frageKurzfassung(antwort.text),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.f_aufklappen),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.link,
                    )
                }
            }
        }
    }
}

@Composable
private fun FrageQuelle(quelle: Quelle) {
    val kontext = LocalContext.current
    val youtube = istYoutube(quelle.url)
    FrageZeile(onClick = { extern(kontext, quelle.url) }) {
        if (youtube) {
            Icon(
                Icons.Outlined.PlayCircle,
                contentDescription = stringResource(R.string.f_video),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            quellenTitel(quelle),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (youtube) FontWeight.SemiBold else null,
            color = MaterialTheme.colorScheme.link,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Outlined.OpenInNew,
            contentDescription = null,
            tint = LocalContentColor.current,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun FrageAnleitung(treffer: AnleitungTreffer, beiAnleitung: (AnleitungTreffer) -> Unit) {
    FrageZeile(onClick = { beiAnleitung(treffer) }) {
        TypMarke(treffer.pfad.ifBlank { "anleitung.pdf" })
        Text(
            stringResource(R.string.f_anleitung_zeile, anleitungTitel(treffer), treffer.seite),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Zeile als Karte mit mindestens 48 dp Tippflaeche. */
@Composable
private fun FrageZeile(onClick: () -> Unit, inhalt: @Composable RowScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            content = inhalt,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FrageArtikelChips(artikel: List<Artikel>) {
    val kontext = LocalContext.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        artikel.forEach { a ->
            AssistChip(
                onClick = { nummerKopieren(kontext, a.nr) },
                label = { Text(artikelText(a), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

private fun Antwort.istOhneAnhang(): Boolean = quellen.isEmpty() && anleitungen.isEmpty() && artikel.isEmpty()

/**
 * Oeffnet die Quelle ausserhalb der App. Bei YouTube-Links reicht ACTION_VIEW:
 * die YouTube-App meldet sich fuer youtube.com/youtu.be an, Android nimmt sie
 * dann statt des Browsers.
 */
private fun extern(kontext: Context, url: String) {
    try {
        kontext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(kontext, R.string.f_keine_app, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Artikelnummer in die Zwischenablage, damit der Monteur sie z. B. beim
 * Grosshaendler einfuegen kann. Ab Android 13 zeigt das System selbst eine
 * Bestaetigung - ein eigener Toast waere dann doppelt.
 */
private fun nummerKopieren(kontext: Context, nr: String) {
    val ablage = kontext.getSystemService(ClipboardManager::class.java) ?: return
    ablage.setPrimaryClip(ClipData.newPlainText(nr, nr))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(kontext, R.string.f_kopiert, Toast.LENGTH_SHORT).show()
    }
}

// ---- reine Logik, getestet in FrageTest ----

/** Host ohne `www.`, kleingeschrieben; leer, wenn die Adresse nicht lesbar ist. */
internal fun urlHost(url: String): String =
    runCatching { URI(url.trim()).host }.getOrNull().orEmpty().lowercase().removePrefix("www.")

/**
 * youtube.com samt Subdomains (m., music.) und youtu.be. Bewusst ueber den Host,
 * nicht per `contains("youtube")` - sonst gaelte auch youtube-fake.de.
 */
internal fun istYoutube(url: String): Boolean {
    val host = urlHost(url)
    return host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be"
}

/** Titel der Quelle, sonst der Host, sonst die nackte Adresse. */
internal fun quellenTitel(quelle: Quelle): String =
    quelle.titel.trim().ifBlank { urlHost(quelle.url) }.ifBlank { quelle.url }

/** Titel der Anleitung, sonst der Dateiname ohne `.pdf`. */
internal fun anleitungTitel(treffer: AnleitungTreffer): String =
    treffer.titel.trim().ifBlank {
        treffer.pfad.substringAfterLast('/').removeSuffix(".pdf").removeSuffix(".PDF")
    }

/** Chip „Name · Nr.“ - ohne Preise (Vertrag liefert keine). */
internal fun artikelText(artikel: Artikel): String =
    if (artikel.name.isBlank()) artikel.nr else "${artikel.name.trim()} · ${artikel.nr}"

/** Eingeklappte Antwort im Verlauf: ohne Markdown-Zeichen, Zeilen zusammengezogen. */
internal fun frageKurzfassung(text: String, max: Int = 200): String {
    val flach = markdownEntfernen(text).replace(Regex("""\s+"""), " ").trim()
    return if (flach.length <= max) flach else flach.take(max).trimEnd() + "…"
}
