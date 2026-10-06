package com.wisp.app.voice

import org.ocpsoft.prettytime.nlp.PrettyTimeParser
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Natural language deadline extraction result.
 */
data class ParsedTask(
    val cleanContent: String,
    val dueAtIso: String?,
    val datePhraseDetected: String?
)

/**
 * Natural language date parser using established PrettyTime NLP library
 * with specialized rules for common conversational voice patterns.
 * Reference: PRD.md Section 6.1.2
 */
object DateParser {

    private val prettyTimeParser by lazy { PrettyTimeParser() }
    private val isoFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    /**
     * Parses a raw voice or typed string into clean content and an optional ISO-8601 deadline.
     */
    fun parse(rawText: String, referenceTime: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())): ParsedTask {
        val stripped = FillerWordStripper.strip(rawText)

        // 1. Try conversational fast-path regex for common voice assistant patterns
        val conversationalResult = parseConversationalPatterns(stripped, referenceTime)
        if (conversationalResult != null) {
            return conversationalResult
        }

        // 2. Fall back to PrettyTime NLP library
        try {
            val dates: List<Date> = prettyTimeParser.parse(stripped)
            if (dates.isNotEmpty()) {
                val parsedDate = dates.first()
                // Format to ISO 8601
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).apply {
                    timeZone = TimeZone.getDefault()
                }
                val dueAtIso = sdf.format(parsedDate)

                // PrettyTime parsed a date. Return cleaned text and deadline
                return ParsedTask(
                    cleanContent = stripped,
                    dueAtIso = dueAtIso,
                    datePhraseDetected = "[PrettyTime NLP matched]"
                )
            }
        } catch (_: Exception) {
            // Ignore PrettyTime exception and return undated
        }

        return ParsedTask(
            cleanContent = stripped,
            dueAtIso = null,
            datePhraseDetected = null
        )
    }

    /**
     * Handles frequent spoken expressions:
     * - "tomorrow at 5pm" / "tomorrow 5pm" / "tomorrow"
     * - "today at 8pm" / "tonight"
     * - "by Friday" / "next Monday at 10am"
     * - "in 2 hours" / "in 30 minutes"
     */
    private fun parseConversationalPatterns(text: String, now: ZonedDateTime): ParsedTask? {
        val lower = text.lowercase(Locale.ROOT)

        // Match: tomorrow (optional at HH(:mm)?(am|pm)?)
        val tomorrowRegex = Regex("\\b(?:tomorrow|tmrw)(?:\\s+(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?)?\\b", RegexOption.IGNORE_CASE)
        val tomorrowMatch = tomorrowRegex.find(lower)
        if (tomorrowMatch != null) {
            val date = now.toLocalDate().plusDays(1)
            val time = extractTime(tomorrowMatch.groupValues[1], tomorrowMatch.groupValues[2], tomorrowMatch.groupValues[3], defaultHour = 9)
            val zdt = ZonedDateTime.of(date, time, now.zone)
            val cleaned = removeDatePhrase(text, tomorrowMatch.value)
            return ParsedTask(cleanContent = cleaned, dueAtIso = zdt.format(isoFormatter), datePhraseDetected = tomorrowMatch.value)
        }

        // Match: today / tonight (optional at HH(:mm)?(am|pm)?)
        val todayRegex = Regex("\\b(?:today|tonight)(?:\\s+(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?)?\\b", RegexOption.IGNORE_CASE)
        val todayMatch = todayRegex.find(lower)
        if (todayMatch != null) {
            val date = now.toLocalDate()
            val defaultHour = if (todayMatch.value.contains("tonight")) 20 else 17
            val time = extractTime(todayMatch.groupValues[1], todayMatch.groupValues[2], todayMatch.groupValues[3], defaultHour = defaultHour)
            val zdt = ZonedDateTime.of(date, time, now.zone)
            val cleaned = removeDatePhrase(text, todayMatch.value)
            return ParsedTask(cleanContent = cleaned, dueAtIso = zdt.format(isoFormatter), datePhraseDetected = todayMatch.value)
        }

        // Match: "by Friday" / "next Monday" / "on Tuesday at 3pm"
        val dayOfWeekRegex = Regex("\\b(?:by|next|this|on)?\\s*(monday|tuesday|wednesday|thursday|friday|saturday|sunday)(?:\\s+(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?)?\\b", RegexOption.IGNORE_CASE)
        val dayMatch = dayOfWeekRegex.find(lower)
        if (dayMatch != null) {
            val dayName = dayMatch.groupValues[1].uppercase(Locale.ROOT)
            val targetDow = try { DayOfWeek.valueOf(dayName) } catch (_: Exception) { null }
            if (targetDow != null) {
                var targetDate = now.toLocalDate().with(TemporalAdjusters.next(targetDow))
                // If saying "this Friday" and today is before Friday:
                if (dayMatch.value.contains("this") && now.dayOfWeek.value < targetDow.value) {
                    targetDate = now.toLocalDate().with(TemporalAdjusters.nextOrSame(targetDow))
                }
                val time = extractTime(dayMatch.groupValues[2], dayMatch.groupValues[3], dayMatch.groupValues[4], defaultHour = 9)
                val zdt = ZonedDateTime.of(targetDate, time, now.zone)
                val cleaned = removeDatePhrase(text, dayMatch.value)
                return ParsedTask(cleanContent = cleaned, dueAtIso = zdt.format(isoFormatter), datePhraseDetected = dayMatch.value)
            }
        }

        // Match: "in X hours" / "in X minutes"
        val inTimeRegex = Regex("\\bin\\s+(\\d+)\\s*(hour|hours|hr|hrs|minute|minutes|min|mins)\\b", RegexOption.IGNORE_CASE)
        val inTimeMatch = inTimeRegex.find(lower)
        if (inTimeMatch != null) {
            val amount = inTimeMatch.groupValues[1].toLongOrNull() ?: 1L
            val unit = inTimeMatch.groupValues[2]
            val zdt = if (unit.startsWith("h")) now.plusHours(amount) else now.plusMinutes(amount)
            val cleaned = removeDatePhrase(text, inTimeMatch.value)
            return ParsedTask(cleanContent = cleaned, dueAtIso = zdt.format(isoFormatter), datePhraseDetected = inTimeMatch.value)
        }

        return null
    }

    private fun extractTime(hourStr: String?, minStr: String?, amPmStr: String?, defaultHour: Int): LocalTime {
        if (hourStr.isNullOrBlank()) {
            return LocalTime.of(defaultHour, 0)
        }
        var hour = hourStr.toIntOrNull() ?: defaultHour
        val minute = minStr?.toIntOrNull() ?: 0
        val amPm = amPmStr?.lowercase(Locale.ROOT)

        if (amPm == "pm" && hour < 12) {
            hour += 12
        } else if (amPm == "am" && hour == 12) {
            hour = 0
        }
        return LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    }

    private fun removeDatePhrase(fullText: String, phrase: String): String {
        val result = fullText.replace(Regex("\\b${Regex.escape(phrase)}\\b", RegexOption.IGNORE_CASE), "").trim()
        // Clean up remaining dangling prepositions like "by" or "at"
        return result.replace(Regex("\\s+(by|at|on)$", RegexOption.IGNORE_CASE), "").trim()
    }
}
