package com.example.telecom

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.example.domain.PhoneNormalizer
import com.example.domain.SimSubscriptionItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Core CallManager to handle telephony states and integrate with Android Telecom InCallService.
 * Replaces all simulated/fake calling with real native Android Telephony integration.
 */
@Suppress("DEPRECATION")
object CallManager {

    private var inCallService: InCallService? = null
    private var currentTelephonyCall: Call? = null

    private val _activeCall = MutableStateFlow<VozCallUiModel?>(null)
    val activeCall: StateFlow<VozCallUiModel?> = _activeCall.asStateFlow()

    private val _audioState = MutableStateFlow(VozAudioState())
    val audioState: StateFlow<VozAudioState> = _audioState.asStateFlow()

    private var lastActionTime = 0L
    private const val DEBOUNCE_MS = 250L

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            val oldState = _activeCall.value?.state?.name ?: "UNKNOWN"
            TelecomDiagnosticLogger.logStateChanged(
                callId = call.details?.handle?.schemeSpecificPart ?: "",
                oldState = oldState,
                newState = state
            )
            updateFromCall(call)
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            updateFromCall(call)
        }
    }

    fun attachInCallService(service: InCallService) {
        inCallService = service
    }

    fun detachInCallService(service: InCallService) {
        if (inCallService === service) {
            inCallService = null
        }
    }

    fun onCallAdded(call: Call, enrichedName: String? = null, assistNote: String? = null) {
        currentTelephonyCall?.unregisterCallback(callCallback)
        currentTelephonyCall = call
        call.registerCallback(callCallback)

        val details = call.details
        val rawNumber = details?.handle?.schemeSpecificPart ?: ""
        val normalized = PhoneNormalizer.normalizeForComparison(rawNumber).ifBlank { rawNumber }
        val mappedState = mapState(call.state)
        val connectTime = details?.connectTimeMillis ?: 0L
        val displayName = enrichedName ?: details?.callerDisplayName?.takeIf { it.isNotBlank() }

        val isIncoming = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            details?.callDirection == Call.Details.DIRECTION_INCOMING
        } else {
            call.state == Call.STATE_RINGING
        }

        TelecomDiagnosticLogger.logCallAdded(call, isIncoming)

        _activeCall.value = VozCallUiModel(
            callId = rawNumber.ifBlank { System.currentTimeMillis().toString() },
            rawNumber = rawNumber,
            normalizedNumber = normalized,
            displayName = displayName,
            photoUri = null,
            state = mappedState,
            connectTimeMillis = connectTime,
            isIncoming = isIncoming,
            assistNote = assistNote
        )

        // Also synchronize with CallSessionController
        CallSessionController.registerCall(call, _activeCall.value!!)
    }

    fun onCallRemoved(call: Call) {
        val lastState = _activeCall.value?.state?.name ?: "DISCONNECTED"
        TelecomDiagnosticLogger.logCallRemoved(
            callId = call.details?.handle?.schemeSpecificPart ?: "",
            finalState = lastState
        )

        if (currentTelephonyCall == call) {
            try {
                call.unregisterCallback(callCallback)
            } catch (_: Exception) {}
            currentTelephonyCall = null
            _activeCall.value = null
        }
        CallSessionController.unregisterCall(call)
    }

    fun onAudioStateChanged(state: CallAudioState?) {
        if (state == null) return
        _audioState.value = VozAudioState(
            isMuted = state.isMuted,
            route = state.route,
            supportedRouteMask = state.supportedRouteMask
        )
        _activeCall.value = _activeCall.value?.copy(
            isMuted = state.isMuted,
            isSpeakerOn = (state.route == CallAudioState.ROUTE_SPEAKER)
        )
        CallSessionController.onAudioStateChanged(state)
    }

    private fun updateFromCall(call: Call) {
        val mappedState = mapState(call.state)
        val details = call.details
        val connectTime = details?.connectTimeMillis ?: 0L
        val canHold = ((details?.callCapabilities ?: 0) and Call.Details.CAPABILITY_HOLD) != 0

        val current = _activeCall.value ?: return
        val duration = if (connectTime > 0 && mappedState == CallLifecycleState.ACTIVE) {
            maxOf(0L, (System.currentTimeMillis() - connectTime) / 1000L)
        } else current.durationSeconds

        _activeCall.value = current.copy(
            state = mappedState,
            connectTimeMillis = if (connectTime > 0) connectTime else current.connectTimeMillis,
            durationSeconds = duration,
            isOnHold = (mappedState == CallLifecycleState.HOLDING),
            isHoldSupported = canHold
        )
    }

    fun isDefaultDialer(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = context.getSystemService(RoleManager::class.java)
                roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
            } else {
                val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                tm?.defaultDialerPackage == context.packageName
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Places a genuine telephone call to the destination number.
     * Guaranteed to dial the real network and never simulate.
     */
    fun placeRealCall(
        context: Context,
        rawNumber: String,
        explicitSimId: String? = null
    ): CallInitiationOutcome {
        val sanitized = PhoneNormalizer.sanitizeDialInput(rawNumber)
        if (!PhoneNormalizer.isValidDialableNumber(sanitized)) {
            return CallInitiationOutcome.Error("Please enter a valid phone number.")
        }

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCallPermission) {
            return CallInitiationOutcome.Error("CALL_PHONE permission is required to place carrier calls.")
        }

        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val pm = context.packageManager
        val hasTelephonyFeature = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

        val isVoiceCapable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            tm?.isVoiceCapable ?: hasTelephonyFeature
        } else {
            (tm?.phoneType ?: TelephonyManager.PHONE_TYPE_NONE) != TelephonyManager.PHONE_TYPE_NONE
        }

        // Detect if environment completely lacks cellular telephony radio
        if (!hasTelephonyFeature && !isVoiceCapable) {
            TelecomDiagnosticLogger.logFallback("Cellular telephony hardware missing in this environment")
            return CallInitiationOutcome.Error(
                "Unable to place a real carrier call in this environment. Install VOZ on a compatible Android phone with an active SIM and the required permissions."
            )
        }

        // Check SIM state
        val simState = tm?.simState ?: TelephonyManager.SIM_STATE_UNKNOWN
        if (simState == TelephonyManager.SIM_STATE_ABSENT) {
            TelecomDiagnosticLogger.logFallback("SIM state is ABSENT")
            return CallInitiationOutcome.Error(
                "Unable to place a real carrier call in this environment. Install VOZ on a compatible Android phone with an active SIM and the required permissions."
            )
        }

        val hasReadPhoneState = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        // Check if device has multiple SIMs active and no SIM was explicitly picked
        if (hasReadPhoneState && explicitSimId.isNullOrBlank()) {
            try {
                val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val subList = subManager?.activeSubscriptionInfoList
                if (!subList.isNullOrEmpty() && subList.size > 1) {
                    val simItems = subList.map { info ->
                        SimSubscriptionItem(
                            subscriptionId = info.subscriptionId,
                            displayName = info.displayName?.toString() ?: "SIM ${info.simSlotIndex + 1}",
                            carrierName = info.carrierName?.toString() ?: "Carrier",
                            slotIndex = info.simSlotIndex,
                            iccId = info.iccId ?: "",
                            color = info.iconTint
                        )
                    }
                    return CallInitiationOutcome.RequiresSimSelection(sanitized, simItems)
                }
            } catch (_: Exception) {}
        }

        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            ?: return CallInitiationOutcome.Error("Telephony service unavailable on this device.")

        val telUri = Uri.fromParts("tel", sanitized, null)
        val defaultDialerHeld = isDefaultDialer(context)

        TelecomDiagnosticLogger.logEnvironment(context, defaultDialerHeld)
        TelecomDiagnosticLogger.logOutgoingCallAttempt(sanitized, defaultDialerHeld, "TelecomManager.placeCall")

        try {
            val extras = Bundle()
            if (!explicitSimId.isNullOrBlank()) {
                val phoneAccounts = telecomManager.callCapablePhoneAccounts
                val matchedHandle: PhoneAccountHandle? = phoneAccounts.firstOrNull { it.id == explicitSimId }
                if (matchedHandle != null) {
                    extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, matchedHandle)
                }
            }
            telecomManager.placeCall(telUri, extras)
            return CallInitiationOutcome.PlacedDirectly
        } catch (e: SecurityException) {
            TelecomDiagnosticLogger.logFallback("placeCall SecurityException -> fallback to ACTION_CALL: ${e.message}")
            try {
                val callIntent = Intent(Intent.ACTION_CALL, telUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(callIntent)
                return CallInitiationOutcome.PlacedDirectly
            } catch (e2: Exception) {
                return CallInitiationOutcome.Error("Permission denied for carrier phone call: ${e2.message}")
            }
        } catch (e: Exception) {
            TelecomDiagnosticLogger.logFallback("placeCall error -> fallback to ACTION_CALL: ${e.message}")
            try {
                val callIntent = Intent(Intent.ACTION_CALL, telUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(callIntent)
                return CallInitiationOutcome.PlacedDirectly
            } catch (_: Exception) {
                return CallInitiationOutcome.Error("Could not place carrier call: ${e.message}")
            }
        }
    }

    fun answerCall() {
        if (!isActionAllowed()) return
        val call = currentTelephonyCall ?: return
        try {
            call.answer(0)
        } catch (_: Exception) {}
    }

    fun declineOrHangup() {
        if (!isActionAllowed()) return
        val call = currentTelephonyCall ?: return
        try {
            if (call.state == Call.STATE_RINGING) {
                call.reject(false, null)
            } else {
                call.disconnect()
            }
        } catch (_: Exception) {}
    }

    fun toggleMute() {
        if (!isActionAllowed()) return
        val service = inCallService ?: return
        val current = _audioState.value.isMuted
        service.setMuted(!current)
    }

    fun toggleSpeaker() {
        if (!isActionAllowed()) return
        val service = inCallService ?: return
        val current = _audioState.value.route
        val target = if (current == CallAudioState.ROUTE_SPEAKER) {
            CallAudioState.ROUTE_EARPIECE
        } else {
            CallAudioState.ROUTE_SPEAKER
        }
        service.setAudioRoute(target)
    }

    fun toggleHold() {
        if (!isActionAllowed()) return
        val call = currentTelephonyCall ?: return
        try {
            if (call.state == Call.STATE_HOLDING) {
                call.unhold()
            } else {
                call.hold()
            }
        } catch (_: Exception) {}
    }

    fun playDtmf(digit: Char) {
        val call = currentTelephonyCall ?: return
        try {
            call.playDtmfTone(digit)
        } catch (_: Exception) {}
    }

    fun stopDtmf() {
        val call = currentTelephonyCall ?: return
        try {
            call.stopDtmfTone()
        } catch (_: Exception) {}
    }

    private fun isActionAllowed(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastActionTime < DEBOUNCE_MS) return false
        lastActionTime = now
        return true
    }

    private fun mapState(telephonyState: Int): CallLifecycleState {
        return when (telephonyState) {
            Call.STATE_RINGING -> CallLifecycleState.RINGING
            Call.STATE_DIALING, Call.STATE_CONNECTING -> CallLifecycleState.DIALING
            Call.STATE_ACTIVE -> CallLifecycleState.ACTIVE
            Call.STATE_HOLDING -> CallLifecycleState.HOLDING
            Call.STATE_DISCONNECTING -> CallLifecycleState.DISCONNECTING
            Call.STATE_DISCONNECTED -> CallLifecycleState.DISCONNECTED
            else -> CallLifecycleState.DIALING
        }
    }
}
