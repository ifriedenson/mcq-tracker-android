package com.mcqtracker.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Warm paper, deep indigo, amber accent, green success, red error.
val Paper = Color(0xFFFBF6EC)
val Ink = Color(0xFF20242E)
val Indigo = Color(0xFF2E3A87)
val Amber = Color(0xFFB7791F)
val Success = Color(0xFF2F7D48)
val Danger = Color(0xFFB23B3B)
val Muted = Color(0xFF6B6459)

private val LightScheme = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    secondary = Amber,
    onSecondary = Color.White,
    tertiary = Success,
    onTertiary = Color.White,
    error = Danger,
    onError = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Color(0xFFFFFDF7),
    onSurface = Ink,
    surfaceVariant = Color(0xFFEFE7D6),
    onSurfaceVariant = Color(0xFF4A4438),
    outline = Color(0xFFC9BFA9),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFAEB6F5),
    onPrimary = Color(0xFF171C3B),
    secondary = Color(0xFFE8C07A),
    onSecondary = Color(0xFF2C2412),
    tertiary = Color(0xFF8FD3A6),
    onTertiary = Color(0xFF10281A),
    error = Color(0xFFF2A0A0),
    onError = Color(0xFF3A1010),
    background = Color(0xFF14161C),
    onBackground = Color(0xFFEDE7DA),
    surface = Color(0xFF1D2028),
    onSurface = Color(0xFFEDE7DA),
    surfaceVariant = Color(0xFF2A2E38),
    onSurfaceVariant = Color(0xFFCFC7B8),
    outline = Color(0xFF4A4E58),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp,
    ),
)

@Composable
fun MCQTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content,
    )
}
