package io.github.yasarmahomedabbas.paperlesscompanion.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * The app's one theme (docs/design/README.md rule 1): no light and dark variants and no
 * Material You dynamic colour, so the app looks the same on every phone and system setting.
 */
@Composable
fun PaperlessTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExtendedColors provides AppExtendedColors) {
        MaterialTheme(
            colorScheme = AppColorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/** The theme values Material has no slot for. */
object AppTheme {
    val extendedColors: ExtendedColors
        @Composable @ReadOnlyComposable get() = LocalExtendedColors.current
}
