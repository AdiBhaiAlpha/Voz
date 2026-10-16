package com.example.telecom

import android.os.SystemClock
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

@Suppress("DEPRECATION")
object CallSessionController {

    private const val ACTION_DEBOUNCE_MS = 300L
    private var lastActionElapsedRealtime = 0L

    private val callMap = ConcurrentHashMap<String, Call>()
    private val callCallbacks = ConcurrentHashMap<String, Call.Callback>()
    private var inCallServiceRef: InCallService? = null

    private val _activeCalls = MutableStateFlow<List<VozCallUiModel>>(emptyList())
    val activeCalls: StateFlow<List<VozCallUiModel>> = _activeCalls.asStateFlow()

    private val _audioState = MutableStateFlow(VozAudioState())
    val audioState: StateFlow<VozAudioState> = _audioState.asStateFlow()

    fun attachService(service: InCallService) {
        inCallServiceRef = service
    }

    fun detachService(service: InCallService) {
        if (inCallServiceRef === service) {
            inCallServiceRef = null
        }
    }

    fun registerCall(call: Call, initialModel: VozCallUiModel) {
        val id = initialModel.callId
        callMap[id] = call

        val callback = object : Call.Callback() {
            override fun onStateChanged(targetCall: Call, state: Int) {
                updateCallFromSystem(id, targetCall)
            }

            override fun onDetailsChanged(targetCall: Call, details: Call.Details) {
                updateCallFromSystem(id, targetCall)
            }
        }
        callCallbacks[id] = callback
        call.registerCallback(callback)

        val currentList = _activeCalls.value.filter { it.callId != id }
        _activeCalls.value = currentList + initialModel
    }

    fun unregisterCall(call: Call) {
        val entry = callMap.entries.firstOrNull { it.value == call } ?: return
        val id = entry.key
        callMap.remove(id)
        callCallbacks.remove(id)?.let { cb ->
            try {
                call.unregisterCallback(cb)
            } catch (_: Exception) {}
        }
        _activeCalls.value = _activeCalls.value.filter { it.callId != id }
    }

    fun updateEnrichedMetadata(
        callId: String,
        displayName: String?,
        photoUri: String?,
        assistNote: String?,
        assistLabel: String?,
        isImportant: Boolean
    ) {
        _activeCalls.value = _activeCalls.value.map { model ->
            if (model.callId == callId) {
                model.copy(
                    displayName = displayName ?: model.displayName,
                    photoUri = photoUri ?: model.photoUri,
                    assistNote = assistNote ?: model.assistNote,
                    assistLabel = assistLabel ?: model.assistLabel,
                    isImportant = isImportant || model.isImportant
                )
            } else model
        }
    }

    fun onAudioStateChanged(audioState: CallAudioState?) {
        if (audioState == null) return
        _audioState.value = VozAudioState(
            isMuted = audioState.isMuted,
            route = audioState.route,
            supportedRouteMask = audioState.supportedRouteMask
        )
        // Update isMuted and isSpeakerOn in active models
        val isSpeaker = (audioState.route == CallAudioState.ROUTE_SPEAKER)
        _activeCalls.value = _activeCalls.value.map { model ->
            model.copy(isMuted = audioState.isMuted, isSpeakerOn = isSpeaker)
        }
    }

    private fun updateCallFromSystem(callId: String, call: Call) {
        val mappedState = mapSystemState(call)
        val details = call.details
        val connectTime = details?.connectTimeMillis ?: 0L
        val canHold = ((details?.callCapabilities ?: 0) and Call.Details.CAPABILITY_HOLD) != 0

        _activeCalls.value = _activeCalls.value.map { model ->
            if (model.callId == callId) {
                val dur = if (connectTime > 0 && mappedState == CallLifecycleState.ACTIVE) {
                    (System.currentTimeMillis() - connectTime) / 1000L
                } else model.durationSeconds

                model.copy(
                    state = mappedState,
                    connectTimeMillis = if (connectTime > 0) connectTime else model.connectTimeMillis,
                    durationSeconds = maxOf(0L, dur),
                    isOnHold = (mappedState == CallLifecycleState.HOLDING),
                    isHoldSupported = canHold
                )
            } else model
        }
    }

    fun answerCall(callId: String) {
        if (!allowAction()) return
        val call = callMap[callId] ?: return
        try {
            call.answer(0)
        } catch (_: Exception) {}
    }

    fun declineOrHangupCall(callId: String) {
        if (!allowAction()) return
        val call = callMap[callId] ?: return
        try {
            if (call.state == Call.STATE_RINGING) {
                call.reject(false, null)
            } else {
                call.disconnect()
            }
        } catch (_: Exception) {}
    }

    fun toggleMute() {
        if (!allowAction()) return
        val currentMuted = _audioState.value.isMuted
        inCallServiceRef?.setMuted(!currentMuted)
    }

    fun toggleSpeaker() {
        if (!allowAction()) return
        val service = inCallServiceRef ?: return
        val currentRoute = _audioState.value.route
        val newRoute = if (currentRoute == CallAudioState.ROUTE_SPEAKER) {
            CallAudioState.ROUTE_EARPIECE
        } else {
            CallAudioState.ROUTE_SPEAKER
        }
        service.setAudioRoute(newRoute)
    }

    fun toggleHold(callId: String) {
        if (!allowAction()) return
        val call = callMap[callId] ?: return
        try {
            if (call.state == Call.STATE_HOLDING) {
                call.unhold()
            } else {
                call.hold()
            }
        } catch (_: Exception) {}
    }

    fun playDtmf(callId: String, digit: Char) {
        val call = callMap[callId] ?: return
        try {
            call.playDtmfTone(digit)
        } catch (_: Exception) {}
    }

    fun stopDtmf(callId: String) {
        val call = callMap[callId] ?: return
        try {
            call.stopDtmfTone()
        } catch (_: Exception) {}
    }

    fun mapSystemState(call: Call): CallLifecycleState {
        return when (call.state) {
            Call.STATE_RINGING -> CallLifecycleState.RINGING
            Call.STATE_DIALING, Call.STATE_CONNECTING -> CallLifecycleState.DIALING
            Call.STATE_ACTIVE -> CallLifecycleState.ACTIVE
            Call.STATE_HOLDING -> CallLifecycleState.HOLDING
            Call.STATE_DISCONNECTING -> CallLifecycleState.DISCONNECTING
            Call.STATE_DISCONNECTED -> CallLifecycleState.DISCONNECTED
            else -> CallLifecycleState.DIALING
        }
    }

    private fun allowAction(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastActionElapsedRealtime < ACTION_DEBOUNCE_MS) {
            return false
        }
        lastActionElapsedRealtime = now
        return true
    }
}
