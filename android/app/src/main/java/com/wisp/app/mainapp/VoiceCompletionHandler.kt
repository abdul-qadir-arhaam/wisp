package com.wisp.app.mainapp

import com.wisp.app.sync.Item
import java.util.Locale

sealed class VoiceCompletionResult {
    data class SingleMatch(val item: Item) : VoiceCompletionResult()
    data class AmbiguousMatch(val candidates: List<Item>, val clarificationQuestion: String) : VoiceCompletionResult()
    data class UndoMatch(val lastCompletedItem: Item?) : VoiceCompletionResult()
    object NotACompletionCommand : VoiceCompletionResult()
    object NoMatchFound : VoiceCompletionResult()
}

/**
 * Parses spoken completion and undo commands and executes fuzzy matching against active items.
 * Reference: PRD.md Section 6.1.4
 */
object VoiceCompletionHandler {

    private val COMPLETE_PATTERNS = listOf(
        Regex("^(?:mark|set)\\s+(.+?)\\s+as\\s+(?:done|complete|finished)$", RegexOption.IGNORE_CASE),
        Regex("^(?:i finished|i've finished|i completed|finished|done with|completed)\\s+(.+)$", RegexOption.IGNORE_CASE),
        Regex("^(?:check off|cross off)\\s+(.+)$", RegexOption.IGNORE_CASE)
    )

    private val UNDO_PATTERNS = listOf(
        Regex("^(?:undo|undo that|mark as not done|uncheck|reopen)$", RegexOption.IGNORE_CASE),
        Regex("^(?:mark|set)\\s+(.+?)\\s+as\\s+(?:not done|incomplete|pending)$", RegexOption.IGNORE_CASE)
    )

    /**
     * Checks if the spoken transcript is a completion or undo command.
     */
    fun evaluate(transcript: String, activeItems: List<Item>, recentCompletedItems: List<Item>): VoiceCompletionResult {
        val trimmed = transcript.trim()

        // 1. Check Undo patterns
        for (pattern in UNDO_PATTERNS) {
            val match = pattern.find(trimmed)
            if (match != null) {
                val targetText = if (match.groupValues.size > 1) match.groupValues[1].trim() else ""
                val targetItem = if (targetText.isNotBlank()) {
                    findBestMatch(targetText, recentCompletedItems)
                } else {
                    recentCompletedItems.firstOrNull()
                }
                return VoiceCompletionResult.UndoMatch(targetItem)
            }
        }

        // 2. Check Complete patterns
        var targetTaskQuery: String? = null
        for (pattern in COMPLETE_PATTERNS) {
            val match = pattern.find(trimmed)
            if (match != null) {
                targetTaskQuery = match.groupValues[1].trim()
                break
            }
        }

        if (targetTaskQuery == null) {
            return VoiceCompletionResult.NotACompletionCommand
        }

        // 3. Match against active items
        val candidates = findCandidateMatches(targetTaskQuery, activeItems)

        return when {
            candidates.size == 1 -> {
                VoiceCompletionResult.SingleMatch(candidates.first())
            }
            candidates.size > 1 -> {
                // Ambiguous match: ask exactly one clarifying question (PRD 6.1.4 rule)
                val topTwo = candidates.take(2)
                val question = "Did you mean \"${topTwo[0].content}\" or \"${topTwo[1].content}\"?"
                VoiceCompletionResult.AmbiguousMatch(topTwo, question)
            }
            else -> {
                VoiceCompletionResult.NoMatchFound
            }
        }
    }

    private fun findCandidateMatches(query: String, items: List<Item>): List<Item> {
        val qLower = query.lowercase(Locale.ROOT)
        val matches = mutableListOf<Pair<Item, Double>>()

        for (item in items) {
            val itemLower = item.content.lowercase(Locale.ROOT)
            val score = calculateSimilarity(qLower, itemLower)
            if (score >= 0.5) {
                matches.add(Pair(item, score))
            }
        }

        return matches.sortedByDescending { it.second }.map { it.first }
    }

    private fun findBestMatch(query: String, items: List<Item>): Item? {
        return findCandidateMatches(query, items).firstOrNull()
    }

    /**
     * Similarity metric combining token overlap and substring presence
     */
    private fun calculateSimilarity(query: String, target: String): Double {
        if (target.contains(query) || query.contains(target)) return 1.0

        val qTokens = query.split(Regex("\\s+")).filter { it.length > 2 }.toSet()
        val tTokens = target.split(Regex("\\s+")).filter { it.length > 2 }.toSet()

        if (qTokens.isEmpty() || tTokens.isEmpty()) return 0.0

        val common = qTokens.intersect(tTokens).size
        return common.toDouble() / qTokens.size.coerceAtLeast(tTokens.size)
    }
}
