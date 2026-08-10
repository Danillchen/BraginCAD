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
    val Accent = Color(0xFF60B7E6)
    val AccentContainer = Color(0xFF173E55)
    val DarkCanvas = Color(0xFF080D11)
    val DarkBackground = Color(0xFF0C1116)
    val DarkSurface = Color(0xFF121920)
    val DarkElevated = Color(0xFF192229)
    val DarkDialog = Color(0xFF20282F)
    val DarkBorder = Color(0xFF34414A)
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
    onPrimary = Color(0xFF002B3D),
    primaryContainer = BraginColors.AccentContainer,
    onPrimaryContainer = Color(0xFFD2EEFC),
    secondary = Color(0xFF9FCADB),
    background = BraginColors.DarkBackground,
    surface = BraginColors.DarkSurface,
    surfaceContainer = BraginColors.DarkElevated,
    surfaceContainerHigh = BraginColors.DarkDialog,
    surfaceContainerHighest = Color(0xFF273139),
    outline = Color(0xFF53616A),
    outlineVariant = BraginColors.DarkBorder,
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF00658A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0EFFF),
    onPrimaryContainer = Color(0xFF002F42),
    secondary = Color(0xFF476572),
    background = Color(0xFFF7F9FA),
    surface = BraginColors.LightSurface,
    surfaceContainer = BraginColors.LightElevated,
    surfaceContainerHigh = Color(0xFFE8EEF1),
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
