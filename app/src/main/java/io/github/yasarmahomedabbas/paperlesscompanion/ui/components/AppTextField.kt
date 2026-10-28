package io.github.yasarmahomedabbas.paperlesscompanion.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing

/**
 * The text field of docs/design/components.md § Text field: the label above in `mfg`, a
 * 52-high `field` box with `r-ctl` corners and no outline, a 2dp `border` ring when focused.
 * [isError] turns the label and ring red; [errorText] adds the line under it. [password] hides
 * the text behind a "Show password" eye.
 */
@Composable
fun AppTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    isError: Boolean = false,
    errorText: String? = null,
    password: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var shown by rememberSaveable { mutableStateOf(false) }
    val showError = isError || errorText != null
    val ring = when {
        showError -> colors.error
        focused -> colors.outline
        else -> null
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.labelGap)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { if (showError) error(errorText ?: "Check the ${label.lowercase()}") },
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            visualTransformation = if (password && !shown) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = if (password) keyboardOptions.copy(keyboardType = KeyboardType.Password) else keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interaction,
            // The label is drawn inside the decoration so TalkBack reads it with the field and a
            // tap on it focuses the field, as with Material's own text fields.
            decorationBox = { inner ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.labelGap)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = if (showError) colors.error else colors.onSurfaceVariant)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .background(colors.surfaceContainerHigh, shape)
                            .then(if (ring != null) Modifier.border(2.dp, ring, shape) else Modifier)
                            .padding(start = Spacing.s16, end = if (password) Spacing.s4 else Spacing.s16),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            if (value.isEmpty() && placeholder != null) {
                                Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                            }
                            inner()
                        }
                        if (password) {
                            IconButton(onClick = { shown = !shown }) {
                                Icon(
                                    if (shown) AppIcons.VisibilityOff else AppIcons.Visibility,
                                    contentDescription = if (shown) "Hide password" else "Show password",
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            },
        )
        if (errorText != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s8), verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Alert, contentDescription = null, tint = colors.error, modifier = Modifier.size(16.dp))
                Text(errorText, style = MaterialTheme.typography.bodySmall, color = colors.error)
            }
        }
    }
}

@Preview(name = "Text fields", widthDp = 360)
@Composable
private fun AppTextFieldPreview() = PaperlessTheme {
    Surface {
        Column(Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.fieldGap)) {
            AppTextField("Server address", "", {}, placeholder = "http://paperless:8000")
            AppTextField("Username", "yasar", {})
            AppTextField("Password", "correcthorse", {}, password = true)
            AppTextField("Title", "", {}, errorText = "A document needs a title.")
        }
    }
}
