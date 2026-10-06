package com.wisp.app.screentime

import android.util.Log
import com.wisp.app.sync.SupabaseManager
import io.github.jan_tennert.supabase.postgrest.postgrest
import io.github.jan_tennert.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Repository for syncing and querying cross-device screen time analytics via Supabase.
 * Reference: PRD.md Section 6.2 & ARCHITECTURE.md Section 2.3, 3.4
 */
class ScreenTimeRepository {

    companion object {
        private const val TAG = "ScreenTimeRepo"
    }

    /**
     * Uploads the latest usage stats for this device to Supabase screen_time_logs.
     */
    suspend fun uploadUsageLogs(
        userId: String,
        device: String = "android",
        stats: List<AppUsageStat>,
        dateString: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (stats.isEmpty()) return@withContext Result.success(Unit)

            // Map stats into DB records
            val records = stats.map { stat ->
                ScreenTimeRecord(
                    userId = userId,
                    device = device,
                    appName = stat.appName,
                    durationSeconds = stat.durationSeconds,
                    date = dateString
                )
            }

            // Insert records into screen_time_logs
            SupabaseManager.client.postgrest["screen_time_logs"].insert(records)
            Log.d(TAG, "Uploaded ${records.size} screen time records for $device on $dateString")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading screen time records: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Fetches and aggregates screen time across all devices for the user today.
     */
    suspend fun getUnifiedScreenTimeToday(
        userId: String,
        todayDate: String
    ): Result<UnifiedScreenTimeSummary> = withContext(Dispatchers.IO) {
        try {
            val response = SupabaseManager.client.postgrest["screen_time_logs"]
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("date", todayDate)
                    }
                }
                .decodeList<ScreenTimeRecord>()

            var totalSeconds = 0L
            var androidSeconds = 0L
            var windowsSeconds = 0L
            val appTotals = mutableMapOf<String, Long>()
            val appDevices = mutableMapOf<String, String>()

            for (log in response) {
                totalSeconds += log.durationSeconds
                if (log.device == "android") {
                    androidSeconds += log.durationSeconds
                } else if (log.device == "windows") {
                    windowsSeconds += log.durationSeconds
                }

                appTotals[log.appName] = (appTotals[log.appName] ?: 0L) + log.durationSeconds
                appDevices[log.appName] = log.device
            }

            val totalForPct = totalSeconds.toFloat().coerceAtLeast(1f)
            val topApps = appTotals.map { (appName, duration) ->
                AppUsageStat(
                    appName = appName,
                    durationSeconds = duration,
                    device = appDevices[appName] ?: "android",
                    percentageOfTotal = (duration / totalForPct) * 100f
                )
            }.sortedByDescending { it.durationSeconds }

            // Fetch weekly trend (past 7 days)
            val weeklyPoints = fetchWeeklyTrend(userId, todayDate)

            Result.success(
                UnifiedScreenTimeSummary(
                    totalSecondsToday = totalSeconds,
                    androidSecondsToday = androidSeconds,
                    windowsSecondsToday = windowsSeconds,
                    topApps = topApps,
                    dailyTrend = weeklyPoints
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching unified screen time: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun fetchWeeklyTrend(
        userId: String,
        todayDate: String
    ): List<DailyScreenTimePoint> {
        return try {
            val response = SupabaseManager.client.postgrest["screen_time_logs"]
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<ScreenTimeRecord>()

            val grouped = response.groupBy { it.date }
            val points = mutableListOf<DailyScreenTimePoint>()

            // Build past 7 days points
            for (i in 6 downTo 0) {
                val date = try {
                    LocalDate.parse(todayDate).minusDays(i.toLong()).toString()
                } catch (e: Exception) {
                    "Day -$i"
                }
                val dayLogs = grouped[date] ?: emptyList()
                val dayTotal = dayLogs.sumOf { it.durationSeconds }
                val label = if (date.length >= 10) date.substring(5) else date // e.g. "10-06"
                points.add(DailyScreenTimePoint(date, dayTotal, label))
            }
            points
        } catch (e: Exception) {
            emptyList()
        }
    }
}
