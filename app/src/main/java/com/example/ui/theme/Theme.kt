package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkVaporwaveColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = OnNeonCyan,
    primaryContainer = NeonCyanContainer,
    onPrimaryContainer = OnNeonCyanContainer,
    secondary = NeonMagenta,
    onSecondary = OnNeonMagenta,
    secondaryContainer = NeonMagentaContainer,
    onSecondaryContainer = OnNeonMagentaContainer,
    tertiary = SunsetTangerine,
    onTertiary = OnSunsetTangerine,
    tertiaryContainer = SunsetTangerineContainer,
    onTertiaryContainer = OnSunsetTangerineContainer,
    background = SynthBg,
    onBackground = VaporWhiteText,
    surface = SynthSurface.copy(alpha = 0.92f),
    onSurface = VaporWhiteText,
    surfaceVariant = SynthSurfaceHigh,
    onSurfaceVariant = VaporLavenderMuted,
    surfaceContainerLowest = SynthBg,
    surfaceContainerLow = SynthSurface,
    surfaceContainer = SynthSurfaceHigh,
    surfaceContainerHigh = SynthSurfaceHighest,
    outline = SynthOutline,
    outlineVariant = SynthOutlineVariant,
    error = DefeatCrimson,
    onError = OnNeonMagenta,
    errorContainer = DefeatCrimsonSoft,
    onErrorContainer = OnNeonMagentaContainer
)

private val LightVaporwaveColorScheme = lightColorScheme(
    primary = DeepElectricTeal,
    onPrimary = PastelCardSurface,
    primaryContainer = DeepElectricTealContainer,
    onPrimaryContainer = VaporInkText,
    secondary = DeepSynthMagenta,
    onSecondary = PastelCardSurface,
    secondaryContainer = DeepSynthMagentaContainer,
    onSecondaryContainer = VaporInkText,
    tertiary = DeepSunsetOrange,
    onTertiary = PastelCardSurface,
    tertiaryContainer = DeepSunsetOrangeContainer,
    onTertiaryContainer = VaporInkText,
    background = PastelSkyBg,
    onBackground = VaporInkText,
    surface = PastelCardSurface,
    onSurface = VaporInkText,
    surfaceVariant = PastelLavenderHigh,
    onSurfaceVariant = VaporInkMuted,
    outline = PastelOutline,
    outlineVariant = PastelLavenderHigh,
    error = DeepSynthMagenta,
    onError = PastelCardSurface,
    errorContainer = DeepSynthMagentaContainer,
    onErrorContainer = VaporInkText
)

private val CommandLedgerShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun CommandLedgerTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkVaporwaveColorScheme else LightVaporwaveColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = CommandLedgerTypography,
        shapes = CommandLedgerShapes,
        content = content
    )
}
