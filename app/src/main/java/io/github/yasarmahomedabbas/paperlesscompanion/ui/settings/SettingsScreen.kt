package io.github.yasarmahomedabbas.paperlesscompanion.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppButton
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppDialog
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppIcons
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.BackBar
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.ButtonVariant
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.SectionHeading
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.SettingsCard
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.SettingsRow
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.SettingsTrailing
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing

/**
 * Settings (layouts.md § Settings): the server, the fingerprint lock when the phone can lock,
 * Updates with a pill when a release waits, and Sign out behind a confirm.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    lockAvailable: Boolean,
    lockEnabled: Boolean,
    onLockChange: (Boolean) -> Unit,
    onOpenUpdates: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() }
    val updateReady by viewModel.updateReady.collectAsState()
    SettingsContent(viewModel.serverUrl, version, lockAvailable, lockEnabled, updateReady, onLockChange, onOpenUpdates, onSignOut, onBack)
}

@Composable
private fun SettingsContent(
    serverUrl: String,
    version: String?,
    lockAvailable: Boolean,
    lockEnabled: Boolean,
    updateReady: String?,
    onLockChange: (Boolean) -> Unit,
    onOpenUpdates: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    confirming: Boolean = false,
) {
    var confirmSignOut by rememberSaveable { mutableStateOf(confirming) }
    val card = Modifier.padding(horizontal = Spacing.cardGutter)
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        BackBar("Settings", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = Spacing.actionBottom)) {
            SectionHeading("Server", Modifier.padding(top = Spacing.s8))
            SettingsCard(card) { SettingsRow(serverUrl, icon = AppIcons.Server) }
            if (lockAvailable) {
                SectionHeading("Security", Modifier.padding(top = Spacing.s16))
                SettingsCard(card) {
                    SettingsRow(
                        "Unlock with fingerprint",
                        secondary = "Asks when the app opens. Falls back to your PIN.",
                        trailing = SettingsTrailing.Switch(lockEnabled, onLockChange),
                    )
                }
            }
            SectionHeading("App", Modifier.padding(top = Spacing.s16))
            SettingsCard(card) {
                SettingsRow(
                    "Updates",
                    secondary = version?.let { "Version $it" },
                    pill = updateReady?.let { "$it ready" },
                    trailing = SettingsTrailing.Chevron,
                    onClick = onOpenUpdates,
                )
            }
            Box(Modifier.fillMaxWidth().padding(top = Spacing.s32), contentAlignment = Alignment.Center) {
                AppButton("Sign out", { confirmSignOut = true }, variant = ButtonVariant.Secondary)
            }
        }
    }
    if (confirmSignOut) {
        AppDialog(
            title = "Sign out?",
            body = "The app forgets the server and its sign-in. Your documents stay on the server. Uploads still on their way are dropped.",
            confirmLabel = "Sign out",
            onConfirm = {
                confirmSignOut = false
                onSignOut()
            },
            onDismiss = { confirmSignOut = false },
        )
    }
}

@Preview(name = "Settings — view", widthDp = 412, heightDp = 915)
@Composable
private fun SettingsPreview() = PaperlessTheme {
    SettingsContent("http://paperless:8000", "0.3.2", lockAvailable = true, lockEnabled = true, updateReady = "0.4.0", {}, {}, {}, {})
}

@Preview(name = "Settings — no fingerprint, up to date", widthDp = 412, heightDp = 915)
@Composable
private fun SettingsNoLockPreview() = PaperlessTheme {
    SettingsContent("http://paperless:8000", "0.4.0", lockAvailable = false, lockEnabled = false, updateReady = null, {}, {}, {}, {})
}

@Preview(name = "Settings — sign out confirm", widthDp = 412, heightDp = 915)
@Composable
private fun SettingsSignOutPreview() = PaperlessTheme {
    SettingsContent("http://paperless:8000", "0.3.2", lockAvailable = true, lockEnabled = true, updateReady = null, {}, {}, {}, {}, confirming = true)
}
