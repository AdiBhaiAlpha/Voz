package com.example.telecom

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.Call
import android.telephony.TelephonyManager
import android.util.Log

/**
 * Diagnostic logger for real Android Telecom integration.
 * Ensures detailed operational tracing while strictly sanitizing user phone numbers.
 */
object TelecomDiagnosticLogger {

    private const val TAG = "VOZ_TELECOM"

    fun logEnvironment(context: Context, isDefaultDialer: Boolean) {
        val pm = context.packageManager
        val hasTelephonyFeature = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

        val isVoiceCapable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            tm?.isVoiceCapable == true
        } else {
            tm?.phoneType != TelephonyManager.PHONE_TYPE_NONE
        }

        val simState = tm?.simState ?: TelephonyManager.SIM_STATE_UNKNOWN
        val simStateName = when (simState) {
            TelephonyManager.SIM_STATE_READY -> "READY"
            TelephonyManager.SIM_STATE_ABSENT -> "ABSENT"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN_REQUIRED"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK_REQUIRED"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "NETWORK_LOCKED"
            else -> "UNKNOWN/NOT_READY ($simState)"
        }

        Log.i(
            TAG,
            "Environment Diagnostics: BuildModel='${Build.MODEL}', SDK=${Build.VERSION.SDK_INT}, " +
                    "TelephonyFeature=$hasTelephonyFeature, VoiceCapable=$isVoiceCapable, " +
                    "SIMState=$simStateName, isDefaultDialerRoleHeld=$isDefaultDialer"
        )
    }

    fun logOutgoingCallAttempt(rawNumber: String, isDefaultDialer: Boolean, apiMethod: String) {
        val masked = maskPhoneNumber(rawNumber)
        Log.i(
            TAG,
            "Real Outgoing Carrier Call Requested: target='$masked', " +
                    "digits=${rawNumber.length}, defaultDialerHeld=$isDefaultDialer, API=$apiMethod"
        )
    }

    fun logCallAdded(call: Call, isIncoming: Boolean) {
        val stateName = telecomStateToString(call.state)
        val maskedNumber = maskPhoneNumber(call.details?.handle?.schemeSpecificPart ?: "")
        Log.i(
            TAG,
            "Telecom InCallService.onCallAdded(): state=$stateName, " +
                    "isIncoming=$isIncoming, handle='$maskedNumber', " +
                    "callCapabilities=${call.details?.callCapabilities}"
        )
    }

    fun logStateChanged(callId: String, oldState: String, newState: Int) {
        val stateName = telecomStateToString(newState)
        Log.i(
            TAG,
            "Telecom Call.Callback.onStateChanged(): callId='$callId', " +
                    "transition='$oldState' -> '$stateName'"
        )
    }

    fun logCallRemoved(callId: String, finalState: String) {
        Log.i(
            TAG,
            "Telecom InCallService.onCallRemoved(): callId='$callId', finalState='$finalState'"
        )
    }

    fun logFallback(reason: String) {
        Log.w(TAG, "Telecom Fallback Occurred: $reason")
    }

    fun logError(message: String, throwable: Throwable? = null) {
        Log.e(TAG, "Telecom Error: $message", throwable)
    }

    fun maskPhoneNumber(number: String): String {
        val clean = number.trim()
        if (clean.length <= 4) return "***"
        val prefix = clean.take(minOf(3, clean.length / 3))
        val suffix = clean.takeLast(4)
        return "$prefix***$suffix"
    }

    fun telecomStateToString(state: Int): String {
        return when (state) {
            Call.STATE_NEW -> "STATE_NEW"
            Call.STATE_CONNECTING -> "STATE_CONNECTING"
            Call.STATE_DIALING -> "STATE_DIALING"
            Call.STATE_RINGING -> "STATE_RINGING"
            Call.STATE_ACTIVE -> "STATE_ACTIVE"
            Call.STATE_HOLDING -> "STATE_HOLDING"
            Call.STATE_DISCONNECTING -> "STATE_DISCONNECTING"
            Call.STATE_DISCONNECTED -> "STATE_DISCONNECTED"
            Call.STATE_SELECT_PHONE_ACCOUNT -> "STATE_SELECT_PHONE_ACCOUNT"
            else -> "STATE_UNKNOWN($state)"
        }
    }
}
