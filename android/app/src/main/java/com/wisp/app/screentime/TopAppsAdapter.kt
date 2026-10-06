package com.wisp.app.screentime

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wisp.app.databinding.ItemTopAppBinding

/**
 * RecyclerView adapter for ranked top apps in the Screen Time dashboard.
 * Reference: DESIGN.md Section 7.5
 */
class TopAppsAdapter : RecyclerView.Adapter<TopAppsAdapter.ViewHolder>() {

    private val items = mutableListOf<AppUsageStat>()

    fun submitList(newItems: List<AppUsageStat>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTopAppBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position + 1)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(private val binding: ItemTopAppBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(stat: AppUsageStat, rank: Int) {
            binding.tvAppRank.text = rank.toString()
            binding.tvAppName.text = stat.appName
            binding.tvAppDuration.text = stat.formattedDuration

            val deviceLabel = if (stat.device == "windows") "💻 Windows" else "📱 Android"
            binding.tvDeviceTag.text = deviceLabel

            binding.progressBarApp.progress = stat.percentageOfTotal.toInt().coerceIn(1, 100)
        }
    }
}
