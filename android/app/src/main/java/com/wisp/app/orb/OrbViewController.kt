package com.wisp.app.orb

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import com.wisp.app.R
import com.wisp.app.databinding.LayoutFloatingOrbBinding
import com.wisp.app.main.Phase2TestActivity
import com.wisp.app.sync.Item
import com.wisp.app.sync.SupabaseManager
import com.wisp.app.voice.DateParser
import com.wisp.app.voice.TtsManager
import com.wisp.app.voice.VoiceCaptureManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Controls the Orb view hierarchy, animations, state transitions, and voice/data bindings.
 * Reference: PRD.md Section 7.1.1, 7.1.2 & DESIGN.md Section 6
 */
class OrbViewController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val layoutParams: WindowManager.LayoutParams,
    private val serviceScope: CoroutineScope,
    private val onStateChanged: (OrbState) -> Unit
) {

    val binding: LayoutFloatingOrbBinding = LayoutFloatingOrbBinding.inflate(LayoutInflater.from(context))
    val rootView: View get() = binding.root

    var currentState: OrbState = OrbState.IDLE
        private set

    private var pulseAnimator: ObjectAnimator? = null
    private var listeningAnimator: ValueAnimator? = null

    private var voiceCaptureManager: VoiceCaptureManager? = null
    private var ttsManager: TtsManager? = null
    private var isRecording: Boolean = false

    init {
        ttsManager = TtsManager(context)
        setupIdlePulseAnimation()
        setupExpandedUI()
        transitionTo(OrbState.IDLE)
    }

    private fun setupIdlePulseAnimation() {
        val pvhX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.25f)
        val pvhY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.25f)
        val pvhAlpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.7f, 0.15f)

        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(binding.orbPulseGlow, pvhX, pvhY, pvhAlpha).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        pulseAnimator?.start()
    }

    private fun setupExpandedUI() {
        // Collapse Button
        binding.btnCollapse.setOnClickListener {
            transitionTo(OrbState.IDLE)
        }

        // Mic Button in Expanded View
        binding.btnOrbMic.setOnClickListener {
            if (!isRecording) {
                startVoiceCaptureFromOrb()
            } else {
                stopVoiceCaptureFromOrb()
            }
        }

        // Open Main App Button
        binding.btnOpenMainApp.setOnClickListener {
            val intent = Intent(context, Phase2TestActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
            transitionTo(OrbState.IDLE)
        }

        // Screen Time Button (Stub for Phase 6)
        binding.btnScreenTime.setOnClickListener {
            binding.tvOrbVoiceFeedback.visibility = View.VISIBLE
            binding.tvOrbVoiceFeedback.text = "Screen Time dashboard will be unlocked in Phase 6"
        }
    }

    /**
     * Morphing transition between IDLE, COMPACT, and EXPANDED states.
     */
    fun transitionTo(newState: OrbState) {
        currentState = newState

        when (newState) {
            OrbState.IDLE -> {
                binding.layoutIdleOrb.visibility = View.VISIBLE
                binding.layoutCompact.visibility = View.GONE
                binding.layoutExpanded.visibility = View.GONE

                layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
                layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
                layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                safeUpdateLayout()

                pulseAnimator?.resume()
                stopListeningAnimation()
            }

            OrbState.COMPACT -> {
                binding.layoutIdleOrb.visibility = View.GONE
                binding.layoutCompact.visibility = View.VISIBLE
                binding.layoutExpanded.visibility = View.GONE

                layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
                layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
                layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                safeUpdateLayout()

                pulseAnimator?.pause()
                startListeningAnimation()
            }

            OrbState.EXPANDED -> {
                binding.layoutIdleOrb.visibility = View.GONE
                binding.layoutCompact.visibility = View.GONE
                binding.layoutExpanded.visibility = View.VISIBLE

                layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
                layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
                // In expanded state, allow touches inside
                layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                safeUpdateLayout()

                pulseAnimator?.pause()
                stopListeningAnimation()
                refreshSnapshotTasks()
            }
        }
        onStateChanged(newState)
    }

    fun toggleExpanded() {
        if (currentState == OrbState.EXPANDED) {
            transitionTo(OrbState.IDLE)
        } else {
            transitionTo(OrbState.EXPANDED)
        }
    }

    private fun startListeningAnimation() {
        listeningAnimator?.cancel()
        listeningAnimator = ValueAnimator.ofFloat(0.6f, 1.0f).apply {
            duration = 500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { anim ->
                binding.compactMicIcon.alpha = anim.animatedValue as Float
            }
        }
        listeningAnimator?.start()
    }

    private fun stopListeningAnimation() {
        listeningAnimator?.cancel()
        listeningAnimator = null
    }

    /**
     * Triggers push-to-talk voice capture directly from the orb.
     */
    fun startVoiceCaptureFromOrb() {
        if (voiceCaptureManager == null) {
            voiceCaptureManager = VoiceCaptureManager(
                context = context,
                onReady = {
                    isRecording = true
                    if (currentState != OrbState.EXPANDED) {
                        transitionTo(OrbState.COMPACT)
                    } else {
                        binding.btnOrbMic.text = "🔴 Listening... (Tap to stop)"
                    }
                },
                onResult = { transcript ->
                    isRecording = false
                    handleVoiceTranscript(transcript)
                },
                onError = { error ->
                    isRecording = false
                    if (currentState == OrbState.COMPACT) {
                        transitionTo(OrbState.IDLE)
                    } else {
                        binding.btnOrbMic.text = "🎤 Tap to Speak"
                        binding.tvOrbVoiceFeedback.visibility = View.VISIBLE
                        binding.tvOrbVoiceFeedback.text = "Error: $error"
                    }
                }
            )
        }
        voiceCaptureManager?.startListening()
    }

    private fun stopVoiceCaptureFromOrb() {
        isRecording = false
        voiceCaptureManager?.stopListening()
        if (currentState == OrbState.COMPACT) {
            transitionTo(OrbState.IDLE)
        } else {
            binding.btnOrbMic.text = "🎤 Tap to Speak"
        }
    }

    private fun handleVoiceTranscript(rawText: String) {
        val parsed = DateParser.parse(rawText)
        val userId = SupabaseManager.currentUserId

        if (userId == null) {
            binding.tvOrbVoiceFeedback.visibility = View.VISIBLE
            binding.tvOrbVoiceFeedback.text = "Please log in first"
            if (currentState == OrbState.COMPACT) transitionTo(OrbState.IDLE)
            return
        }

        val item = Item(
            userId = userId,
            content = parsed.cleanContent,
            dueAt = parsed.dueAtIso,
            sourceDevice = "android"
        )

        serviceScope.launch {
            val result = SupabaseManager.insertItem(item)
            withContext(Dispatchers.Main) {
                result.onSuccess {
                    ttsManager?.speakConfirmation("Got it, saved")
                    if (currentState == OrbState.EXPANDED) {
                        binding.btnOrbMic.text = "🎤 Tap to Speak"
                        binding.tvOrbVoiceFeedback.visibility = View.VISIBLE
                        binding.tvOrbVoiceFeedback.text = "✓ Saved: \"${parsed.cleanContent}\""
                        refreshSnapshotTasks()
                    } else {
                        // Return to idle after voice capture
                        transitionTo(OrbState.IDLE)
                    }
                }.onFailure { err ->
                    if (currentState == OrbState.EXPANDED) {
                        binding.btnOrbMic.text = "🎤 Tap to Speak"
                        binding.tvOrbVoiceFeedback.visibility = View.VISIBLE
                        binding.tvOrbVoiceFeedback.text = "Save error: ${err.message}"
                    } else {
                        transitionTo(OrbState.IDLE)
                    }
                }
            }
        }
    }

    /**
     * Refreshes upcoming tasks snapshot in the Expanded panel.
     */
    fun refreshSnapshotTasks() {
        serviceScope.launch {
            val result = SupabaseManager.getItems()
            withContext(Dispatchers.Main) {
                result.onSuccess { items ->
                    populateSnapshotTasks(items)
                }
            }
        }
    }

    private fun populateSnapshotTasks(items: List<Item>) {
        val container = binding.snapshotTasksContainer
        container.removeAllViews()

        val activeItems = items.filter { !it.completed }.take(4)

        if (activeItems.isEmpty()) {
            val emptyTv = TextView(context).apply {
                text = "No upcoming tasks. Speak or tap to add!"
                setTextColor(context.getColor(R.color.wisp_text_secondary))
                textSize = 13f
                setPadding(0, 16, 0, 16)
            }
            container.addView(emptyTv)
            return
        }

        val inflater = LayoutInflater.from(context)
        for (item in activeItems) {
            val row = inflater.inflate(R.layout.item_orb_snapshot_task, container, false)
            val tvContent = row.findViewById<TextView>(R.id.tvTaskContent)
            val tvDue = row.findViewById<TextView>(R.id.tvTaskDue)

            tvContent.text = item.content
            if (!item.dueAt.isNullOrBlank()) {
                tvDue.visibility = View.VISIBLE
                tvDue.text = item.dueAt.substringBefore("T")
            } else {
                tvDue.visibility = View.GONE
            }
            container.addView(row)
        }
    }

    /**
     * Automatically hides or shrinks the orb during fullscreen applications.
     */
    fun setAutoHide(hidden: Boolean) {
        rootView.visibility = if (hidden) View.GONE else View.VISIBLE
    }

    private fun safeUpdateLayout() {
        try {
            if (rootView.isAttachedToWindow) {
                windowManager.updateViewLayout(rootView, layoutParams)
            }
        } catch (_: Exception) {}
    }

    fun destroy() {
        pulseAnimator?.cancel()
        listeningAnimator?.cancel()
        voiceCaptureManager?.destroy()
        ttsManager?.shutdown()
    }
}
