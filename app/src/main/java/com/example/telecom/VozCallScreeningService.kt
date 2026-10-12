package com.example.telecom

import android.telecom.Call
import android.telecom.CallScreeningService
import com.example.data.local.VozDatabase
import com.example.data.repository.VozAssistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android CallScreeningService that enforces the user's local blocklist when VOZ is granted
 * ROLE_CALL_SCREENING or ROLE_DIALER.
 * Never transmits caller numbers over the network; matches strictly against local Room database.
 */
class VozCallScreeningService : CallScreeningService() {

    private val screeningScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        val rawNumber = callDetails.handle?.schemeSpecificPart.orEmpty()
        if (rawNumber.isBlank()) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        screeningScope.launch {
            val dao = VozDatabase.getInstance(applicationContext).vozAssistDao()
            val repo = VozAssistRepository(dao)
            val isBlocked = repo.isBlocked(rawNumber)

            val response = if (isBlocked) {
                CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipCallLog(false)
                    .setSkipNotification(true)
                    .build()
            } else {
                CallResponse.Builder()
                    .setDisallowCall(false)
                    .setRejectCall(false)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build()
            }
            respondToCall(callDetails, response)
        }
    }

    override fun onDestroy() {
        screeningScope.cancel()
        super.onDestroy()
    }
}
