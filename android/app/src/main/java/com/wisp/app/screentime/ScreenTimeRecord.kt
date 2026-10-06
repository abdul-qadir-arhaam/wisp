package com.wisp.app.screentime

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data model for screen time logs stored in Supabase.
 * Reference: PRD.md Section 6.2 & ARCHITECTURE.md Section 4
 */
@Serializable
data class ScreenTimeRecord(
    @SerialName("id")
    val id: String? = null,

    @SerialName("user_id")
    val userId: String,

    @SerialName("device")
    val device: String, // 'android' or 'windows'

    @SerialName("app_name")
    val appName: String,

    @SerialName("duration_seconds")
    val durationSeconds: Long,

    @SerialName("date")
    val date: String, // YYYY-MM-DD

    @SerialName("category")
    val category: String? = null,

    @SerialName("created_at")
    val createdAt: String? = null
)

/**
 * Aggregated app usage item for dashboard presentation.
 */
data class AppUsageStat(
    val appName: String,
    val durationSeconds: Long,
    val device: String,
    val percentageOfTotal: Float = 0f
) {
    val formattedDuration: String
        get() {
            val hours = durationSeconds / 3600
            val minutes = (durationSeconds % 3600) / 60
            return when {
                hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
                hours > 0 -> "${hours}h"
                minutes > 0 -> "${minutes}m"
                else -> "${durationSeconds}s"
            }
        }
}

/**
 * Unified dashboard data payload aggregated across all user devices.
 */
data class UnifiedScreenTimeSummary(
    val totalSecondsToday: Long,
    val androidSecondsToday: Long,
    val windowsSecondsToday: Long,
    val topApps: List<AppUsageStat>,
    val dailyTrend: List<DailyScreenTimePoint>
) {
    val formattedTotalToday: String
        get() {
            val hours = totalSecondsToday / 3600
            val minutes = (totalSecondsToday % 3600) / 60
            return when {
                hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
                hours > 0 -> "${hours}h"
                minutes > 0 -> "${minutes}m"
                else -> "0m"
            }
        }
}

data class DailyScreenTimePoint(
    val date: String,
    val totalSeconds: Long,
    val formattedDate: String
)
