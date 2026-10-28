package io.github.yasarmahomedabbas.paperlesscompanion.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.lead

/**
 * Where snackbars appear (docs/design/components.md § Snackbar): `raised`, `r-ctl`, 12 from the
 * sides and 28 from the bottom, text 15 `fg`, and its action as a text button in `fg` (`primary`
 * for Undo). A sideways swipe dismisses it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    SnackbarHost(state, modifier.padding(start = Spacing.s12, end = Spacing.s12, bottom = Spacing.actionBottom)) { data ->
        val action = data.visuals.actionLabel
        val swipe = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value != SwipeToDismissBoxValue.Settled) data.dismiss()
                true
            },
        )
        SwipeToDismissBox(swipe, backgroundContent = {}) {
            Snackbar(
                shape = MaterialTheme.shapes.small,
                containerColor = colors.surfaceContainerHigh,
                contentColor = colors.onSurface,
                action = action?.let { label ->
                    {
                        TextButton(
                            onClick = data::performAction,
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.textButtonColors(contentColor = if (label == "Undo") colors.primary else colors.onSurface),
                        ) { Text(label, style = MaterialTheme.typography.labelLarge) }
                    }
                },
            ) {
                Text(data.visuals.message, style = MaterialTheme.typography.lead)
            }
        }
    }
}
