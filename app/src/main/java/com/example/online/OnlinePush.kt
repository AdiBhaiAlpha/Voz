package com.example.online

import android.app.Application
import android.content.Context
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class VozApplication : Application() {
    override fun onCreate() { super.onCreate(); OnlineCalls.initialize(this) }
}
object OnlinePush {
    fun initialize(context: Context) {
        if (FirebaseApp.getApps(context).isNotEmpty() || listOf(BuildConfig.FCM_APPLICATION_ID, BuildConfig.FCM_API_KEY, BuildConfig.FCM_PROJECT_ID, BuildConfig.FCM_SENDER_ID).any { it.isBlank() }) return
        // Partial/malformed deployment configuration must not crash the cellular app.
        runCatching { FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
            .setApplicationId(BuildConfig.FCM_APPLICATION_ID).setApiKey(BuildConfig.FCM_API_KEY)
            .setProjectId(BuildConfig.FCM_PROJECT_ID).setGcmSenderId(BuildConfig.FCM_SENDER_ID).build()) }
    }
    suspend fun token(context: Context): String? {
        if (FirebaseApp.getApps(context).isEmpty()) return null
        return suspendCancellableCoroutine { cont ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (cont.isActive) cont.resume(if (task.isSuccessful) task.result else null)
            }
        }
    }
}
class VozMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        OnlineCalls.initialize(this)
        if (OnlineCalls.api.store.load() != null) OnlineCalls.run { OnlineCalls.restore() }
    }
    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["kind"] != "voz.incoming") return
        val id = message.data["callId"] ?: return
        if (runCatching { java.util.UUID.fromString(id) }.isFailure) return
        OnlineCalls.initialize(this)
        // No mic or peer connection in push callback. Service validates against authenticated API.
        OnlineCallService.incoming(this, id)
    }
}
