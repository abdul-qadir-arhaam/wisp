package com.wisp.app.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Unified Item data class for tasks and notes in Wisp.
 * Reference: PRD.md Section 6.1.1 & ARCHITECTURE.md Section 4
 */
@Serializable
data class Item(
    @SerialName("id")
    val id: String? = null,

    @SerialName("user_id")
    val userId: String,

    @SerialName("content")
    val content: String,

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("due_at")
    val dueAt: String? = null,

    @SerialName("completed")
    val completed: Boolean = false,

    @SerialName("completed_at")
    val completedAt: String? = null,

    @SerialName("source_device")
    val sourceDevice: String = "android",

    @SerialName("reminder_sent_day_before")
    val reminderSentDayBefore: Boolean = false,

    @SerialName("reminder_sent_overdue")
    val reminderSentOverdue: Boolean = false
)
