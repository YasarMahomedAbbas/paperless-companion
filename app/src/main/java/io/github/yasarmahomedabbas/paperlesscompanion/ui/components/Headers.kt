package io.github.yasarmahomedabbas.paperlesscompanion.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing

/**
 * The large-title bar of a top-level screen (docs/design/components.md § Top bars): the
 * 28/500 title on the left, its actions on the right, padding 12 / 20 / 8. Not collapsing.
 */
@Composable
fun LargeTitleBar(title: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .padding(start = Spacing.gutter, end = Spacing.s12, top = Spacing.s12, bottom = Spacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        actions()
    }
}

/**
 * The back bar of a pushed screen (components.md § Top bars): the back icon button ("Back")
 * and the 22/500 title.
 */
@Composable
fun BackBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = Spacing.s4, end = Spacing.gutter, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        AppIconButton(AppIcons.Back, "Back", onBack)
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
    }
}

/**
 * The bar of a form (components.md § Top bars, Form bar): a close icon button ("Close without
 * saving") and the 22/500 title. The actions are at the bottom of the form, not here.
 */
@Composable
fun FormBar(title: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = Spacing.s4, end = Spacing.gutter, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        AppIconButton(AppIcons.Close, "Close without saving", onClose)
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
    }
}

/**
 * A section heading ("Uploading", "Tags"): 14/500 in `mfg`, on the 20 page gutter, with an
 * optional action at the end (Clear finished).
 */
@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier, action: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(start = Spacing.gutter, end = Spacing.s8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        action()
    }
}
