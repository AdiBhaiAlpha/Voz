package com.example.telecom

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.room.Room
import com.example.data.local.VozDatabase
import com.example.domain.PhoneNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class VozCallScreeningService : CallScreeningService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var database: VozDatabase? = null

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            applicationContext, VozDatabase::class.java, "voz_app_database"
        ).build()
    }

    override fun onScreenCall(callDetails: Call.Details) {
        // Screening is never allowed to prevent outgoing (including emergency) calls.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            callDetails.callDirection != Call.Details.DIRECTION_INCOMING) return

        val normalized = PhoneNormalizer.normalizeForComparison(
            callDetails.handle?.schemeSpecificPart.orEmpty()
        )
        serviceScope.launch {
            // Android requires a response within 5 seconds. Fail open on timeout or DB error.
            val blocked = try {
                withTimeoutOrNull(2000) {
                    normalized.isNotEmpty() && database?.assistDao()?.isBlocked(normalized) == true
                } ?: false
            } catch (_: Exception) {
                false
            }
            respondToCall(callDetails, CallResponse.Builder()
                .setDisallowCall(blocked)
                .setRejectCall(blocked)
                .setSkipCallLog(false)
                .setSkipNotification(blocked)
                .build())
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        database?.close()
        database = null
        super.onDestroy()
    }
}
