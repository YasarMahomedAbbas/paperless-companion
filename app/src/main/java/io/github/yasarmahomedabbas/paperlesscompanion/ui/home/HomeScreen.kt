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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import io.github.yasarmahomedabbas.paperlesscompanion.data.UriDocuments
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSource
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.UploadState
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.isFinished
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    documents: UriDocuments,
    shared: List<DocumentSource>,
    onSharedConsumed: () -> Unit,
    onSignedOut: () -> Unit,
    onOpenUpdates: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    var playServicesNeedsCamera by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(shared) {
        if (shared.isNotEmpty()) {
            viewModel.upload(shared)
            onSharedConsumed()
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pdf?.uri?.let { uri ->
            viewModel.upload(listOf(documents.from(uri, "Scan ${timestamp()}.pdf")))
        }
    }
    var photoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = photoUri
        if (saved && uri != null) viewModel.upload(listOf(documents.from(uri, "Photo ${timestamp()}.jpg")))
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.upload(uris.map { documents.from(it, "Document ${timestamp()}") })
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paperless Scan") },
                actions = {
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Updates") },
                                onClick = {
                                    menu = false
                                    onOpenUpdates()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Sign out") },
                                onClick = {
                                    menu = false
                                    viewModel.signOut()
                                    onSignedOut()
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize(),
        ) {
            Text(listOfNotNull(state.serverUrl, version?.let { "v$it" }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Button(onClick = ::scan, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Text("Scan document", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = ::takePhoto, modifier = Modifier.weight(1f)) { Text("Take photo") }
                OutlinedButton(
                    onClick = { fileLauncher.launch(arrayOf("application/pdf", "image/*")) },
                    modifier = Modifier.weight(1f),
                ) { Text("Choose file") }
            }
            notice?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (playServicesNeedsCamera) {
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", PLAY_SERVICES, null)),
                        )
                    }) { Text("Open settings") }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Uploads", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (state.uploads.any { it.state.isFinished }) {
                    TextButton(onClick = viewModel::clearFinished) { Text("Clear finished") }
                }
            }
            if (state.uploads.isEmpty()) {
                Text(
                    "Nothing uploaded yet. You can also share PDFs and photos to this app from other apps.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.uploads, key = { it.id }) { item ->
                    UploadCard(
                        item = item,
                        onRetry = { viewModel.retry(item.id) },
                        onOpen = { id ->
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.documentUrl(id))))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun UploadCard(item: UploadItem, onRetry: () -> Unit, onOpen: (Int) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val (text, isError) = when (val s = item.state) {
                    UploadState.Uploading -> "Uploading…" to false
                    is UploadState.Processing -> "Paperless is processing it…" to false
                    is UploadState.Done -> "Added to Paperless" to false
                    is UploadState.Failed -> s.message to true
                }
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            when (val s = item.state) {
                UploadState.Uploading, is UploadState.Processing ->
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                is UploadState.Done -> s.documentId?.let { id -> TextButton(onClick = { onOpen(id) }) { Text("Open") } }
                is UploadState.Failed -> TextButton(onClick = onRetry) { Text("Retry") }
            }
        }
    }
}

private const val PLAY_SERVICES = "com.google.android.gms"

private fun timestamp(): String = SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.ROOT).format(Date())
