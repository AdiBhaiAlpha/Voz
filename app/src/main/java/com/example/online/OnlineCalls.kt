package com.example.online

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.telecom.CallLifecycleState
import com.example.telecom.CallSessionController
import com.example.telecom.VozCallUiModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import java.util.UUID

/** Native online call owner. Server owns invitation state; ONLY WebRTC callbacks report connected. */
object OnlineCalls {
    data class Ui(val signedIn: Boolean = false, val identity: String = "", val displayName: String = "",
        val callId: String? = null, val incoming: Boolean = false, val state: String = "Sign in", val error: String? = null,
        val muted: Boolean = false, val speaker: Boolean = false, val pushReady: Boolean = false, val remoteName: String = "VOZ caller")
    private val mutable = MutableStateFlow(Ui())
    val state = mutable.asStateFlow()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var api: OnlineApi
        private set
    private lateinit var context: Context
    private var snapshot: JSONObject? = null
    private val locallyEnded = linkedSetOf<String>()
    private var accountId = ""
    private var deviceId = ""
    private var peer: NativeVoicePeer? = null
    private var socket: WebSocket? = null
    private var reconnect: Job? = null
    private var heartbeat: Job? = null
    private var connectTimer: Job? = null
    private var invitationTimer: Job? = null
    private var uiVisible = false
    private val uiOwners = mutableSetOf<Int>()
    private var consent = false
    private var connected = false
    private var initialized = false
    private var seq = 0L
    private var mediaGeneration = 0
    private var pendingIce = mutableListOf<JSONObject>()
    private val eventLock = Mutex()
    private val commandLock = Mutex()
    private var reconnectDelay = 1000L
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var audioHeld = false
    private var focus: android.media.AudioFocusRequest? = null
    private var previousAudioMode = AudioManager.MODE_NORMAL
    private val audio: AudioManager get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Synchronized fun initialize(ctx: Context) {
        if (initialized) return
        context = ctx.applicationContext; api = OnlineApi(context); initialized = true
        OnlinePush.initialize(context)
    }
    fun ui(visible: Boolean, owner: Any) {
        val id = System.identityHashCode(owner)
        if (visible) uiOwners.add(id) else uiOwners.remove(id)
        uiVisible = uiOwners.isNotEmpty()
        if (visible && api.store.load() != null) run { restore(); connect() }
        else if (!uiVisible && snapshot == null) { heartbeat?.cancel(); socket?.close(1000, "Background"); socket = null; reconnect?.cancel() }
    }
    fun run(block: suspend () -> Unit) { scope.launch { try { block() } catch (e: Exception) {
        if (e is CancellationException) throw e
        report(e)
    } } }
    private fun report(error: Exception) {
        if (api.store.load() == null && mutable.value.signedIn) {
            if (snapshot != null) cleanup("Sign in again")
            mutable.value = Ui(error = "Session expired. Sign in again.")
        } else mutable.value = mutable.value.copy(error = error.message ?: "Service unavailable")
    }
    fun clearError() { mutable.value = mutable.value.copy(error = null) }
    suspend fun signIn(username: String, password: String, name: String, create: Boolean) {
        val body = JSONObject().put("username", username.trim().lowercase()).put("password", password)
        val tokens = api.request("POST", if (create) "/v1/auth/signup" else "/v1/auth/login", body, false)
        api.store.save(tokens)
        if (create) api.request("POST", "/v1/identity", JSONObject().put("displayName", name.trim()))
        restore()
        // Recover an account created before identity registration completed.
        if (mutable.value.identity.isBlank()) api.request("POST", "/v1/identity", JSONObject().put("displayName", name.trim()))
        restore(); connect()
    }
    suspend fun restore() {
        val me = api.request("GET", "/v1/me")
        accountId = me.getString("accountId")
        val token = OnlinePush.token(context)
        val registration = JSONObject().put("installationId", api.store.installationId).put("name", android.os.Build.MODEL.take(80))
        if (token != null) registration.put("pushToken", token)
        val d = api.request("POST", "/v1/devices", registration)
        deviceId = d.getString("id")
        val identity = me.optJSONObject("identity")
        mutable.value = mutable.value.copy(signedIn = true, identity = identity?.optString("voz_id").orEmpty(),
            displayName = identity?.optString("display_name").orEmpty(), pushReady = token != null, state = if (snapshot == null) "Ready" else mutable.value.state)
    }
    suspend fun finishIdentity(name: String) {
        api.request("POST", "/v1/identity", JSONObject().put("displayName", name.trim()))
        restore(); connect()
    }
    suspend fun signOut(delete: Boolean) {
        var succeeded = false
        try {
            if (snapshot != null) end()
            api.request(if (delete) "DELETE" else "POST", if (delete) "/v1/account" else "/v1/auth/logout")
            succeeded = true
        } finally {
            if (!delete || succeeded) {
                if (snapshot != null) cleanup("Signed out")
                api.store.clear(); socket?.close(1000, "Signed out"); socket = null; reconnect?.cancel(); mutable.value = Ui()
            }
        }
    }
    suspend fun endInvitation(id: String) {
        if (owns(id)) { end(); return }
        if (accountId.isBlank()) restore()
        val c = api.request("GET", "/v1/calls/$id")
        if (c.optString("state") == "ended") return
        api.command(id, if (c.optString("state") == "ringing") {
            if (c.getString("from_account") == accountId) "cancel" else "reject"
        } else "end")
    }
    fun owns(id: String) = snapshot?.optString("id") == id
    fun isOnlineActive() = snapshot != null
    suspend fun lookup(target: String): JSONObject = api.request("GET", "/v1/recipients/$target")

