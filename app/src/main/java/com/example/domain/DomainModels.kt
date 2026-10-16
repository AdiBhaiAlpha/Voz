package com.example.domain

enum class CallType {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED,
    BLOCKED
}

enum class CallLogFilter {
    ALL,
    INCOMING,
    MISSED,
    OUTGOING
}

data class ContactNumber(
    val rawNumber: String,
    val normalizedNumber: String,
    val label: String = "Mobile",
    val type: Int = 2
)

data class ContactItem(
    val contactId: Long,
    val lookupKey: String,
    val displayName: String,
    val primaryNumber: String,
    val primaryNormalizedNumber: String,
    val numbers: List<ContactNumber> = emptyList(),
    val photoUri: String? = null,
    val isFavorite: Boolean = false,
    val isImportant: Boolean = false,
    val customNote: String? = null,
    val customLabel: String? = null
)

data class CallRecord(
    val id: Long,
    val callId: String,
    val rawNumber: String,
    val normalizedNumber: String,
    val displayName: String?,
    val photoUri: String?,
    val timestampMillis: Long,
    val durationSeconds: Long,
    val callType: CallType,
    val simAccountName: String?,
    val simAccountId: String?,
    val isBlocked: Boolean = false,
    val assistNote: String? = null,
    val assistLabel: String? = null,
    val isImportant: Boolean = false
)

data class GroupedCallSession(
    val sessionKey: String,
    val primaryRecord: CallRecord,
    val totalAttempts: Int,
    val latestTimestampMillis: Long,
    val allCallTypes: List<CallType>,
    val dateHeader: String
)

data class SimSubscriptionItem(
    val subscriptionId: Int,
    val displayName: String,
    val carrierName: String,
    val slotIndex: Int,
    val iccId: String,
    val color: Int
)
