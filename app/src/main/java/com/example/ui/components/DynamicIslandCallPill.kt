package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.PhoneNormalizer
import com.example.telecom.*
import kotlinx.coroutines.delay

/** One expansion state and one size animation: no competing transition/measurement trees. */
@Composable
fun DynamicIslandCallPill(
    call: VozCallUiModel,
    modifier: Modifier = Modifier,
    onOpenFullScreen: (() -> Unit)? = null
) {
    var expanded by remember(call.callId) { mutableStateOf(false) }
    var elapsed by remember(call.callId) { mutableLongStateOf(0) }
    LaunchedEffect(call.callId, call.state, call.connectTimeMillis) {
        while (call.connectTimeMillis > 0) {
            elapsed = ((System.currentTimeMillis() - call.connectTimeMillis) / 1000).coerceAtLeast(0)
            delay(1000)
        }
    }
    val ringing = call.state == CallLifecycleState.RINGING
    val title = call.displayName?.takeIf { it.isNotBlank() }
        ?: PhoneNormalizer.formatForDisplay(call.rawNumber).ifBlank { "Unknown caller" }
    val lime = Color(0xFFA3E635)
    Surface(
        modifier = modifier.widthIn(max = 360.dp).fillMaxWidth()
            .animateContentSize(tween(180)).testTag("dynamic_island_container"),
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF0D1318), contentColor = Color.White,
        border = BorderStroke(1.dp, Color(0xFF29343B)), shadowElevation = 6.dp
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { expanded = !expanded }.padding(8.dp)) {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall)
                    Text(if (ringing) "Incoming call" else when (call.state) {
                        CallLifecycleState.ACTIVE -> "%02d:%02d".format(elapsed / 60, elapsed % 60)
                        CallLifecycleState.HOLDING -> "On hold"
                        CallLifecycleState.DIALING -> "Calling…"
                        else -> "Ending…"
                    }, color = lime, style = MaterialTheme.typography.labelMedium)
                }
                if (ringing) {
                    IconButton(onClick = { CallSessionController.answerCall(call.callId) },
                        modifier = Modifier.size(48.dp).testTag("island_answer_button")) {
                        Icon(Icons.Default.Call, "Answer", tint = lime)
                    }
                } else {
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(48.dp)) {
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            if (expanded) "Collapse controls" else "Expand controls")
                    }
                }
                IconButton(onClick = { CallSessionController.declineOrHangupCall(call.callId) },
                    modifier = Modifier.size(48.dp).testTag("island_decline_button")) {
                    Icon(Icons.Default.CallEnd, if (ringing) "Decline" else "End call", tint = Color(0xFFFF6B75))
                }
            }
            if (expanded) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = { CallSessionController.toggleMute() }, enabled = !ringing) {
                        Icon(if (call.isMuted) Icons.Default.MicOff else Icons.Default.Mic, "Toggle mute",
                            tint = if (call.isMuted) lime else Color.White)
                    }
                    IconButton(onClick = { CallSessionController.toggleSpeaker() }, enabled = !ringing) {
                        Icon(Icons.Default.SpeakerPhone, "Toggle speaker",
                            tint = if (call.isSpeakerOn) lime else Color.White)
                    }
                    if (call.isHoldSupported && !ringing) {
                        IconButton(onClick = { CallSessionController.toggleHold(call.callId) }) {
                            Icon(if (call.isOnHold) Icons.Default.PlayArrow else Icons.Default.Pause, "Toggle hold")
                        }
                    }
                    if (onOpenFullScreen != null) {
                        IconButton(onClick = onOpenFullScreen) { Icon(Icons.Default.Fullscreen, "Full call controls") }
                    }
                }
            }
        }
    }
}
