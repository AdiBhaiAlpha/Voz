package com.example.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.example.R
import com.example.data.local.VozDatabase
import com.example.data.preferences.UserPreferencesRepository
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
    private val notificationCallbacks = java.util.concurrent.ConcurrentHashMap<Call, Call.Callback>()
    private var proximityRouter: ProximityAudioRouter? = null
    private var preferencesRepository: UserPreferencesRepository? = null

    companion object {
        private const val TAG = "VozInCallService"
        const val CHANNEL_INCOMING_ID = "voz_incoming_calls_channel"
        const val CHANNEL_ONGOING_ID = "voz_ongoing_calls_channel"
        const val NOTIFICATION_ID = 4001
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "VozInCallService created and attached")
        CallManager.attachInCallService(this)
        CallSessionController.attachService(this)
        ensureNotificationChannels()
        proximityRouter = ProximityAudioRouter(this).also { it.start() }

        preferencesRepository = UserPreferencesRepository(applicationContext)
        database = Room.databaseBuilder(
            applicationContext,
            VozDatabase::class.java,
            "voz_app_database"
        ).build()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        if (com.example.online.OnlineCalls.isOnlineActive()) com.example.online.OnlineCalls.endFromUi()
        val details = call.details
        val rawNumber = CallManager.extractCallNumber(details)
        val normalized = PhoneNormalizer.normalizeForComparison(rawNumber).ifBlank { rawNumber }
        val state = CallSessionController.mapSystemState(call)
        val rawCallerDisplay = details?.callerDisplayName?.takeIf { it.isNotBlank() }
        val callerDisplay = rawCallerDisplay?.trim()?.takeIf { it.isNotBlank() && it != rawNumber && it != "null" }
        val connectTime = details?.connectTimeMillis ?: 0L

        // Real incoming call detection from Android Telecom state and direction
        val isIncoming = (call.state == Call.STATE_RINGING) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && details?.callDirection == Call.Details.DIRECTION_INCOMING)

        // Canonical ID shared between CallManager and CallSessionController
        val callId = UUID.randomUUID().toString()

        TelecomDiagnosticLogger.logCallAdded(call, isIncoming)

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

        // Register with canonical callId
        CallManager.onCallAdded(call, callerDisplay, null, callId)
        CallSessionController.registerCall(call, initialModel)

        // Enrich with contact resolution and local metadata in background
        serviceScope.launch {
            try {
                var resolvedName = callerDisplay
                if (resolvedName.isNullOrBlank() && normalized.isNotBlank()) {
                    try {
                        val lookupUri = Uri.withAppendedPath(
                            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                            Uri.encode(rawNumber.ifBlank { normalized })
                        )
                        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
                        applicationContext.contentResolver.query(lookupUri, projection, null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val nameIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                                if (nameIdx >= 0) {
                                    resolvedName = cursor.getString(nameIdx)?.trim()?.takeIf { it.isNotBlank() && it != rawNumber && it != "null" }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "PhoneLookup not available or permission denied", e)
                    }
                }

                val assist = database?.assistDao()?.getAssistByKey(normalized)
                CallSessionController.updateEnrichedMetadata(
                    callId = callId,
                    displayName = resolvedName,
                    photoUri = null,
                    assistNote = assist?.note,
                    assistLabel = assist?.customLabel,
                    isImportant = assist?.isImportant ?: false
                )
            } catch (e: Exception) {
                Log.w(TAG, "Error enriching caller metadata", e)
            }
        }

        val callerTitle = initialModel.displayTitle
        updateCallNotification(callId, callerTitle, isIncoming, isRinging = (call.state == Call.STATE_RINGING))

        // Listen for call state transitions on this call
        val notificationCallback = object : Call.Callback() {
            override fun onStateChanged(targetCall: Call, newState: Int) {
                val isRingingNow = (newState == Call.STATE_RINGING)
                val isIncomingNow = (newState == Call.STATE_RINGING) ||
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && targetCall.details?.callDirection == Call.Details.DIRECTION_INCOMING) ||
                        isIncoming
                updateCallNotification(callId, callerTitle, isIncomingNow, isRinging = isRingingNow)

                // When call transitions to active, ensure overlay is running if enabled
                if (newState == Call.STATE_ACTIVE) {
                    checkAndLaunchOverlay()
                }
            }

            override fun onDetailsChanged(targetCall: Call, newDetails: Call.Details) {
                val updatedDisplay = newDetails.callerDisplayName?.takeIf { it.isNotBlank() } ?: callerTitle
                val isRingingNow = (targetCall.state == Call.STATE_RINGING)
                val isIncomingNow = (targetCall.state == Call.STATE_RINGING) ||
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && newDetails.callDirection == Call.Details.DIRECTION_INCOMING) ||
                        isIncoming
                updateCallNotification(callId, updatedDisplay, isIncomingNow, isRinging = isRingingNow)
            }
        }
        notificationCallbacks[call] = notificationCallback
        call.registerCallback(notificationCallback)

        // Check if floating overlay is permitted and enabled (compact banner over other apps)
        checkAndLaunchOverlay()

        // An unlocked user may be working in another app: never launch over it.
        if (CallPresentationPolicy.shouldUseFullScreen(this)) {
            val intent = Intent(this, InCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            try { startActivity(intent) } catch (_: Exception) {
                // Eligible full-screen notification remains the platform fallback.
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        notificationCallbacks.remove(call)?.let { call.unregisterCallback(it) }
        TelecomDiagnosticLogger.logCallRemoved("redacted", CallSessionController.mapSystemState(call).name)
        CallManager.onCallRemoved(call)
        CallSessionController.unregisterCall(call)

        if (CallSessionController.activeCalls.value.isEmpty()) {
            cancelCallNotification()
            VozCallOverlayService.stop(this)
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        CallManager.onAudioStateChanged(audioState)
        CallSessionController.onAudioStateChanged(audioState)
    }

    override fun onDestroy() {
        notificationCallbacks.forEach { (call, callback) -> call.unregisterCallback(callback) }
        notificationCallbacks.clear()
        CallAudioRecorder.stopRecording()
        CallManager.detachInCallService(this)
        CallSessionController.detachService(this)
        cancelCallNotification()
        VozCallOverlayService.stop(this)
        database?.close()
        proximityRouter?.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun checkAndLaunchOverlay() {
        val prefs = preferencesRepository?.loadPreferences()
        if (!CallPresentationPolicy.fullScreenVisible && !CallPresentationPolicy.mainVisible && prefs?.floatingIslandOverlayEnabled == true && Settings.canDrawOverlays(this)) {
            VozCallOverlayService.startIfPermitted(this)
        }
    }

    private fun ensureNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // 1. High-priority incoming call channel with ringtone and vibration
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()

            val incomingChannel = NotificationChannel(
                CHANNEL_INCOMING_ID,
                "VOZ Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for incoming phone calls with ringtone and heads-up banner"
                setSound(ringtoneUri, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 1000)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            nm.createNotificationChannel(incomingChannel)

            // 2. Ongoing call channel (low importance, silent while user is on call)
            val ongoingChannel = NotificationChannel(
                CHANNEL_ONGOING_ID,
                "VOZ Ongoing Calls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows status of connected ongoing calls"
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(ongoingChannel)
        }
    }

    private fun updateCallNotification(
        callId: String,
        callerTitle: String,
        isIncoming: Boolean,
        isRinging: Boolean
    ) {
        val channelId = if (isRinging) CHANNEL_INCOMING_ID else CHANNEL_ONGOING_ID

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

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_call_answer)
            .setContentTitle(callerTitle)
            .setContentText(
                if (isRinging) getString(R.string.incoming_call_label)
                else getString(R.string.ongoing_call_label)
            )
            .setPriority(if (isRinging) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(fullScreenPending)

        if (isRinging) {
            // Heads-up notification trigger when user is inside other apps or device is locked
            if (CallPresentationPolicy.shouldUseFullScreen(this)) {
                builder.setFullScreenIntent(fullScreenPending, true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // CallStyle handles the Answer and Decline action buttons natively
                builder.setStyle(
                    NotificationCompat.CallStyle.forIncomingCall(callerPerson, declinePending, answerPending)
                )
            } else {
                builder.addAction(
                    R.drawable.ic_call_decline,
                    getString(R.string.action_decline),
                    declinePending
                )
                builder.addAction(
                    R.drawable.ic_call_answer,
                    getString(R.string.action_answer),
                    answerPending
                )
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setStyle(
                    NotificationCompat.CallStyle.forOngoingCall(callerPerson, declinePending)
                )
            } else {
                builder.addAction(
                    R.drawable.ic_call_decline,
                    getString(R.string.action_hangup),
                    declinePending
                )
            }
        }

        val notification = builder.build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.i(TAG, "Call notification promoted to foreground: isRinging=$isRinging, channel=$channelId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to startForeground on notification", e)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun cancelCallNotification() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {}
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.cancel(NOTIFICATION_ID)
        Log.i(TAG, "Call notification cancelled")
    }
}
