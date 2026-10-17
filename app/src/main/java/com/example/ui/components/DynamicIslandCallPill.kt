package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.PhoneNormalizer
import com.example.telecom.CallLifecycleState
import com.example.telecom.CallSessionController
import com.example.telecom.InCallActivity
import com.example.telecom.VozCallUiModel
import kotlinx.coroutines.delay

enum class DynamicIslandMode {
    INCOMING,
    CONNECTED_BRIEF,
    COMPACT_TIMER,
    EXPANDED_CONTROLS
}

// VOZ Dark Obsidian & Electric Lime dynamic palette
private val IslandObsidian = Color(0xFF06090B)
private val IslandSurface = Color(0xFF0F151A)
private val IslandBorder = Color(0xFF22303A)
private val IslandGlowLime = Color(0xFFA3E635)
private val IslandAnswerEmerald = Color(0xFF22C55E)
private val IslandDeclineCrimson = Color(0xFFEF4444)

@Composable
fun DynamicIslandCallPill(
    call: VozCallUiModel,
    modifier: Modifier = Modifier,
    onOpenFullScreen: (() -> Unit)? = null
) {
    val context = LocalContext.current

    // Live elapsed call timer
    var elapsedSeconds by remember(call.callId, call.connectTimeMillis) {
        val initial = if (call.connectTimeMillis > 0 && call.state == CallLifecycleState.ACTIVE) {
            maxOf(0L, (System.currentTimeMillis() - call.connectTimeMillis) / 1000L)
        } else 0L
        mutableLongStateOf(initial)
    }

    LaunchedEffect(call.connectTimeMillis, call.state) {
        if (call.state == CallLifecycleState.ACTIVE && call.connectTimeMillis > 0) {
            while (true) {
                val now = System.currentTimeMillis()
                elapsedSeconds = maxOf(0L, (now - call.connectTimeMillis) / 1000L)
                delay(1000L)
            }
        } else {
            elapsedSeconds = 0L
        }
    }

    // Determine current island mode
    var islandMode by remember(call.callId) {
        mutableStateOf(
            if (call.state == CallLifecycleState.RINGING) DynamicIslandMode.INCOMING
            else DynamicIslandMode.COMPACT_TIMER
        )
    }

    // React to call state transitions
    LaunchedEffect(call.state) {
        when (call.state) {
            CallLifecycleState.RINGING -> {
                islandMode = DynamicIslandMode.INCOMING
            }
            CallLifecycleState.ACTIVE -> {
                if (islandMode == DynamicIslandMode.INCOMING) {
                    islandMode = DynamicIslandMode.CONNECTED_BRIEF
                    delay(1800L)
                    if (islandMode == DynamicIslandMode.CONNECTED_BRIEF) {
                        islandMode = DynamicIslandMode.COMPACT_TIMER
                    }
                }
            }
            CallLifecycleState.DIALING -> {
                if (islandMode != DynamicIslandMode.EXPANDED_CONTROLS) {
                    islandMode = DynamicIslandMode.COMPACT_TIMER
                }
            }
            else -> {}
        }
    }

    // Subtle pulsing animation for ringing/active states
    val infiniteTransition = rememberInfiniteTransition(label = "island_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val waveformScale by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveform_scale"
    )

    val callerTitle = remember(call.displayName, call.rawNumber) {
        call.displayName?.takeIf { it.isNotBlank() }
            ?: PhoneNormalizer.formatForDisplay(call.rawNumber).ifBlank { "Unknown" }
    }

    val formattedTimer = remember(elapsedSeconds) {
        val m = elapsedSeconds / 60
        val s = elapsedSeconds % 60
        String.format("%02d:%02d", m, s)
    }

    // Border glow animation based on state
    val borderColor by animateColorAsState(
        targetValue = when (islandMode) {
            DynamicIslandMode.INCOMING -> IslandGlowLime.copy(alpha = 0.6f)
            DynamicIslandMode.CONNECTED_BRIEF -> IslandAnswerEmerald.copy(alpha = 0.7f)
            DynamicIslandMode.EXPANDED_CONTROLS -> IslandGlowLime.copy(alpha = 0.45f)
            DynamicIslandMode.COMPACT_TIMER -> IslandBorder
        },
        animationSpec = tween(400),
        label = "border_color"
    )

    // Outer shadow glow
    val shadowElevation = if (islandMode == DynamicIslandMode.EXPANDED_CONTROLS) 16.dp else 10.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 180.dp, max = 400.dp)
                .shadow(
                    elevation = shadowElevation,
                    shape = RoundedCornerShape(32.dp),
                    spotColor = IslandGlowLime.copy(alpha = 0.35f),
                    ambientColor = Color.Black
                )
                .clip(RoundedCornerShape(32.dp))
                .background(IslandObsidian)
                .border(
                    BorderStroke(
                        1.2.dp,
                        Brush.verticalGradient(
                            listOf(
                                borderColor,
                                IslandBorder.copy(alpha = 0.5f)
                            )
                        )
                    ),
                    shape = RoundedCornerShape(32.dp)
                )
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    if (islandMode == DynamicIslandMode.COMPACT_TIMER) {
                        islandMode = DynamicIslandMode.EXPANDED_CONTROLS
                    }
                }
                .testTag("dynamic_island_container"),
            color = IslandObsidian
        ) {
            when (islandMode) {
                DynamicIslandMode.INCOMING -> {
                    IncomingPillContent(
                        callerTitle = callerTitle,
                        rawNumber = call.rawNumber,
                        pulseScale = pulseScale,
                        onAnswer = {
                            CallSessionController.answerCall(call.callId)
                        },
                        onDecline = {
                            CallSessionController.declineOrHangupCall(call.callId)
                        }
                    )
                }

                DynamicIslandMode.CONNECTED_BRIEF -> {
                    ConnectedBriefContent(
                        callerTitle = callerTitle
                    )
                }

                DynamicIslandMode.COMPACT_TIMER -> {
                    CompactTimerContent(
                        callerTitle = callerTitle,
                        formattedTimer = formattedTimer,
                        waveformScale = waveformScale,
                        callState = call.state,
                        isOnlineCall = call.isVozOnlineCall,
                        onExpand = {
                            islandMode = DynamicIslandMode.EXPANDED_CONTROLS
                        }
                    )
                }

                DynamicIslandMode.EXPANDED_CONTROLS -> {
                    ExpandedControlsContent(
                        call = call,
                        callerTitle = callerTitle,
                        formattedTimer = formattedTimer,
                        waveformScale = waveformScale,
                        context = context,
                        onCollapse = {
                            islandMode = DynamicIslandMode.COMPACT_TIMER
                        },
                        onOpenFullScreen = onOpenFullScreen ?: {
                            val intent = Intent(context, InCallActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun IncomingPillContent(
    callerTitle: String,
    rawNumber: String,
    pulseScale: Float,
    onAnswer: () -> Unit,
    onDecline: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Avatar with glowing neon pulse ring
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .scale(pulseScale)
                    .background(IslandGlowLime.copy(alpha = 0.25f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(IslandSurface, CircleShape)
                    .border(1.dp, IslandGlowLime, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = callerTitle.take(1).uppercase(),
                    color = IslandGlowLime,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Center: Caller Info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = callerTitle,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(IslandGlowLime, CircleShape)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "VOZ Incoming Call",
                    color = IslandGlowLime,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right: Answer and Decline Buttons with 48dp touch targets
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Decline (Red)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(IslandDeclineCrimson)
                    .clickable(onClick = onDecline)
                    .testTag("island_decline_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "Decline Call",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Answer (Green)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(IslandAnswerEmerald)
                    .clickable(onClick = onAnswer)
                    .testTag("island_answer_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Answer Call",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ConnectedBriefContent(
    callerTitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = IslandAnswerEmerald,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Connected with $callerTitle",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CompactTimerContent(
    callerTitle: String,
    formattedTimer: String,
    waveformScale: Float,
    callState: CallLifecycleState,
    isOnlineCall: Boolean,
    onExpand: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Animated call glyph
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(IslandSurface, CircleShape)
                    .border(1.dp, IslandGlowLime.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (callState == CallLifecycleState.ACTIVE) Icons.Default.Call else Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = IslandGlowLime,
                    modifier = Modifier
                        .size(14.dp)
                        .scale(if (callState == CallLifecycleState.ACTIVE) 1f else waveformScale)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column {
                Text(
                    text = callerTitle,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isOnlineCall) {
                    Text(
                        text = "VOZ HD Online",
                        color = IslandGlowLime,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Right: Live Timer or State badge
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (callState == CallLifecycleState.ACTIVE) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(IslandAnswerEmerald, CircleShape)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = formattedTimer,
                    color = IslandGlowLime,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            } else {
                Text(
                    text = "Dialing…",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun ExpandedControlsContent(
    call: VozCallUiModel,
    callerTitle: String,
    formattedTimer: String,
    waveformScale: Float,
    context: Context,
    onCollapse: () -> Unit,
    onOpenFullScreen: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp)
    ) {
        // Top row: Caller identity + timer + window action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(IslandSurface, CircleShape)
                        .border(1.2.dp, IslandGlowLime, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = callerTitle.take(1).uppercase(),
                        color = IslandGlowLime,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = callerTitle,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (call.state == CallLifecycleState.ACTIVE) formattedTimer else "Connecting…",
                        color = if (call.state == CallLifecycleState.ACTIVE) IslandGlowLime else Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Full-screen mode button
                IconButton(
                    onClick = onOpenFullScreen,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Full Screen",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Collapse back to compact pill
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Collapse Pill",
                        tint = IslandGlowLime,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Inline recording restriction alert if any
        if (!call.recordingError.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2B1D0E), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = call.recordingError,
                    color = Color(0xFFFDE68A),
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { CallSessionController.clearRecordingError(call.callId) },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = Color(0xFFFDE68A),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Bottom row: Interactive call controls (Speaker, Mute, Record, Hangup)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Speaker toggle (Real Telecom audio route)
            IslandControlButton(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                label = "Speaker",
                isActive = call.isSpeakerOn,
                activeColor = IslandGlowLime,
                onClick = {
                    CallSessionController.toggleSpeaker()
                }
            )

            // Mute toggle (Real Telecom mute)
            IslandControlButton(
                icon = if (call.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                label = if (call.isMuted) "Muted" else "Mute",
                isActive = call.isMuted,
                activeColor = Color(0xFFF59E0B),
                onClick = {
                    CallSessionController.toggleMute()
                }
            )

            // Record action (Security compliant audio recording)
            IslandControlButton(
                icon = Icons.Default.FiberManualRecord,
                label = if (call.isRecording) "Rec..." else "Record",
                isActive = call.isRecording,
                activeColor = IslandDeclineCrimson,
                onClick = {
                    CallSessionController.toggleRecord(context, call.callId)
                }
            )

            // End Call (Real Telecom disconnect)
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(IslandDeclineCrimson)
                    .clickable {
                        CallSessionController.declineOrHangupCall(call.callId)
                    }
                    .testTag("island_hangup_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "Hang Up Call",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun IslandControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (isActive) activeColor else IslandSurface)
                .border(
                    1.dp,
                    if (isActive) activeColor else IslandBorder,
                    CircleShape
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) Color.Black else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (isActive) activeColor else Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
