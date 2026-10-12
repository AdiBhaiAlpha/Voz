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
import com.example.R
import com.example.data.local.VozDatabase
import com.example.data.repository.ContactsLoadResult
import com.example.data.repository.ContactsRepository
import com.example.data.repository.VozAssistRepository
import com.example.domain.PhoneNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Real Android Telecom InCallService implementation.
 * Bound by the Android OS when VOZ holds RoleManager.ROLE_DIALER and a call is placed or received.
 */
class VozInCallService : InCallService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        CallSessionController.attachService(this)
        ensureNotificationChannel()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val callId = UUID.randomUUID().toString()
        val rawNumber = call.details?.handle?.schemeSpecificPart.orEmpty()
        val normalized = PhoneNormalizer.normalizeForComparison(rawNumber).ifBlank { rawNumber }
        val state = CallSessionController.mapSystemState(call)
        val callerDisplay = call.details?.callerDisplayName?.takeIf { it.isNotBlank() }

        val initialModel = VozCallUiModel(
            callId = callId,
            rawNumber = rawNumber,
            normalizedNumber = normalized,
            displayName = callerDisplay,
            state = state,
            connectTimeMillis = call.details?.connectTimeMillis ?: 0L,
            canHold = ((call.details?.callCapabilities ?: 0) and Call.Details.CAPABILITY_HOLD) != 0
        )

        serviceScope.launch {
            val db = VozDatabase.getInstance(applicationContext)
            val assistRepo = VozAssistRepository(db.vozAssistDao())

            // Enforce user-managed blocklist on incoming calls
            if (state == CallLifecycleState.RINGING && rawNumber.isNotBlank() && assistRepo.isBlocked(rawNumber)) {
                call.reject(false, null)
                return@launch
            }

            CallSessionController.registerCall(call, initialModel)

            // Enrich with authorized local contact & VOZ Call Assist note
            val contactsRepo = ContactsRepository(applicationContext)
            var resolvedName = callerDisplay
            var resolvedPhoto: String? = null
            var isKnown = false

            if (rawNumber.isNotBlank()) {
                when (val result = contactsRepo.loadAuthorizedContacts()) {
                    is ContactsLoadResult.Success -> {
                        val matched = result.contacts.firstOrNull { contact ->
                            contact.numbers.any { num ->
                                num.normalizedNumber == normalized ||
                                    PhoneNormalizer.areNumbersEquivalent(num.rawNumber, rawNumber)
                            }
                        }
                        if (matched != null) {
                            resolvedName = matched.displayName
                            resolvedPhoto = matched.photoUri
                            isKnown = true
                        }
                    }
                    else -> Unit
                }
            }

            val assistEntity = if (normalized.isNotBlank()) {
                db.vozAssistDao().getAssistForNumber(normalized)
            } else {
                null
            }

            CallSessionController.updateEnrichedMetadata(
                callId = callId,
                displayName = resolvedName ?: assistEntity?.displayNameHint?.takeIf { it.isNotBlank() },
                photoUri = resolvedPhoto,
                assistNote = assistEntity?.note?.takeIf { it.isNotBlank() },
                assistLabel = assistEntity?.customLabel?.takeIf { it.isNotBlank() },
                isKnownContact = isKnown
            )

            val title = resolvedName ?: rawNumber.ifBlank { getString(R.string.unknown_caller) }
            showCallNotification(callId, title, state == CallLifecycleState.RINGING)
        }

        // Launch InCallActivity for active or ringing call
        val activityIntent = Intent(this, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        runCatching { startActivity(activityIntent) }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallSessionController.unregisterCall(call)
        if (CallSessionController.activeCalls.value.isEmpty()) {
            cancelCallNotification()
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        CallSessionController.onAudioStateChanged(audioState)
    }

    override fun onDestroy() {
        CallSessionController.detachService(this)
        cancelCallNotification()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "VOZ Active & Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Displays incoming and ongoing telephone calls"
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun showCallNotification(callId: String, callerTitle: String, isIncoming: Boolean) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
            .setContentText(
                if (isIncoming) getString(R.string.incoming_call_label) else getString(R.string.ongoing_call_label)
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPending, true)
            .setContentIntent(fullScreenPending)
            .setStyle(style)
            .build()

        runCatching {
            nm.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun cancelCallNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
    }

    companion object {
        private const val CHANNEL_ID = "voz_calls_channel"
        private const val NOTIFICATION_ID = 4001
    }
}
