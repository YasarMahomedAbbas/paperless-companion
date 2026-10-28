package io.github.yasarmahomedabbas.paperlesscompanion.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** The token pairs docs/design/foundations.md § Colour promises, measured on the theme's values. */
class ThemeTest {
    private val scheme = AppColorScheme

    @Test
    fun `text colours meet the contrast the manual lists`() {
        // text, ground, ratio from the foundations.md table
        val pairs = listOf(
            Triple(scheme.onSurface, scheme.background, 13.3),
            Triple(scheme.onSurface, scheme.surfaceContainer, 11.9),
            Triple(scheme.onSurface, scheme.surfaceContainerHigh, 10.5),
            Triple(scheme.onSurfaceVariant, scheme.background, 6.4),
            Triple(scheme.onSurfaceVariant, scheme.surfaceContainer, 5.7),
            Triple(scheme.onSurfaceVariant, scheme.surfaceContainerHigh, 5.0),
            Triple(scheme.primary, scheme.background, 7.1),
            Triple(scheme.primary, scheme.surfaceContainer, 6.4),
            Triple(scheme.onPrimary, scheme.primary, 7.9),
            Triple(scheme.error, scheme.background, 5.4),
            Triple(scheme.error, scheme.surfaceContainer, 4.9),
            Triple(scheme.onError, scheme.error, 6.4),
            Triple(AppExtendedColors.warning, scheme.surfaceContainer, 7.6),
        )
        for ((text, ground, listed) in pairs) {
            val ratio = contrast(text, ground)
            assertTrue("$text on $ground is $ratio, under 4.5:1", ratio >= 4.5)
            assertEquals("$text on $ground drifted from the manual's $listed", listed, ratio, 0.06)
        }
    }

    @Test
    fun `red text is not used on raised, as the manual says, because it fails there`() {
        assertTrue(contrast(scheme.error, scheme.surfaceContainerHigh) < 4.5)
    }

    @Test
    fun `surfaces step up from page to card to field`() {
        val steps = listOf(scheme.background, scheme.surfaceContainer, scheme.surfaceContainerHigh)
        assertEquals(steps, steps.sortedBy { luminance(it) })
        assertEquals(3, steps.distinct().size)
    }

    @Test
    fun `the type scale uses Figtree in 400, 500 and 600 only`() {
        val t = AppTypography
        val styles = listOf(
            t.displayLarge, t.displayMedium, t.displaySmall, t.headlineLarge, t.headlineMedium,
            t.headlineSmall, t.titleLarge, t.titleMedium, t.titleSmall, t.bodyLarge, t.bodyMedium,
            t.bodySmall, t.labelLarge, t.labelMedium, t.labelSmall, t.cardTitle, t.primaryButton, t.lead,
        )
        val weights = setOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold)
        for (style in styles) {
            assertEquals(Figtree, style.fontFamily)
            assertTrue("${style.fontWeight} is not 400/500/600", style.fontWeight in weights)
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    /** WCAG relative luminance; translucent colours are not text colours, so alpha is ignored. */
    private fun luminance(c: Color): Double {
        fun channel(v: Float): Double = if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }
}
