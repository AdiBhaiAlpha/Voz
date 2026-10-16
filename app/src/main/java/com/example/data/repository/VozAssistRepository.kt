package com.example.data.repository

import com.example.data.local.BlockedNumberEntity
import com.example.data.local.CallReminderEntity
import com.example.data.local.ContactAssistEntity
import com.example.data.local.VozAssistDao
import com.example.domain.PhoneNormalizer
import kotlinx.coroutines.flow.Flow

class VozAssistRepository(private val dao: VozAssistDao) {

    val allAssist: Flow<List<ContactAssistEntity>> = dao.getAllAssist()
    val allBlocked: Flow<List<BlockedNumberEntity>> = dao.getAllBlocked()
    val pendingReminders: Flow<List<CallReminderEntity>> = dao.getPendingReminders()

    suspend fun getAssist(phoneOrKey: String): ContactAssistEntity? {
        val norm = PhoneNormalizer.normalizeForComparison(phoneOrKey)
        val key = norm.ifBlank { phoneOrKey }
        return dao.getAssistByKey(key)
    }

    suspend fun saveAssist(
        phoneOrKey: String,
        note: String,
        customLabel: String,
        isFavorite: Boolean,
        isImportant: Boolean
    ) {
        val norm = PhoneNormalizer.normalizeForComparison(phoneOrKey)
        val key = norm.ifBlank { phoneOrKey }
        dao.upsertAssist(
            ContactAssistEntity(
                phoneKey = key,
                note = note.trim(),
                customLabel = customLabel.trim(),
                isFavorite = isFavorite,
                isImportant = isImportant,
                lastModifiedMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteAssist(phoneOrKey: String) {
        val norm = PhoneNormalizer.normalizeForComparison(phoneOrKey)
        val key = norm.ifBlank { phoneOrKey }
        dao.deleteAssist(key)
    }

    suspend fun toggleFavorite(phoneOrKey: String): Boolean {
        val current = getAssist(phoneOrKey)
        val newFav = !(current?.isFavorite ?: false)
        val norm = PhoneNormalizer.normalizeForComparison(phoneOrKey)
        val key = norm.ifBlank { phoneOrKey }
        dao.upsertAssist(
            ContactAssistEntity(
                phoneKey = key,
                note = current?.note ?: "",
                customLabel = current?.customLabel ?: "",
                isFavorite = newFav,
                isImportant = current?.isImportant ?: false,
                lastModifiedMillis = System.currentTimeMillis()
            )
        )
        return newFav
    }

    suspend fun toggleImportant(phoneOrKey: String): Boolean {
        val current = getAssist(phoneOrKey)
        val newImp = !(current?.isImportant ?: false)
        val norm = PhoneNormalizer.normalizeForComparison(phoneOrKey)
        val key = norm.ifBlank { phoneOrKey }
        dao.upsertAssist(
            ContactAssistEntity(
                phoneKey = key,
                note = current?.note ?: "",
                customLabel = current?.customLabel ?: "",
                isFavorite = current?.isFavorite ?: false,
                isImportant = newImp,
                lastModifiedMillis = System.currentTimeMillis()
            )
        )
        return newImp
    }

    suspend fun isBlocked(rawNumber: String): Boolean {
        val norm = PhoneNormalizer.normalizeForComparison(rawNumber)
        if (norm.isEmpty()) return false
        return dao.isBlocked(norm)
    }

    suspend fun blockNumber(rawNumber: String, reason: String = "") {
        val norm = PhoneNormalizer.normalizeForComparison(rawNumber)
        val key = norm.ifBlank { rawNumber }
        dao.blockNumber(
            BlockedNumberEntity(
                normalizedNumber = key,
                rawNumber = rawNumber,
                labelOrReason = reason,
                blockedTimestampMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun unblockNumber(normalizedNumber: String) {
        dao.unblockNumber(normalizedNumber)
    }

    suspend fun addReminder(
        contactName: String,
        rawNumber: String,
        scheduledMillis: Long,
        reason: String
    ) {
        dao.insertReminder(
            CallReminderEntity(
                contactName = contactName,
                rawNumber = rawNumber,
                scheduledMillis = scheduledMillis,
                reason = reason
            )
        )
    }

    suspend fun completeReminder(id: Long) {
        dao.markReminderCompleted(id)
    }

    suspend fun deleteReminder(id: Long) {
        dao.deleteReminder(id)
    }
}
