package com.wisp.app.voice

/**
 * Strips conversational filler prefixes and phrases from captured speech.
 * Reference: PRD.md Section 6.1.2
 */
object FillerWordStripper {

    private val FILLER_PATTERNS = listOf(
        Regex("^(can you please |please |hey wisp |wisp )?(remind me to|remind me)\\s+", RegexOption.IGNORE_CASE),
        Regex("^(can you please |please )?(make sure to|remember to|don't forget to|dont forget to)\\s+", RegexOption.IGNORE_CASE),
        Regex("^(i need to|i have to|i want to|i gotta|i'd like to|id like to)\\s+", RegexOption.IGNORE_CASE),
        Regex("^(add a task to|create a task to|add task|add note to|add note)\\s+", RegexOption.IGNORE_CASE)
    )

    /**
     * Strips leading filler phrases and cleans the text.
     * E.g.: "Remind me to call mom tomorrow" -> "call mom tomorrow"
     * E.g.: "I need to review the report" -> "review the report"
     */
    fun strip(rawText: String): String {
        var cleaned = rawText.trim()

        for (pattern in FILLER_PATTERNS) {
            val match = pattern.find(cleaned)
            if (match != null) {
                cleaned = cleaned.substring(match.range.last + 1).trim()
                break
            }
        }

        // Clean up trailing punctuation if it ends with just a period or comma
        cleaned = cleaned.replace(Regex("[,.]+$"), "").trim()

        // Capitalize the first letter if not empty
        return if (cleaned.isNotEmpty()) {
            cleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        } else {
            rawText.trim()
        }
    }
}
