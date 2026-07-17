package io.s2qtech.shenk

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF3F6B55),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBDF),
    onPrimaryContainer = Color(0xFF163728),
    secondary = Color(0xFF53665B),
    secondaryContainer = Color(0xFFE0E7E1),
    tertiary = Color(0xFF9B7425),
    tertiaryContainer = Color(0xFFFFE7AF),
    background = Color(0xFFF7F9F6),
    surface = Color(0xFFFBFCFA),
    surfaceVariant = Color(0xFFE9EEEA),
    outline = Color(0xFF78827B),
    outlineVariant = Color(0xFFD4DDD6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5D1B4),
    onPrimary = Color(0xFF123725),
    primaryContainer = Color(0xFF2A503D),
    onPrimaryContainer = Color(0xFFC2EBD0),
    secondary = Color(0xFFBBCAC0),
    secondaryContainer = Color(0xFF394B41),
    tertiary = Color(0xFFF0C66D),
    tertiaryContainer = Color(0xFF624B12),
    background = Color(0xFF101512),
    surface = Color(0xFF151B17),
    surfaceVariant = Color(0xFF27302A),
    outline = Color(0xFF8C968F),
    outlineVariant = Color(0xFF3A443E),
)

@Composable
fun ShenkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
