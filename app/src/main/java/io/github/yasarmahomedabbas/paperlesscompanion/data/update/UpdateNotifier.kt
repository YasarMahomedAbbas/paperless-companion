package io.github.yasarmahomedabbas.paperlesscompanion.data.update

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.yasarmahomedabbas.paperlesscompanion.R
import io.github.yasarmahomedabbas.paperlesscompanion.domain.model.AppRelease

/**
 * The "Update ready" notification. A tap opens [opens] with [EXTRA_OPEN_UPDATES], which shows the
 * Updates screen. Without the notification permission (API 33+) nothing is posted; the offer still
 * waits on the Updates screen.
 */
class UpdateNotifier(private val context: Context, private val opens: Class<out Activity>) {
    fun createChannel() {
        val channel = NotificationChannel(CHANNEL, "Updates", NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun announce(release: AppRelease) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val open = Intent(context, opens)
            .putExtra(EXTRA_OPEN_UPDATES, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = PendingIntent.getActivity(
            context,
            0,
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_update)
            .setContentTitle("Update ready")
            .setContentText("Version ${release.version} is ready to install")
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val EXTRA_OPEN_UPDATES = "io.github.yasarmahomedabbas.paperlesscompanion.OPEN_UPDATES"
        private const val CHANNEL = "updates"
        private const val NOTIFICATION_ID = 1
    }
}
