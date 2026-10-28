package io.github.yasarmahomedabbas.paperlesscompanion.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.primaryButton

/** The button roles of docs/design/components.md § Buttons. */
enum class ButtonVariant {
    /** Filled green: the one main action on a screen. */
    Primary,

    /** Outline in `border`: the second choice (Cancel, Try again). */
    Secondary,

    /** No container: third choices and in-line actions (Retry, Open, Clear finished). */
    Text,

    /** Filled red: only the confirm in a delete dialog. */
    Destructive,

    /** Red text: "Move to trash" in a menu, Trash in the bulk bar. */
    DestructiveText,
}

/**
 * A button in one of the house [ButtonVariant]s, `r-ctl` corners. [onlyControl] makes a
 * primary 56 high, for the screen whose only control it is (Sign in). [busy] keeps the size
 * and swaps the label for a spinner, and ignores taps meanwhile.
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    enabled: Boolean = true,
    busy: Boolean = false,
    onlyControl: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    val click = if (busy) ({}) else onClick
    val busyModifier = if (busy) modifier.semantics { stateDescription = "Working" } else modifier
    when (variant) {
        ButtonVariant.Primary, ButtonVariant.Destructive -> {
            val primary = variant == ButtonVariant.Primary
            val container = if (primary) colors.primary else colors.error
            val content = if (primary) colors.onPrimary else colors.onError
            val height = when {
                !primary -> 48.dp
                onlyControl -> 56.dp
                else -> 52.dp
            }
            Button(
                onClick = click,
                modifier = busyModifier.height(height),
                enabled = enabled,
                shape = shape,
                colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
                elevation = null,
                contentPadding = PaddingValues(horizontal = Spacing.s20),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = content, strokeWidth = 2.dp)
                } else {
                    Text(text, style = MaterialTheme.typography.primaryButton)
                }
            }
        }
        ButtonVariant.Secondary -> OutlinedButton(
            onClick = click,
            modifier = busyModifier.height(48.dp),
            enabled = enabled,
            shape = shape,
            border = BorderStroke(1.dp, colors.outline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface),
            contentPadding = PaddingValues(horizontal = Spacing.s20),
        ) {
            ButtonContent(text, busy, colors.onSurfaceVariant, MaterialTheme.typography.labelLarge)
        }
        ButtonVariant.Text, ButtonVariant.DestructiveText -> {
            val content = if (variant == ButtonVariant.Text) colors.onSurface else colors.error
            TextButton(
                onClick = click,
                modifier = busyModifier.height(48.dp),
                enabled = enabled,
                shape = shape,
                colors = ButtonDefaults.textButtonColors(contentColor = content),
                contentPadding = PaddingValues(horizontal = Spacing.s12),
            ) {
                ButtonContent(text, busy, colors.onSurfaceVariant, MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ButtonContent(text: String, busy: Boolean, spinner: Color, style: TextStyle) {
    if (busy) {
        CircularProgressIndicator(Modifier.size(20.dp), color = spinner, strokeWidth = 2.dp)
    } else {
        Text(text, style = style)
    }
}

/** The icon button looks of docs/design/components.md § Icon button. */
enum class IconButtonVariant {
    /** Transparent, icon `fg`: in top bars (back, clear). */
    Plain,

    /** `surface` circle, icon `mfg`: the Settings button on the Documents title row. */
    Tonal,
}

/** A 48 × 48, fully round icon-only button. [contentDescription] says the action ("Back", "Settings"). */
@Composable
fun AppIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: IconButtonVariant = IconButtonVariant.Plain,
    iconSize: Dp = 22.dp,
) {
    val colors = MaterialTheme.colorScheme
    val tonal = variant == IconButtonVariant.Tonal
    Surface(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = if (tonal) colors.surfaceContainer else Color.Transparent,
        contentColor = if (tonal) colors.onSurfaceVariant else colors.onSurface,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(iconSize))
        }
    }
}

/**
 * The one action button on Documents (components.md § Action button): an extended FAB, 56
 * high, `r-card`, green with a 22dp plus and "Add". The only shadow on the page.
 */
@Composable
fun AddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        icon = { Icon(AppIcons.Plus, contentDescription = null, modifier = Modifier.size(22.dp)) },
        text = { Text("Add", style = MaterialTheme.typography.primaryButton) },
    )
}

@Preview(name = "Buttons", widthDp = 360)
@Composable
private fun ButtonsPreview() = PaperlessTheme {
    Surface {
        Column(Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.s12)) {
            AppButton("Sign in", {}, Modifier.fillMaxWidth(), onlyControl = true)
            AppButton("Sign in", {}, Modifier.fillMaxWidth(), busy = true, onlyControl = true)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                AppButton("Cancel", {}, Modifier.weight(1f), ButtonVariant.Secondary)
                AppButton("Save", {}, Modifier.weight(1f))
            }
            AppButton("Retry", {}, variant = ButtonVariant.Text)
            AppButton("Move to trash", {}, variant = ButtonVariant.Destructive)
            AppButton("Move to trash", {}, variant = ButtonVariant.DestructiveText)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                AppIconButton(AppIcons.Back, "Back", {})
                AppIconButton(AppIcons.Settings, "Settings", {}, variant = IconButtonVariant.Tonal)
            }
            AddButton({})
        }
    }
}
