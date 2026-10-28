package io.github.yasarmahomedabbas.paperlesscompanion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing

/** Where a [SearchField] sits: on the page (52 high) or inside a sheet (48 high). Both on the `field` ground. */
enum class SearchFieldVariant { Page, Sheet }

/**
 * The search field of docs/design/components.md § Search field: fully round, a 20dp search
 * icon, the placeholder, a 2dp `border` ring while focused, and a Clear icon button once there
 * is text. The keyboard's action is Search, which calls [onSearch]. [onFocusChange] reports
 * focus, so the screen can switch into searching.
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search your documents",
    variant: SearchFieldVariant = SearchFieldVariant.Page,
    onSearch: () -> Unit = {},
    onFocusChange: (Boolean) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { onFocusChange(focused) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(if (variant == SearchFieldVariant.Page) 52.dp else 48.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.onSurface),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        interactionSource = interaction,
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceContainerHigh, CircleShape)
                    .then(if (focused) Modifier.border(2.dp, colors.outline, CircleShape) else Modifier)
                    .padding(start = Spacing.s16, end = Spacing.s4),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(AppIcons.Search, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    AppIconButton(AppIcons.Close, "Clear", onClick = { onValueChange("") }, iconSize = 20.dp)
                }
            }
        },
    )
}

@Preview(name = "Search fields", widthDp = 360)
@Composable
private fun SearchFieldPreview() = PaperlessTheme {
    Surface {
        Column(Modifier.padding(Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.s12)) {
            SearchField("", {})
            SearchField("rates", {})
            SearchField("", {}, placeholder = "Find a correspondent", variant = SearchFieldVariant.Sheet)
        }
    }
}
