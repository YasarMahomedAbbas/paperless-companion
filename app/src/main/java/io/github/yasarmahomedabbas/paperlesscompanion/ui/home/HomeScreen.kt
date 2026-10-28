package io.github.yasarmahomedabbas.paperlesscompanion.ui.home

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.FileProvider
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSource
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.UploadState
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.isFinished
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.DocumentFiles
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.AppButton
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.ButtonVariant
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.LargeTitleBar
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.NoticeBanner
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.SectionHeading
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.UploadCard
import io.github.yasarmahomedabbas.paperlesscompanion.ui.components.UploadStatus
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.PaperlessTheme
import io.github.yasarmahomedabbas.paperlesscompanion.ui.theme.Spacing
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    documents: DocumentFiles,
    /** Documents shared, scanned, photographed or picked, waiting to be uploaded now that Home is unlocked. */
    pending: List<DocumentSource>,
    onPendingConsumed: () -> Unit,
    /** Every document the screen brings in goes here first, so one that arrives while locked waits. */
    onDocuments: (List<DocumentSource>) -> Unit,
    lockAvailable: Boolean,
    lockEnabled: Boolean,
    onLockChange: (Boolean) -> Unit,
    onSignedOut: () -> Unit,
    onOpenUpdates: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    var playServicesNeedsCamera by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(pending) {
        if (pending.isNotEmpty()) {
            viewModel.upload(pending)
            onPendingConsumed()
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pdf?.uri?.let { uri ->
            onDocuments(listOf(documents.from(uri.toString(), "Scan ${timestamp()}.pdf")))
        }
    }
    var photoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = photoUri
        if (saved && uri != null) onDocuments(listOf(documents.from(uri.toString(), "Photo ${timestamp()}.jpg")))
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onDocuments(uris.map { documents.from(it.toString(), "Document ${timestamp()}") })
    }

    fun scan() {
        val activity = context as? Activity ?: return
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener {
                // The scanner runs inside Play services, so it is Play services that needs the camera, not this app.
                // On GrapheneOS's sandboxed Play it starts without it, and the scanner only says to go and enable it.
                if (context.packageManager.checkPermission(Manifest.permission.CAMERA, PLAY_SERVICES) != PackageManager.PERMISSION_GRANTED) {
                    notice = "The scanner runs in Google Play services, which needs camera access. " +
                        "Open settings, then Permissions → Camera → Allow only while using the app."
                    playServicesNeedsCamera = true
                    return@addOnSuccessListener
                }
                notice = null
                playServicesNeedsCamera = false
                scanLauncher.launch(IntentSenderRequest.Builder(it).build())
            }
            .addOnFailureListener {
                notice = "The scanner needs Google Play services, which this phone doesn't have. Use Take photo instead."
                playServicesNeedsCamera = false
            }
    }

    fun takePhoto() {
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(dir, "Photo ${timestamp()}.jpg"))
        photoUri = uri
        photoLauncher.launch(uri)
    }

    HomeContent(
        serverLine = listOfNotNull(state.serverUrl, version?.let { "v$it" }).joinToString(" · "),
        uploads = state.uploads,
        notice = notice,
        showOpenSettings = playServicesNeedsCamera,
        onScan = ::scan,
        onTakePhoto = ::takePhoto,
        onChooseFile = { fileLauncher.launch(arrayOf("application/pdf", "image/*")) },
        onOpenSettings = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", PLAY_SERVICES, null)))
        },
        onSignOut = {
            viewModel.signOut()
            onSignedOut()
        },
        onClearFinished = viewModel::clearFinished,
        onRetry = viewModel::retry,
        onOpen = { id -> viewModel.documentUrl(id)?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) } },
        lockAvailable = lockAvailable,
        lockEnabled = lockEnabled,
        onLockChange = onLockChange,
        onOpenUpdates = onOpenUpdates,
    )
}

/**
 * Today's home until #9 makes Documents the home (layouts.md): a list shape whose only section
 * is the uploads. The large title bar with its overflow menu (Updates, the fingerprint lock,
 * Sign out), the server line, Scan as the one primary
 * action with Take photo and Choose file as secondaries, the scanner notice, then the upload
 * cards under "Uploads".
 */
