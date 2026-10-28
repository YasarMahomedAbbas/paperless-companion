package io.github.yasarmahomedabbas.paperlesscompanion.data

import android.content.Context
import coil3.ImageLoader
import coil3.Uri
import coil3.disk.DiskCache
import coil3.fetch.Fetcher
import coil3.key.Keyer
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.Options
import coil3.toUri
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.DocumentThumbnail
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.SettingsRepository
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * Loads a [DocumentThumbnail] from the signed-in server by handing its address to Coil's network fetcher.
 * The disk cache key is replaced by [thumbnailCacheKey]: Coil's default (the address) would ignore the version.
 * Signed out, there is no fetch.
 */
class ThumbnailFetcherFactory(
    private val settings: SettingsRepository,
    private val network: Fetcher.Factory<Uri>,
) : Fetcher.Factory<DocumentThumbnail> {
    override fun create(data: DocumentThumbnail, options: Options, imageLoader: ImageLoader): Fetcher? {
        val config = settings.load() ?: return null
        val keyed = options.copy(diskCacheKey = thumbnailCacheKey(config, data))
        return network.create(thumbnailUrl(config, data).toUri(), keyed, imageLoader)
    }
}

/** The memory cache's key for a thumbnail: the same as the disk cache's. */
class ThumbnailKeyer(private val settings: SettingsRepository) : Keyer<DocumentThumbnail> {
    override fun key(data: DocumentThumbnail, options: Options): String? =
        settings.load()?.let { thumbnailCacheKey(it, data) }
}

/** The app's image loader: thumbnails over [client] with the token (for the signed-in server only), cached. */
fun thumbnailImageLoader(context: Context, settings: SettingsRepository, client: OkHttpClient): ImageLoader {
    val authed = client.newBuilder().addInterceptor(ServerTokenInterceptor(settings)).build()
    val network = OkHttpNetworkFetcherFactory(callFactory = { authed })
    return ImageLoader.Builder(context)
        .components {
            add(ThumbnailFetcherFactory(settings, network), DocumentThumbnail::class)
            add(ThumbnailKeyer(settings), DocumentThumbnail::class)
            add(network)
        }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.15).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(File(context.cacheDir, "thumbnails").toOkioPath())
                .maxSizeBytes(64L * 1024 * 1024)
                .build()
        }
        .build()
}
