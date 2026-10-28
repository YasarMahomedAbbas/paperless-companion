package io.github.yasarmahomedabbas.paperlesscompanion.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentFile
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.ServerException.Kind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.DocumentExports
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * [DocumentExports] on the phone: shares through this app's FileProvider ([authority], which must serve the
 * cache's `documents/` folder) and saves through MediaStore's Downloads (Android 10+) or a picked content URI.
 * Needs no storage permission.
 */
class PhoneExports(
    private val context: Context,
    private val authority: String,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : DocumentExports {

    override fun shareLocation(file: DocumentFile): String =
        FileProvider.getUriForFile(context, authority, file.file).toString()

    override val canSaveDirectly: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    override suspend fun saveToDownloads(file: DocumentFile): String = withContext(io) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) throw couldNotSave(file)
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, file.mimeType ?: "application/octet-stream")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = try {
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        } catch (e: RuntimeException) {
            throw couldNotSave(file, e)
        } ?: throw couldNotSave(file)
        try {
            val out = resolver.openOutputStream(uri, "w") ?: throw IOException("No stream for $uri")
            out.use { stream -> file.file.inputStream().use { it.copyTo(stream) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            // MediaStore may have renamed it ("bill (1).pdf") to keep names unique.
            resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: file.name
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw couldNotSave(file, e)
        }
    }

    override suspend fun saveTo(file: DocumentFile, location: String): Unit = withContext(io) {
        try {
            val out = context.contentResolver.openOutputStream(Uri.parse(location), "w")
                ?: throw IOException("No stream for $location")
            out.use { stream -> file.file.inputStream().use { it.copyTo(stream) } }
        } catch (e: Exception) {
            throw couldNotSave(file, e)
        }
    }

    private fun couldNotSave(file: DocumentFile, cause: Throwable? = null) =
        ServerException(Kind.UNREADABLE, "Couldn't save ${file.name}", cause)
}
