package com.example.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface VozAssistDao {

    @Query("SELECT * FROM contact_assist")
    fun getAllAssist(): Flow<List<ContactAssistEntity>>

    @Query("SELECT * FROM contact_assist WHERE phoneKey = :phoneKey LIMIT 1")
    suspend fun getAssistByKey(phoneKey: String): ContactAssistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAssist(entity: ContactAssistEntity)

    @Query("DELETE FROM contact_assist WHERE phoneKey = :phoneKey")
    suspend fun deleteAssist(phoneKey: String)

    @Query("SELECT * FROM blocked_numbers ORDER BY blockedTimestampMillis DESC")
    fun getAllBlocked(): Flow<List<BlockedNumberEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_numbers WHERE normalizedNumber = :normalizedNumber)")
    suspend fun isBlocked(normalizedNumber: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun blockNumber(entity: BlockedNumberEntity)

    @Query("DELETE FROM blocked_numbers WHERE normalizedNumber = :normalizedNumber")
    suspend fun unblockNumber(normalizedNumber: String)

    @Query("SELECT * FROM call_reminders WHERE isCompleted = 0 ORDER BY scheduledMillis ASC")
    fun getPendingReminders(): Flow<List<CallReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: CallReminderEntity)

    @Query("UPDATE call_reminders SET isCompleted = 1 WHERE id = :id")
    suspend fun markReminderCompleted(id: Long)

    @Query("DELETE FROM call_reminders WHERE id = :id")
    suspend fun deleteReminder(id: Long)
}

@Database(
    entities = [
        ContactAssistEntity::class,
        BlockedNumberEntity::class,
        CallReminderEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class VozDatabase : RoomDatabase() {
    abstract fun assistDao(): VozAssistDao
}
