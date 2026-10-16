package com.example.telecom

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.room.Room
import com.example.R
import com.example.data.local.VozDatabase
import com.example.domain.PhoneNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

@Suppress("DEPRECATION")
class VozInCallService : InCallService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var database: VozDatabase? = null

    companion object {
        private const val CHANNEL_ID = "voz_calls_channel"
        private const val NOTIFICATION_ID = 4001
    }

    override fun onCreate() {
        super.onCreate()
        CallManager.attachInCallService(this)
        CallSessionController.attachService(this)
        ensureNotificationChannel()
        database = Room.databaseBuilder(
            applicationContext,
            VozDatabase::class.java,
            "voz_app_database"
        ).build()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val callId = UUID.randomUUID().toString()
        val details = call.details
        val rawNumber = details?.handle?.schemeSpecificPart ?: ""
        val normalized = PhoneNormalizer.normalizeForComparison(rawNumber).ifBlank { rawNumber }
        val state = CallSessionController.mapSystemState(call)
        val callerDisplay = details?.callerDisplayName?.takeIf { it.isNotBlank() }
        val connectTime = details?.connectTimeMillis ?: 0L

        val isIncoming = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            details?.callDirection == Call.Details.DIRECTION_INCOMING
        } else {
            state == CallLifecycleState.RINGING
        }

        val initialModel = VozCallUiModel(
            callId = callId,
            rawNumber = rawNumber,
            normalizedNumber = normalized,
            displayName = callerDisplay,
            photoUri = null,
            state = state,
            connectTimeMillis = connectTime,
            isIncoming = isIncoming
        )

        CallManager.onCallAdded(call, callerDisplay, null)
        CallSessionController.registerCall(call, initialModel)

        // Enrich with local metadata in background
        serviceScope.launch {
            try {
                val assist = database?.assistDao()?.getAssistByKey(normalized)
                CallSessionController.updateEnrichedMetadata(
                    callId = callId,
                    displayName = callerDisplay,
                    photoUri = null,
                    assistNote = assist?.note,
                    assistLabel = assist?.customLabel,
                    isImportant = assist?.isImportant ?: false
                )
            } catch (_: Exception) {}
        }

        val callerTitle = callerDisplay ?: PhoneNormalizer.formatForDisplay(rawNumber).ifBlank { "Unknown Caller" }
        showCallNotification(callId, callerTitle, state == CallLifecycleState.RINGING)

        // Launch in-call UI activity
        val intent = Intent(this, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {}
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.onCallRemoved(call)
        CallSessionController.unregisterCall(call)
        if (CallSessionController.activeCalls.value.isEmpty()) {
            cancelCallNotification()
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        CallManager.onAudioStateChanged(audioState)
        CallSessionController.onAudioStateChanged(audioState)
    }

    override fun onDestroy() {
        CallManager.detachInCallService(this)
        CallSessionController.detachService(this)
        cancelCallNotification()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VOZ Active & Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Displays incoming and ongoing telephone calls"
                setSound(null, null) // Avoid double ring when system dialer manages ringtone
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun showCallNotification(callId: String, callerTitle: String, isIncoming: Boolean) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val fullScreenIntent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val fullScreenPending = PendingIntent.getActivity(
            this,
            100,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_DECLINE_OR_HANGUP
            putExtra(CallActionReceiver.EXTRA_CALL_ID, callId)
        }
        val declinePending = PendingIntent.getBroadcast(
            this,
            101,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val answerIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_ANSWER
            putExtra(CallActionReceiver.EXTRA_CALL_ID, callId)
        }
        val answerPending = PendingIntent.getBroadcast(
            this,
            102,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val callerPerson = Person.Builder()
            .setName(callerTitle)
            .setImportant(true)
            .build()

        val style = if (isIncoming) {
            NotificationCompat.CallStyle.forIncomingCall(callerPerson, declinePending, answerPending)
        } else {
            NotificationCompat.CallStyle.forOngoingCall(callerPerson, declinePending)
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(callerTitle)
            .setContentText(getString(if (isIncoming) R.string.incoming_call_label else R.string.ongoing_call_label))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPending, true)
            .setContentIntent(fullScreenPending)
            .setStyle(style)
            .build()

        try {
            nm.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }

    private fun cancelCallNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.cancel(NOTIFICATION_ID)
    }
}
