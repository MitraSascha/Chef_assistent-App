package de.chefassistent.app.ui.komponenten

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.chefassistent.app.R

/*
 * Gemeinsame Bausteine fuer den Look nach One UI 8, abgeglichen am 2026-09-28
 * mit Einstellungen, Eigene Dateien und Reminder auf dem Galaxy S25, dazu
 * Material 3 Expressive (Google Play).
 *
 * - Kopfzeile wie bei Samsung: grosser zentrierter Titel im oberen Drittel,
 *   der beim Scrollen zu einer schmalen Leiste schrumpft. Die Knoepfe sitzen
 *   unten im Kopf, also naeher am Daumen.
 * - Listen als Gruppen: eine stark gerundete Karte je Gruppe, die Eintraege
 *   darin durch eingerueckte Linien getrennt.
 */

/** Seitenrand fuer Listen in Gruppen. */
val SEITENRAND = 16.dp

/** Karten 12 dp (Auftrag 1.21, B1), vorher 26 dp nach One UI. */
private val AUSSEN = 12.dp

/** Einzug der Trennlinie bei Eintraegen mit Symbol (Symbol + Abstand). */
val EINZUG_MIT_SYMBOL = 72.dp

/**
 * Form eines Eintrags an Position [index] in einer Gruppe mit [anzahl]
 * Eintraegen: zusammen ergeben sie eine Karte mit runden Ecken.
 */
fun gruppenForm(index: Int, anzahl: Int): Shape {
    val oben = if (index == 0) AUSSEN else 0.dp
    val unten = if (index == anzahl - 1) AUSSEN else 0.dp
    return RoundedCornerShape(topStart = oben, topEnd = oben, bottomStart = unten, bottomEnd = unten)
}

/**
 * Ein Eintrag einer Gruppe, siehe [gruppenForm]. Ab dem zweiten Eintrag steht
 * oben eine Trennlinie mit [einzug], wie in den Samsung-Einstellungen.
 */
@Composable
fun GruppenBlock(
    index: Int,
    anzahl: Int,
    modifier: Modifier = Modifier,
    farbe: Color = MaterialTheme.colorScheme.surfaceContainer,
    einzug: Dp = 20.dp,
    inhalt: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = gruppenForm(index, anzahl),
        color = farbe,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            if (index > 0) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(start = einzug, end = 20.dp),
                )
            }
            inhalt()
        }
    }
}

/** Eine ganze Gruppe als eine Karte, fuer kurze Listen ausserhalb einer LazyColumn. */
@Composable
fun GruppenKarte(
    modifier: Modifier = Modifier,
    farbe: Color = MaterialTheme.colorScheme.surfaceContainer,
    inhalt: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(AUSSEN),
        color = farbe,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(content = inhalt)
    }
}

/** Ueberschrift ueber einer Gruppe, klein und eingerueckt wie bei Samsung. */
@Composable
fun GruppenTitel(
    text: String,
    modifier: Modifier = Modifier,
    farbe: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = farbe,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** Symbol in einem farbigen Kreis, wie vor den Eintraegen der Samsung-Einstellungen. */
@Composable
fun RundesSymbol(
    symbol: ImageVector,
    farbe: Color,
    modifier: Modifier = Modifier,
    symbolFarbe: Color = Color.White,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(36.dp)
            .background(farbe, CircleShape),
    ) {
        Icon(symbol, contentDescription = null, tint = symbolFarbe, modifier = Modifier.size(20.dp))
    }
}

/** Innenabstand fuer eine LazyColumn mit Gruppen unter einer [GrosseKopfleiste]. */
fun listenAbstand(innen: PaddingValues): PaddingValues = PaddingValues(
    start = SEITENRAND,
    end = SEITENRAND,
    top = innen.calculateTopPadding() + 4.dp,
    bottom = innen.calculateBottomPadding() + 24.dp,
)

/** Gruppen liegen ohne Luft aneinander; Abstaende zwischen Gruppen macht [GruppenTitel]. */
val gruppenAnordnung = Arrangement.Top

