package com.wisp.app.screentime

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.wisp.app.R
import com.wisp.app.databinding.ActivityScreenTimeBinding
import com.wisp.app.sync.SupabaseManager
import kotlinx.coroutines.launch

/**
 * Screen Time Analytics Dashboard Activity.
 * Displays cross-device screen time summary, per-app breakdown, and weekly trend.
 * Reference: PRD.md Section 6.2 & DESIGN.md Section 7.5
 */
class ScreenTimeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScreenTimeBinding
    private lateinit var usageStatsHelper: UsageStatsHelper
    private val repository = ScreenTimeRepository()
    private val topAppsAdapter = TopAppsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScreenTimeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        usageStatsHelper = UsageStatsHelper(this)

        setupUI()
        loadData()
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnRefresh.setOnClickListener {
            binding.btnRefresh.animate().rotationBy(360f).setDuration(400).start()
            ScreenTimeSyncService.syncNow(this) {
                lifecycleScope.launch {
                    loadData()
                }
            }
        }

        binding.btnGrantPermission.setOnClickListener {
            startActivity(usageStatsHelper.getUsageAccessSettingsIntent())
        }

        binding.rvTopApps.layoutManager = LinearLayoutManager(this)
        binding.rvTopApps.adapter = topAppsAdapter
    }

    private fun checkPermissions() {
        val hasPermission = usageStatsHelper.hasUsagePermission()
        binding.layoutPermissionBanner.visibility = if (hasPermission) View.GONE else View.VISIBLE
    }

    private fun loadData() {
        val userId = SupabaseManager.currentUserId
        if (userId == null) {
            Toast.makeText(this, "Please sign in to view analytics", Toast.LENGTH_SHORT).show()
            return
        }

        val todayDate = usageStatsHelper.getTodayDateString()

        // 1. Sync local usage stats first if permitted
        if (usageStatsHelper.hasUsagePermission()) {
            ScreenTimeSyncService.syncNow(this) {
                fetchAndDisplayData(userId, todayDate)
            }
        } else {
            fetchAndDisplayData(userId, todayDate)
        }
    }

    private fun fetchAndDisplayData(userId: String, todayDate: String) {
        lifecycleScope.launch {
            val result = repository.getUnifiedScreenTimeToday(userId, todayDate)
            result.onSuccess { summary ->
                displaySummary(summary)
            }.onFailure { err ->
                Toast.makeText(this@ScreenTimeActivity, "Error loading screen time: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun displaySummary(summary: UnifiedScreenTimeSummary) {
        binding.tvTotalScreenTime.text = summary.formattedTotalToday

        val androidHours = summary.androidSecondsToday / 3600
        val androidMinutes = (summary.androidSecondsToday % 3600) / 60
        binding.tvAndroidTime.text = "${androidHours}h ${androidMinutes}m"

        val windowsHours = summary.windowsSecondsToday / 3600
        val windowsMinutes = (summary.windowsSecondsToday % 3600) / 60
        binding.tvWindowsTime.text = "${windowsHours}h ${windowsMinutes}m"

        if (summary.topApps.isEmpty()) {
            binding.rvTopApps.visibility = View.GONE
            binding.tvEmptyApps.visibility = View.VISIBLE
            binding.tvAppCount.text = "0 apps"
        } else {
            binding.rvTopApps.visibility = View.VISIBLE
            binding.tvEmptyApps.visibility = View.GONE
            binding.tvAppCount.text = "${summary.topApps.size} apps"
            topAppsAdapter.submitList(summary.topApps)
        }

        renderWeeklyTrend(summary.dailyTrend)
    }

    private fun renderWeeklyTrend(trend: List<DailyScreenTimePoint>) {
        binding.layoutWeeklyTrend.removeAllViews()
        if (trend.isEmpty()) return

        val maxSeconds = trend.maxOfOrNull { it.totalSeconds }?.coerceAtLeast(1L) ?: 1L

        for (point in trend) {
            val dayColumn = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }

            // Height-proportional mini-bar
            val barHeightDp = ((point.totalSeconds.toFloat() / maxSeconds.toFloat()) * 60).coerceIn(4f, 60f)
            val barHeightPx = (barHeightDp * resources.displayMetrics.density).toInt()

            val bar = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (14 * resources.displayMetrics.density).toInt(),
                    barHeightPx
                )
                setBackgroundResource(R.drawable.bg_orb_compact)
            }

            val dateLabel = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (6 * resources.displayMetrics.density).toInt()
                }
                text = point.formattedDate
                textSize = 9f
                setTextColor(getColor(R.color.wisp_text_secondary))
            }

            dayColumn.addView(bar)
            dayColumn.addView(dateLabel)
            binding.layoutWeeklyTrend.addView(dayColumn)
        }
    }
}
