package com.bragincad.ui

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

object BraginColors {
    val Accent = Color(0xFFFF3048)
    val AccentPressed = Color(0xFFD9243B)
    val AccentContainer = Color(0xFF4A1720)
    val DarkCanvas = Color(0xFF080D12)
    val DarkBackground = Color(0xFF0A111A)
    val DarkSurface = Color(0xFF121D29)
    val DarkElevated = Color(0xFF192530)
    val DarkDialog = Color(0xFF1D2935)
    val DarkBorder = Color(0xFF293744)
    val LightCanvas = Color(0xFFF4F7F9)
    val LightSurface = Color(0xFFFCFDFE)
    val LightElevated = Color(0xFFF0F4F6)
    val LightBorder = Color(0xFFC9D2D8)
}

object BraginRadii {
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val Floating = 20.dp
}

object BraginSpacing {
    val XSmall = 4.dp
    val Small = 8.dp
    val Medium = 12.dp
    val Large = 16.dp
    val XLarge = 24.dp
}

private val BraginShapes = Shapes(
    extraSmall = RoundedCornerShape(BraginRadii.Small),
    small = RoundedCornerShape(BraginRadii.Medium),
    medium = RoundedCornerShape(BraginRadii.Large),
    large = RoundedCornerShape(BraginRadii.Floating),
    extraLarge = RoundedCornerShape(24.dp),
)

private val BraginTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
)

private val DarkScheme = darkColorScheme(
    primary = BraginColors.Accent,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = BraginColors.AccentContainer,
    onPrimaryContainer = Color(0xFFFFD9DE),
    secondary = Color(0xFFFF8A99),
    onSecondary = Color(0xFF31000A),
    secondaryContainer = Color(0xFF4A1720),
    onSecondaryContainer = Color(0xFFFFD9DE),
    tertiary = Color(0xFFFF7184),
    onTertiary = Color(0xFF3A000D),
    tertiaryContainer = Color(0xFF521522),
    onTertiaryContainer = Color(0xFFFFD9DE),
    background = BraginColors.DarkBackground,
    onBackground = Color(0xFFF5F6F8),
    surface = BraginColors.DarkSurface,
    onSurface = Color(0xFFF5F6F8),
    onSurfaceVariant = Color(0xFF9CAABB),
    surfaceContainer = BraginColors.DarkElevated,
    surfaceContainerHigh = BraginColors.DarkDialog,
    surfaceContainerHighest = Color(0xFF23313E),
    outline = Color(0xFF5A6876),
    outlineVariant = BraginColors.DarkBorder,
    error = Color(0xFFFF6B78),
    surfaceTint = BraginColors.Accent,
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFFC71935),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9DE),
    onPrimaryContainer = Color(0xFF40000C),
    secondary = Color(0xFF74565B),
    secondaryContainer = Color(0xFFFFD9DE),
    onSecondaryContainer = Color(0xFF3B0711),
    tertiary = Color(0xFF9C4050),
    tertiaryContainer = Color(0xFFFFD9DE),
    background = Color(0xFFF7F9FA),
    onBackground = Color(0xFF181B1D),
    surface = BraginColors.LightSurface,
    onSurface = Color(0xFF181B1D),
    onSurfaceVariant = Color(0xFF566168),
    surfaceContainer = BraginColors.LightElevated,
    surfaceContainerHigh = Color(0xFFE8EEF1),
    surfaceContainerHighest = Color(0xFFDDE5E9),
    outline = Color(0xFF718087),
    outlineVariant = BraginColors.LightBorder,
)

@Composable
fun BraginCadTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        shapes = BraginShapes,
        typography = BraginTypography,
        content = content,
    )
}
