package com.wisp.app.orb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.wisp.app.R
import com.wisp.app.main.Phase2TestActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Foreground Service that maintains the persistent floating orb overlay across Android.
 * Reference: PRD.md Section 7.1.1 & PHASES.md Phase 3
 */
class OrbService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var windowManager: WindowManager
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var positionPrefs: OrbPositionPreferences
    private var viewController: OrbViewController? = null
    private var touchHandler: OrbTouchHandler? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIFICATION_ID,
                    buildForegroundNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, buildForegroundNotification())
            }
        } catch (e: Exception) {
            android.util.Log.e("OrbService", "startForeground error", e)
        }

        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            positionPrefs = OrbPositionPreferences(this)
            initOverlayWindow()
        } catch (e: Exception) {
            android.util.Log.e("OrbService", "initOverlayWindow error", e)
            stopSelf()
        }
    }

    private fun initOverlayWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            android.util.Log.w("OrbService", "Cannot display overlay: permission not granted")
            stopSelf()
            return
        }

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Restore saved position or default to top-right
        val savedPos = positionPrefs.getSavedPosition()
        val initialX = savedPos?.first ?: 24
        val initialY = savedPos?.second ?: 200

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }

        viewController = OrbViewController(
            context = this,
            windowManager = windowManager,
            layoutParams = layoutParams,
            serviceScope = serviceScope,
            onStateChanged = { state ->
                // When transitioning back to IDLE, snap to nearest edge
                if (state == OrbState.IDLE) {
                    touchHandler?.snapToNearestEdge()
                }
            }
        )

        val rootView = viewController!!.rootView

        touchHandler = OrbTouchHandler(
            context = this,
            windowManager = windowManager,
            layoutParams = layoutParams,
            orbView = rootView,
            positionPrefs = positionPrefs,
            onClick = {
                viewController?.toggleExpanded()
            },
            onLongPress = {
                viewController?.startVoiceCaptureFromOrb()
            }
        )

        // Attach touchHandler ONLY to layoutIdleOrb (and compact bubble), NOT to rootView.
        // Attaching it to rootView steals all clicks from child views (btnOrbMic, task items, etc.)
        // in layoutExpanded and immediately collapses the orb.
        viewController?.binding?.layoutIdleOrb?.setOnTouchListener(touchHandler)
        viewController?.binding?.layoutCompact?.setOnTouchListener(touchHandler)

        try {
            if (!rootView.isAttachedToWindow) {
                windowManager.addView(rootView, layoutParams)
            }
        } catch (e: Exception) {
            android.util.Log.e("OrbService", "Failed to add orb view to WindowManager", e)
        }
    }

    /**
     * Auto-hide during orientation change / presentation / fullscreen contexts
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // If device enters landscape (often game/video fullscreen), temporarily auto-hide or adjust
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            viewController?.setAutoHide(true)
        } else {
            viewController?.setAutoHide(false)
            touchHandler?.snapToNearestEdge()
        }
    }

    private fun buildForegroundNotification(): Notification {
        val channelId = "wisp_orb_service_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Wisp Floating Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Wisp's ambient floating orb accessible"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(this, com.wisp.app.mainapp.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Wisp")
            .setContentText("Ambient assistant active • Tap to open")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        val action = intent?.action
        if (action == ACTION_AUTO_HIDE) {
            val hide = intent.getBooleanExtra(EXTRA_HIDE, false)
            viewController?.setAutoHide(hide)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
        try {
            viewController?.let {
                if (it.rootView.isAttachedToWindow) {
                    windowManager.removeView(it.rootView)
                }
                it.destroy()
            }
        } catch (e: Exception) {
            android.util.Log.e("OrbService", "Error during onDestroy", e)
        }
        viewController = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
        const val ACTION_AUTO_HIDE = "com.wisp.app.orb.ACTION_AUTO_HIDE"
        const val EXTRA_HIDE = "extra_hide"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            try {
                val intent = Intent(context, OrbService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("OrbService", "Failed to start OrbService", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, OrbService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                android.util.Log.e("OrbService", "Failed to stop OrbService", e)
            }
        }
    }
}
