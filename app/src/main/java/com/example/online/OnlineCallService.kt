package com.example.online

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.telecom.CallPresentationPolicy
import com.example.telecom.VozCallOverlayService
import com.example.telecom.VozOnlineActivity
import kotlinx.coroutines.*

/** Ringing FGS has NO microphone. Only a visible user action upgrades it to microphone FGS. */
class OnlineCallService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var verifying = false
    private var mediaActive = false
    override fun onCreate() {
        super.onCreate(); OnlineCalls.initialize(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(RING, "VOZ incoming internet calls", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE),
                android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build())
            enableVibration(true); lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        })
        nm.createNotificationChannel(NotificationChannel(ACTIVE, "VOZ internet call", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) { stopSelf(); return START_NOT_STICKY }
        if (intent.action == MEDIA) mediaActive = true
        val media = mediaActive
        val needsVerification = intent.action == INCOMING && !OnlineCalls.owns(intent.getStringExtra("callId").orEmpty())
        try {
            ServiceCompat.startForeground(this, ID, notification(verifying = needsVerification),
                if (Build.VERSION.SDK_INT >= 30 && media) ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                else if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL else 0)
        } catch (_: Exception) { fallback(this, intent.getStringExtra("callId")); stopSelf(); return START_NOT_STICKY }
        if (needsVerification && !verifying) {
            verifying = true
            scope.launch {
                try {
                    val id = intent.getStringExtra("callId") ?: error("Missing invitation")
                    OnlineCalls.incoming(id)
                    getSystemService(NotificationManager::class.java).notify(ID, notification(false))
                    if (!CallPresentationPolicy.shouldUseFullScreen(this@OnlineCallService)) VozCallOverlayService.startIfPermitted(this@OnlineCallService)
                } catch (_: Exception) { if (!OnlineCalls.isOnlineActive()) stopSelf() }
                finally { verifying = false }
            }
        }
        return START_NOT_STICKY
    }
    private fun notification(verifying: Boolean): Notification {
        val ui = OnlineCalls.state.value
        val ringing = !verifying && ui.incoming && ui.state == "Incoming call"
        val open = PendingIntent.getActivity(this, 9001, Intent(this, VozOnlineActivity::class.java)
            .putExtra("callId", ui.callId).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val end = PendingIntent.getBroadcast(this, 9002, Intent(this, OnlineActionReceiver::class.java).setAction("end").setData(android.net.Uri.parse("voz://end/${ui.callId}")).putExtra("callId", ui.callId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, if (ringing) RING else ACTIVE).setSmallIcon(R.drawable.ic_call_answer)
            .setContentTitle(if (verifying) "Checking incoming call…" else "VOZ internet call")
            .setContentText(if (verifying) "" else ui.state).setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(open)
        if (ringing) {
            builder.setStyle(NotificationCompat.CallStyle.forIncomingCall(androidx.core.app.Person.Builder().setName(ui.remoteName).build(), end, open))
            if (CallPresentationPolicy.shouldUseFullScreen(this)) builder.setFullScreenIntent(open, true)
        } else if (!verifying && ui.callId != null) builder.addAction(R.drawable.ic_call_decline, "End call", end)
        return builder.build()
    }
    override fun onDestroy() { scope.cancel(); if (OnlineCalls.isOnlineActive()) OnlineCalls.endFromUi() else VozCallOverlayService.stop(this); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        private const val ID = 5001
        private const val RING = "voz_ip_incoming_v1"
        private const val ACTIVE = "voz_ip_active_v1"
        private const val INCOMING = "incoming"
        private const val MEDIA = "media"
        fun incoming(context: Context, id: String) {
            try { ContextCompat.startForegroundService(context, Intent(context, OnlineCallService::class.java).setAction(INCOMING).putExtra("callId", id)) }
            catch (_: Exception) { fallback(context, id) }
        }
        fun startMedia(context: Context) { ContextCompat.startForegroundService(context, Intent(context, OnlineCallService::class.java).setAction(MEDIA)) }
        fun refresh(context: Context) {
            if (!OnlineCalls.isOnlineActive()) return
            try { context.startService(Intent(context, OnlineCallService::class.java).setAction("refresh")) } catch (_: Exception) {}
        }
        fun stop(context: Context) { context.stopService(Intent(context, OnlineCallService::class.java)); context.getSystemService(NotificationManager::class.java).cancel(ID) }
        fun fallback(context: Context, id: String?) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("voz_ip_fallback", "VOZ call alerts", NotificationManager.IMPORTANCE_HIGH))
            val tap = PendingIntent.getActivity(context, 9003, Intent(context, VozOnlineActivity::class.java).putExtra("callId", id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            try { nm.notify(5002, NotificationCompat.Builder(context, "voz_ip_fallback").setSmallIcon(R.drawable.ic_call_answer)
                .setContentTitle("VOZ call invitation").setTimeoutAfter(30_000).setContentText("Tap to verify and open").setContentIntent(tap).setAutoCancel(true).build()) } catch (_: SecurityException) {}
        }
    }
}
class OnlineActionReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        OnlineCalls.initialize(context)
        val pending = goAsync()
        OnlineCalls.scope.launch { try {
            withTimeout(8000) { intent.getStringExtra("callId")?.let { OnlineCalls.endInvitation(it) } }
        } catch (_: Exception) {} finally { pending.finish() } }
    }
}
