package com.wisp.app.mainapp

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.wisp.app.databinding.ItemArchiveCardBinding
import com.wisp.app.sync.Item

/**
 * Adapter for the permanent Completed Archive section.
 * Reference: PRD.md Section 6.1.5
 */
class CompletedArchiveAdapter(
    private val onRestore: (Item) -> Unit
) : ListAdapter<Item, CompletedArchiveAdapter.ArchiveViewHolder>(DiffCallback) {

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Item>() {
            override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean =
                oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ArchiveViewHolder {
        val binding = ItemArchiveCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ArchiveViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ArchiveViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ArchiveViewHolder(private val binding: ItemArchiveCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Item) {
            binding.tvArchiveContent.text = item.content
            binding.tvArchiveContent.paintFlags =
                binding.tvArchiveContent.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG

            val completedTime = item.completedAt?.replace("T", " ")?.substringBefore(".") ?: "Recently"
            binding.tvCompletedAt.text = "Completed: $completedTime"

            binding.btnArchiveRestore.setOnClickListener {
                onRestore(item)
            }
        }
    }
}
