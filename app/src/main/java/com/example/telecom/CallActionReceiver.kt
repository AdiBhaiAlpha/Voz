package com.example.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CallActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val callId = intent?.getStringExtra(EXTRA_CALL_ID) ?: return
        when (intent.action) {
            ACTION_ANSWER -> CallSessionController.answerCall(callId)
            ACTION_DECLINE_OR_HANGUP -> CallSessionController.declineOrHangupCall(callId)
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.example.telecom.ACTION_ANSWER"
        const val ACTION_DECLINE_OR_HANGUP = "com.example.telecom.ACTION_DECLINE_OR_HANGUP"
        const val EXTRA_CALL_ID = "extra_call_id"
    }
}
