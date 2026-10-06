package com.wisp.app.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.wisp.app.databinding.ActivityPhase2TestBinding
import com.wisp.app.sync.Item
import com.wisp.app.sync.SupabaseManager
import com.wisp.app.voice.DateParser
import com.wisp.app.voice.TtsManager
import com.wisp.app.voice.VoiceCaptureManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 2 Test Screen: Core Capture -> NLP Parsing -> Supabase Realtime Sync Loop.
 * Reference: PHASES.md Phase 2 & PRD.md Section 6.1
 */
class Phase2TestActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhase2TestBinding

    private var voiceCaptureManager: VoiceCaptureManager? = null
    private var ttsManager: TtsManager? = null

    private var isRecording: Boolean = false
    private val logDateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // Permission launcher for RECORD_AUDIO
    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            initVoiceCapture()
            startVoiceCapture()
        } else {
            Toast.makeText(this, "Microphone permission is required for voice capture", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhase2TestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ttsManager = TtsManager(this)

        setupAuthUI()
        setupOrbControls()
        setupCaptureUI()

        // Check initial auth state
        if (SupabaseManager.isAuthenticated) {
            onUserAuthenticated(SupabaseManager.currentUserId ?: "")
        }
    }

    override fun onResume() {
        super.onResume()
        updateOverlayPermissionUI()
    }

    private var isOrbRunning: Boolean = false

    private fun setupOrbControls() {
        updateOverlayPermissionUI()

        binding.btnRequestOverlayPerm.setOnClickListener {
            val intent = com.wisp.app.orb.OrbPermissionHelper.createOverlayPermissionIntent(this)
            startActivity(intent)
        }

        binding.btnToggleOrb.setOnClickListener {
            if (!com.wisp.app.orb.OrbPermissionHelper.hasOverlayPermission(this)) {
                Toast.makeText(this, "Please grant overlay permission first", Toast.LENGTH_SHORT).show()
                val intent = com.wisp.app.orb.OrbPermissionHelper.createOverlayPermissionIntent(this)
                startActivity(intent)
                return@setOnClickListener
            }

            if (!isOrbRunning) {
                com.wisp.app.orb.OrbService.start(this)
                isOrbRunning = true
                binding.btnToggleOrb.text = "Stop Floating Orb"
                binding.btnToggleOrb.backgroundTintList = ContextCompat.getColorStateList(this, com.wisp.app.R.color.wisp_warning)
                appendLog("✓ Floating Orb Service started (Idle glowing state active)")
            } else {
                com.wisp.app.orb.OrbService.stop(this)
                isOrbRunning = false
                binding.btnToggleOrb.text = "Start Floating Orb"
                binding.btnToggleOrb.backgroundTintList = ContextCompat.getColorStateList(this, com.wisp.app.R.color.wisp_accent)
                appendLog("✓ Floating Orb Service stopped")
            }
        }
    }

    private fun updateOverlayPermissionUI() {
        val hasPerm = com.wisp.app.orb.OrbPermissionHelper.hasOverlayPermission(this)
        if (hasPerm) {
            binding.tvOverlayPermissionStatus.text = "Overlay Permission: Granted ✓"
            binding.tvOverlayPermissionStatus.setTextColor(ContextCompat.getColor(this, com.wisp.app.R.color.wisp_success))
            binding.btnRequestOverlayPerm.visibility = android.view.View.GONE
        } else {
            binding.tvOverlayPermissionStatus.text = "Overlay Permission: Required (SYSTEM_ALERT_WINDOW)"
            binding.tvOverlayPermissionStatus.setTextColor(ContextCompat.getColor(this, com.wisp.app.R.color.wisp_warning))
            binding.btnRequestOverlayPerm.visibility = android.view.View.VISIBLE
        }
    }

    private fun setupAuthUI() {
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.tvAuthStatus.text = "Authenticating..."
            binding.btnLogin.isEnabled = false

            lifecycleScope.launch {
                val result = SupabaseManager.signIn(email, password)
                binding.btnLogin.isEnabled = true
                result.onSuccess { userId ->
                    onUserAuthenticated(userId)
                }.onFailure { error ->
                    binding.tvAuthStatus.text = "Error: ${error.message}"
                    Toast.makeText(this@Phase2TestActivity, "Login failed: ${error.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun onUserAuthenticated(userId: String) {
        binding.tvAuthStatus.text = "Logged in as User ID: $userId"
        binding.tvAuthStatus.setTextColor(ContextCompat.getColor(this, com.wisp.app.R.color.wisp_success))

        appendLog("✓ Authenticated with Supabase session: $userId")

        // Initialize Realtime subscription
        startRealtimeListener()
    }

    private fun setupCaptureUI() {
        // Voice Button
        binding.btnSpeak.setOnClickListener {
            if (!SupabaseManager.isAuthenticated) {
                Toast.makeText(this, "Please sign in first to test sync", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                toggleVoiceCapture()
            }
        }

        // Typed Fallback Button
        binding.btnSendTyped.setOnClickListener {
            if (!SupabaseManager.isAuthenticated) {
                Toast.makeText(this, "Please sign in first to test sync", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val typedText = binding.etTypedInput.text.toString().trim()
            if (typedText.isNotBlank()) {
                processAndSaveInput(rawInput = typedText, isVoice = false)
                binding.etTypedInput.setText("")
            }
        }
    }

    private fun initVoiceCapture() {
        if (voiceCaptureManager != null) return

        voiceCaptureManager = VoiceCaptureManager(
            context = this,
            onReady = {
                binding.btnSpeak.text = "🔴 Listening... (Tap to stop)"
            },
            onResult = { transcript ->
                binding.btnSpeak.text = "🎤 Tap to Speak"
                isRecording = false
                binding.tvTranscription.text = "Recognized: \"$transcript\""
                processAndSaveInput(rawInput = transcript, isVoice = true)
            },
            onError = { error ->
                binding.btnSpeak.text = "🎤 Tap to Speak"
                isRecording = false
                binding.tvTranscription.text = "Error: $error"
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun toggleVoiceCapture() {
        if (voiceCaptureManager == null) {
            initVoiceCapture()
        }

        if (!isRecording) {
            startVoiceCapture()
        } else {
            stopVoiceCapture()
        }
    }

    private fun startVoiceCapture() {
        isRecording = true
        voiceCaptureManager?.startListening()
    }

    private fun stopVoiceCapture() {
        isRecording = false
        voiceCaptureManager?.stopListening()
        binding.btnSpeak.text = "🎤 Tap to Speak"
    }

    /**
     * Executes the Core Capture -> Parsing -> Supabase Write Loop
     */
    private fun processAndSaveInput(rawInput: String, isVoice: Boolean) {
        val userId = SupabaseManager.currentUserId ?: return

        // 1. Natural language date parsing and filler stripping
        val parsed = DateParser.parse(rawInput)

        binding.tvParsedResult.text = "Clean Content: \"${parsed.cleanContent}\"\nDue At: ${parsed.dueAtIso ?: "[None]"}"
        appendLog("Input captured [${if (isVoice) "Voice" else "Typed"}]: \"$rawInput\"")
        appendLog("Parsed: content=\"${parsed.cleanContent}\", due_at=${parsed.dueAtIso}")

        // 2. Construct Item
        val item = Item(
            userId = userId,
            content = parsed.cleanContent,
            dueAt = parsed.dueAtIso,
            sourceDevice = "android"
        )

        // 3. Write to Supabase items table
        lifecycleScope.launch {
            val result = SupabaseManager.insertItem(item)
            result.onSuccess { savedItem ->
                appendLog("✓ Saved to Supabase (ID: ${savedItem.id})")

                // Voice confirmation rule: Spoken feedback ONLY for voice input
                if (isVoice) {
                    ttsManager?.speakConfirmation("Got it, saved")
                } else {
                    Toast.makeText(this@Phase2TestActivity, "✓ Saved: ${savedItem.content}", Toast.LENGTH_SHORT).show()
                }
            }.onFailure { err ->
                appendLog("❌ Insert failed: ${err.message}")
                Toast.makeText(this@Phase2TestActivity, "Write error: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startRealtimeListener() {
        lifecycleScope.launch {
            SupabaseManager.subscribeToItems(
                scope = lifecycleScope,
                onEvent = { eventType, item ->
                    val timestamp = logDateFormat.format(Date())
                    val logMsg = "[$timestamp] Realtime $eventType: \"${item?.content}\" (due: ${item?.dueAt ?: "none"}) [ID: ${item?.id}]"
                    appendLog(logMsg)
                    Log.i("WispRealtime", logMsg)
                },
                onStatusChange = { status ->
                    binding.tvRealtimeStatus.text = status
                    if (status.contains("Connected")) {
                        binding.tvRealtimeStatus.setTextColor(ContextCompat.getColor(this@Phase2TestActivity, com.wisp.app.R.color.wisp_success))
                    }
                }
            )
        }
    }

    private fun appendLog(message: String) {
        val current = binding.tvRealtimeLogs.text.toString()
        val updated = if (current.contains("[Awaiting")) message else "$message\n$current"
        binding.tvRealtimeLogs.text = updated
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceCaptureManager?.destroy()
        ttsManager?.shutdown()
        lifecycleScope.launch {
            SupabaseManager.unsubscribe()
        }
    }
}
