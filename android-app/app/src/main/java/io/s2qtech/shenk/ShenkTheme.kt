package io.s2qtech.shenk

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val ShenkLightColors = lightColorScheme(
    primary = Color(0xFF3F6B55),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBDF),
    onPrimaryContainer = Color(0xFF163728),
    secondary = Color(0xFF53665B),
    secondaryContainer = Color(0xFFE0E7E1),
    tertiary = Color(0xFF906819),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE7AF),
    onTertiaryContainer = Color(0xFF342100),
    background = Color(0xFFF7F9F6),
    onBackground = Color(0xFF182019),
    surface = Color(0xFFFBFCFA),
    onSurface = Color(0xFF182019),
    surfaceVariant = Color(0xFFE9EEEA),
    onSurfaceVariant = Color(0xFF404A43),
    surfaceContainer = Color(0xFFF0F4F0),
    surfaceContainerHigh = Color(0xFFE9EEEA),
    surfaceContainerHighest = Color(0xFFE2E8E3),
    // This token is also used for supporting text. Keep 4.5:1 contrast even on
    // surfaceVariant, not only the page background.
    outline = Color(0xFF646E67),
    outlineVariant = Color(0xFFD4DDD6),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

internal val ShenkDarkColors = darkColorScheme(
    primary = Color(0xFFA5D1B4),
    onPrimary = Color(0xFF123725),
    primaryContainer = Color(0xFF2A503D),
    onPrimaryContainer = Color(0xFFC2EBD0),
    secondary = Color(0xFFBBCAC0),
    onSecondary = Color(0xFF26342C),
    secondaryContainer = Color(0xFF394B41),
    onSecondaryContainer = Color(0xFFD7E7DC),
    tertiary = Color(0xFFF0C66D),
    onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFF624B12),
    onTertiaryContainer = Color(0xFFFFE3A3),
    background = Color(0xFF101512),
    onBackground = Color(0xFFE1E9E2),
    surface = Color(0xFF151B17),
    onSurface = Color(0xFFE1E9E2),
    surfaceVariant = Color(0xFF27302A),
    onSurfaceVariant = Color(0xFFC1CAC3),
    surfaceContainer = Color(0xFF1B221D),
    surfaceContainerHigh = Color(0xFF222A25),
    surfaceContainerHighest = Color(0xFF2A332D),
    outline = Color(0xFF909A93),
    outlineVariant = Color(0xFF3A443E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun ShenkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) ShenkDarkColors else ShenkLightColors,
        content = content,
    )
}