/**
 * Bildschirm mit Samsung-Kopfzeile. Der Inhalt bekommt den Innenabstand und
 * muss scrollbar sein, damit die Kopfzeile schrumpft.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrosseKopfleiste(
    titel: String,
    beiZurueck: (() -> Unit)?,
    modifier: Modifier = Modifier,
    untertitel: String? = null,
    /** Grosser Titel: hoechstens so viele Zeilen, danach "…". */
    titelZeilen: Int = 2,
    snackbar: SnackbarHostState? = null,
    /**
     * Seit v1.21.0: nur die schmale Leiste mit Titel und Untertitel, ohne den
     * grossen Kopf. Fuer Ansichten mit festem Kopfbereich wie den Kalender.
     */
    kompakt: Boolean = false,
    aktionen: @Composable RowScope.() -> Unit = {},
    knopf: @Composable () -> Unit = {},
    inhalt: @Composable (PaddingValues) -> Unit,
) {
    if (kompakt) {
        Scaffold(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = { KompakterKopf(titel, untertitel, beiZurueck, aktionen) },
            snackbarHost = { if (snackbar != null) FehlerSnackbar(snackbar) },
            floatingActionButton = knopf,
            content = inhalt,
        )
        return
    }
    val verhalten = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(verhalten.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            SamsungKopf(
                titel = titel,
                untertitel = untertitel,
                titelZeilen = titelZeilen,
                verhalten = verhalten,
                beiZurueck = beiZurueck,
                aktionen = aktionen,
            )
        },
        snackbarHost = {
            if (snackbar != null) FehlerSnackbar(snackbar)
        },
        floatingActionButton = knopf,
        content = inhalt,
    )
}

/**
 * Ausgeklappt knapp ein Drittel der Bildschirmhoehe mit zentriertem Titel,
 * eingeklappt eine 64-dp-Leiste mit kleinem Titel. Die Hoehe haengt am
 * [TopAppBarScrollBehavior], das auch das Einrasten am Ende eines Wischers uebernimmt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SamsungKopf(
    titel: String,
    untertitel: String?,
    titelZeilen: Int,
    verhalten: TopAppBarScrollBehavior,
    beiZurueck: (() -> Unit)?,
    aktionen: @Composable RowScope.() -> Unit,
) {
    val farben = MaterialTheme.colorScheme
    val dichte = LocalDensity.current
    val eingeklappt = 64.dp
    val ausgeklappt = (LocalConfiguration.current.screenHeightDp * 0.30f).dp.coerceAtLeast(eingeklappt + 96.dp)
    val grenzePx = with(dichte) { (eingeklappt - ausgeklappt).toPx() }
    SideEffect {
        if (verhalten.state.heightOffsetLimit != grenzePx) verhalten.state.heightOffsetLimit = grenzePx
    }
    val versatz = with(dichte) { verhalten.state.heightOffset.toDp() }
    val anteil = verhalten.state.collapsedFraction

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(farben.surface)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        // Grosser Titel: verblasst beim Einklappen schneller, als der Platz schrumpft.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height((ausgeklappt - eingeklappt + versatz).coerceAtLeast(0.dp))
                .alpha((1f - anteil * 1.6f).coerceIn(0f, 1f)),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = titel,
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp, lineHeight = 44.sp),
                    textAlign = TextAlign.Center,
                    maxLines = titelZeilen,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                if (untertitel != null) {
                    Text(
                        text = untertitel,
                        style = MaterialTheme.typography.bodyLarge,
                        color = farben.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(eingeklappt)
                .padding(horizontal = 4.dp),
        ) {
            if (beiZurueck != null) {
                IconButton(onClick = beiZurueck) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.zurueck))
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text = titel,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
                    .alpha(((anteil - 0.5f) * 2f).coerceIn(0f, 1f)),
            )
            aktionen()
        }
    }
}

@Composable
private fun KompakterKopf(
    titel: String,
    untertitel: String?,
    beiZurueck: (() -> Unit)?,
    aktionen: @Composable RowScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(64.dp)
            .padding(horizontal = 4.dp),
    ) {
        if (beiZurueck != null) {
            IconButton(onClick = beiZurueck) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.zurueck))
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
            Text(titel, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (untertitel != null) {
                Text(
                    untertitel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        aktionen()
    }
}

/** Fehlermeldungen des Servers (`detail`) als Snackbar im Fehlerton. */
@Composable
fun FehlerSnackbar(zustand: SnackbarHostState) {
    SnackbarHost(zustand) { daten ->
        Snackbar(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = RoundedCornerShape(20.dp),
        ) { Text(daten.visuals.message) }
    }
}

/** Leerer Zustand: ruhiger Text in einer Karte statt verloren oben links. */
@Composable
fun LeerHinweis(text: String, modifier: Modifier = Modifier) {
    GruppenKarte(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(24.dp),
        )
    }
}
