package com.wisp.app.orb

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import com.wisp.app.R
import com.wisp.app.databinding.LayoutFloatingOrbBinding
import com.wisp.app.mainapp.FilterManager
import com.wisp.app.mainapp.ViewFilter
import com.wisp.app.sync.Item
import com.wisp.app.sync.SupabaseManager
import com.wisp.app.voice.DateParser
import com.wisp.app.voice.TtsManager
import com.wisp.app.voice.VoiceCaptureManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

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

    // Wrap the service context in Theme.Wisp so Material3 components inflate properly without crashing
    private val themedContext: Context = android.view.ContextThemeWrapper(context, R.style.Theme_Wisp)

    val binding: LayoutFloatingOrbBinding = LayoutFloatingOrbBinding.inflate(LayoutInflater.from(themedContext))
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

        // Quick Add Task Button
        binding.btnOrbAddTask.setOnClickListener {
            val text = binding.etOrbTaskInput.text.toString().trim()
            if (text.isNotBlank()) {
                addTypedTaskFromOrb(text)
            }
        }

        // Keyboard Done action on Quick Add EditText
        binding.etOrbTaskInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val text = binding.etOrbTaskInput.text.toString().trim()
                if (text.isNotBlank()) {
                    addTypedTaskFromOrb(text)
                }
                true
            } else false
        }

        // Push-to-Talk Mic Button in Expanded View
        binding.btnOrbMic.setOnClickListener {
            if (!isRecording) {
                startVoiceCaptureFromOrb()
            } else {
                stopVoiceCaptureFromOrb()
            }
        }

        // Open Main App Button
        binding.btnOpenMainApp.setOnClickListener {
            val intent = Intent(context, com.wisp.app.mainapp.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
            transitionTo(OrbState.IDLE)
        }

        // Screen Time Button (Phase 6)
        binding.btnScreenTime.setOnClickListener {
            val intent = Intent(context, com.wisp.app.screentime.ScreenTimeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
            transitionTo(OrbState.IDLE)
        }
    }

    /**
     * Morphing transition between IDLE, COMPACT, and EXPANDED states.
     */
    fun transitionTo(newState: OrbState) {
        currentState = newState

        when (newState) {
            OrbState.IDLE -> {
                hideKeyboard(binding.etOrbTaskInput)
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
                hideKeyboard(binding.etOrbTaskInput)
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

                // Clamp X so the 320dp expanded panel stays fully visible on screen
                val displayMetrics = context.resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                val panelWidthPx = (320 * displayMetrics.density).toInt()
                val maxX = (screenWidth - panelWidthPx - 24).coerceAtLeast(24)
                if (layoutParams.x > maxX) {
                    layoutParams.x = maxX
                }

                layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
                layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
                // Allow focus so software keyboard can be opened for quick-add input
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

        serviceScope.launch {
            SupabaseManager.awaitAuthReady()
            val userId = SupabaseManager.currentUserId

            if (userId == null) {
                withContext(Dispatchers.Main) {
                    binding.tvOrbVoiceFeedback.visibility = View.VISIBLE
                    binding.tvOrbVoiceFeedback.text = "Please log in first"
                    if (currentState == OrbState.COMPACT) transitionTo(OrbState.IDLE)
                }
                return@launch
            }

            val item = Item(
                userId = userId,
                content = parsed.cleanContent,
                dueAt = parsed.dueAtIso,
                sourceDevice = "android"
            )

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

    private fun addTypedTaskFromOrb(text: String) {
        val parsed = DateParser.parse(text)
        serviceScope.launch {
            SupabaseManager.awaitAuthReady()
            val userId = SupabaseManager.currentUserId

            if (userId == null) {
                withContext(Dispatchers.Main) {
                    binding.tvOrbVoiceFeedback.visibility = View.VISIBLE
                    binding.tvOrbVoiceFeedback.text = "Please log in first"
                }
                return@launch
            }

            val item = Item(
                userId = userId,
                content = parsed.cleanContent,
                dueAt = parsed.dueAtIso,
                sourceDevice = "android"
            )

            val result = SupabaseManager.insertItem(item)
            withContext(Dispatchers.Main) {
                result.onSuccess {
                    binding.etOrbTaskInput.text.clear()
                    hideKeyboard(binding.etOrbTaskInput)
                    binding.tvOrbVoiceFeedback.visibility = View.VISIBLE
                    binding.tvOrbVoiceFeedback.text = "✓ Added: \"${parsed.cleanContent}\""
                    refreshSnapshotTasks()
                }.onFailure { err ->
                    binding.tvOrbVoiceFeedback.visibility = View.VISIBLE
                    binding.tvOrbVoiceFeedback.text = "Error: ${err.message}"
                }
            }
        }
    }

    /**
     * Refreshes upcoming tasks snapshot in the Expanded panel.
     */
    fun refreshSnapshotTasks() {
        serviceScope.launch {
            SupabaseManager.awaitAuthReady()
            val result = SupabaseManager.getItems()
            withContext(Dispatchers.Main) {
                result.onSuccess { items ->
                    populateSnapshotTasks(items)
                }.onFailure { err ->
                    val container = binding.snapshotTasksContainer
                    container.removeAllViews()
                    val errorTv = TextView(themedContext).apply {
                        text = "Unable to load tasks: ${err.localizedMessage ?: "Sync error"}"
                        setTextColor(themedContext.getColor(R.color.wisp_warning))
                        textSize = 12f
                        setPadding(0, 12, 0, 12)
                    }
                    container.addView(errorTv)
                }
            }
        }
    }

    private fun populateSnapshotTasks(items: List<Item>) {
        val container = binding.snapshotTasksContainer
        container.removeAllViews()

        // Active upcoming tasks (priority sorted)
        val activeItems = FilterManager.sort(items.filter { !it.completed }, ViewFilter.PRIORITY).take(3)
        // Recently completed tasks (completed today/yesterday)
        val recentCompleted = items.filter { it.completed && FilterManager.isCompletedRecently(it) }.take(2)
        val displayItems = activeItems + recentCompleted

        if (displayItems.isEmpty()) {
            val emptyTv = TextView(themedContext).apply {
                text = "No upcoming tasks. Speak or tap to add!"
                setTextColor(themedContext.getColor(R.color.wisp_text_secondary))
                textSize = 13f
                setPadding(0, 14, 0, 14)
            }
            container.addView(emptyTv)
            return
        }

        val inflater = LayoutInflater.from(themedContext)
        for (item in displayItems) {
            val row = inflater.inflate(R.layout.item_orb_snapshot_task, container, false)
            val tvIndicator = row.findViewById<TextView>(R.id.tvTaskIndicator)
            val tvContent = row.findViewById<TextView>(R.id.tvTaskContent)
            val tvDue = row.findViewById<TextView>(R.id.tvTaskDue)

            tvContent.text = item.content
            if (item.completed) {
                tvIndicator.text = "✓"
                tvIndicator.setTextColor(themedContext.getColor(R.color.wisp_success))
                tvContent.paintFlags = tvContent.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                tvContent.setTextColor(themedContext.getColor(R.color.wisp_text_secondary))
            } else {
                tvIndicator.text = "○"
                tvIndicator.setTextColor(themedContext.getColor(R.color.wisp_accent))
                tvContent.paintFlags = tvContent.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                tvContent.setTextColor(themedContext.getColor(R.color.wisp_text_primary))
            }

            if (!item.dueAt.isNullOrBlank() && !item.completed) {
                tvDue.visibility = View.VISIBLE
                tvDue.text = item.dueAt.substringBefore("T")
            } else {
                tvDue.visibility = View.GONE
            }

            // Quick toggle completion on tap from the snapshot
            row.setOnClickListener {
                toggleTaskCompletion(item)
            }

            container.addView(row)
        }
    }

    private fun toggleTaskCompletion(item: Item) {
        val itemId = item.id ?: return
        val newCompleted = !item.completed
        val completedAt = if (newCompleted) Instant.now().toString() else null

        serviceScope.launch {
            val result = SupabaseManager.updateItemCompletion(itemId, newCompleted, completedAt)
            withContext(Dispatchers.Main) {
                result.onSuccess {
                    if (newCompleted) {
                        ttsManager?.speakConfirmation("Marked done")
                    } else {
                        ttsManager?.speakConfirmation("Restored")
                    }
                    refreshSnapshotTasks()
                }
            }
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
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
        hideKeyboard(binding.etOrbTaskInput)
        pulseAnimator?.cancel()
        listeningAnimator?.cancel()
        voiceCaptureManager?.destroy()
        ttsManager?.shutdown()
    }
}
