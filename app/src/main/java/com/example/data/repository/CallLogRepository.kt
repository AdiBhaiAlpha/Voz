package com.example.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.example.domain.CallRecord
import com.example.domain.CallType
import com.example.domain.PhoneNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class CallLogLoadResult {
    data class Success(val records: List<CallRecord>) : CallLogLoadResult()
    data object PermissionDenied : CallLogLoadResult()
    data class Error(val message: String) : CallLogLoadResult()
}

/**
 * Reads and manages real system call history via Android's CallLog.Calls provider on Dispatchers.IO.
 * Never fabricates call records, timestamps, or durations.
 */
class CallLogRepository(private val context: Context) {

    fun hasReadCallLogPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasWriteCallLogPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun loadSystemCallHistory(limit: Int = 300): CallLogLoadResult = withContext(Dispatchers.IO) {
        if (!hasReadCallLogPermission()) {
            return@withContext CallLogLoadResult.PermissionDenied
        }

        try {
            val projection = arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.CACHED_PHOTO_URI,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.PHONE_ACCOUNT_ID
            )

            val sortOrder = "${CallLog.Calls.DATE} DESC"
            val records = mutableListOf<CallRecord>()

            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(CallLog.Calls._ID)
                val numberIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val nameIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
                val photoIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_PHOTO_URI)
                val typeIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                val dateIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
                val durationIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                val accountIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.PHONE_ACCOUNT_ID)

                var count = 0
                while (cursor.moveToNext() && count < limit) {
                    val id = cursor.getLong(idIdx)
                    val rawNumber = cursor.getString(numberIdx)?.trim().orEmpty()
                    val cachedName = cursor.getString(nameIdx)?.trim()?.takeIf { it.isNotEmpty() }
                    val photoUri = cursor.getString(photoIdx)?.takeIf { it.isNotEmpty() }
                    val rawType = cursor.getInt(typeIdx)
                    val dateMillis = cursor.getLong(dateIdx)
                    val durationSec = cursor.getLong(durationIdx).coerceAtLeast(0L)
                    val accountId = cursor.getString(accountIdx)?.takeIf { it.isNotBlank() }

                    val callType = when (rawType) {
                        CallLog.Calls.INCOMING_TYPE,
                        CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> CallType.INCOMING
                        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                        CallLog.Calls.MISSED_TYPE -> CallType.MISSED
                        CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
                        CallLog.Calls.BLOCKED_TYPE -> CallType.BLOCKED
                        else -> CallType.UNKNOWN
                    }

                    val normalized = PhoneNormalizer.normalizeForComparison(rawNumber)
                        .ifBlank { rawNumber }

                    records.add(
                        CallRecord(
                            id = id,
                            rawNumber = rawNumber,
                            normalizedNumber = normalized,
                            cachedName = cachedName,
                            photoUri = photoUri,
                            callType = callType,
                            timestampMillis = dateMillis,
                            durationSeconds = durationSec,
                            simSlotLabel = accountId?.take(12)
                        )
                    )
                    count++
                }
            }

            CallLogLoadResult.Success(records)
        } catch (se: SecurityException) {
            CallLogLoadResult.PermissionDenied
        } catch (e: Exception) {
            CallLogLoadResult.Error("Unable to read system call history.")
        }
    }

    suspend fun deleteCallRecordsByIds(ids: List<Long>): Boolean = withContext(Dispatchers.IO) {
        if (ids.isEmpty() || !hasWriteCallLogPermission()) return@withContext false
        try {
            val placeholders = ids.joinToString(",") { "?" }
            val args = ids.map { it.toString() }.toTypedArray()
            val deleted = context.contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls._ID} IN ($placeholders)",
                args
            )
            deleted > 0
        } catch (e: Exception) {
            false
        }
    }

    suspend fun clearAllCallHistory(): Boolean = withContext(Dispatchers.IO) {
        if (!hasWriteCallLogPermission()) return@withContext false
        try {
            context.contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null)
            true
        } catch (e: Exception) {
            false
        }
    }
}
