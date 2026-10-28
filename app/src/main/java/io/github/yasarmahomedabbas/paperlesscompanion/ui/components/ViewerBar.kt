package io.github.yasarmahomedabbas.paperlesscompanion.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.lead
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.viewerTitle

/**
 * The viewer bar of docs/design/components.md § Top bars: back, the title at 18/500 on one line,
 * then Share and More. The More menu ([menuOpen], closed through [onMenuOpenChange]) holds the
 * [MenuItem]s of [menu].
 */
@Composable
fun ViewerBar(
    title: String,
    onBack: () -> Unit,
    onShare: () -> Unit,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    shareEnabled: Boolean = true,
    menu: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = Spacing.s4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.onSurface)
        }
        Text(
            title,
            style = MaterialTheme.typography.viewerTitle,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = Spacing.s4).semantics { heading() },
        )
        IconButton(onClick = onShare, enabled = shareEnabled) {
            Icon(Icons.Default.Share, contentDescription = "Share", tint = if (shareEnabled) colors.onSurface else colors.onSurfaceVariant)
        }
        Box {
            IconButton(
                onClick = { onMenuOpenChange(true) },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (menuOpen) colors.surfaceContainerHigh else Color.Transparent,
                ),
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = colors.onSurface)
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { onMenuOpenChange(false) },
                modifier = Modifier.width(244.dp),
                shape = MaterialTheme.shapes.medium,
                containerColor = colors.surfaceContainer,
                tonalElevation = 0.dp,
                content = menu,
            )
        }
    }
}

/**
 * One item of the More menu (docs/design/components.md § Menu): 48 high, a 20dp `mfg` icon and 15
 * `fg` text. A [destructive] item (Move to trash) is `destructive` text and icon; put a
 * [MenuDivider] before it.
 */
@Composable
fun MenuItem(text: String, icon: ImageVector, onClick: () -> Unit, enabled: Boolean = true, destructive: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.lead) },
        onClick = onClick,
        enabled = enabled,
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
        colors = MenuDefaults.itemColors(
            textColor = if (destructive) colors.error else colors.onSurface,
            leadingIconColor = if (destructive) colors.error else colors.onSurfaceVariant,
        ),
        modifier = Modifier.height(48.dp).fillMaxWidth(),
        contentPadding = MenuDefaults.DropdownMenuItemContentPadding,
    )
}

/** The `border` divider before a menu's destructive item. */
@Composable
fun MenuDivider() {
    HorizontalDivider(Modifier.padding(vertical = Spacing.s4), color = MaterialTheme.colorScheme.outline)
}
