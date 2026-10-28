package io.github.yasarmahomedabbas.paperlesscompanion.data.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.yasarmahomedabbas.paperlesscompanion.PaperlessApp
import io.github.yasarmahomedabbas.paperlesscompanion.domain.usecase.CheckForUpdateUseCase.Outcome

/** Runs one update check; no network or a server error is tried twice more, then left to the next one. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val check = (applicationContext as PaperlessApp).container.checkForUpdate
        return when (check.run()) {
            Outcome.Done -> Result.success()
            Outcome.TryAgain -> if (runAttemptCount < RETRIES) Result.retry() else Result.success()
        }
    }

    private companion object {
        const val RETRIES = 2
    }
}
