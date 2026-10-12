package com.example.domain

data class ContactNumber(
    val rawNumber: String,
    val normalizedNumber: String,
    val typeLabel: String
)

data class ContactItem(
    val contactId: Long,
    val lookupKey: String,
    val displayName: String,
    val photoUri: String?,
    val numbers: List<ContactNumber>,
    val customNote: String? = null,
    val customLabel: String? = null,
    val isFavorite: Boolean = false,
    val isImportant: Boolean = false,
    val isBlocked: Boolean = false
) {
    val primaryNumber: String
        get() = numbers.firstOrNull()?.rawNumber.orEmpty()

    val primaryNormalizedNumber: String
        get() = numbers.firstOrNull()?.normalizedNumber.orEmpty()

    val initials: String
        get() {
            val trimmed = displayName.trim()
            if (trimmed.isEmpty()) return "#"
            val parts = trimmed.split(" ").filter { it.isNotBlank() }
            return if (parts.size >= 2) {
                "${parts.first().first()}${parts.last().first()}".uppercase()
            } else {
                trimmed.take(2).uppercase()
            }
        }
}

enum class CallType {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED,
    BLOCKED,
    UNKNOWN
}

enum class CallLogFilter {
    ALL,
    MISSED,
    INCOMING,
    OUTGOING
}

data class CallRecord(
    val id: Long,
    val rawNumber: String,
    val normalizedNumber: String,
    val cachedName: String?,
    val resolvedContactName: String? = null,
    val resolvedContactId: Long? = null,
    val photoUri: String? = null,
    val callType: CallType,
    val timestampMillis: Long,
    val durationSeconds: Long,
    val simSlotLabel: String? = null,
    val isBlocked: Boolean = false,
    val assistNote: String? = null,
    val assistLabel: String? = null
) {
    val displayIdentity: String
        get() = when {
            !resolvedContactName.isNullOrBlank() -> resolvedContactName
            !cachedName.isNullOrBlank() -> cachedName
            rawNumber.isNotBlank() -> rawNumber
            else -> ""
        }

    val isKnownContact: Boolean
        get() = !resolvedContactName.isNullOrBlank() || !cachedName.isNullOrBlank()
}

data class GroupedCallSession(
    val sessionKey: String,
    val primaryRecord: CallRecord,
    val records: List<CallRecord>,
    val dateHeader: String
) {
    val callCount: Int get() = records.size
    val hasMissedCall: Boolean get() = records.any { it.callType == CallType.MISSED }
}

data class SimSubscriptionItem(
    val id: String,
    val label: String,
    val carrierName: String,
    val slotIndex: Int
)
