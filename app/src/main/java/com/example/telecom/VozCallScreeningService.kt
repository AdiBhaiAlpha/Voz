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
import kotlinx.coroutines.launch

class VozCallScreeningService : CallScreeningService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var database: VozDatabase? = null

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            applicationContext,
            VozDatabase::class.java,
            "voz_app_database"
        ).build()
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val rawNumber = callDetails.handle?.schemeSpecificPart ?: ""
        val normalized = PhoneNormalizer.normalizeForComparison(rawNumber)

        serviceScope.launch {
            val isBlocked = database?.assistDao()?.isBlocked(normalized) ?: false
            if (isBlocked) {
                val response = CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipCallLog(false)
                    .setSkipNotification(true)
                    .build()
                respondToCall(callDetails, response)
            } else {
                val response = CallResponse.Builder()
                    .setDisallowCall(false)
                    .setRejectCall(false)
                    .setSkipCallLog(false)
                    .setSkipNotification(false)
                    .build()
                respondToCall(callDetails, response)
            }
        }
    }
}
