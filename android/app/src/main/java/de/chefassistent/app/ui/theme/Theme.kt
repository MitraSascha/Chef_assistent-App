package de.chefassistent.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Gedecktes Gruen-Teal wie im App-Symbol. Kein dynamisches Material-You-Schema:
 * die App wird in Werkstatt und Buero benutzt, da ist ein vorhersagbarer,
 * kontraststarker Look mehr wert als Anpassung an das Handy-Hintergrundbild.
 *
 * Aufbau nach One UI 8 und Material 3 Expressive (2026-09-28): Der Hintergrund
 * ist eine ruhige Flaeche ([surface]), Inhalte liegen darauf in helleren bzw.
 * im Dunkeln etwas aufgehellten Bloecken ([surfaceContainer]). Trennlinien
 * braucht es dann kaum noch. Die `surfaceContainer*`-Stufen muessen hier stehen:
 * sonst nimmt Material die Vorgabe aus dem Baseline-Schema, und die ist violett.
 */
private val Gruen = Color(0xFF1F6F5C)
private val GruenHell = Color(0xFF6FD8BC)
private val Sand = Color(0xFF7A5B2E)
private val SandHell = Color(0xFFE8C48A)

private val HellesSchema = lightColorScheme(
    primary = Gruen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8EEDC),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Sand,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDDB0),
    onSecondaryContainer = Color(0xFF291800),
    tertiary = Color(0xFF3B6470),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBFE9F8),
    onTertiaryContainer = Color(0xFF001F27),
    background = Color(0xFFF1F4F2),
    onBackground = Color(0xFF191C1B),
    surface = Color(0xFFF1F4F2),
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFDBE5E0),
    onSurfaceVariant = Color(0xFF3F4945),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFCFB),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFE6ECE9),
    surfaceContainerHighest = Color(0xFFDDE4E1),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFD8DEDB),
    inverseSurface = Color(0xFF2D3130),
    inverseOnSurface = Color(0xFFEFF1EF),
    inversePrimary = GruenHell,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF6F7975),
    outlineVariant = Color(0xFFD3DBD7),
)

private val DunklesSchema = darkColorScheme(
    primary = GruenHell,
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF005143),
    onPrimaryContainer = Color(0xFFB8EEDC),
    secondary = SandHell,
    onSecondary = Color(0xFF442B00),
    secondaryContainer = Color(0xFF5F4100),
    onSecondaryContainer = Color(0xFFFFDDB0),
    tertiary = Color(0xFFA3CDDC),
    onTertiary = Color(0xFF033541),
    tertiaryContainer = Color(0xFF224C58),
    onTertiaryContainer = Color(0xFFBFE9F8),
    // Fast schwarz wie bei Samsung: spart auf OLED Strom und laesst die
    // Bloecke darueber sichtbar abheben.
    background = Color.Black,
    onBackground = Color(0xFFE1E3E1),
    surface = Color.Black,
    onSurface = Color(0xFFE1E3E1),
    surfaceVariant = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBFC9C4),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF131817),
    surfaceContainer = Color(0xFF1A1D1D),
    surfaceContainerHigh = Color(0xFF242A28),
    surfaceContainerHighest = Color(0xFF2F3533),
    surfaceBright = Color(0xFF343A38),
    surfaceDim = Color.Black,
    inverseSurface = Color(0xFFE1E3E1),
    inverseOnSurface = Color(0xFF2D3130),
    inversePrimary = Gruen,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF899390),
    outlineVariant = Color(0xFF3A413F),
)

/** Kraeftigere Titel wie in One UI und Material 3 Expressive, Fliesstext unveraendert. */
private val Schrift = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

/**
 * Grosszuegige Rundungen: Karten und Dialoge mit 24-28 dp wie die "focus blocks"
 * in One UI. Knoepfe sind in Material 3 ohnehin pillenfoermig.
 */
private val Formen = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

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
