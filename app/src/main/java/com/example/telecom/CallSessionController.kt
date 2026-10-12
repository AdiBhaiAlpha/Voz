package com.example.telecom

import android.os.SystemClock
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import com.example.domain.PhoneNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

enum class CallLifecycleState {
    CONNECTING,
    DIALING,
    RINGING,
    ACTIVE,
    HOLDING,
    DISCONNECTING,
    DISCONNECTED
}

data class VozAudioState(
    val isMuted: Boolean = false,
    val route: Int = CallAudioState.ROUTE_EARPIECE,
    val supportedRouteMask: Int = CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER
) {
    val isSpeakerOn: Boolean
        get() = (route and CallAudioState.ROUTE_SPEAKER) != 0

    val isBluetoothOn: Boolean
        get() = (route and CallAudioState.ROUTE_BLUETOOTH) != 0
}

data class VozCallUiModel(
    val callId: String,
    val rawNumber: String,
    val normalizedNumber: String,
    val displayName: String?,
    val photoUri: String? = null,
    val state: CallLifecycleState,
    val connectTimeMillis: Long = 0L,
    val canHold: Boolean = false,
    val simLabel: String? = null,
    val assistNote: String? = null,
    val assistLabel: String? = null,
    val isKnownContact: Boolean = false
)

/**
 * Coordinates real Android Telecom Call objects exposed by [VozInCallService] with the UI layer.
 * Includes tap-debouncing to prevent duplicate answer/decline/hangup actions during rapid taps.
 */
object CallSessionController {

    private val callMap = ConcurrentHashMap<String, Call>()
    private val callCallbacks = ConcurrentHashMap<String, Call.Callback>()
    private var inCallServiceRef: InCallService? = null

    private val _activeCalls = MutableStateFlow<List<VozCallUiModel>>(emptyList())
    val activeCalls: StateFlow<List<VozCallUiModel>> = _activeCalls.asStateFlow()

    private val _audioState = MutableStateFlow(VozAudioState())
    val audioState: StateFlow<VozAudioState> = _audioState.asStateFlow()

    private var lastActionElapsedRealtime = 0L
    private const val ACTION_DEBOUNCE_MS = 350L

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
            override fun onStateChanged(call: Call, state: Int) {
                updateCallFromSystem(id, call)
            }

            override fun onDetailsChanged(call: Call, details: Call.Details) {
                updateCallFromSystem(id, call)
            }
        }
        callCallbacks[id] = callback
        call.registerCallback(callback)

        _activeCalls.update { list ->
            val filtered = list.filterNot { it.callId == id }
            filtered + initialModel
        }
    }

    fun updateEnrichedMetadata(
        callId: String,
        displayName: String?,
        photoUri: String?,
        assistNote: String?,
        assistLabel: String?,
        isKnownContact: Boolean
    ) {
        _activeCalls.update { list ->
            list.map { item ->
                if (item.callId == callId) {
                    item.copy(
                        displayName = displayName ?: item.displayName,
                        photoUri = photoUri ?: item.photoUri,
                        assistNote = assistNote ?: item.assistNote,
                        assistLabel = assistLabel ?: item.assistLabel,
                        isKnownContact = isKnownContact
                    )
                } else {
                    item
                }
            }
        }
    }

    fun unregisterCall(call: Call) {
        val entry = callMap.entries.find { it.value === call } ?: return
        val id = entry.key
        callCallbacks.remove(id)?.let { call.unregisterCallback(it) }
        callMap.remove(id)
        _activeCalls.update { list -> list.filterNot { it.callId == id } }
    }

    fun onAudioStateChanged(audioState: CallAudioState?) {
        audioState ?: return
        _audioState.value = VozAudioState(
            isMuted = audioState.isMuted,
            route = audioState.route,
            supportedRouteMask = audioState.supportedRouteMask
        )
    }

    private fun updateCallFromSystem(callId: String, call: Call) {
        val mappedState = mapSystemState(call)
        val details = call.details
        val handleNumber = details?.handle?.schemeSpecificPart.orEmpty()
        val normalized = PhoneNormalizer.normalizeForComparison(handleNumber).ifBlank { handleNumber }
        val canHoldCap = details != null &&
            (details.callCapabilities and Call.Details.CAPABILITY_HOLD) != 0

        _activeCalls.update { list ->
            list.map { existing ->
                if (existing.callId == callId) {
                    existing.copy(
                        rawNumber = handleNumber.ifBlank { existing.rawNumber },
                        normalizedNumber = normalized.ifBlank { existing.normalizedNumber },
                        state = mappedState,
                        connectTimeMillis = details?.connectTimeMillis ?: existing.connectTimeMillis,
                        canHold = canHoldCap
                    )
                } else {
                    existing
                }
            }
        }
    }

    fun answerCall(callId: String) {
        if (!allowAction()) return
        val call = callMap[callId] ?: return
        if (mapSystemState(call) == CallLifecycleState.RINGING) {
            call.answer(VideoProfile.STATE_AUDIO_ONLY)
        }
    }

    fun declineOrHangupCall(callId: String) {
        if (!allowAction()) return
        val call = callMap[callId] ?: return
        when (mapSystemState(call)) {
            CallLifecycleState.RINGING -> call.reject(false, null)
            else -> call.disconnect()
        }
    }

    fun toggleMute() {
        val service = inCallServiceRef ?: return
        val nextMute = !_audioState.value.isMuted
        service.setMuted(nextMute)
        _audioState.update { it.copy(isMuted = nextMute) }
    }

    fun toggleSpeaker() {
        val service = inCallServiceRef ?: return
        val current = _audioState.value
        val nextRoute = if (current.isSpeakerOn) {
            CallAudioState.ROUTE_WIRED_OR_EARPIECE
        } else {
            CallAudioState.ROUTE_SPEAKER
        }
        service.setAudioRoute(nextRoute)
        _audioState.update { it.copy(route = nextRoute) }
    }

    fun toggleHold(callId: String) {
        if (!allowAction()) return
        val call = callMap[callId] ?: return
        when (mapSystemState(call)) {
            CallLifecycleState.HOLDING -> call.unhold()
            CallLifecycleState.ACTIVE -> call.hold()
            else -> Unit
        }
    }

    fun playDtmf(callId: String, digit: Char) {
        val call = callMap[callId] ?: return
        call.playDtmfTone(digit)
    }

    fun stopDtmf(callId: String) {
        val call = callMap[callId] ?: return
        call.stopDtmfTone()
    }

    private fun allowAction(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastActionElapsedRealtime < ACTION_DEBOUNCE_MS) {
            return false
        }
        lastActionElapsedRealtime = now
        return true
    }

    @Suppress("DEPRECATION")
    fun mapSystemState(call: Call): CallLifecycleState {
        val rawState = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            call.details?.state ?: call.state
        } else {
            call.state
        }
        return when (rawState) {
            Call.STATE_RINGING -> CallLifecycleState.RINGING
            Call.STATE_DIALING, Call.STATE_PULLING_CALL -> CallLifecycleState.DIALING
            Call.STATE_CONNECTING, Call.STATE_SELECT_PHONE_ACCOUNT -> CallLifecycleState.CONNECTING
            Call.STATE_ACTIVE -> CallLifecycleState.ACTIVE
            Call.STATE_HOLDING -> CallLifecycleState.HOLDING
            Call.STATE_DISCONNECTING -> CallLifecycleState.DISCONNECTING
            Call.STATE_DISCONNECTED -> CallLifecycleState.DISCONNECTED
            else -> CallLifecycleState.CONNECTING
        }
    }
}
