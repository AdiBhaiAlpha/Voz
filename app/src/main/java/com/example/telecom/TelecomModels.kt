package com.example.telecom

import com.example.domain.PhoneNormalizer
import com.example.domain.SimSubscriptionItem

enum class CallLifecycleState {
    DIALING,
    RINGING,
    ACTIVE,
    HOLDING,
    DISCONNECTING,
    DISCONNECTED
}

data class VozCallUiModel(
    val callId: String,
    val rawNumber: String,
    val normalizedNumber: String,
    val displayName: String?,
    val photoUri: String?,
    val state: CallLifecycleState,
    val connectTimeMillis: Long,
    val durationSeconds: Long = 0L,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isOnHold: Boolean = false,
    val isIncoming: Boolean = false,
    val isHoldSupported: Boolean = true,
    val assistNote: String? = null,
    val assistLabel: String? = null,
    val isImportant: Boolean = false,
    val isRecording: Boolean = false,
    val recordingError: String? = null,
    val isVozOnlineCall: Boolean = false
) {
    /**
     * Resolves the primary title to display. If the number is unsaved (no contact name),
     * this MUST display the formatted phone number, NEVER blank!
     */
    val displayTitle: String
        get() {
            val name = displayName?.trim()?.takeIf { it.isNotBlank() && it != rawNumber && it != "null" }
            if (name != null) return name
            val formatted = PhoneNormalizer.formatForDisplay(rawNumber).trim()
            if (formatted.isNotBlank()) return formatted
            if (rawNumber.isNotBlank()) return rawNumber.trim()
            return "Unknown Caller"
        }

    val hasContactName: Boolean
        get() = !displayName.isNullOrBlank() && displayName.trim() != rawNumber.trim() && displayName.trim() != "null"
}


data class VozAudioState(
    val isMuted: Boolean = false,
    val route: Int = 1, // 1: EARPIECE, 2: BLUETOOTH, 4: SPEAKER, 8: WIRED_HEADSET
    val supportedRouteMask: Int = 15
) {
    val isSpeaker: Boolean get() = (route == 4) // CallAudioState.ROUTE_SPEAKER
    val isBluetooth: Boolean get() = (route == 2) // CallAudioState.ROUTE_BLUETOOTH
    val isEarpiece: Boolean get() = (route == 1) // CallAudioState.ROUTE_EARPIECE
}

sealed interface CallInitiationOutcome {
    data object PlacedDirectly : CallInitiationOutcome
    data class RequiresSimSelection(
        val sanitizedNumber: String,
        val availableSims: List<SimSubscriptionItem>
    ) : CallInitiationOutcome
    data class Error(val reason: String) : CallInitiationOutcome
}
