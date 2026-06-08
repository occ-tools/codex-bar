package com.codexbar.android.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.codexbar.android.MainActivity
import com.codexbar.android.R
import com.codexbar.android.core.domain.model.QuotaInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuotaNotificationService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "quota_monitor"
        const val RESET_CHANNEL_ID = "quota_reset_alert"
        const val NOTIFICATION_ID = 1001
        const val RESET_NOTIFICATION_ID_BASE = 2000
        const val ACTION_REFRESH = "com.codexbar.android.ACTION_REFRESH"
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val monitorChannel = NotificationChannel(
            CHANNEL_ID,
            "AI Quota Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows current AI service quota usage"
            setShowBadge(false)
        }

        val resetChannel = NotificationChannel(
            RESET_CHANNEL_ID,
            "Quota Reset Alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifies when AI service quotas have been reset"
        }

        manager.createNotificationChannels(listOf(monitorChannel, resetChannel))
    }

    fun showQuotaNotification(quotas: List<QuotaInfo>) {
        if (!canPostNotifications()) return

        val remoteViews = RemoteViews(context.packageName, R.layout.notification_compact)

        // Populate service data
        quotas.forEachIndexed { index, quota ->
            if (index >= 3) return@forEachIndexed // Max 3 services

            val maxUtilization = quota.windows.maxOfOrNull { it.utilization } ?: 0.0
            val progress = (maxUtilization * 100).toInt()

            when (index) {
                0 -> {
                    remoteViews.setTextViewText(R.id.service_name_1, quota.displayName)
                    remoteViews.setProgressBar(R.id.progress_bar_1, 100, progress, false)
                    remoteViews.setTextViewText(R.id.progress_text_1, "${progress}%")
                }
                1 -> {
                    remoteViews.setTextViewText(R.id.service_name_2, quota.displayName)
                    remoteViews.setProgressBar(R.id.progress_bar_2, 100, progress, false)
                    remoteViews.setTextViewText(R.id.progress_text_2, "${progress}%")
                }
                2 -> {
                    remoteViews.setTextViewText(R.id.service_name_3, quota.displayName)
                    remoteViews.setProgressBar(R.id.progress_bar_3, 100, progress, false)
                    remoteViews.setTextViewText(R.id.progress_text_3, "${progress}%")
                }
            }
        }

        val elapsed = formatElapsed(quotas.firstOrNull()?.fetchedAt)
        remoteViews.setTextViewText(R.id.update_time, "Updated: $elapsed")

        // Refresh action
        val refreshIntent = Intent(context, RefreshReceiver::class.java).apply {
            action = ACTION_REFRESH
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context, 0, refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dashboard tap intent
        val dashboardIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("codexbar://dashboard")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val dashboardPendingIntent = PendingIntent.getActivity(
            context, 0, dashboardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quota)
            .setCustomContentView(remoteViews)
            .setContentIntent(dashboardPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .addAction(R.drawable.ic_refresh, "Refresh", refreshPendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun showResetNotification(quota: QuotaInfo, windowLabel: String) {
        if (!canPostNotifications()) return

        val dashboardIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse("codexbar://dashboard")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val dashboardPendingIntent = PendingIntent.getActivity(
            context, 0, dashboardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, RESET_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quota)
            .setContentTitle("${quota.displayName} quota reset")
            .setContentText("$windowLabel window has been reset. Your quota is fully available.")
            .setContentIntent(dashboardPendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationId = RESET_NOTIFICATION_ID_BASE +
            "${quota.service.name}_${quota.accountId}_$windowLabel".hashCode().and(0xFFFF)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }

    private fun formatElapsed(fetchedAt: Instant?): String {
        if (fetchedAt == null) return "just now"
        val elapsed = Duration.between(fetchedAt, Instant.now())
        return when {
            elapsed.toMinutes() < 1 -> "just now"
            elapsed.toMinutes() < 60 -> "${elapsed.toMinutes()} min ago"
            else -> "${elapsed.toHours()}h ago"
        }
    }

    private fun canPostNotifications(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