@Composable
private fun HomeContent(
    serverLine: String,
    uploads: List<UploadItem>,
    notice: String?,
    showOpenSettings: Boolean,
    onScan: () -> Unit,
    onTakePhoto: () -> Unit,
    onChooseFile: () -> Unit,
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit,
    onClearFinished: () -> Unit,
    onRetry: (Long) -> Unit,
    onOpen: (Int) -> Unit,
    lockAvailable: Boolean = false,
    lockEnabled: Boolean = false,
    onLockChange: (Boolean) -> Unit = {},
    onOpenUpdates: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding(),
        contentPadding = PaddingValues(bottom = Spacing.actionBottom),
    ) {
        item(key = "title") {
            LargeTitleBar("Paperless Scan") {
                OverflowMenu(lockAvailable, lockEnabled, onLockChange, onOpenUpdates, onSignOut)
            }
        }
        item(key = "server") {
            Text(
                serverLine,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Spacing.gutter),
            )
        }
        item(key = "actions") {
            Column(
                Modifier.padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.s24),
                verticalArrangement = Arrangement.spacedBy(Spacing.s8),
            ) {
                AppButton("Scan document", onScan, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                    AppButton("Take photo", onTakePhoto, Modifier.weight(1f), ButtonVariant.Secondary)
                    AppButton("Choose file", onChooseFile, Modifier.weight(1f), ButtonVariant.Secondary)
                }
            }
        }
        notice?.let {
            item(key = "notice") {
                NoticeBanner(
                    it,
                    modifier = Modifier.padding(start = Spacing.cardGutter, end = Spacing.cardGutter, top = Spacing.s16),
                    actionLabel = if (showOpenSettings) "Open settings" else null,
                    onAction = onOpenSettings,
                )
            }
        }
        item(key = "heading") {
            SectionHeading("Uploads", Modifier.padding(top = Spacing.s16)) {
                if (uploads.any { it.state.isFinished }) {
                    AppButton("Clear finished", onClearFinished, variant = ButtonVariant.Text)
                }
            }
        }
        if (uploads.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Nothing uploaded yet. You can also share PDFs and photos to this app from other apps.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.gutter),
                )
            }
        }
        items(uploads, key = { it.id }) { item ->
            val (status, text) = item.state.card()
            val documentId = (item.state as? UploadState.Done)?.documentId
            UploadCard(
                name = item.name,
                status = status,
                statusText = text,
                modifier = Modifier.padding(horizontal = Spacing.cardGutter, vertical = Spacing.s4),
                actionLabel = when {
                    item.state is UploadState.Failed -> "Retry"
                    documentId != null -> "Open"
                    else -> null
                },
                onAction = { if (documentId != null) onOpen(documentId) else onRetry(item.id) },
            )
        }
    }
}

/** The title bar's ⋮ menu: Updates (#13), Lock with fingerprint when the phone can (#8), Sign out. */
@Composable
private fun OverflowMenu(
    lockAvailable: Boolean,
    lockEnabled: Boolean,
    onLockChange: (Boolean) -> Unit,
    onOpenUpdates: () -> Unit,
    onSignOut: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Updates") },
                onClick = {
                    open = false
                    onOpenUpdates()
                },
            )
            if (lockAvailable) {
                DropdownMenuItem(
                    text = { Text("Lock with fingerprint") },
                    trailingIcon = { Switch(checked = lockEnabled, onCheckedChange = null) },
                    onClick = {
                        open = false
                        onLockChange(!lockEnabled)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("Sign out") },
                onClick = {
                    open = false
                    onSignOut()
                },
            )
        }
    }
}

private fun UploadState.card(): Pair<UploadStatus, String> = when (this) {
    UploadState.Uploading -> UploadStatus.Working to "Uploading…"
    is UploadState.Processing -> UploadStatus.Working to "Paperless is reading it…"
    is UploadState.Done -> UploadStatus.Added to "Added to Paperless"
    is UploadState.Failed -> (if (unreachable) UploadStatus.Unreachable else UploadStatus.Refused) to message
}

private val previewUploads = listOf(
    UploadItem(3, "Scan 2026-10-28 09-40-12.pdf", UploadState.Processing("t3")),
    UploadItem(2, "Photo 2026-10-28 09-38-50.jpg", UploadState.Done(42)),
    UploadItem(1, "Scan 2026-10-27 18-02-11.pdf", UploadState.Failed("Not consuming it: it's a duplicate of 'Electricity bill — October'")),
    UploadItem(
        0,
        "Document 2026-10-26 08-15-00",
        UploadState.Failed("Can't reach Paperless (timeout). Are you on the home network or the VPN?", unreachable = true),
    ),
)

@Composable
private fun HomePreview(uploads: List<UploadItem>, notice: String? = null) = PaperlessTheme {
    HomeContent("http://paperless:8000 · v0.4.0", uploads, notice, notice != null, {}, {}, {}, {}, {}, {}, {}, {})
}

@Preview(name = "Home — no uploads", widthDp = 412, heightDp = 915)
@Composable
private fun HomeEmptyPreview() = HomePreview(emptyList())

@Preview(name = "Home — uploads", widthDp = 412, heightDp = 915)
@Composable
private fun HomeUploadsPreview() = HomePreview(previewUploads)

@Preview(name = "Home — scanner needs camera", widthDp = 360, heightDp = 800)
@Composable
private fun HomeNeedsCameraPreview() = HomePreview(
    previewUploads.take(1),
    notice = "The scanner runs in Google Play services, which needs camera access. " +
        "Open settings, then Permissions → Camera → Allow only while using the app.",
)

private const val PLAY_SERVICES = "com.google.android.gms"

private fun timestamp(): String = SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.ROOT).format(Date())