    /** Called ONLY from an Activity after explicit answer/call action + microphone permission. */
    suspend fun start(target: String) {
        check(snapshot == null) { "Another call is in progress" }
        requirePermission(); check(CallSessionController.activeCalls.value.none { !it.isVozOnlineCall }) { "A cellular call is active" }
        val recipient = lookup(target)
        consent = true
        OnlineCallService.startMedia(context)
        val request = JSONObject().put("target", recipient.getString("voz_id")).put("requestId", UUID.randomUUID().toString())
        val result = retry { api.request("POST", "/v1/calls", request) }
        seq = 0; adopt(result.getJSONObject("call").put("recipient_id", target).put("recipient_name", recipient.getString("display_name"))); connect()
    }
    suspend fun incoming(id: String): JSONObject {
        if (accountId.isBlank()) restore()
        check(id !in locallyEnded) { "Invitation dismissed" }
        val call = api.request("GET", "/v1/calls/$id")
        check(call.getString("to_account") == accountId && call.getString("state") == "ringing" &&
            call.optBoolean("unexpired", false)) { "Invitation expired or unavailable" }
        if (CallSessionController.activeCalls.value.any { !it.isVozOnlineCall }) {
            api.command(id, "reject"); error("A cellular call is active")
        }
        check(snapshot == null || owns(id)) { "Already in a call" }
        if (!owns(id)) { seq = 0; consent = false }
        adopt(call); connect(); return call
    }
    suspend fun openInvitation(id: String) {
        check(id !in locallyEnded) { "Invitation dismissed" }
        if (accountId.isBlank()) restore()
        val call = api.request("GET", "/v1/calls/$id")
        if (call.optString("state") == "accepted" && (call.optString("from_device") == deviceId || call.optString("to_device") == deviceId)) {
            adopt(call); connect()
        } else incoming(id)
    }
    suspend fun accept() {
        requirePermission()
        check(CallSessionController.activeCalls.value.none { !it.isVozOnlineCall }) { "A cellular call is active" }
        val call = snapshot ?: error("No call")
        val id = call.getString("id")
        val verified = api.request("GET", "/v1/calls/$id")
        check(verified.getString("state") == "ringing" && verified.getString("to_account") == accountId) { "Invitation expired" }
        consent = true; OnlineCallService.startMedia(context)
        ensurePeer(verified.getInt("revision") + 1)
        val result = command("accept")
        if (owns(id)) adopt(result.getJSONObject("call"))
    }
    suspend fun resumeMedia() {
        val call = snapshot ?: return
        requirePermission(); consent = true; OnlineCallService.startMedia(context)
        ensurePeer(call.getInt("revision"))
        restart()
    }
    private fun requirePermission() { check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "Microphone permission required" } }
    private suspend fun <T> retry(block: suspend () -> T): T = try { block() } catch (e: java.io.IOException) { delay(400); block() }
    private suspend fun command(action: String, payload: JSONObject = JSONObject()): JSONObject {
        val id = snapshot?.getString("id") ?: error("No call")
        val event = UUID.randomUUID().toString()
        return commandLock.withLock { retry { api.command(id, action, payload, event) } }
    }
    suspend fun end() {
        val c = snapshot ?: return
        val id = c.getString("id")
        val action = if (c.getString("state") == "ringing") {
            if (c.getString("from_account") == accountId) "cancel" else "reject"
        } else "end"
        locallyEnded.add(id); if (locallyEnded.size > 32) locallyEnded.remove(locallyEnded.first())
        // Stop capture immediately even when the network is down; server leases bound cleanup.
        val eventId = UUID.randomUUID().toString()
        val ending = scope.async { commandLock.withLock { retry { api.command(id, action, eventId = eventId) } } }
        cleanup("Call ended")
        ending.await()
    }
    fun endFromUi() { run { end() } }
    fun answerFromUi() { context.startActivity(Intent(context, com.example.telecom.VozOnlineActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("callId", snapshot?.optString("id"))) }
    fun mute() { val next = !mutable.value.muted; peer?.mute(next); mutable.value = mutable.value.copy(muted = next); publish() }
    @Suppress("DEPRECATION") fun speaker() {
        val next = !mutable.value.speaker
        audio.isSpeakerphoneOn = next
        mutable.value = mutable.value.copy(speaker = next); publish()
    }
    private fun publish() {
        val c = snapshot
        if (c == null) { CallSessionController.publishOnline(null); return }
        val ringing = c.getString("state") == "ringing"
        val incoming = c.getString("to_account") == accountId
        CallSessionController.publishOnline(VozCallUiModel(callId = c.getString("id"), rawNumber = if (incoming) c.optString("caller_id", "VOZ call") else c.optString("recipient_id", "VOZ call"),
            normalizedNumber = c.optString("caller_id"), displayName = (if (incoming) c.optString("caller_name") else c.optString("recipient_name")).takeIf { it.isNotBlank() }, photoUri = null,
            state = if (ringing && incoming) CallLifecycleState.RINGING else if (connected) CallLifecycleState.ACTIVE else CallLifecycleState.DIALING,
            connectTimeMillis = if (connected) connectedAt else 0, isMuted = mutable.value.muted, isSpeakerOn = mutable.value.speaker,
            isIncoming = incoming, isHoldSupported = false, isVozOnlineCall = true))
    }
    private var connectedAt = 0L
    private fun adopt(call: JSONObject) {
        if (snapshot != null && !owns(call.optString("id"))) return
        if (call.getString("state") == "ended") { cleanup(call.optString("reason", "Call ended")); return }
        listOf("caller_id", "caller_name", "recipient_id", "recipient_name").forEach { key ->
            if (!call.has(key) && snapshot?.has(key) == true) call.put(key, snapshot!!.get(key))
        }
        snapshot = call
        mutable.value = mutable.value.copy(callId = call.getString("id"), remoteName = if (call.getString("to_account") == accountId) call.optString("caller_name", "VOZ caller") else call.optString("recipient_name", "VOZ caller"), incoming = call.getString("to_account") == accountId,
            state = if (connected) "Connected" else if (call.getString("state") == "ringing") {
                if (call.getString("to_account") == accountId) "Incoming call" else "Ringing…"
            } else "Connecting…")
        invitationTimer?.cancel()
        if (call.getString("state") == "ringing") invitationTimer = scope.launch {
            delay((java.time.Instant.parse(call.getString("expires_at")).toEpochMilli() - java.time.Instant.parse(call.optString("server_time", call.getString("created_at"))).toEpochMilli()).coerceIn(0, 30_000))
            if (owns(call.getString("id")) && snapshot?.optString("state") == "ringing") {
                runCatching { end() }; cleanup("No answer")
            }
        }
        publish(); OnlineCallService.refresh(context)
    }
    private suspend fun ensurePeer(generation: Int) {
        if (peer != null) {
            if (mediaGeneration != generation) {
                peer?.prepareGeneration(); mediaGeneration = generation
                peer?.updateIce(api.request("GET", "/v1/ice").getJSONArray("iceServers"))
                pendingIce.filter { it.optInt("generation") == generation }.forEach { candidate(it) }; pendingIce.clear()
            }
            return
        }
        check(consent) { "Tap answer to grant microphone consent" }
        requirePermission()
        val ice = api.request("GET", "/v1/ice").getJSONArray("iceServers")
        mediaGeneration = generation
        val id = snapshot?.optString("id") ?: return
        @Suppress("DEPRECATION")
        previousAudioMode = audio.mode
        focus = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { change -> if (change == AudioManager.AUDIOFOCUS_LOSS) endFromUi() }.build()
        check(audio.requestAudioFocus(focus!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "Call audio is busy" }
        audioHeld = true
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        peer = NativeVoicePeer(context, ice, mutable.value.muted, { candidate -> scope.launch {
            if (owns(id)) runCatching { command("ice", JSONObject().put("generation", mediaGeneration)
                .put("candidate", candidate.sdp).put("sdpMid", candidate.sdpMid ?: JSONObject.NULL).put("sdpMLineIndex", candidate.sdpMLineIndex)) }
        } }, { state -> scope.launch {
            if (!owns(id)) return@launch
            when (state) {
                PeerConnection.PeerConnectionState.CONNECTED -> { connected = true; if (connectedAt == 0L) connectedAt = System.currentTimeMillis(); connectTimer?.cancel(); mutable.value = mutable.value.copy(state = "Connected"); publish() }
                PeerConnection.PeerConnectionState.DISCONNECTED -> { connected = false; mutable.value = mutable.value.copy(state = "Reconnecting…"); publish(); scheduleRestart() }
                PeerConnection.PeerConnectionState.FAILED -> { connected = false; publish(); run { restart() }; connectionDeadline() }
                else -> Unit
            }
        } })
        registerNetwork(); connectionDeadline()
        pendingIce.filter { it.optInt("generation") == generation }.forEach { candidate(it) }; pendingIce.clear()
    }
    private fun candidate(p: JSONObject) {
        peer?.ice(IceCandidate(if (p.isNull("sdpMid")) null else p.optString("sdpMid"), p.getInt("sdpMLineIndex"), p.getString("candidate")))
    }
    private fun connectionDeadline() {
        connectTimer?.cancel(); connectTimer = scope.launch { delay(30_000); if (!connected && snapshot != null) { runCatching { end() }; cleanup("Audio connection timed out") } }
    }
    private var restartJob: Job? = null
    private fun scheduleRestart() {
        if (restartJob?.isActive == true) return
        restartJob = scope.launch { delay(2000); if (!connected) runCatching { restart() }; connectionDeadline() }
    }
    private suspend fun restart() {
        val c = snapshot ?: return
        if (c.optString("state") != "accepted" || !consent) return
        if (c.getString("from_account") != accountId) { command("restart_request"); return }
        val result = command("restart")
        snapshot = result.getJSONObject("call")
        ensurePeer(snapshot!!.getInt("revision"))
        val offer = peer!!.offer(true)
        command("offer", JSONObject().put("generation", mediaGeneration).put("sdp", offer.description))
    }
    private fun registerNetwork() {
        if (networkCallback != null) return
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { scope.launch { if (snapshot?.optString("state") == "accepted" && peer != null) scheduleRestart() } }
        }.also { manager.registerDefaultNetworkCallback(it) }
    }
    private suspend fun event(action: String, call: JSONObject, payload: JSONObject, eventSeq: Long, actorDevice: String? = null) {
        if (eventSeq <= seq) return
        if (snapshot == null) {
            if (action == "incoming" && call.optString("state") == "ringing" && call.getString("to_account") == accountId) {
                OnlineCallService.incoming(context, call.getString("id"))
            }
            return
        }
        if (!owns(call.getString("id"))) return
        seq = eventSeq
        adopt(call)
        if (snapshot == null) return
        if (call.optString("state") == "accepted" && call.getString("to_account") == accountId && call.optString("to_device") != deviceId) { cleanup("Answered on another device"); return }
        if (!consent) return
        when (action) {
            "accept" -> if (call.getString("from_account") == accountId && peer == null) {
                ensurePeer(call.getInt("revision")); val offer = peer!!.offer(false)
                command("offer", JSONObject().put("generation", mediaGeneration).put("sdp", offer.description))
            }
            "offer" -> if (call.getString("to_account") == accountId && payload.optInt("generation") == call.getInt("revision")) {
                ensurePeer(call.getInt("revision")); peer!!.remote(SessionDescription.Type.OFFER, payload.getString("sdp"))
                val answer = peer!!.answer(); command("answer", JSONObject().put("generation", mediaGeneration).put("sdp", answer.description))
            }
            "answer" -> if (call.getString("from_account") == accountId && payload.optInt("generation") == mediaGeneration) peer?.remote(SessionDescription.Type.ANSWER, payload.getString("sdp"))
            "ice" -> if (actorDevice != deviceId) {
                if (peer == null || payload.optInt("generation") > mediaGeneration) pendingIce.add(payload) else if (payload.optInt("generation") == mediaGeneration) candidate(payload)
            }
            "restart_request" -> if (call.getString("from_account") == accountId) restart()
        }
    }
    private fun connect() {
        if (socket != null || api.store.load() == null) return
        val url = BuildConfig.VOZ_SERVICE_URL.replaceFirst("https://", "wss://") + "/v1/signal"
        socket = api.client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(JSONObject().put("type", "auth").put("accessToken", api.store.load()?.optString("accessToken")).toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) { scope.launch {
                eventLock.withLock {
                    try {
                        val m = JSONObject(text)
                        when (m.optString("type")) {
                            "ready" -> {
                                reconnectDelay = 1000
                                heartbeat?.cancel()
                                heartbeat = scope.launch {
                                    while (socket === webSocket) {
                                        snapshot?.takeIf { it.optString("state") == "accepted" }?.let {
                                            webSocket.send(JSONObject().put("type", "heartbeat").put("callId", it.getString("id")).toString())
                                        }
                                        delay(20_000)
                                    }
                                }
                                replay()
                            }
                            "call.event" -> {
                                // Fetch durable rows: own/remote order is identical on initial connect and reconnect.
                                val call = m.getJSONObject("call")
                                if (call.optString("state") == "ended" && owns(call.getString("id"))) cleanup(call.optString("reason", "Call ended"))
                                else replay(call.getString("id"))
                            }
                            "error" -> if (m.optString("error") == "UNAUTHORIZED") { webSocket.close(1000, "Refresh"); reconnectSocket(webSocket) }
                        }
                    } catch (e: Exception) { if (e.message == "CALL_NOT_FOUND" && snapshot != null) cleanup("Call unavailable") else report(e) }
                }
            } }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { scope.launch { reconnectSocket(webSocket) } }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { scope.launch { reconnectSocket(webSocket) } }
        })
    }
    private fun reconnectSocket(expected: WebSocket? = null) {
        if (expected != null && socket !== expected) return
        socket = null
        if ((!uiVisible && snapshot == null) || api.store.load() == null || reconnect?.isActive == true) return
        reconnect = scope.launch {
            while ((uiVisible || snapshot != null) && api.store.load() != null && socket == null) {
                delay(reconnectDelay); reconnectDelay = (reconnectDelay * 2).coerceAtMost(30_000)
                try { api.request("GET", "/v1/me"); connect() } catch (e: Exception) { report(e) }
            }
        }
    }
    private suspend fun replay(hint: String? = null) {
        val id = snapshot?.optString("id") ?: hint ?: api.request("GET", "/v1/calls/active").optJSONObject("call")?.optString("id") ?: return
        if (id in locallyEnded) return
        val result = api.request("GET", "/v1/calls/$id/events?after=$seq")
        if (id in locallyEnded) return
        val call = result.getJSONObject("call")
        if (snapshot == null && call.optString("state") == "ringing" && call.getString("to_account") == accountId) { incoming(id); return }
        if (snapshot == null && call.optString("state") == "accepted" &&
            (call.optString("from_device") == deviceId || call.optString("to_device") == deviceId)) adopt(call)
        val events = result.getJSONArray("events")
        for (i in 0 until events.length()) {
            val e = events.getJSONObject(i)
            // Replay against the latest authoritative snapshot; old terminal/accept states are not recreated.
            event(e.getString("type"), call, e.getJSONObject("payload"), e.getString("seq").toLong(), e.optString("actor_device"))
        }
        if (events.length() == 0 && snapshot != null) adopt(call)
        if (events.length() == 500 && snapshot != null) replay(id)
    }
    private fun cleanup(reason: String) {
        snapshot = null; consent = false; connected = false; connectedAt = 0; seq = 0; mediaGeneration = 0
        invitationTimer?.cancel(); connectTimer?.cancel(); restartJob?.cancel()
        peer?.close(); peer = null; pendingIce.clear()
        networkCallback?.let { (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(it) }; networkCallback = null
        if (audioHeld) {
            @Suppress("DEPRECATION")
            audio.isSpeakerphoneOn = false
            audio.mode = previousAudioMode
            focus?.let { audio.abandonAudioFocusRequest(it) }; focus = null; audioHeld = false
        }
        mutable.value = mutable.value.copy(callId = null, incoming = false, state = reason, muted = false, speaker = false)
        publish(); OnlineCallService.stop(context)
        if (!uiVisible) { heartbeat?.cancel(); socket?.close(1000, "Idle"); socket = null; reconnect?.cancel() }
    }
}
