package io.github.yasarmahomedabbas.paperlesscompanion.domain.model

/** The installable file of a release, as its publisher describes it. [sha256] is lower-case hex. */
data class ApkFile(
    val name: String,
    val size: Long,
    val sha256: String,
    /** Where to download it from; only the data layer reads it. */
    val location: String,
)

/** A published version of this app. [versionCode] orders releases: higher is newer. */
data class AppRelease(
    val version: String,
    val versionCode: Long,
    val notes: String,
    val apk: ApkFile,
) {
    fun isNewerThan(installed: Long): Boolean = versionCode > installed
}

/**
 * Why an update couldn't be found, downloaded or installed. [status] is set on a [Kind.SERVER]
 * answer when there was one; none or one of 500 and above is worth another try.
 */
class UpdateException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
    val status: Int? = null,
) : Exception(message, cause) {
    enum class Kind {
        /** The connection failed or dropped. */
        OFFLINE,

        /** The release source answered with something other than what was asked for. */
        SERVER,

        /** The download isn't the file the release describes. */
        CHECKSUM,

        /** The file isn't signed with the app's own key. */
        NOT_OURS,

        /** Installing apps isn't allowed for this app yet; the download is kept. */
        NEEDS_PERMISSION,

        /** The system refused the install, or it was cancelled. */
        REFUSED,

        /** The phone couldn't write it (usually: no space). */
        STORAGE,
    }
}

/** How an install ended, once it was handed to the system. */
sealed interface InstallEvent {
    data object Installed : InstallEvent

    /** Refused or cancelled; [detail] is the system's message, empty when cancelled. */
    data class Failed(val detail: String) : InstallEvent
}
