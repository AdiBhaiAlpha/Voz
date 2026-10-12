package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VozAssistDao {

    // --- Contact Assist Metadata ---
    @Query("SELECT * FROM contact_assist_metadata ORDER BY isImportant DESC, isFavorite DESC, updatedAtMillis DESC")
    fun observeAllAssistMetadata(): Flow<List<ContactAssistEntity>>

    @Query("SELECT * FROM contact_assist_metadata WHERE normalizedNumber = :normalizedNumber LIMIT 1")
    suspend fun getAssistForNumber(normalizedNumber: String): ContactAssistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAssistMetadata(entity: ContactAssistEntity)

    @Query("DELETE FROM contact_assist_metadata WHERE normalizedNumber = :normalizedNumber")
    suspend fun deleteAssistMetadata(normalizedNumber: String)

    // --- Blocked Numbers ---
    @Query("SELECT * FROM blocked_numbers ORDER BY createdAtMillis DESC")
    fun observeBlockedNumbers(): Flow<List<BlockedNumberEntity>>

    @Query("SELECT * FROM blocked_numbers")
    suspend fun getAllBlockedNumbersSnapshot(): List<BlockedNumberEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_numbers WHERE normalizedNumber = :normalizedNumber)")
    suspend fun isNumberBlocked(normalizedNumber: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedNumber(entity: BlockedNumberEntity)

    @Query("DELETE FROM blocked_numbers WHERE normalizedNumber = :normalizedNumber")
    suspend fun unblockNumber(normalizedNumber: String)

    // --- Call Reminders ---
    @Query("SELECT * FROM call_reminders WHERE isCompleted = 0 ORDER BY dueAtMillis ASC")
    fun observeActiveReminders(): Flow<List<CallReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(entity: CallReminderEntity): Long

    @Query("UPDATE call_reminders SET isCompleted = 1 WHERE id = :reminderId")
    suspend fun markReminderCompleted(reminderId: Long)

    @Query("DELETE FROM call_reminders WHERE id = :reminderId")
    suspend fun deleteReminder(reminderId: Long)
}
