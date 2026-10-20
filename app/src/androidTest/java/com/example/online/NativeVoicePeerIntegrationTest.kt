package com.example.online

import android.Manifest
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.rule.GrantPermissionRule
import com.example.telecom.VozOnlineActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription

/** Actual native SDK negotiation and RTP between two peers on ONE emulator.
 * This does NOT test public signaling, accounts, TURN, FCM, physical microphones,
 * human-audible speech or a call between two Android devices/networks.
 */
class NativeVoicePeerIntegrationTest {
    @get:Rule(order = 0) val microphone = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)
    @get:Rule(order = 1) val activity = ActivityScenarioRule(VozOnlineActivity::class.java)

    @Test fun twoNativePeersNegotiateAndExchangeAudioRtp() = runBlocking {
        withContext(Dispatchers.Main) {
            val callerIce = Channel<IceCandidate>(Channel.UNLIMITED)
            val answererIce = Channel<IceCandidate>(Channel.UNLIMITED)
            val callerConnected = CompletableDeferred<Unit>()
            val answererConnected = CompletableDeferred<Unit>()
            fun observe(connected: CompletableDeferred<Unit>, state: PeerConnection.PeerConnectionState) {
                when (state) {
                    PeerConnection.PeerConnectionState.CONNECTED -> connected.complete(Unit)
                    PeerConnection.PeerConnectionState.FAILED -> connected.completeExceptionally(AssertionError("Native ICE/DTLS failed"))
                    else -> Unit
                }
            }
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val caller = NativeVoicePeer(context, JSONArray(), false,
                { callerIce.trySend(it) }, { observe(callerConnected, it) })
            var answerer: NativeVoicePeer? = null
            try {
                val remote = NativeVoicePeer(context, JSONArray(), false,
                    { answererIce.trySend(it) }, { observe(answererConnected, it) })
                answerer = remote
                val forwardCallerIce = launch { for (candidate in callerIce) remote.ice(candidate) }
                val forwardAnswererIce = launch { for (candidate in answererIce) caller.ice(candidate) }
                try {
                    withTimeout(30_000) {
                        val offer = caller.offer(false)
                        remote.remote(SessionDescription.Type.OFFER, offer.description)
                        val answer = remote.answer()
                        caller.remote(SessionDescription.Type.ANSWER, answer.description)
                        callerConnected.await(); answererConnected.await()
                        // Native RTP counters, not UI timers or a mocked CONNECTED state.
                        while (true) {
                            val first = caller.audioPackets()
                            val second = remote.audioPackets()
                            if (first.sent > 0 && first.received > 0 && second.sent > 0 && second.received > 0) {
                                assertTrue(first.received > 0 && second.received > 0)
                                break
                            }
                            delay(100)
                        }
                    }
                } finally {
                    forwardCallerIce.cancelAndJoin(); forwardAnswererIce.cancelAndJoin()
                }
            } finally {
                callerIce.close(); answererIce.close()
                answerer?.close(); caller.close()
            }
        }
    }
}
