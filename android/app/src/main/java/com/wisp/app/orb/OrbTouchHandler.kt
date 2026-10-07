package com.wisp.app.orb

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import kotlin.math.hypot

/**
 * Handles drag behavior, edge-snapping physics, and click/long-press gestures for the floating orb.
 * Reference: PRD.md Section 7.1.1 & PHASES.md Phase 3
 */
class OrbTouchHandler(
    private val context: Context,
    private val windowManager: WindowManager,
    private val layoutParams: WindowManager.LayoutParams,
    private val orbView: View,
    private val positionPrefs: OrbPositionPreferences,
    private val onClick: () -> Unit,
    private val onLongPress: () -> Unit
) : View.OnTouchListener {

    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isDragging: Boolean = false

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val handler = Handler(Looper.getMainLooper())
    private var hasFiredLongPress = false

    private val longPressRunnable = Runnable {
        if (!isDragging) {
            hasFiredLongPress = true
            onLongPress()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = layoutParams.x
                initialY = layoutParams.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                hasFiredLongPress = false

                handler.removeCallbacks(longPressRunnable)
                handler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY

                if (!isDragging && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                    isDragging = true
                    handler.removeCallbacks(longPressRunnable)
                }

                if (isDragging) {
                    layoutParams.x = initialX + dx.toInt()
                    layoutParams.y = initialY + dy.toInt()
                    safeUpdateLayout()
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)

                if (!isDragging && !hasFiredLongPress) {
                    // Tap / Click action
                    onClick()
                } else if (isDragging) {
                    // Released after drag -> snap to nearest screen edge
                    snapToNearestEdge()
                }
                return true
            }
        }
        return false
    }

    /**
     * Physics-based animation that snaps the orb to the left or right edge of the display.
     */
    fun snapToNearestEdge() {
        val screenSize = getScreenSize()
        val screenWidth = screenSize.x
        val viewWidth = orbView.width.coerceAtLeast(140)
        val edgePadding = 24 // 24px margin from screen boundary

        val currentCenterX = layoutParams.x + viewWidth / 2
        val targetX = if (currentCenterX < screenWidth / 2) {
            edgePadding // Snap to left edge
        } else {
            screenWidth - viewWidth - edgePadding // Snap to right edge
        }

        // Clamp Y within screen bounds safely
        val minY = 100
        val safeMaxY = (screenSize.y - 200).coerceAtLeast(minY)
        val targetY = layoutParams.y.coerceIn(minY, safeMaxY)

        // Animate smoothly to target position
        val startX = layoutParams.x
        val animator = ValueAnimator.ofInt(startX, targetX).apply {
            duration = 280
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { anim ->
                layoutParams.x = anim.animatedValue as Int
                layoutParams.y = targetY
                safeUpdateLayout()
            }
        }
        animator.start()

        // Persist final coordinates
        positionPrefs.savePosition(targetX, targetY)
    }

    private fun getScreenSize(): Point {
        val size = Point()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getSize(size)
        return size
    }

    private fun safeUpdateLayout() {
        try {
            if (orbView.isAttachedToWindow) {
                windowManager.updateViewLayout(orbView, layoutParams)
            }
        } catch (_: Exception) {}
    }
}
