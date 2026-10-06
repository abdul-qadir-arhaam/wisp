package com.wisp.app.screentime

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

/**
 * Handles UsageStatsManager interactions, permissions, and app usage aggregation on Android.
 * Reference: PRD.md Section 6.2 & PHASES.md Phase 6
 */
class UsageStatsHelper(private val context: Context) {

    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    /**
     * Checks if the app has been granted the special usage-access permission.
     */
    fun hasUsagePermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Returns an intent to navigate the user directly to the Usage Access settings screen.
     */
    fun getUsageAccessSettingsIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Queries foreground usage statistics for the current day (midnight to now).
     * Returns a list of AppUsageStat ordered descending by duration.
     */
    fun getTodayUsageStats(): List<AppUsageStat> {
        if (!hasUsagePermission() || usageStatsManager == null) {
            return emptyList()
        }

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        ) ?: return emptyList()

        val pm = context.packageManager
        val appDurations = mutableMapOf<String, Long>()

        for (usage in stats) {
            val totalTimeInForeground = usage.totalTimeInForeground / 1000 // Convert ms to seconds
            if (totalTimeInForeground <= 5) continue // Skip noise / negligible durations

            val packageName = usage.packageName
            // Exclude our own app and common background system services
            if (packageName == context.packageName || packageName == "android") continue

            val humanLabel = resolveAppLabel(pm, packageName)
            appDurations[humanLabel] = (appDurations[humanLabel] ?: 0L) + totalTimeInForeground
        }

        val totalDuration = appDurations.values.sum().toFloat().coerceAtLeast(1f)

        return appDurations.map { (appName, duration) ->
            AppUsageStat(
                appName = appName,
                durationSeconds = duration,
                device = "android",
                percentageOfTotal = (duration / totalDuration) * 100f
            )
        }.sortedByDescending { it.durationSeconds }
    }

    /**
     * Converts raw Android package name into a human-readable application title.
     */
    private fun resolveAppLabel(pm: PackageManager, packageName: String): String {
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * Returns today's ISO date string in YYYY-MM-DD format.
     */
    fun getTodayDateString(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LocalDate.now(ZoneId.systemDefault()).toString()
        } else {
            val cal = Calendar.getInstance()
            String.format(
                "%04d-%02d-%02d",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
        }
    }
}
