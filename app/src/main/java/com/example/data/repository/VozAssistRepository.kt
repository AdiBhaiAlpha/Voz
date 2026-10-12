package com.example.data.repository

import com.example.data.local.BlockedNumberEntity
import com.example.data.local.CallReminderEntity
import com.example.data.local.ContactAssistEntity
import com.example.data.local.VozAssistDao
import com.example.domain.PhoneNormalizer
import kotlinx.coroutines.flow.Flow

class VozAssistRepository(private val dao: VozAssistDao) {

    val assistMetadataFlow: Flow<List<ContactAssistEntity>> = dao.observeAllAssistMetadata()
    val blockedNumbersFlow: Flow<List<BlockedNumberEntity>> = dao.observeBlockedNumbers()
    val activeRemindersFlow: Flow<List<CallReminderEntity>> = dao.observeActiveReminders()

    suspend fun saveContactAssist(
        rawNumber: String,
        displayNameHint: String,
        note: String,
        customLabel: String,
        isFavorite: Boolean,
        isImportant: Boolean
    ) {
        val normalized = PhoneNormalizer.normalizeForComparison(rawNumber).ifBlank { rawNumber.trim() }
        if (normalized.isEmpty()) return

        if (note.isBlank() && customLabel.isBlank() && !isFavorite && !isImportant) {
            dao.deleteAssistMetadata(normalized)
        } else {
            dao.upsertAssistMetadata(
                ContactAssistEntity(
                    normalizedNumber = normalized,
                    rawNumber = rawNumber.trim(),
                    displayNameHint = displayNameHint.trim(),
                    note = note.trim(),
                    customLabel = customLabel.trim(),
                    isFavorite = isFavorite,
                    isImportant = isImportant,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun toggleFavorite(rawNumber: String, displayNameHint: String) {
        val normalized = PhoneNormalizer.normalizeForComparison(rawNumber).ifBlank { rawNumber.trim() }
        if (normalized.isEmpty()) return
        val existing = dao.getAssistForNumber(normalized)
        val updated = if (existing != null) {
            existing.copy(
                isFavorite = !existing.isFavorite,
                updatedAtMillis = System.currentTimeMillis()
            )
        } else {
            ContactAssistEntity(
                normalizedNumber = normalized,
                rawNumber = rawNumber.trim(),
                displayNameHint = displayNameHint.trim(),
                isFavorite = true
            )
        }
        dao.upsertAssistMetadata(updated)
    }

    suspend fun toggleImportant(rawNumber: String, displayNameHint: String) {
        val normalized = PhoneNormalizer.normalizeForComparison(rawNumber).ifBlank { rawNumber.trim() }
        if (normalized.isEmpty()) return
        val existing = dao.getAssistForNumber(normalized)
        val updated = if (existing != null) {
            existing.copy(
                isImportant = !existing.isImportant,
                updatedAtMillis = System.currentTimeMillis()
            )
        } else {
            ContactAssistEntity(
                normalizedNumber = normalized,
                rawNumber = rawNumber.trim(),
                displayNameHint = displayNameHint.trim(),
                isImportant = true
            )
        }
        dao.upsertAssistMetadata(updated)
    }

    suspend fun deleteContactAssist(rawOrNormalizedNumber: String) {
        val normalized = PhoneNormalizer.normalizeForComparison(rawOrNormalizedNumber)
            .ifBlank { rawOrNormalizedNumber.trim() }
        dao.deleteAssistMetadata(normalized)
    }

    suspend fun blockNumber(rawNumber: String, reason: String = ""): Boolean {
        val normalized = PhoneNormalizer.normalizeForComparison(rawNumber)
        if (normalized.isEmpty()) return false
        dao.insertBlockedNumber(
            BlockedNumberEntity(
                normalizedNumber = normalized,
                originalNumber = rawNumber.trim(),
                reason = reason.trim(),
                createdAtMillis = System.currentTimeMillis()
            )
        )
        return true
    }

    suspend fun unblockNumber(rawOrNormalizedNumber: String) {
        val normalized = PhoneNormalizer.normalizeForComparison(rawOrNormalizedNumber)
            .ifBlank { rawOrNormalizedNumber.trim() }
        dao.unblockNumber(normalized)
    }

    suspend fun isBlocked(rawNumber: String): Boolean {
        val normalized = PhoneNormalizer.normalizeForComparison(rawNumber)
        if (normalized.isEmpty()) return false
        if (dao.isNumberBlocked(normalized)) return true
        val allBlocked = dao.getAllBlockedNumbersSnapshot()
        return allBlocked.any { PhoneNormalizer.areNumbersEquivalent(it.normalizedNumber, normalized) }
    }

    suspend fun addCallReminder(
        rawNumber: String,
        contactName: String,
        reason: String,
        dueInMinutes: Int
    ): Long {
        val normalized = PhoneNormalizer.normalizeForComparison(rawNumber).ifBlank { rawNumber.trim() }
        val dueAt = System.currentTimeMillis() + (dueInMinutes.coerceAtLeast(1) * 60_000L)
        return dao.insertReminder(
            CallReminderEntity(
                normalizedNumber = normalized,
                rawNumber = rawNumber.trim(),
                contactName = contactName.trim().ifBlank { rawNumber.trim() },
                reason = reason.trim(),
                dueAtMillis = dueAt
            )
        )
    }

    suspend fun completeReminder(reminderId: Long) {
        dao.markReminderCompleted(reminderId)
    }

    suspend fun deleteReminder(reminderId: Long) {
        dao.deleteReminder(reminderId)
    }
}
