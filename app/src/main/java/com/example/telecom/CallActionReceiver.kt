package com.example.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CallActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ANSWER = "com.voz.calling.ACTION_ANSWER"
        const val ACTION_DECLINE_OR_HANGUP = "com.voz.calling.ACTION_DECLINE_OR_HANGUP"
        const val EXTRA_CALL_ID = "extra_call_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return

        when (intent.action) {
            ACTION_ANSWER -> {
                CallManager.answerCall()
                CallSessionController.answerCall(callId)
            }
            ACTION_DECLINE_OR_HANGUP -> {
                CallManager.declineOrHangup()
                CallSessionController.declineOrHangupCall(callId)
            }
        }
    }
}
