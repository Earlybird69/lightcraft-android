package ai.storyteller.lightcraft.ui

import ai.storyteller.lightcraft.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * The phone's colours: the reference app's dark theme on a phone, observed from the running app
 * (sampled from its screens), as LightCraft's desktop theme was observed from the desktop app.
 * The controls it doesn't let us sample keep LightCraft's desktop values (`crates/ui-egui/src/theme.rs`).
 */
object Lc {
    /** Behind the status bar (the top bar's colour, as the reference app's home screen). */
    val statusBar = Color(0xFF1D1D1D)

    /** Behind the navigation bar. */
    val navBar = Color(0xFF1D1D1D)

    /** Top bar, editor panels and tool rows. */
    val bar = Color(0xFF1D1D1D)

    /** The library's bottom area. */
    val bottomBar = Color(0xFF232323)

    /** Behind the photos and the photo being edited. */
    val canvas = Color(0xFF000000)

    /** A thumbnail cell while it loads. */
    val cell = Color(0xFF161616)
    val text = Color(0xFFD0D0D0)
    val textLabel = Color(0xFFC8C8C8)
    val textDim = Color(0xFF8E8E8E)
    val textDisabled = Color(0xFF5C5C5C)
    val icon = Color(0xFFA2A2A2)

    /** The selected tool (the editor's, which can't be sampled). */
    val selected = Color(0xFF4B9CF5)

    /** A sheet's current choice: its icon and label (sampled). */
    val choice = Color(0xFF4096F3)

    /** The check beside a sheet's current choice (sampled). */
    val check = Color(0xFF2680EB)

    /** The chosen tab of the bottom navigation, and a switch that's on (sampled). */
    val highlight = Color(0xFF5EAAF7)

    /** The primary action (the add button). */
    val primary = Color(0xFF066CE7)

    /** A line between a sheet's groups. */
    val divider = Color(0xFF4B4B4B)

    // LightCraft's own (desktop) values for what can't be sampled
    val button = Color(0xFF242424)
    val buttonBorder = Color(0xFF4C4C4C)
    val toolActive = Color(0xFF3F3F3F)
    val track = Color(0xFF5A5A5A)
    val thumb = Color(0xFFA0A0A0)
    val thumbActive = Color(0xFFE0E0E0)
    val star = Color(0xFFD8D8D8)
    val pick = Color(0xFFF0F0F0)
    val reject = Color(0xFFE04A4A)
    val caution = Color(0xFFE3A83C)

    /** The colour mixer's bands, as the desktop draws them: red, orange, yellow, green, aqua, blue, purple, magenta. */
    val bandColors = listOf(0xFFAA0000, 0xFFBB6600, 0xFFAAAA00, 0xFF009900, 0xFF00AAAA, 0xFF0044CC, 0xFF7722AA, 0xFFAA0077).map { Color(it) }
}

/** Inter, LightCraft's UI font (`assets/fonts`, OFL), copied into the app by the build. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

private val colors = darkColorScheme(
    primary = Lc.primary,
    onPrimary = Color.White,
    primaryContainer = Lc.primary,
    onPrimaryContainer = Color.White,
    secondary = Lc.selected,
    onSecondary = Color.White,
    secondaryContainer = Lc.toolActive,
    onSecondaryContainer = Lc.text,
    background = Lc.canvas,
    onBackground = Lc.text,
    surface = Lc.bar,
    onSurface = Lc.text,
    surfaceVariant = Lc.bottomBar,
    onSurfaceVariant = Lc.textLabel,
    surfaceContainerLowest = Lc.canvas,
    surfaceContainerLow = Lc.bar,
    surfaceContainer = Lc.bottomBar,
    surfaceContainerHigh = Lc.bottomBar,
    surfaceContainerHighest = Lc.toolActive,
    inverseSurface = Lc.toolActive,
    inverseOnSurface = Lc.text,
    inversePrimary = Lc.selected,
    outline = Lc.buttonBorder,
    outlineVariant = Lc.bottomBar,
    error = Lc.reject,
)

private fun TextStyle.inter() = copy(fontFamily = Inter)

private val typography = Typography().run {
    Typography(
        displayLarge = displayLarge.inter(),
        displayMedium = displayMedium.inter(),
        displaySmall = displaySmall.inter(),
        headlineLarge = headlineLarge.inter(),
        headlineMedium = headlineMedium.inter(),
        headlineSmall = headlineSmall.inter(),
        titleLarge = titleLarge.inter(),
        titleMedium = titleMedium.inter(),
        titleSmall = titleSmall.inter(),
        bodyLarge = bodyLarge.inter(),
        bodyMedium = bodyMedium.inter(),
        bodySmall = bodySmall.inter(),
        labelLarge = labelLarge.inter(),
        labelMedium = labelMedium.inter(),
        labelSmall = labelSmall.inter(),
    )
}

@Composable
fun LightcraftTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = colors, typography = typography, content = content)
