package io.github.yasarmahomedabbas.paperlesscompanion.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.IntentCompat
import androidx.core.content.pm.PackageInfoCompat
import io.github.yasarmahomedabbas.paperlesscompanion.PaperlessApp
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.InstallEvent
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.UpdateException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.UpdateException.Kind
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.AppInstaller
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.UpdateStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * [AppInstaller] for this app's own package through a `PackageInstaller` session, committed with a
 * `PendingIntent` to [InstallResultReceiver]. Every install is confirmed by the user: the session
 * asks for it explicitly (API 31+; below that Android always asks this app anyway). Nothing is
 * installed that isn't signed with the app's own key.
 *
 * Android's confirmation is an `Intent`, so it stays here: [confirmation] holds it until the
 * activity starts it.
 */
class PackageInstallerUpdates(
    private val context: Context,
    private val store: UpdateStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : AppInstaller {
    private val _events = MutableStateFlow<InstallEvent?>(null)
    override val events: StateFlow<InstallEvent?> = _events.asStateFlow()

    private val _confirmation = MutableStateFlow<Intent?>(null)

    /** Android's confirmation of a committed install, for the activity to start. */
    val confirmation: StateFlow<Intent?> = _confirmation.asStateFlow()

    override fun installedVersionCode(): Long =
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    override fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** The settings screen that lets this app install updates. */
    fun permissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    override suspend fun install(apk: File) {
        if (!canInstall()) throw UpdateException(Kind.NEEDS_PERMISSION, "installing updates isn't allowed yet")
        try {
            withContext(io) {
                if (!sameSigner(context, apk)) throw UpdateException(Kind.NOT_OURS, "signed with another key")
                commit(apk)
            }
        } finally {
            apk.delete()
        }
    }

    override fun clearEvents() {
        _events.value = null
    }

    /** Marks [intent] started, unless a newer one came meanwhile. */
    fun consumeConfirmation(intent: Intent) {
        _confirmation.compareAndSet(intent, null)
    }

    /** What [InstallResultReceiver] heard. */
    internal fun onResult(status: Int, message: String?, confirm: Intent?) {
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> confirm?.let { _confirmation.value = it }
            PackageInstaller.STATUS_SUCCESS -> {
                store.saveOffer(null)
                _events.value = InstallEvent.Installed
            }
            else -> {
                val detail = if (status == PackageInstaller.STATUS_FAILURE_ABORTED) "" else message.orEmpty()
                _events.value = InstallEvent.Failed(detail)
            }
        }
    }

    private fun commit(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Always Android's confirmation, even once this app is its own installer of record.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val id = try {
            installer.createSession(params)
        } catch (e: IOException) {
            throw UpdateException(Kind.STORAGE, "can't start the install", e)
        }
        val session = installer.openSession(id)
        try {
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            session.commit(resultIntent().intentSender)
        } catch (e: IOException) {
            session.abandon()
            throw UpdateException(Kind.STORAGE, "can't write the install", e)
        } catch (e: RuntimeException) {
            session.abandon()
            throw UpdateException(Kind.REFUSED, e.message.orEmpty(), e)
        } finally {
            session.close()
        }
    }

    // Mutable: the system adds the status to it. Explicit, to our own non-exported receiver.
    private fun resultIntent(): PendingIntent {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, 0, Intent(context, InstallResultReceiver::class.java), flags)
    }
}

/**
 * The install's outcome (not exported: only the system, through the installer's `PendingIntent`,
 * reaches it). Updating the app itself ends this process, so its own success is usually seen by
 * the next launch's check instead.
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val installer = (context.applicationContext as PaperlessApp).container.installer
        installer.onResult(
            status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE),
            message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE),
            confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java),
        )
    }
}
