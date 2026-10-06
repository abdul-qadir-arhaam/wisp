package com.wisp.app.screentime

import android.content.Context
import android.util.Log
import com.wisp.app.sync.SupabaseManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Background helper for periodically syncing foreground app usage with Supabase.
 * Reference: ARCHITECTURE.md Section 3.4 & PHASES.md Phase 6
 */
class ScreenTimeSyncService {

    companion object {
        private const val TAG = "ScreenTimeSync"
        private val repository = ScreenTimeRepository()

        /**
         * Collects today's usage stats from UsageStatsManager and uploads them to Supabase.
         */
        fun syncNow(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
            val userId = SupabaseManager.currentUserId
            if (userId == null) {
                Log.d(TAG, "User not authenticated, skipping screen time sync.")
                onComplete?.invoke(false)
                return
            }

            val helper = UsageStatsHelper(context)
            if (!helper.hasUsagePermission()) {
                Log.d(TAG, "Usage access permission not granted.")
                onComplete?.invoke(false)
                return
            }

            CoroutineScope(Dispatchers.IO).launch {
                val stats = helper.getTodayUsageStats()
                val todayDate = helper.getTodayDateString()

                if (stats.isNotEmpty()) {
                    val result = repository.uploadUsageLogs(
                        userId = userId,
                        device = "android",
                        stats = stats,
                        dateString = todayDate
                    )
                    val success = result.isSuccess
                    Log.d(TAG, "Screen time sync finished. Success: $success (${stats.size} apps)")
                    onComplete?.invoke(success)
                } else {
                    Log.d(TAG, "No usage stats logged yet for today.")
                    onComplete?.invoke(true)
                }
            }
        }
    }
}
