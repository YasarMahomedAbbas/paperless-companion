package io.github.yasarmahomedabbas.paperlesscompanion.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The corners of docs/design/foundations.md § Shape. Fully round things (chips, tags, the
 * search field, icon buttons) use `CircleShape`.
 */
internal val AppShapes = Shapes(
    // r-thumb: thumbnails, skeleton blocks.
    extraSmall = RoundedCornerShape(10.dp),
    // r-ctl: buttons, fields, menu items, options, snackbars.
    small = RoundedCornerShape(14.dp),
    // r-card: cards, rows, menus, the action button, notice banners.
    medium = RoundedCornerShape(20.dp),
    // r-sheet: sheets (top corners) and dialogs.
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
