package io.github.yasarmahomedabbas.paperlesscompanion.ui.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ApkFile
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.AppRelease
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme
import java.util.Locale

/**
 * The Updates screen. [onAllow] opens Android's "Install unknown apps" setting for this app; the
 * screen picks up the change when it comes back to the front.
 */
@Composable
fun UpdatesScreen(viewModel: UpdatesViewModel, onAllow: () -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.resumed() }
    // A download or Android's confirmation shouldn't be cut short by the screen going dark.
    val view = LocalView.current
    DisposableEffect(state.busy) {
        view.keepScreenOn = state.busy
        onDispose { view.keepScreenOn = false }
    }
    UpdatesContent(
        state = state,
        onBack = onBack,
        onInstall = viewModel::install,
        onStop = viewModel::stop,
        onAllow = onAllow,
        onCheckNow = viewModel::checkNow,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesContent(
    state: UpdatesUiState,
    onBack: () -> Unit,
    onInstall: () -> Unit,
    onStop: () -> Unit,
    onAllow: () -> Unit,
    onCheckNow: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Updates") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column {
                        Text("Paperless Scan", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Version ${state.installedVersion}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val offer = state.offer
                    if (offer == null) {
                        Text("Up to date", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Offer(offer, state.phase, onInstall, onStop, onAllow)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCheckNow, enabled = !state.checking && !state.busy) {
                    Text(if (state.checking) "Checking…" else "Check now")
                }
            }
            if (state.checkFailed) {
                Text(
                    "Couldn't reach GitHub. Check the connection and try again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun Offer(offer: AppRelease, phase: Phase, onInstall: () -> Unit, onStop: () -> Unit, onAllow: () -> Unit) {
    Text("Version ${offer.version} is ready", style = MaterialTheme.typography.titleSmall)
    if (offer.notes.isNotBlank()) {
        Text(offer.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    when (phase) {
        Phase.Idle -> InstallButton(onInstall)
        is Phase.Downloading -> {
            val fraction = if (phase.total > 0) (phase.done.toFloat() / phase.total).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Downloading ${megabytes(phase.done)} of ${megabytes(phase.total)} MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onStop) { Text("Stop") }
            }
        }
        Phase.Installing -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text("Installing…", style = MaterialTheme.typography.bodyMedium)
        }
        Phase.NeedsPermission -> {
            Text(
                "Allow Paperless Scan to install apps, then come back here to finish the update.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onAllow, modifier = Modifier.fillMaxWidth()) { Text("Allow") }
        }
        is Phase.Failed -> {
            Text(phase.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            InstallButton(onInstall)
        }
    }
}

@Composable
private fun InstallButton(onInstall: () -> Unit) {
    Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) { Text("Install") }
}

private fun megabytes(bytes: Long) = String.format(Locale.ROOT, "%.1f", bytes / 1_000_000.0)

private val previewRelease = AppRelease(
    version = "0.2.0",
    versionCode = 200,
    notes = "Scans upload in the background.\nFixes a crash when sharing a photo.",
    apk = ApkFile("paperless-scan-0.2.0.apk", 8_570_413, "", ""),
)

@Composable
private fun Preview(state: UpdatesUiState) = PaperlessTheme {
    UpdatesContent(state, onBack = {}, onInstall = {}, onStop = {}, onAllow = {}, onCheckNow = {})
}

@Preview
@Composable
private fun UpToDatePreview() = Preview(UpdatesUiState("0.1.4", offer = null))

@Preview
@Composable
private fun ReadyPreview() = Preview(UpdatesUiState("0.1.4", previewRelease))

@Preview
@Composable
private fun DownloadingPreview() = Preview(UpdatesUiState("0.1.4", previewRelease, Phase.Downloading(3_200_000, 8_570_413)))

@Preview
@Composable
private fun InstallingPreview() = Preview(UpdatesUiState("0.1.4", previewRelease, Phase.Installing))

@Preview
@Composable
private fun NeedsPermissionPreview() = Preview(UpdatesUiState("0.1.4", previewRelease, Phase.NeedsPermission))

@Preview
@Composable
private fun FailedPreview() = Preview(
    UpdatesUiState("0.1.4", previewRelease, Phase.Failed("The download didn't match the release, so it wasn't installed.")),
)

@Preview
@Composable
private fun CheckFailedPreview() = Preview(UpdatesUiState("0.1.4", offer = null, checkFailed = true))
