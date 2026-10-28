package io.github.yasarmahomedabbas.paperlesscompanion.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

/**
 * Whether [apk] is signed by the same key as the running app. Android refuses another key on an
 * update anyway; this says so in words before any install starts.
 */
fun sameSigner(context: Context, apk: File): Boolean {
    val pm = context.packageManager
    val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        @Suppress("DEPRECATION")
        PackageManager.GET_SIGNATURES
    }
    val ours = pm.getPackageInfo(context.packageName, flags).signers()
    val theirs = pm.getPackageArchiveInfo(apk.path, flags)?.signers().orEmpty()
    return sameSigners(ours, theirs)
}

/** The certificates the package's contents are signed with now (not its rotation history). */
private fun PackageInfo.signers(): List<ByteArray> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        signingInfo?.apkContentsSigners?.map { it.toByteArray() }.orEmpty()
    } else {
        // Below API 28 there's only the deprecated field, which holds the same certificates.
        @Suppress("DEPRECATION")
        signatures?.map { it.toByteArray() }.orEmpty()
    }

/**
 * The compare, apart for tests: the same set of certificates (by sha256), and at least one. An
 * unsigned or unreadable APK never matches, nor does an empty [ours].
 */
fun sameSigners(ours: List<ByteArray>, theirs: List<ByteArray>): Boolean {
    if (ours.isEmpty() || theirs.isEmpty()) return false
    return ours.digests() == theirs.digests()
}

private fun List<ByteArray>.digests(): Set<String> = mapTo(mutableSetOf()) { cert ->
    MessageDigest.getInstance("SHA-256").digest(cert).joinToString("") { "%02x".format(it) }
}
