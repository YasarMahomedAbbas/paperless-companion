package io.github.yasarmahomedabbas.paperlesscompanion.data.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** When the app looks for a newer release: once a day, and once each time it opens. Both wait for a network. */
object UpdateSchedule {
    private const val DAILY = "updates-daily"
    private const val NOW = "updates-now"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** The daily check; kept as it is when it's already queued. */
    fun daily(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS).setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** One check now (as soon as there's a network). */
    fun now(context: Context) {
        val request = OneTimeWorkRequestBuilder<UpdateWorker>().setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
    }
}
