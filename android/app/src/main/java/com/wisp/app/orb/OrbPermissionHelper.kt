package com.wisp.app.orb

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Helper to check and request Android's SYSTEM_ALERT_WINDOW overlay permission.
 * Reference: PHASES.md Phase 3 & PRD.md Section 7.1.1
 */
object OrbPermissionHelper {

    /**
     * Checks if the app currently has permission to draw floating overlays.
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Creates an intent to navigate the user directly to the Overlay Permission setting.
     */
    fun createOverlayPermissionIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent()
        }
    }
}
