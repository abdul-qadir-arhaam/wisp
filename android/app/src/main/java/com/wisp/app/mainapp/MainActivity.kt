package com.wisp.app.mainapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.wisp.app.R
import com.wisp.app.auth.LoginActivity
import com.wisp.app.databinding.ActivityMainBinding
import com.wisp.app.sync.Item
import com.wisp.app.sync.SupabaseManager
import com.wisp.app.voice.DateParser
import com.wisp.app.voice.TtsManager
import com.wisp.app.voice.VoiceCaptureManager
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Locale

/**
 * Full Management Main App: Priority/Oldest/Newest filtering, double-click-confirm tickboxes,
 * hybrid completed history, voice completion with clarification, and persistent user preferences.
 * Reference: PRD.md Section 6.1, 7.1.3 & DESIGN.md Section 7.4
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var taskAdapter: TaskAdapter
    private lateinit var archiveAdapter: CompletedArchiveAdapter

    private var allItems = mutableListOf<Item>()
    private var voiceCaptureManager: VoiceCaptureManager? = null
    private var ttsManager: TtsManager? = null

    private var pendingClarificationCandidates: List<Item>? = null

    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startVoiceCapture()
        } else {
            Toast.makeText(this, "Microphone permission required for voice commands", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure user is authenticated
        if (!SupabaseManager.isAuthenticated) {
            navigateToLogin()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ttsManager = TtsManager(this)

        setupRecyclerViews()
        setupTabsAndFilters()
        setupInputs()
        setupAccountMenu()

        // Load saved view preference and items
        val userId = SupabaseManager.currentUserId ?: ""
        lifecycleScope.launch {
            val savedFilter = FilterManager.loadSavedPreference(userId)
            applyFilterToChips(savedFilter)
            fetchItems()
            startRealtimeListener()
        }
    }

    private fun setupRecyclerViews() {
        taskAdapter = TaskAdapter(
            onCompleteConfirmed = { item ->
                markItemCompleted(item, true)
            },
            onUndo = { item ->
                markItemCompleted(item, false)
            }
        )
        binding.rvTasks.layoutManager = LinearLayoutManager(this)
        binding.rvTasks.adapter = taskAdapter

        archiveAdapter = CompletedArchiveAdapter(
            onRestore = { item ->
                markItemCompleted(item, false)
            }
        )
        binding.rvArchive.layoutManager = LinearLayoutManager(this)
        binding.rvArchive.adapter = archiveAdapter
    }

    private fun setupTabsAndFilters() {
        // Tab Layout: Tasks (0) vs Completed Archive (1)
        binding.tabLayoutPrimary.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab?.position == 0) {
                    binding.rvTasks.visibility = View.VISIBLE
                    binding.rvArchive.visibility = View.GONE
                    binding.layoutFilterBar.visibility = View.VISIBLE
                    binding.layoutBottomInput.visibility = View.VISIBLE
                    binding.fabVoice.visibility = View.VISIBLE
                    updateTaskLists()
                } else {
                    binding.rvTasks.visibility = View.GONE
                    binding.rvArchive.visibility = View.VISIBLE
                    binding.layoutFilterBar.visibility = View.GONE
                    binding.layoutBottomInput.visibility = View.GONE
                    binding.fabVoice.visibility = View.GONE
                    updateArchiveList()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Filter Chips (Priority, Oldest, Newest)
        binding.chipPriority.setOnClickListener { changeFilter(ViewFilter.PRIORITY) }
        binding.chipOldest.setOnClickListener { changeFilter(ViewFilter.OLDEST) }
        binding.chipNewest.setOnClickListener { changeFilter(ViewFilter.NEWEST) }
    }

    private fun changeFilter(newFilter: ViewFilter) {
        val userId = SupabaseManager.currentUserId ?: return
        lifecycleScope.launch {
            FilterManager.savePreference(userId, newFilter)
            updateTaskLists()
        }
    }

    private fun applyFilterToChips(filter: ViewFilter) {
        when (filter) {
            ViewFilter.PRIORITY -> binding.chipPriority.isChecked = true
            ViewFilter.OLDEST -> binding.chipOldest.isChecked = true
            ViewFilter.NEWEST -> binding.chipNewest.isChecked = true
        }
    }

    private fun setupInputs() {
        // Typed fallback input
        binding.btnSendInput.setOnClickListener {
            val text = binding.etCaptureInput.text.toString().trim()
            if (text.isNotBlank()) {
                val parsed = DateParser.parse(text)
                createItem(content = parsed.cleanContent, dueAt = parsed.dueAtIso, isVoice = false)
                binding.etCaptureInput.setText("")
            }
        }

        // Floating Mic Button (push-to-talk)
        binding.fabVoice.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                startVoiceCapture()
            }
        }
    }

    private fun startVoiceCapture() {
        if (voiceCaptureManager == null) {
            voiceCaptureManager = VoiceCaptureManager(
                context = this,
                onReady = {
                    Toast.makeText(this, "Listening...", Toast.LENGTH_SHORT).show()
                },
                onResult = { transcript ->
                    handleSpokenInput(transcript)
                },
                onError = { err ->
                    Toast.makeText(this, err, Toast.LENGTH_SHORT).show()
                }
            )
        }
        voiceCaptureManager?.startListening()
    }

    /**
     * Handles voice command execution: Add, Complete, Clarify, Undo, or Change View.
     */
    private fun handleSpokenInput(transcript: String) {
        val activeItems = allItems.filter { !it.completed }
        val recentCompleted = allItems.filter { it.completed && FilterManager.isCompletedRecently(it) }

        // 1. Check if user is responding to an ambiguous clarification question
        if (pendingClarificationCandidates != null) {
            val candidates = pendingClarificationCandidates!!
            pendingClarificationCandidates = null

            val matched = candidates.find {
                it.content.lowercase(Locale.ROOT).contains(transcript.lowercase(Locale.ROOT))
            } ?: candidates.first()

            markItemCompleted(matched, true)
            ttsManager?.speakConfirmation("Marked ${matched.content} as done")
            return
        }

        // 2. Check if spoken input is a view-switching voice command
        val lower = transcript.lowercase(Locale.ROOT)
        if (lower.contains("priority view") || lower.contains("sort by priority")) {
            changeFilter(ViewFilter.PRIORITY)
            applyFilterToChips(ViewFilter.PRIORITY)
            ttsManager?.speakConfirmation("Switched to priority view")
            return
        } else if (lower.contains("oldest first")) {
            changeFilter(ViewFilter.OLDEST)
            applyFilterToChips(ViewFilter.OLDEST)
            ttsManager?.speakConfirmation("Switched to oldest first")
            return
        } else if (lower.contains("newest first")) {
            changeFilter(ViewFilter.NEWEST)
            applyFilterToChips(ViewFilter.NEWEST)
            ttsManager?.speakConfirmation("Switched to newest first")
            return
        }

        // 3. Check if spoken input is a completion or undo command (PRD 6.1.4)
        when (val compResult = VoiceCompletionHandler.evaluate(transcript, activeItems, recentCompleted)) {
            is VoiceCompletionResult.SingleMatch -> {
                markItemCompleted(compResult.item, true)
                ttsManager?.speakConfirmation("Marked \"${compResult.item.content}\" as done")
                return
            }

            is VoiceCompletionResult.AmbiguousMatch -> {
                // Ambiguous match: ask ONE clarifying question (PRD 6.1.4 rule)
                pendingClarificationCandidates = compResult.candidates
                ttsManager?.speakConfirmation(compResult.clarificationQuestion)
                AlertDialog.Builder(this)
                    .setTitle("Disambiguation")
                    .setMessage(compResult.clarificationQuestion)
                    .setPositiveButton(compResult.candidates[0].content) { _, _ ->
                        markItemCompleted(compResult.candidates[0], true)
                    }
                    .setNegativeButton(compResult.candidates[1].content) { _, _ ->
                        markItemCompleted(compResult.candidates[1], true)
                    }
                    .show()
                return
            }

            is VoiceCompletionResult.UndoMatch -> {
                compResult.lastCompletedItem?.let { item ->
                    markItemCompleted(item, false)
                    ttsManager?.speakConfirmation("Undone: restored \"${item.content}\"")
                } ?: run {
                    ttsManager?.speakConfirmation("No recent completed tasks to undo")
                }
                return
            }

            is VoiceCompletionResult.NoMatchFound -> {
                // Spoken completion had no matching task, inform user
                ttsManager?.speakConfirmation("Could not find a matching task to complete")
                return
            }

            is VoiceCompletionResult.NotACompletionCommand -> {
                // Standard capture: parse deadline & filler words
                val parsed = DateParser.parse(transcript)
                createItem(content = parsed.cleanContent, dueAt = parsed.dueAtIso, isVoice = true)
            }
        }
    }

    private fun createItem(content: String, dueAt: String?, isVoice: Boolean) {
        val userId = SupabaseManager.currentUserId ?: return
        val newItem = Item(
            userId = userId,
            content = content,
            dueAt = dueAt,
            sourceDevice = "android"
        )

        lifecycleScope.launch {
            val result = SupabaseManager.insertItem(newItem)
            result.onSuccess { saved ->
                allItems.add(0, saved)
                updateTaskLists()
                if (isVoice) {
                    ttsManager?.speakConfirmation("Got it, saved")
                } else {
                    Toast.makeText(this@MainActivity, "Saved: ${saved.content}", Toast.LENGTH_SHORT).show()
                }
            }.onFailure { err ->
                Toast.makeText(this@MainActivity, "Error saving: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun markItemCompleted(item: Item, completed: Boolean) {
        val itemId = item.id ?: return
        val completedAt = if (completed) Instant.now().toString() else null

        lifecycleScope.launch {
            val result = SupabaseManager.updateItemCompletion(itemId, completed, completedAt)
            result.onSuccess {
                val index = allItems.indexOfFirst { it.id == itemId }
                if (index != -1) {
                    allItems[index] = item.copy(completed = completed, completedAt = completedAt)
                    updateTaskLists()
                    updateArchiveList()
                }
            }.onFailure { err ->
                Toast.makeText(this@MainActivity, "Update error: ${err.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchItems() {
        lifecycleScope.launch {
            val result = SupabaseManager.getItems()
            result.onSuccess { items ->
                allItems.clear()
                allItems.addAll(items)
                updateTaskLists()
                updateArchiveList()
            }
        }
    }

    private fun updateTaskLists() {
        // Hybrid completed display rule (PRD 6.1.5):
        // Main list includes active items + items completed TODAY or YESTERDAY.
        val mainListItems = allItems.filter { item ->
            !item.completed || FilterManager.isCompletedRecently(item)
        }

        val sorted = FilterManager.sort(mainListItems, FilterManager.currentFilter)
        taskAdapter.submitList(sorted)

        binding.layoutEmptyState.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateArchiveList() {
        // Completed section (PRD 6.1.5): permanent, full archive of every completed item
        val completedAll = allItems.filter { it.completed }
            .sortedByDescending { it.completedAt ?: it.createdAt }

        archiveAdapter.submitList(completedAll)
    }

    private fun startRealtimeListener() {
        lifecycleScope.launch {
            SupabaseManager.subscribeToItems(
                scope = lifecycleScope,
                onEvent = { eventType, item ->
                    if (item != null) {
                        when (eventType) {
                            "INSERT" -> {
                                if (allItems.none { it.id == item.id }) {
                                    allItems.add(0, item)
                                }
                            }
                            "UPDATE" -> {
                                val idx = allItems.indexOfFirst { it.id == item.id }
                                if (idx != -1) allItems[idx] = item else allItems.add(item)
                            }
                            "DELETE" -> {
                                allItems.removeAll { it.id == item.id }
                            }
                        }
                        updateTaskLists()
                        updateArchiveList()
                    }
                },
                onStatusChange = { status ->
                    binding.tvSyncStatus.text = if (status.contains("Connected")) "Synced ✓" else status
                }
            )
        }
    }

    private fun setupAccountMenu() {
        binding.btnAccount.setOnClickListener { v ->
            val popup = PopupMenu(this, v)
            val userEmail = SupabaseManager.client.auth.currentUserOrNull()?.email ?: "Account"
            popup.menu.add(userEmail).isEnabled = false
            popup.menu.add("Sign Out")
            popup.setOnMenuItemClickListener { menuItem ->
                if (menuItem.title == "Sign Out") {
                    lifecycleScope.launch {
                        SupabaseManager.signOut()
                        navigateToLogin()
                    }
                    true
                } else false
            }
            popup.show()
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceCaptureManager?.destroy()
        ttsManager?.shutdown()
    }
}
