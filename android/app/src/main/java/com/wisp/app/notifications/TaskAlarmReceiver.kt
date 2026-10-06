package com.wisp.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * BroadcastReceiver triggered by AlarmManager when a scheduled reminder is due.
 * Dispatches the visual notification via WispNotificationManager.
 * Reference: PRD.md Section 6.1.6
 */
class TaskAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "TaskAlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TaskAlarmScheduler.ACTION_REMINDER_ALARM) return

        val itemId = intent.getStringExtra(TaskAlarmScheduler.EXTRA_ITEM_ID) ?: return
        val content = intent.getStringExtra(TaskAlarmScheduler.EXTRA_CONTENT) ?: return
        val scenario = intent.getStringExtra(TaskAlarmScheduler.EXTRA_SCENARIO) ?: "REMINDER"
        val dueAt = intent.getStringExtra(TaskAlarmScheduler.EXTRA_DUE_AT)

        Log.d(TAG, "Alarm received for item $itemId, scenario: $scenario")

        val notificationManager = WispNotificationManager(context)

        when (scenario) {
            "DAY_BEFORE" -> notificationManager.showDayBeforeReminder(itemId, content, dueAt)
            "SAME_DAY_FALLBACK" -> notificationManager.showSameDayReminder(itemId, content, dueAt)
            "OVERDUE" -> notificationManager.showOverdueReminder(itemId, content, dueAt)
            else -> notificationManager.showNotification(itemId, "Task Reminder", content, scenario)
        }
    }
}
