package lifeos.md.tapilot.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import lifeos.md.tapilot.AppTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val LocalThemePalette = staticCompositionLocalOf { ThemePalettes.get("classic", false) }

/** Vibelet palettes adapted to native Android components. */
internal fun tapilotColors(dark: Boolean, paletteId: String = "classic"): ColorScheme {
    val palette = ThemePalettes.get(paletteId, dark)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = Color(palette.primary),
        onPrimary = Color(palette.contrast),
        primaryContainer = Color(palette.soft),
        onPrimaryContainer = Color(palette.text),
        secondary = Color(palette.primaryDark),
        background = Color(palette.background),
        onBackground = Color(palette.text),
        surface = Color(palette.surface),
        onSurface = Color(palette.text),
        onSurfaceVariant = Color(palette.secondaryText),
        surfaceVariant = Color(palette.cardAlt),
        surfaceContainer = Color(palette.surface),
        surfaceContainerHigh = Color(palette.surface),
        surfaceContainerHighest = Color(palette.cardAlt),
        surfaceContainerLow = Color(palette.header),
        surfaceContainerLowest = Color(palette.background),
        surfaceTint = Color.Transparent,
        outline = Color(palette.strongLine),
        outlineVariant = Color(palette.line),
        error = Color(palette.danger),
    )
}

private val typography = Typography(
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
)

@Composable
internal fun TapilotTheme(dark: Boolean, paletteId: String = AppTheme.paletteId(LocalContext.current), content: @Composable () -> Unit) {
    val colors = tapilotColors(dark, paletteId)
    MaterialTheme(colorScheme = colors, typography = typography,
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(24.dp))) {
        CompositionLocalProvider(LocalThemePalette provides ThemePalettes.get(paletteId, dark),
            LocalContentColor provides colors.onBackground, content = content)
    }
}
