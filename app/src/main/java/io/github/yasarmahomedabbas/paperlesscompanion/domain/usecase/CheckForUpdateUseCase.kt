package io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase

import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.AppRelease
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.UpdateException
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.ReleaseSource
import io.github.yasarmahomedabbas.paperlesscompanion.domain.repository.UpdateStore

/**
 * One update check: asks [releases] for the newest release, keeps it as the offer when it's newer
 * than the [installed] version code, and [announce]s it once per version code. The daily worker and
 * *Check now* run it.
 */
class CheckForUpdateUseCase(
    private val releases: ReleaseSource,
    private val store: UpdateStore,
    private val installed: () -> Long,
    private val announce: (AppRelease) -> Unit,
) {
    enum class Outcome { Done, TryAgain }

    /**
     * No network, or a server error (or no answer at all), is worth another try, and the stored
     * offer stays; any other refusal (a rate limit, say) isn't.
     */
    suspend fun run(): Outcome {
        val latest = try {
            releases.latest()
        } catch (e: UpdateException) {
            val retry = e.kind == UpdateException.Kind.OFFLINE ||
                (e.kind == UpdateException.Kind.SERVER && (e.status == null || e.status >= 500))
            return if (retry) Outcome.TryAgain else Outcome.Done
        }
        val offer = latest?.takeIf { it.isNewerThan(installed()) }
        store.saveOffer(offer)
        if (offer != null && offer.versionCode > store.notifiedVersionCode()) {
            announce(offer)
            store.markNotified(offer.versionCode)
        }
        return Outcome.Done
    }
}
