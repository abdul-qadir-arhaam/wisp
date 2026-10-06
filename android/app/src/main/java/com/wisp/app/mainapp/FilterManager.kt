package com.wisp.app.mainapp

import com.wisp.app.sync.Item
import com.wisp.app.sync.SupabaseManager
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.format.DateTimeFormatter

enum class ViewFilter(val value: String) {
    PRIORITY("priority"),
    OLDEST("oldest"),
    NEWEST("newest")
}

@Serializable
data class UserPreferenceDto(
    val user_id: String,
    val selected_view: String
)

/**
 * Implements the sorting and filtering logic per PRD.md Section 6.1.3
 * and manages persistence to the Supabase user_preferences table.
 */
object FilterManager {

    var currentFilter: ViewFilter = ViewFilter.PRIORITY

    /**
     * Sorts a list of items according to the active filter mode.
     */
    fun sort(items: List<Item>, filter: ViewFilter = currentFilter): List<Item> {
        val now = Instant.now()

        return when (filter) {
            ViewFilter.PRIORITY -> {
                // Separate overdue, upcoming, and undated
                val (dated, undated) = items.partition { !it.dueAt.isNullOrBlank() }

                val (overdue, upcoming) = dated.partition { item ->
                    try {
                        val dueInstant = Instant.parse(item.dueAt)
                        dueInstant.isBefore(now) && !item.completed
                    } catch (_: Exception) {
                        false
                    }
                }

                val sortedOverdue = overdue.sortedBy { parseInstant(it.dueAt) }
                val sortedUpcoming = upcoming.sortedBy { parseInstant(it.dueAt) }
                val sortedUndated = undated.sortedBy { parseInstant(it.createdAt) }

                sortedOverdue + sortedUpcoming + sortedUndated
            }

            ViewFilter.OLDEST -> {
                items.sortedBy { parseInstant(it.createdAt) }
            }

            ViewFilter.NEWEST -> {
                items.sortedByDescending { parseInstant(it.createdAt) }
            }
        }
    }

    /**
     * Checks if an active item is overdue.
     */
    fun isOverdue(item: Item): Boolean {
        if (item.completed || item.dueAt.isNullOrBlank()) return false
        return try {
            Instant.parse(item.dueAt).isBefore(Instant.now())
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks if a completed item was completed today or yesterday (PRD Section 6.1.5).
     */
    fun isCompletedRecently(item: Item): Boolean {
        if (!item.completed || item.completedAt.isNullOrBlank()) return false
        return try {
            val completedInstant = Instant.parse(item.completedAt)
            val twoDaysAgo = Instant.now().minusSeconds(48 * 3600)
            completedInstant.isAfter(twoDaysAgo)
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Fetches user's saved view filter preference from Supabase user_preferences table.
     */
    suspend fun loadSavedPreference(userId: String): ViewFilter = withContext(Dispatchers.IO) {
        try {
            val list = SupabaseManager.client.postgrest["user_preferences"]
                .select {
                    filter { eq("user_id", userId) }
                }
                .decodeList<UserPreferenceDto>()

            val saved = list.firstOrNull()?.selected_view
            val matched = ViewFilter.values().find { it.value == saved } ?: ViewFilter.PRIORITY
            currentFilter = matched
            matched
        } catch (_: Exception) {
            currentFilter
        }
    }

    /**
     * Persists user's selected view filter to Supabase user_preferences table.
     */
    suspend fun savePreference(userId: String, filter: ViewFilter) = withContext(Dispatchers.IO) {
        currentFilter = filter
        try {
            val dto = UserPreferenceDto(user_id = userId, selected_view = filter.value)
            SupabaseManager.client.postgrest["user_preferences"]
                .upsert(dto)
        } catch (_: Exception) {
            // Keep local state if network write fails
        }
    }

    private fun parseInstant(isoString: String?): Long {
        if (isoString.isNullOrBlank()) return Long.MAX_VALUE
        return try {
            Instant.parse(isoString).toEpochMilli()
        } catch (_: Exception) {
            Long.MAX_VALUE
        }
    }
}
