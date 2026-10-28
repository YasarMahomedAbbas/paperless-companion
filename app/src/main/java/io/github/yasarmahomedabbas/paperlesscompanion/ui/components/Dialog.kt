package io.github.yasarmahomedabbas.paperlesscompanion.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.lead

/**
 * A decision that Back can't undo (components.md § Dialog): `surface`, `r-sheet` on all
 * corners, the 20/500 title, 15 body in `mfg`, then a text Cancel and the [confirmVariant]
 * button at the end (`Primary`, or `Destructive` for a delete).
 */
@Composable
fun AppDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmVariant: ButtonVariant = ButtonVariant.Primary,
    dismissLabel: String = "Cancel",
) {
    val colors = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { AppButton(confirmLabel, onConfirm, variant = confirmVariant) },
        dismissButton = { AppButton(dismissLabel, onDismiss, variant = ButtonVariant.Text) },
        title = { Text(title, style = MaterialTheme.typography.titleSmall) },
        text = { Text(body, style = MaterialTheme.typography.lead) },
        shape = MaterialTheme.shapes.large,
        containerColor = colors.surfaceContainer,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
        tonalElevation = 0.dp,
    )
}
