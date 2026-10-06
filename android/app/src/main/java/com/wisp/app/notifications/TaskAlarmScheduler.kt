package com.wisp.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.wisp.app.sync.Item
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Schedules exact on-device alarms for dated tasks using Android AlarmManager.
 * Operates as a resilient on-device fallback / companion to the backend Edge Function.
 * Reference: PRD.md Section 6.1.6
 */
class TaskAlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        private const val TAG = "TaskAlarmScheduler"
        const val ACTION_REMINDER_ALARM = "com.wisp.app.ACTION_REMINDER_ALARM"
        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_CONTENT = "extra_content"
        const val EXTRA_SCENARIO = "extra_scenario"
        const val EXTRA_DUE_AT = "extra_due_at"
    }

    /**
     * Schedules day-before/same-day and overdue alarms for a task if due_at is set.
     */
    fun scheduleTaskReminders(item: Item) {
        val itemId = item.id ?: return
        val dueAtStr = item.dueAt ?: return
        if (item.completed) return

        val dueMillis = parseIsoToMillis(dueAtStr) ?: return
        val nowMillis = System.currentTimeMillis()

        // 1. Day-Before or Same-Day Reminder
        if (!item.reminderSentDayBefore && dueMillis > nowMillis) {
            val hoursRemaining = (dueMillis - nowMillis) / (3600 * 1000)

            if (hoursRemaining > 24) {
                // Day-before alarm (fire 24h prior)
                val dayBeforeMillis = dueMillis - (24 * 3600 * 1000)
                if (dayBeforeMillis > nowMillis) {
                    setAlarm(itemId, item.content, "DAY_BEFORE", dueAtStr, dayBeforeMillis, 1001)
                }
            } else {
                // Same-day fallback (due within 24h)
                // Fire immediately if during active hours or in 5 minutes
                val fallbackMillis = nowMillis + 5 * 60 * 1000
                if (fallbackMillis < dueMillis) {
                    setAlarm(itemId, item.content, "SAME_DAY_FALLBACK", dueAtStr, fallbackMillis, 1002)
                }
            }
        }

        // 2. Overdue Follow-Up Alarm
        if (!item.reminderSentOverdue) {
            if (dueMillis > nowMillis) {
                // Fire exactly at due_at timestamp
                setAlarm(itemId, item.content, "OVERDUE", dueAtStr, dueMillis, 1003)
            } else {
                // Already overdue -> fire one-time overdue alert now
                setAlarm(itemId, item.content, "OVERDUE", dueAtStr, nowMillis + 1000, 1004)
            }
        }
    }

    /**
     * Cancels scheduled alarms for a task (e.g. when completed)
     */
    fun cancelTaskReminders(itemId: String) {
        listOf(1001, 1002, 1003, 1004).forEach { requestCode ->
            val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
                action = ACTION_REMINDER_ALARM
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                itemId.hashCode() + requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    private fun setAlarm(
        itemId: String,
        content: String,
        scenario: String,
        dueAt: String,
        triggerAtMillis: Long,
        requestCodeOffset: Int
    ) {
        val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
            action = ACTION_REMINDER_ALARM
            putExtra(EXTRA_ITEM_ID, itemId)
            putExtra(EXTRA_CONTENT, content)
            putExtra(EXTRA_SCENARIO, scenario)
            putExtra(EXTRA_DUE_AT, dueAt)
        }

        val requestCode = itemId.hashCode() + requestCodeOffset
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled $scenario alarm for item $itemId at $triggerAtMillis")
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm permission not granted: ${e.message}")
        }
    }

    private fun parseIsoToMillis(isoString: String): Long? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Instant.parse(isoString).toEpochMilli()
            } else {
                null
            }
        } catch (e: DateTimeParseException) {
            null
        }
    }
}
