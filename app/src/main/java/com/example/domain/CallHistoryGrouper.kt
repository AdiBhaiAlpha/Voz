package com.example.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object CallHistoryGrouper {

    private val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val fullDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    fun groupCallRecords(records: List<CallRecord>): List<GroupedCallSession> {
        if (records.isEmpty()) return emptyList()

        val grouped = mutableListOf<GroupedCallSession>()
        var currentBatch = mutableListOf<CallRecord>()
        var currentKey = ""

        fun flushBatch() {
            if (currentBatch.isNotEmpty()) {
                val primary = currentBatch.first()
                val sessionKey = "${primary.normalizedNumber}_${primary.timestampMillis}"
                val allTypes = currentBatch.map { it.callType }
                val header = formatDateHeader(primary.timestampMillis)
                grouped.add(
                    GroupedCallSession(
                        sessionKey = sessionKey,
                        primaryRecord = primary,
                        totalAttempts = currentBatch.size,
                        latestTimestampMillis = primary.timestampMillis,
                        allCallTypes = allTypes,
                        dateHeader = header
                    )
                )
                currentBatch = mutableListOf()
            }
        }

        val recordCal = Calendar.getInstance()

        for (record in records) {
            recordCal.timeInMillis = record.timestampMillis
            val dayKey = dayFormat.format(Date(record.timestampMillis))
            val numberKey = record.normalizedNumber.ifBlank { record.rawNumber }
            val batchKey = "${numberKey}_$dayKey"

            if (currentBatch.isEmpty()) {
                currentKey = batchKey
                currentBatch.add(record)
            } else if (currentKey == batchKey) {
                currentBatch.add(record)
            } else {
                flushBatch()
                currentKey = batchKey
                currentBatch.add(record)
            }
        }
        flushBatch()

        return grouped
    }

    fun formatDateHeader(timestampMillis: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = timestampMillis }

        return when {
            isSameDay(now, then) -> "Today"
            isYesterday(now, then) -> "Yesterday"
            now.get(Calendar.WEEK_OF_YEAR) == then.get(Calendar.WEEK_OF_YEAR) &&
                    now.get(Calendar.YEAR) == then.get(Calendar.YEAR) -> {
                SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestampMillis))
            }
            else -> fullDateFormat.format(Date(timestampMillis))
        }
    }

    fun formatCallTime(timestampMillis: Long): String {
        return timeFormat.format(Date(timestampMillis))
    }

    fun formatDuration(durationSeconds: Long): String {
        if (durationSeconds <= 0) return "0s"
        val hours = durationSeconds / 3600
        val minutes = (durationSeconds % 3600) / 60
        val seconds = durationSeconds % 60
        return when {
            hours > 0 -> String.format(Locale.getDefault(), "%dh %dm %ds", hours, minutes, seconds)
            minutes > 0 -> String.format(Locale.getDefault(), "%dm %ds", minutes, seconds)
            else -> String.format(Locale.getDefault(), "%ds", seconds)
        }
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(today: Calendar, other: Calendar): Boolean {
        val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        return isSameDay(yesterday, other)
    }
}
