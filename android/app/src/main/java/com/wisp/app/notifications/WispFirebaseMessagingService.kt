package com.wisp.app.notifications

import android.content.Context
import android.util.Log
import com.wisp.app.sync.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Handles incoming push notification payloads from Firebase Cloud Messaging / Edge Function.
 * Reference: PRD.md Section 6.1.6 & ARCHITECTURE.md Section 2.1, 2.4
 */
class WispFirebaseMessagingService {

    companion object {
        private const val TAG = "WispFCM"
        private const val PREFS_NAME = "wisp_fcm_prefs"
        private const val KEY_FCM_TOKEN = "fcm_token"

        /**
         * Dispatches a received notification payload to the local notification manager.
         * Can be called by FCM service or directly by local testing harness.
         */
        fun handlePayload(context: Context, data: Map<String, String>) {
            val itemId = data["itemId"] ?: return
            val scenario = data["scenario"] ?: "REMINDER"
            val title = data["title"] ?: "Task Reminder"
            val body = data["body"] ?: ""
            val dueAt = data["dueAt"]

            val notificationManager = WispNotificationManager(context)

            when (scenario) {
                "DAY_BEFORE" -> notificationManager.showDayBeforeReminder(itemId, body.removePrefix("Remember: ").trim(), dueAt)
                "SAME_DAY_FALLBACK" -> notificationManager.showSameDayReminder(itemId, body.removePrefix("Due today: ").trim(), dueAt)
                "OVERDUE" -> notificationManager.showOverdueReminder(itemId, body.removePrefix("Overdue: ").trim(), dueAt)
                else -> notificationManager.showNotification(itemId, title, body, scenario)
            }
        }

        /**
         * Persists and syncs the updated device push token with Supabase
         */
        fun updateDeviceToken(context: Context, token: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_FCM_TOKEN, token).apply()

            val userId = SupabaseManager.currentUserId
            if (userId != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val record = DeviceTokenRecord(
                            userId = userId,
                            token = token,
                            deviceType = "android"
                        )
                        SupabaseManager.client.postgrest["device_tokens"].upsert(record)
                        Log.d(TAG, "Device token registered with Supabase: $token")
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not register device token in Supabase: ${e.message}")
                    }
                }
            }
        }

        fun getSavedToken(context: Context): String? {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_FCM_TOKEN, null)
        }
    }
}

@Serializable
data class DeviceTokenRecord(
    val userId: String,
    val token: String,
    val deviceType: String = "android"
)
