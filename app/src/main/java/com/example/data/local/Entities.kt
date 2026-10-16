package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contact_assist")
data class ContactAssistEntity(
    @PrimaryKey
    val phoneKey: String, // Normalized phone or lookupKey
    val note: String = "",
    val customLabel: String = "",
    val isFavorite: Boolean = false,
    val isImportant: Boolean = false,
    val lastModifiedMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "blocked_numbers")
data class BlockedNumberEntity(
    @PrimaryKey
    val normalizedNumber: String,
    val rawNumber: String,
    val labelOrReason: String = "",
    val blockedTimestampMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "call_reminders")
data class CallReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contactName: String,
    val rawNumber: String,
    val scheduledMillis: Long,
    val reason: String,
    val isCompleted: Boolean = false
)
