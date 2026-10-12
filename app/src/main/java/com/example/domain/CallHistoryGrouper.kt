package com.example.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Turns raw system call-log entries into a clean, grouped communication timeline.
 * Consecutive calls with the same normalized phone number on the same calendar day are grouped
 * together so repetitive call attempts do not clutter the screen while keeping full access
 * to individual timestamps and durations.
 */
object CallHistoryGrouper {

    fun groupConsecutiveCalls(
        records: List<CallRecord>,
        nowMillis: Long = System.currentTimeMillis(),
        languageCode: String = "en"
    ): List<GroupedCallSession> {
        if (records.isEmpty()) return emptyList()

        val sorted = records.sortedByDescending { it.timestampMillis }
        val result = mutableListOf<GroupedCallSession>()

        var currentBatch = mutableListOf<CallRecord>()
        var currentDayKey = ""
        var currentNormalized = ""

        for (record in sorted) {
            val dayKey = formatDayBucket(record.timestampMillis, nowMillis, languageCode)
            val norm = record.normalizedNumber.ifBlank { record.rawNumber }

            val canGroupWithCurrent = currentBatch.isNotEmpty() &&
                currentDayKey == dayKey &&
                norm.isNotBlank() &&
                (currentNormalized == norm || PhoneNormalizer.areNumbersEquivalent(currentNormalized, norm))

            if (canGroupWithCurrent) {
                currentBatch.add(record)
            } else {
                if (currentBatch.isNotEmpty()) {
                    val head = currentBatch.first()
                    result.add(
                        GroupedCallSession(
                            sessionKey = "${head.id}_${currentBatch.size}",
                            primaryRecord = head,
                            records = currentBatch.toList(),
                            dateHeader = currentDayKey
                        )
                    )
                }
                currentBatch = mutableListOf(record)
                currentDayKey = dayKey
                currentNormalized = norm
            }
        }

        if (currentBatch.isNotEmpty()) {
            val head = currentBatch.first()
            result.add(
                GroupedCallSession(
                    sessionKey = "${head.id}_${currentBatch.size}",
                    primaryRecord = head,
                    records = currentBatch.toList(),
                    dateHeader = currentDayKey
                )
            )
        }

        return result
    }

    fun formatDayBucket(timestampMillis: Long, nowMillis: Long, languageCode: String): String {
        val isBangla = languageCode.startsWith("bn")
        val calNow = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val calTarget = Calendar.getInstance().apply { timeInMillis = timestampMillis }

        val sameYear = calNow.get(Calendar.YEAR) == calTarget.get(Calendar.YEAR)
        val sameDay = sameYear && calNow.get(Calendar.DAY_OF_YEAR) == calTarget.get(Calendar.DAY_OF_YEAR)
        if (sameDay) {
            return if (isBangla) "আজ" else "Today"
        }

        val calYesterday = (calNow.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        val isYesterday = calYesterday.get(Calendar.YEAR) == calTarget.get(Calendar.YEAR) &&
            calYesterday.get(Calendar.DAY_OF_YEAR) == calTarget.get(Calendar.DAY_OF_YEAR)
        if (isYesterday) {
            return if (isBangla) "গতকাল" else "Yesterday"
        }

        val locale = if (isBangla) Locale.forLanguageTag("bn-BD") else Locale.US
        val pattern = if (sameYear) "EEE, d MMM" else "d MMM yyyy"
        val formatted = SimpleDateFormat(pattern, locale).format(Date(timestampMillis))
        return if (isBangla) PhoneNormalizer.convertAsciiDigitsToBangla(formatted) else formatted
    }

    fun formatTime(timestampMillis: Long, languageCode: String = "en"): String {
        val isBangla = languageCode.startsWith("bn")
        val locale = if (isBangla) Locale.forLanguageTag("bn-BD") else Locale.US
        val formatted = SimpleDateFormat("h:mm a", locale).format(Date(timestampMillis))
        return if (isBangla) PhoneNormalizer.convertAsciiDigitsToBangla(formatted) else formatted
    }

    fun formatDuration(durationSeconds: Long, languageCode: String = "en"): String {
        val isBangla = languageCode.startsWith("bn")
        if (durationSeconds <= 0L) return if (isBangla) "০ সেকেন্ড" else "0s"
        val hours = durationSeconds / 3600
        val minutes = (durationSeconds % 3600) / 60
        val seconds = durationSeconds % 60
        val raw = when {
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
        return if (isBangla) {
            PhoneNormalizer.convertAsciiDigitsToBangla(
                raw.replace("h", " ঘণ্টা").replace("m", " মি.").replace("s", " সে.")
            )
        } else {
            raw
        }
    }
}
