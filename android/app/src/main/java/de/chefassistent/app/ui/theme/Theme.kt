package de.chefassistent.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Farben seit v1.21.0 (Auftrag 1.21, B1): dieselbe Handschrift wie die
 * Weboberflaeche (`/web/app.css`). Ruhige helle Flaeche, Navy als Hauptfarbe,
 * Gold als Akzent. Kein dynamisches Material-You-Schema.
 *
 * Zuordnung zu Material: `surface` = Hintergrund, `surfaceContainer` = Flaeche
 * (Karten, Leisten), `primaryContainer` = eigene Nachricht, `secondary` = Gold,
 * `secondaryContainer` = Hinweis-/Fragekarte. Die `surfaceContainer*`-Stufen
 * muessen hier stehen, sonst nimmt Material die violette Vorgabe.
 *
 * **`primary` ist im Dunkeln das helle Linkblau, nicht `#2F5878`.** Material
 * nimmt `primary` auch als Textfarbe (Textknoepfe, Links), und `#2F5878` hat auf
 * `#1B232A` nur etwa 2:1 Kontrast. Gefuellte Flaechen in der Hauptfarbe (Senden,
 * Neues Gespraech, Ansehen, M-Zeichen) nehmen deshalb [hauptflaeche].
 */
private val Navy = Color(0xFF1B3244)
private val NavyDunkel = Color(0xFF2F5878)
private val Gold = Color(0xFFDA9C4D)

private val HellesSchema = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EEF3),
    onPrimaryContainer = Color(0xFF1D2327),
    secondary = Gold,
    onSecondary = Navy,
    secondaryContainer = Color(0xFFFBF0DF),
    onSecondaryContainer = Color(0xFF8A5A17),
    tertiary = Color(0xFF2A5F86),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE2E7EC),
    onTertiaryContainer = Color(0xFF1D2327),
    background = Color(0xFFF6F5F2),
    onBackground = Color(0xFF1D2327),
    surface = Color(0xFFF6F5F2),
    onSurface = Color(0xFF1D2327),
    surfaceVariant = Color(0xFFEEF0F2),
    onSurfaceVariant = Color(0xFF5B6570),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAF9F7),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEFEDE8),
    surfaceContainerHighest = Color(0xFFE9E7E2),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE9E7E2),
    inverseSurface = Color(0xFF1D2327),
    inverseOnSurface = Color(0xFFF6F5F2),
    inversePrimary = Color(0xFF8CC0EA),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFFBE7E5),
    onErrorContainer = Color(0xFF8C1D18),
    outline = Color(0xFFCFCAC0),
    outlineVariant = Color(0xFFE3E0D9),
)

private val DunklesSchema = darkColorScheme(
    primary = Color(0xFF8CC0EA),
    onPrimary = Color(0xFF0F1418),
    primaryContainer = Color(0xFF24323E),
    onPrimaryContainer = Color(0xFFE6E8EA),
    secondary = Gold,
    onSecondary = Navy,
    secondaryContainer = Color(0xFF33291B),
    onSecondaryContainer = Color(0xFFE8B673),
    tertiary = Color(0xFF8CC0EA),
    onTertiary = Color(0xFF0F1418),
    tertiaryContainer = Color(0xFF26323C),
    onTertiaryContainer = Color(0xFFE6E8EA),
    background = Color(0xFF12181D),
    onBackground = Color(0xFFE6E8EA),
    surface = Color(0xFF12181D),
    onSurface = Color(0xFFE6E8EA),
    surfaceVariant = Color(0xFF232D35),
    onSurfaceVariant = Color(0xFF9AA4AD),
    surfaceContainerLowest = Color(0xFF0F1418),
    surfaceContainerLow = Color(0xFF182027),
    surfaceContainer = Color(0xFF1B232A),
    surfaceContainerHigh = Color(0xFF232D35),
    surfaceContainerHighest = Color(0xFF2A343D),
    surfaceBright = Color(0xFF2A343D),
    surfaceDim = Color(0xFF0F1418),
    inverseSurface = Color(0xFFE6E8EA),
    inverseOnSurface = Color(0xFF12181D),
    inversePrimary = Navy,
    error = Color(0xFFF2A49C),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF3A2220),
    onErrorContainer = Color(0xFFF2A49C),
    outline = Color(0xFF3A4651),
    outlineVariant = Color(0xFF2A343D),
)

private val ColorScheme.istDunkel: Boolean get() = background.luminance() < 0.5f

/** Gefuellte Flaechen in der Hauptfarbe: Senden, Neues Gespraech, Ansehen, M-Zeichen. Text darauf weiss. */
val ColorScheme.hauptflaeche: Color get() = if (istDunkel) NavyDunkel else Navy

/** Akzent fuer Zaehler, Fokus und „laeuft“. Nie als Textfarbe auf Weiss, nur als Flaeche oder Punkt. */
val ColorScheme.gold: Color get() = Gold

/** Aktiver Eintrag in Listen und Navigation. */
val ColorScheme.auswahl: Color get() = if (istDunkel) Color(0xFF26323C) else Color(0xFFE2E7EC)

/** Untere Navigationsleiste (Seitenleiste im Web). */
val ColorScheme.navigationsflaeche: Color get() = if (istDunkel) Color(0xFF0F1418) else Color(0xFFEFEDE8)

/**
 * Erfolg, z. B. „fertig“, „genehmigt“, „BER-1330“. Material 3 kennt keine
 * Erfolgsfarbe, und `primary` ist hier Navy. Hell etwa 5:1 auf Weiss.
 */
val ColorScheme.erfolg: Color get() = if (istDunkel) Color(0xFF81C784) else Color(0xFF2E7D32)

/** Schrift auf einer Flaeche in [erfolg]. */
val ColorScheme.aufErfolg: Color get() = if (istDunkel) Color(0xFF0F1418) else Color.White

/** Links im Fliesstext. */
val ColorScheme.link: Color get() = if (istDunkel) Color(0xFF8CC0EA) else Color(0xFF2A5F86)

/** Kraeftigere Titel wie in One UI und Material 3 Expressive, Fliesstext unveraendert. */
private val Schrift = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    // Text 15 sp, leise Zeilen 12-13 sp (Auftrag 1.21, B1).
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

/**
 * Rundungen nach Auftrag 1.21 (B1): Knoepfe 8-10 dp, Karten 12 dp, Eingabe 18 dp.
 * Dialoge und Sheets behalten `extraLarge`.
 */
private val Formen = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/**
 * Warnfarbe Bernstein, z. B. fuer teure Modelle (Auftrag 1.20). Material 3 kennt
 * keine eigene Warnstufe, und `error` (rot) soll Fehlern vorbehalten bleiben.
 * Werte wie `--warnung` der Weboberflaeche. Unterschieden wird am Hintergrund des Schemas.
 */
val ColorScheme.warnung: Color
    get() = if (istDunkel) Color(0xFFF0A14A) else Color(0xFFB45309)

@Composable
fun ChefAssistentTheme(
    dunkel: Boolean = isSystemInDarkTheme(),
    inhalt: @Composable () -> Unit,
) {
    // Systemleisten regelt `enableEdgeToEdge()` in der MainActivity. `window.statusBarColor`
    // ist ab API 35 wirkungslos und wird hier bewusst nicht gesetzt.
    val schema = if (dunkel) DunklesSchema else HellesSchema

    MaterialTheme(
        colorScheme = schema,
        typography = Schrift,
        shapes = Formen,
        content = inhalt,
    )
}
