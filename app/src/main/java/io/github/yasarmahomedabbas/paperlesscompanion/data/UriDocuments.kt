package io.github.yasarmahomedabbas.paperlesscompanion.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentSource
import java.io.FileNotFoundException

/** Turns content:// and file:// URIs (scans, photos, picked or shared files) into DocumentSources. */
class UriDocuments(private val resolver: ContentResolver) {

    fun from(uri: Uri, fallbackName: String): DocumentSource {
        val mimeType = resolver.getType(uri) ?: guessMimeType(fallbackName)
        return DocumentSource(
            name = displayName(uri) ?: fallbackName,
            mimeType = mimeType,
            open = { resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString()) },
        )
    }

    private fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun guessMimeType(name: String): String = when (name.substringAfterLast('.').lowercase()) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        else -> "image/jpeg"
    }
}
