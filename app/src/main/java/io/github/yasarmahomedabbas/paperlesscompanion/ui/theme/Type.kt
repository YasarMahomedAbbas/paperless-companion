package io.github.yasarmahomedabbas.paperlesscompanion.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.yasarmahomedabbas.paperlesscompanion.R

/**
 * Figtree, bundled in res/font as static 400 / 500 / 600 instances of the Google Fonts
 * variable font (OFL, licence in assets/licenses). Not a downloadable font: GrapheneOS phones
 * without Google Play can't fetch one.
 */
val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
)

private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = Figtree,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = 0.sp,
)

/** The type scale of docs/design/foundations.md § Type. */
internal val AppTypography = Typography(
    // Not in the manual's scale; set so nothing falls back to Roboto or a fourth weight.
    displayLarge = style(40, 48, FontWeight.Medium),
    displayMedium = style(36, 44, FontWeight.Medium),
    displaySmall = style(32, 40, FontWeight.Medium),
    headlineLarge = style(30, 36, FontWeight.Medium),
    headlineMedium = style(28, 34, FontWeight.Medium),
    // Screen title: "Documents", "Paperless Scan".
    headlineSmall = style(28, 34, FontWeight.Medium).copy(letterSpacing = (-0.01).em),
    // Version, big number.
    titleLarge = style(24, 30, FontWeight.Medium),
    // Bar title.
    titleMedium = style(22, 28, FontWeight.Medium),
    // Sheet and dialog title.
    titleSmall = style(20, 26, FontWeight.Medium),
    // Body: field values, option labels.
    bodyLarge = style(16, 24),
    // Body small: secondary lines, explanations, dialog text.
    bodyMedium = style(14, 21),
    // Caption: hints, snippets, the note under a form.
    bodySmall = style(13, 19),
    // Button labels.
    labelLarge = style(15, 20, FontWeight.Medium),
    // Field labels and section headings (in mfg).
    labelMedium = style(14, 20, FontWeight.Medium),
    // Tag pills.
    labelSmall = style(12, 16),
)

/** Card title: row titles, card headings, a notice's first line. `bodyLarge` at 500. */
val Typography.cardTitle: TextStyle get() = bodyLarge.copy(fontWeight = FontWeight.Medium, lineHeight = 22.sp)

/** The filled buttons' label: 16 at 600. */
val Typography.primaryButton: TextStyle get() = labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

/** Explanations under a screen title: 15 / 22. */
val Typography.lead: TextStyle get() = bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp)

/** The viewer bar's title: 18 / 24 at 500, between the bar title (22) and the sheet title (20). */
val Typography.viewerTitle: TextStyle get() = titleSmall.copy(fontSize = 18.sp, lineHeight = 24.sp)

/** A status card's statement: 17 / 24 at 500. */
val Typography.statement: TextStyle get() = bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
