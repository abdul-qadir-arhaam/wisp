package com.wisp.app.orb

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists the floating orb screen coordinates locally per device.
 * Reference: PRD.md Section 7.1.1 & PHASES.md Phase 3
 */
class OrbPositionPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSavedPosition(): Pair<Int, Int>? {
        if (!prefs.contains(KEY_X) || !prefs.contains(KEY_Y)) {
            return null
        }
        val x = prefs.getInt(KEY_X, 0)
        val y = prefs.getInt(KEY_Y, 200)
        return Pair(x, y)
    }

    fun savePosition(x: Int, y: Int) {
        prefs.edit()
            .putInt(KEY_X, x)
            .putInt(KEY_Y, y)
            .apply()
    }

    fun isOrbEnabled(): Boolean {
        return prefs.getBoolean(KEY_ENABLED, false)
    }

    fun setOrbEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "wisp_orb_position_prefs"
        private const val KEY_X = "orb_last_x"
        private const val KEY_Y = "orb_last_y"
        private const val KEY_ENABLED = "orb_enabled"
    }
}
