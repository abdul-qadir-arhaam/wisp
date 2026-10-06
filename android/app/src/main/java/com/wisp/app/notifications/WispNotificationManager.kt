package com.wisp.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.wisp.app.mainapp.MainActivity

/**
 * Handles creation and presentation of native Android notifications for tasks.
 * Reference: PRD.md Section 6.1.6 & ARCHITECTURE.md Section 2.1, 2.4
 * 
 * STRICT PRD RULE:
 * Reminders are delivered exclusively as visual push notifications.
 * Spoken/TTS delivery of reminders is strictly prohibited under any circumstance.
 */
class WispNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "wisp_reminders"
        const val CHANNEL_NAME = "Wisp Reminders"
        const val CHANNEL_DESCRIPTION = "Scheduled reminders and alerts for dated tasks"
        const val EXTRA_ITEM_ID = "extra_item_id"
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Shows a day-before reminder (~24h before due date)
     */
    fun showDayBeforeReminder(itemId: String, content: String, dueAt: String?) {
        val title = "Upcoming Task Tomorrow"
        val body = "Remember: $content"
        showNotification(itemId, title, body, "DAY_BEFORE")
    }

    /**
     * Shows a same-day reminder (fallback for tasks created <24h before due date)
     */
    fun showSameDayReminder(itemId: String, content: String, dueAt: String?) {
        val title = "Task Due Today"
        val body = "Due today: $content"
        showNotification(itemId, title, body, "SAME_DAY_FALLBACK")
    }

    /**
     * Shows a one-time overdue reminder (task passed due_at and not yet completed)
     */
    fun showOverdueReminder(itemId: String, content: String, dueAt: String?) {
        val title = "Task Overdue"
        val body = "Overdue: $content"
        showNotification(itemId, title, body, "OVERDUE")
    }

    /**
     * Generic notification dispatcher conforming strictly to visual-only delivery
     */
    fun showNotification(itemId: String, title: String, body: String, scenario: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ITEM_ID, itemId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            itemId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            val notificationId = itemId.hashCode()
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Android 13+ permission not granted
        }
    }

    /**
     * Cancels an active notification when the task is marked completed
     */
    fun cancelReminder(itemId: String) {
        notificationManager.cancel(itemId.hashCode())
    }
}
