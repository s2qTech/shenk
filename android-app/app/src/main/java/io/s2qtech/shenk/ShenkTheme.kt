package io.s2qtech.shenk

import androidx.compose.foundation.isSystemInDarkTheme
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

internal val ShenkLightColors = lightColorScheme(
    primary = Color(0xFF2F674F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEADF),
    onPrimaryContainer = Color(0xFF173F31),
    secondary = Color(0xFF52655B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9EEE8),
    onSecondaryContainer = Color(0xFF263B30),
    tertiary = Color(0xFF8A6011),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF5E7C6),
    onTertiaryContainer = Color(0xFF342100),
    background = Color(0xFFFBF7EF),
    onBackground = Color(0xFF173F31),
    surface = Color(0xFFFFFDF9),
    onSurface = Color(0xFF173F31),
    surfaceDim = Color(0xFFE4E0D8),
    surfaceBright = Color(0xFFFFFDF9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9F5ED),
    surfaceVariant = Color(0xFFEDEFE9),
    onSurfaceVariant = Color(0xFF3E4D45),
    surfaceContainer = Color(0xFFF5F2EA),
    surfaceContainerHigh = Color(0xFFEDECE5),
    surfaceContainerHighest = Color(0xFFE5E7E0),
    // This token is also used for supporting text. Keep 4.5:1 contrast even on
    // surfaceVariant, not only the page background.
    outline = Color(0xFF626E67),
    outlineVariant = Color(0xFFD8DDD6),
    error = Color(0xFFB3261E),
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
    background = Color(0xFF101713),
    onBackground = Color(0xFFE1E9E2),
    surface = Color(0xFF151D18),
    onSurface = Color(0xFFE1E9E2),
    surfaceDim = Color(0xFF0C120E),
    surfaceBright = Color(0xFF303A33),
    surfaceContainerLowest = Color(0xFF0A100C),
    surfaceContainerLow = Color(0xFF151D18),
    surfaceVariant = Color(0xFF27302A),
    onSurfaceVariant = Color(0xFFC1CAC3),
    surfaceContainer = Color(0xFF1A231D),
    surfaceContainerHigh = Color(0xFF222C25),
    surfaceContainerHighest = Color(0xFF2A352D),
    outline = Color(0xFF909A93),
    outlineVariant = Color(0xFF3A443E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val ShenkTypography = Typography(
    displaySmall = TextStyle(
        fontSize = 44.sp,
        lineHeight = 48.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-1.2).sp,
    ),
    headlineLarge = TextStyle(
        fontSize = 36.sp,
        lineHeight = 41.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.7).sp,
    ),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

private val ShenkShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

@Composable
fun ShenkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) ShenkDarkColors else ShenkLightColors,
        typography = ShenkTypography,
        shapes = ShenkShapes,
        content = content,
    )
}
