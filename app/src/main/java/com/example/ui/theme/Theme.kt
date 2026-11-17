package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val TradingViewDarkColorScheme = darkColorScheme(
    primary = TvAccentBlue,
    onPrimary = TvTextBright,
    primaryContainer = TvAccentBlueContainer,
    onPrimaryContainer = TvTextBright,
    secondary = TvBullTeal,
    onSecondary = TvBackground,
    secondaryContainer = TvBullContainer,
    onSecondaryContainer = TvBullTeal,
    tertiary = TvBearRed,
    onTertiary = TvTextBright,
    tertiaryContainer = TvBearContainer,
    onTertiaryContainer = TvBearCoral,
    background = TvBackground,
    onBackground = TvTextPrimary,
    surface = TvSurface,
    onSurface = TvTextPrimary,
    surfaceVariant = TvSurfaceElevated,
    onSurfaceVariant = TvTextSecondary,
    outline = TvDivider,
    outlineVariant = TvBorderSubtle,
    error = TvBearRed,
    onError = TvTextBright,
)

private val TerminalShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

@Composable
fun FnOGapBoardTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = TradingViewDarkColorScheme,
        typography = AppTypography,
        shapes = TerminalShapes,
        content = content,
    )
}
