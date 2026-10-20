package com.example.online

import android.content.Context
import org.json.JSONArray
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Native audio only; WebRTC provides Opus, AEC/NS, ICE and DTLS-SRTP. No backend media relay. */
class NativeVoicePeer(
    context: Context,
    ice: JSONArray,
    initiallyMuted: Boolean,
    private val onIce: (IceCandidate) -> Unit,
    private val onState: (PeerConnection.PeerConnectionState) -> Unit
) {
    private val initialized = initialize(context)
    private val audioDevice = JavaAudioDeviceModule.builder(context).createAudioDeviceModule()
    private val factory: PeerConnectionFactory
    private val source: AudioSource
    private val track: AudioTrack
    private val peer: PeerConnection
    private val queued = mutableListOf<IceCandidate>()
    private var remoteReady = false
    init {
        factory = PeerConnectionFactory.builder().setAudioDeviceModule(audioDevice).createPeerConnectionFactory()
        val servers = (0 until ice.length()).map { i ->
            val item = ice.getJSONObject(i)
            val urls = item.optJSONArray("urls") ?: JSONArray().put(item.getString("urls"))
            PeerConnection.IceServer.builder((0 until urls.length()).map { urls.getString(it) })
                .setUsername(item.optString("username")).setPassword(item.optString("credential")).createIceServer()
        }
        val config = PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = if (com.example.BuildConfig.VOZ_FORCE_RELAY) PeerConnection.IceTransportsType.RELAY else PeerConnection.IceTransportsType.ALL
        }
        peer = requireNotNull(factory.createPeerConnection(config, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) = Unit
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit
            override fun onIceCandidate(candidate: IceCandidate) { onIce(candidate) }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
            override fun onAddStream(stream: MediaStream?) = Unit
            override fun onRemoveStream(stream: MediaStream?) = Unit
            override fun onDataChannel(channel: DataChannel?) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) = Unit
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) { onState(state) }
        }))
        source = factory.createAudioSource(MediaConstraints())
        track = factory.createAudioTrack("voz-audio", source)
        track.setEnabled(!initiallyMuted)
        peer.addTrack(track, listOf("voz-voice"))
    }
    suspend fun offer(restart: Boolean): SessionDescription {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            if (restart) mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
        }
        return create(true, constraints).also { local(it) }
    }
    suspend fun answer(): SessionDescription = create(false, MediaConstraints()).also { local(it) }
    private suspend fun create(offer: Boolean, constraints: MediaConstraints): SessionDescription = suspendCancellableCoroutine { cont ->
        val observer = object : EmptySdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) { if (cont.isActive) { if (sdp != null) cont.resume(sdp) else cont.resumeWithException(IllegalStateException("Empty SDP")) } }
            override fun onCreateFailure(error: String?) { if (cont.isActive) cont.resumeWithException(IllegalStateException(error)) }
        }
        if (offer) peer.createOffer(observer, constraints) else peer.createAnswer(observer, constraints)
    }
    private suspend fun local(sdp: SessionDescription): Unit = suspendCancellableCoroutine { cont ->
        peer.setLocalDescription(object : EmptySdpObserver() {
            override fun onSetSuccess() { if (cont.isActive) cont.resume(Unit) }
            override fun onSetFailure(error: String?) { if (cont.isActive) cont.resumeWithException(IllegalStateException(error)) }
        }, sdp)
    }
    suspend fun remote(type: SessionDescription.Type, sdp: String) {
        suspendCancellableCoroutine<Unit> { cont ->
            peer.setRemoteDescription(object : EmptySdpObserver() {
                override fun onSetSuccess() { if (cont.isActive) cont.resume(Unit) }
                override fun onSetFailure(error: String?) { if (cont.isActive) cont.resumeWithException(IllegalStateException(error)) }
            }, SessionDescription(type, sdp))
        }
        remoteReady = true
        queued.toList().forEach { peer.addIceCandidate(it) }; queued.clear()
    }
    fun prepareGeneration() { remoteReady = false; queued.clear() }
    fun updateIce(ice: JSONArray) {
        val servers = (0 until ice.length()).map { i ->
            val item = ice.getJSONObject(i)
            val urls = item.optJSONArray("urls") ?: JSONArray().put(item.getString("urls"))
            PeerConnection.IceServer.builder((0 until urls.length()).map { urls.getString(it) })
                .setUsername(item.optString("username")).setPassword(item.optString("credential")).createIceServer()
        }
        peer.setConfiguration(PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = if (com.example.BuildConfig.VOZ_FORCE_RELAY) PeerConnection.IceTransportsType.RELAY else PeerConnection.IceTransportsType.ALL
        })
    }
    fun ice(candidate: IceCandidate) { if (remoteReady) peer.addIceCandidate(candidate) else queued.add(candidate) }
    fun mute(muted: Boolean) { track.setEnabled(!muted) }
    fun close() { peer.close(); peer.dispose(); track.dispose(); source.dispose(); factory.dispose(); audioDevice.release(); queued.clear() }
    companion object {
        private var ready = false
        @Synchronized private fun initialize(context: Context) {
            if (!ready) {
                PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context.applicationContext).createInitializationOptions())
                ready = true
            }
        }
    }
    private open class EmptySdpObserver : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetFailure(error: String?) = Unit
    }
}
