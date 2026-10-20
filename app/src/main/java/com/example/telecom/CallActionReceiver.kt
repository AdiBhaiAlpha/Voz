package com.example.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

import android.util.Log

class CallActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ANSWER = "com.voz.caller.ACTION_ANSWER"
        const val ACTION_DECLINE_OR_HANGUP = "com.voz.caller.ACTION_DECLINE_OR_HANGUP"
        const val EXTRA_CALL_ID = "extra_call_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(EXTRA_CALL_ID)
        val targetCallId = if (!callId.isNullOrBlank()) {
            callId
        } else {
            CallSessionController.activeCalls.value.firstOrNull()?.callId
        }

        when (intent.action) {
            ACTION_ANSWER -> {
                if (targetCallId != null) {
                    CallSessionController.answerCall(targetCallId)
                } else {
                    CallManager.answerCall()
                }

                if (CallPresentationPolicy.shouldUseFullScreen(context)) {
                    val inCallIntent = Intent(context, InCallActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    try { context.startActivity(inCallIntent) } catch (_: Exception) {}
                } else {
                    VozCallOverlayService.startIfPermitted(context)
                }
            }
            ACTION_DECLINE_OR_HANGUP -> {
                if (targetCallId != null) {
                    CallSessionController.declineOrHangupCall(targetCallId)
                } else {
                    CallManager.declineOrHangup()
                }
            }
        }
    }
}
