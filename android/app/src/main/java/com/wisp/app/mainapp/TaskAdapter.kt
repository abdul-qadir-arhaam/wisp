package com.wisp.app.mainapp

import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wisp.app.R
import com.wisp.app.databinding.ItemTaskCardBinding
import com.wisp.app.sync.Item

/**
 * Adapter for the main task list, implementing the double-click-confirm tickbox interaction,
 * overdue indicators, strike-through styling, and undo completion.
 * Reference: PRD.md Section 6.1.4
 */
class TaskAdapter(
    private val onCompleteConfirmed: (Item) -> Unit,
    private val onUndo: (Item) -> Unit
) : ListAdapter<Item, TaskAdapter.TaskViewHolder>(DiffCallback) {

    // Tracks item IDs currently in the "confirming" (first click) state
    private val confirmingItemIds = mutableSetOf<String>()
    private val handler = Handler(Looper.getMainLooper())
    private val resetRunnables = mutableMapOf<String, Runnable>()

    companion object {
        private const val CONFIRM_TIMEOUT_MS = 2500L

        private val DiffCallback = object : DiffUtil.ItemCallback<Item>() {
            override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean =
                oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TaskViewHolder(private val binding: ItemTaskCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Item) {
            val context = binding.root.context
            val itemId = item.id ?: ""
            val isConfirming = confirmingItemIds.contains(itemId)

            binding.tvTaskContent.text = item.content

            if (item.completed) {
                // Completed State (recent today/yesterday)
                binding.tvTaskContent.paintFlags =
                    binding.tvTaskContent.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                binding.tvTaskContent.setTextColor(
                    ContextCompat.getColor(context, R.color.wisp_text_secondary)
                )

                binding.viewTickIndicator.background =
                    ContextCompat.getDrawable(context, R.drawable.bg_tickbox_done)
                binding.tvTickIcon.text = "✓"
                binding.tvTickIcon.visibility = View.VISIBLE

                binding.tvConfirmHint.visibility = View.GONE
                binding.tvOverdueBadge.visibility = View.GONE
                binding.tvDueBadge.visibility = View.GONE
                binding.btnUndo.visibility = View.VISIBLE

                binding.btnUndo.setOnClickListener {
                    onUndo(item)
                }
                binding.btnTickbox.setOnClickListener {
                    onUndo(item)
                }

            } else {
                // Active / Incomplete State
                binding.tvTaskContent.paintFlags =
                    binding.tvTaskContent.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                binding.tvTaskContent.setTextColor(
                    ContextCompat.getColor(context, R.color.wisp_text_primary)
                )
                binding.btnUndo.visibility = View.GONE

                // Due & Overdue Badges
                val isOverdue = FilterManager.isOverdue(item)
                if (isOverdue) {
                    binding.tvOverdueBadge.visibility = View.VISIBLE
                    binding.tvDueBadge.visibility = View.GONE
                } else if (!item.dueAt.isNullOrBlank()) {
                    binding.tvOverdueBadge.visibility = View.GONE
                    binding.tvDueBadge.visibility = View.VISIBLE
                    binding.tvDueBadge.text = formatDueTimestamp(item.dueAt)
                } else {
                    binding.tvOverdueBadge.visibility = View.GONE
                    binding.tvDueBadge.visibility = View.GONE
                }

                if (isConfirming) {
                    // First click: Partial-fill / pulsing highlight
                    binding.viewTickIndicator.background =
                        ContextCompat.getDrawable(context, R.drawable.bg_tickbox_confirming)
                    binding.tvTickIcon.text = ""
                    binding.tvTickIcon.visibility = View.GONE
                    binding.tvConfirmHint.visibility = View.VISIBLE
                } else {
                    // Default Empty State
                    binding.viewTickIndicator.background =
                        ContextCompat.getDrawable(context, R.drawable.bg_tickbox_empty)
                    binding.tvTickIcon.text = ""
                    binding.tvTickIcon.visibility = View.GONE
                    binding.tvConfirmHint.visibility = View.GONE
                }

                // Double-Click Confirmation Logic (PRD 6.1.4)
                binding.btnTickbox.setOnClickListener {
                    if (isConfirming) {
                        // Second Click within timeout: Confirm completion!
                        cancelResetTimer(itemId)
                        confirmingItemIds.remove(itemId)
                        onCompleteConfirmed(item)
                    } else {
                        // First Click: Enter partial-fill state and start timeout timer
                        confirmingItemIds.add(itemId)
                        notifyItemChanged(bindingAdapterPosition)

                        scheduleResetTimer(itemId)
                    }
                }
            }
        }

        private fun scheduleResetTimer(itemId: String) {
            cancelResetTimer(itemId)
            val runnable = Runnable {
                if (confirmingItemIds.remove(itemId)) {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        notifyItemChanged(pos)
                    }
                }
            }
            resetRunnables[itemId] = runnable
            handler.postDelayed(runnable, CONFIRM_TIMEOUT_MS)
        }

        private fun cancelResetTimer(itemId: String) {
            resetRunnables.remove(itemId)?.let { handler.removeCallbacks(it) }
        }

        private fun formatDueTimestamp(isoString: String): String {
            return try {
                val clean = isoString.replace("T", " ").substringBefore("Z")
                if (clean.length > 16) clean.substring(0, 16) else clean
            } catch (_: Exception) {
                isoString
            }
        }
    }
}
