package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores local-only VOZ Call Assist metadata associated with a normalized phone number.
 * Strictly separate from system contacts and call log records.
 */
@Entity(tableName = "contact_assist_metadata")
data class ContactAssistEntity(
    @PrimaryKey
    val normalizedNumber: String,
    val rawNumber: String,
    val displayNameHint: String,
    val note: String = "",
    val customLabel: String = "",
    val isFavorite: Boolean = false,
    val isImportant: Boolean = false,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

/**
 * Stores user-managed blocked phone numbers locally on the device.
 */
@Entity(tableName = "blocked_numbers")
data class BlockedNumberEntity(
    @PrimaryKey
    val normalizedNumber: String,
    val originalNumber: String,
    val reason: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)

/**
 * Stores user-scheduled call-back reminders in VOZ Call Assist.
 */
@Entity(tableName = "call_reminders")
data class CallReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val normalizedNumber: String,
    val rawNumber: String,
    val contactName: String,
    val reason: String,
    val dueAtMillis: Long,
    val isCompleted: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)
