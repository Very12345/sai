package com.phoneagent.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF335CFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5EAFF),
    onPrimaryContainer = Color(0xFF10236F),
    secondary = Color(0xFF00897B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8F4EE),
    onSecondaryContainer = Color(0xFF003D36),
    tertiary = Color(0xFF7360E8),
    tertiaryContainer = Color(0xFFEAE5FF),
    background = Color(0xFFF6F7FB),
    surface = Color(0xFFFCFCFF),
    surfaceVariant = Color(0xFFECEEF5),
    outline = Color(0xFF747784),
    outlineVariant = Color(0xFFD7D9E2),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9C3FF),
    onPrimary = Color(0xFF08247D),
    primaryContainer = Color(0xFF1A368F),
    secondary = Color(0xFF72D8C8),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF145047),
    onSecondaryContainer = Color(0xFFB5F2E8),
    tertiary = Color(0xFFCDC4FF),
    tertiaryContainer = Color(0xFF4D3FB3),
    background = Color(0xFF101116),
    surface = Color(0xFF17181E),
    surfaceVariant = Color(0xFF292B33),
    outline = Color(0xFF90919B),
    outlineVariant = Color(0xFF42434B),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

private val AppTypography = Typography(
    headlineSmall = Typography().headlineSmall.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = Typography().titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = Typography().titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = Typography().bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = Typography().bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = Typography().labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
)

private fun themedLight(theme: String) = when (theme) {
    "ocean" -> LightColors.copy(
        primary = Color(0xFF0061A4), primaryContainer = Color(0xFFD1E4FF),
        secondary = Color(0xFF00796B), secondaryContainer = Color(0xFFC9F3EA),
        tertiary = Color(0xFF4B5FB5), tertiaryContainer = Color(0xFFDDE1FF),
    )
    "sunset" -> LightColors.copy(
        primary = Color(0xFFC43D62), primaryContainer = Color(0xFFFFD9E1),
        secondary = Color(0xFF9A4D00), secondaryContainer = Color(0xFFFFDCC2),
        tertiary = Color(0xFF7B4EA3), tertiaryContainer = Color(0xFFF1DAFF),
    )
    "forest" -> LightColors.copy(
        primary = Color(0xFF0C714F), primaryContainer = Color(0xFFA8F2CE),
        secondary = Color(0xFF4C662B), secondaryContainer = Color(0xFFCDEDA4),
        tertiary = Color(0xFF35618A), tertiaryContainer = Color(0xFFD1E5FF),
    )
    else -> LightColors
}

private fun themedDark(theme: String) = when (theme) {
    "ocean" -> DarkColors.copy(primary = Color(0xFF9CCAFF), primaryContainer = Color(0xFF00497D), secondary = Color(0xFF6EDBC9), tertiary = Color(0xFFBAC3FF))
    "sunset" -> DarkColors.copy(primary = Color(0xFFFFB1C3), primaryContainer = Color(0xFF8F153F), secondary = Color(0xFFFFB77C), tertiary = Color(0xFFDDB7FF))
    "forest" -> DarkColors.copy(primary = Color(0xFF8CD7B2), primaryContainer = Color(0xFF005238), secondary = Color(0xFFB2D18A), tertiary = Color(0xFF9ECAFA))
    else -> DarkColors
}

@Composable
fun PhoneAgentTheme(theme: String = "aurora", content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) themedDark(theme) else themedLight(theme),
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
