package io.s2qtech.shenk

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertTrue
import org.junit.Test

class ShenkThemeContrastTest {
    @Test
    fun lightAndDarkTextTokensMeetNormalTextContrast() {
        assertTextPairs("light", ShenkLightColors)
        assertTextPairs("dark", ShenkDarkColors)
    }

    @Test
    fun calendarCategoryAccentsMeetNormalTextContrast() {
        val types = listOf(
            "strength",
            "quality_walk",
            "easy_walk",
            "recovery",
            "rest",
            "unknown",
        )
        types.forEach { type ->
            assertContrast("light calendar accent $type", accessibleTrainingColor(type, false), ShenkLightColors.background)
            assertContrast("dark calendar accent $type", accessibleTrainingColor(type, true), ShenkDarkColors.background)
        }
    }

    private fun assertTextPairs(name: String, colors: ColorScheme) {
        listOf(
            "onBackground" to (colors.onBackground to colors.background),
            "onSurface" to (colors.onSurface to colors.surface),
            "onSurfaceVariant" to (colors.onSurfaceVariant to colors.surfaceVariant),
            "primary" to (colors.primary to colors.background),
            "secondary" to (colors.secondary to colors.background),
            "outline on background" to (colors.outline to colors.background),
            "outline on surface" to (colors.outline to colors.surface),
            "outline on surfaceVariant" to (colors.outline to colors.surfaceVariant),
            "onPrimary" to (colors.onPrimary to colors.primary),
            "onPrimaryContainer" to (colors.onPrimaryContainer to colors.primaryContainer),
            "onSecondary" to (colors.onSecondary to colors.secondary),
            "onSecondaryContainer" to (colors.onSecondaryContainer to colors.secondaryContainer),
            "onTertiary" to (colors.onTertiary to colors.tertiary),
            "onTertiaryContainer" to (colors.onTertiaryContainer to colors.tertiaryContainer),
            "onError" to (colors.onError to colors.error),
            "onErrorContainer" to (colors.onErrorContainer to colors.errorContainer),
        ).forEach { (label, pair) -> assertContrast("$name $label", pair.first, pair.second) }
    }

    private fun assertContrast(label: String, foreground: Color, background: Color) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("$label contrast was $ratio", ratio >= 4.5)
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val firstLuminance = luminance(first)
        val secondLuminance = luminance(second)
        return (max(firstLuminance, secondLuminance) + 0.05) /
            (min(firstLuminance, secondLuminance) + 0.05)
    }

    private fun luminance(color: Color): Double =
        0.2126 * linear(color.red.toDouble()) +
            0.7152 * linear(color.green.toDouble()) +
            0.0722 * linear(color.blue.toDouble())

    private fun linear(component: Double): Double =
        if (component <= 0.04045) component / 12.92 else Math.pow((component + 0.055) / 1.055, 2.4)
}
