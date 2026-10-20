package com.example.data.repository

import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.domain.CallRecord
import com.example.domain.CallType
import com.example.domain.PhoneNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface CallLogLoadResult {
    data class Success(val records: List<CallRecord>) : CallLogLoadResult
    data class PermissionDenied(val reason: String) : CallLogLoadResult
    data class Error(val message: String) : CallLogLoadResult
}

class CallLogRepository(private val context: Context) {

    suspend fun loadSystemCallHistory(): CallLogLoadResult = withContext(Dispatchers.IO) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALL_LOG
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            return@withContext CallLogLoadResult.PermissionDenied(
                "READ_CALL_LOG permission is required to view recent calls."
            )
        }

        try {
            val records = mutableListOf<CallRecord>()
            val uri = CallLog.Calls.CONTENT_URI
            val projection = arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.CACHED_PHOTO_URI,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.TYPE,
                CallLog.Calls.PHONE_ACCOUNT_ID
            )

            val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val queryArgs = Bundle().apply {
                    putInt(ContentResolver.QUERY_ARG_LIMIT, 500)
                    putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(CallLog.Calls.DATE))
                    putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                }
                context.contentResolver.query(uri, projection, queryArgs, null)
            } else {
                val pagedUri = uri.buildUpon()
                    .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, "500")
                    .build()
                context.contentResolver.query(pagedUri, projection, null, null, "${CallLog.Calls.DATE} DESC")
            }

            cursor?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CallLog.Calls._ID)
                val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val photoIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_PHOTO_URI)
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
                val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val simIdIdx = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)

                while (cursor.moveToNext() && records.size < 500) {
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                    val rawNumber = if (numIdx >= 0) cursor.getString(numIdx) ?: "" else ""
                    val rawName = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                    val name = rawName?.trim()?.takeIf { it.isNotBlank() && it != rawNumber && it != "null" && it != "-1" && it != "-2" }
                    val photo = if (photoIdx >= 0) cursor.getString(photoIdx) else null
                    val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                    val dur = if (durIdx >= 0) cursor.getLong(durIdx) else 0L
                    val typeRaw = if (typeIdx >= 0) cursor.getInt(typeIdx) else 1
                    val simId = if (simIdIdx >= 0) cursor.getString(simIdIdx) else null

                    val callType = when (typeRaw) {
                        CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
                        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                        CallLog.Calls.MISSED_TYPE -> CallType.MISSED
                        CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
                        CallLog.Calls.BLOCKED_TYPE -> CallType.BLOCKED
                        else -> CallType.INCOMING
                    }

                    val normalized = PhoneNormalizer.normalizeForComparison(rawNumber)
                    records.add(
                        CallRecord(
                            id = id,
                            callId = id.toString(),
                            rawNumber = rawNumber,
                            normalizedNumber = normalized,
                            displayName = name,
                            photoUri = photo,
                            timestampMillis = date,
                            durationSeconds = dur,
                            callType = callType,
                            simAccountName = null,
                            simAccountId = simId
                        )
                    )
                }
            }
            CallLogLoadResult.Success(records)
        } catch (e: Exception) {
            CallLogLoadResult.Error(e.message ?: "Failed to read call log")
        }
    }

    suspend fun deleteCallRecordsByIds(ids: List<Long>): Boolean = withContext(Dispatchers.IO) {
        val hasWritePermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_CALL_LOG
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasWritePermission || ids.isEmpty()) return@withContext false

        try {
            val selection = "${CallLog.Calls._ID} IN (${ids.joinToString(",")})"
            val count = context.contentResolver.delete(CallLog.Calls.CONTENT_URI, selection, null)
            count > 0
        } catch (e: Exception) {
            false
        }
    }

    suspend fun clearAllCallHistory(): Boolean = withContext(Dispatchers.IO) {
        val hasWritePermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.WRITE_CALL_LOG
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasWritePermission) return@withContext false

        try {
            context.contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null) >= 0
        } catch (e: Exception) {
            false
        }
    }
}
