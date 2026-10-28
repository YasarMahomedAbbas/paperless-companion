package io.github.yasarmahomedabbas.paperlesscompanion.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The colour tokens of docs/design/README.md § Tokens, by their manual names. The canvas
 * template (.claude/skills/design-canvas/assets/artboard.dc.html) carries the same values;
 * change both together. Screens read them through [MaterialTheme.colorScheme] and
 * [AppTheme.extendedColors], never from here.
 */
internal object Tokens {
    val Bg = Color(0xFF1B2229)
    val Surface = Color(0xFF222B33)
    val Raised = Color(0xFF2A343D)
    val Fg = Color(0xFFE6EBE8)
    val Mfg = Color(0xFF9AA6A1)
    val Primary = Color(0xFF5CBF8B)
    val OnPrimary = Color(0xFF0E1A13)

    /** `primary` at 14%. */
    val PrimaryTint = Color(0x245CBF8B)
    val Border = Color(0xFF36424C)
    val BorderMuted = Color(0xFF2C363F)
    val Destructive = Color(0xFFEC7166)
    val OnDestructive = Color(0xFF1F0D0B)
    val Warning = Color(0xFFE2B65C)

    /** `#080c0f` at 60%. */
    val Scrim = Color(0x99080C0F)
}

/**
 * The one colour scheme. Every slot is set, so no Material default (purple, tonal tints) can
 * leak into a component that reads a slot the manual doesn't name.
 */
internal val AppColorScheme: ColorScheme = darkColorScheme(
    primary = Tokens.Primary,
    onPrimary = Tokens.OnPrimary,
    primaryContainer = Tokens.PrimaryTint,
    onPrimaryContainer = Tokens.Primary,
    inversePrimary = Tokens.Primary,
    secondary = Tokens.Primary,
    onSecondary = Tokens.OnPrimary,
    secondaryContainer = Tokens.PrimaryTint,
    onSecondaryContainer = Tokens.Primary,
    tertiary = Tokens.Primary,
    onTertiary = Tokens.OnPrimary,
    tertiaryContainer = Tokens.PrimaryTint,
    onTertiaryContainer = Tokens.Primary,
    background = Tokens.Bg,
    onBackground = Tokens.Fg,
    surface = Tokens.Bg,
    onSurface = Tokens.Fg,
    surfaceVariant = Tokens.Raised,
    onSurfaceVariant = Tokens.Mfg,
    surfaceTint = Tokens.Bg,
    inverseSurface = Tokens.Fg,
    inverseOnSurface = Tokens.Bg,
    error = Tokens.Destructive,
    onError = Tokens.OnDestructive,
    errorContainer = Tokens.Surface,
    onErrorContainer = Tokens.Destructive,
    outline = Tokens.Border,
    outlineVariant = Tokens.BorderMuted,
    scrim = Tokens.Scrim,
    surfaceBright = Tokens.Raised,
    surfaceDim = Tokens.Bg,
    surfaceContainerLowest = Tokens.Bg,
    surfaceContainerLow = Tokens.Surface,
    surfaceContainer = Tokens.Surface,
    surfaceContainerHigh = Tokens.Raised,
    surfaceContainerHighest = Tokens.Raised,
)

/** The tokens Material's colour scheme has no slot for. */
@Immutable
data class ExtendedColors(
    /** Amber: the phone can't reach the server, unsaved changes, warning icons. Never body text. */
    val warning: Color,
)

internal val AppExtendedColors = ExtendedColors(warning = Tokens.Warning)

internal val LocalExtendedColors = staticCompositionLocalOf { AppExtendedColors }
