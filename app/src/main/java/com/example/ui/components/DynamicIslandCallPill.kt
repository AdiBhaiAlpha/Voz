package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.draw.shadow
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

private val IslandBlack = Color(0xFF000000)
private val IslandSurface = Color(0xFF0F151B)
private val IslandBorder = Color(0xFF263238)
private val IslandGlowLime = Color(0xFFA3E635)
private val IslandAnswerEmerald = Color(0xFF22C55E)
private val IslandDeclineCrimson = Color(0xFFEF4444)

/**
 * Authentic iPhone-style Dynamic Island pill:
 * - Centered at top of screen (never stretches full width)
 * - Compact pill during active call (~185dp x 38dp)
 * - Medium incoming pill with quick Answer/Decline buttons (~285dp x 54dp)
 * - Expanded controls card with Mute, Speaker, Hold, Hangup (~310dp)
 */
@Composable
fun DynamicIslandCallPill(
    call: VozCallUiModel,
    modifier: Modifier = Modifier,
    onOpenFullScreen: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var expanded by remember(call.callId) { mutableStateOf(false) }
    var elapsedSeconds by remember(call.callId, call.connectTimeMillis) {
        val initial = if (call.connectTimeMillis > 0 && call.state == CallLifecycleState.ACTIVE) {
            maxOf(0L, (System.currentTimeMillis() - call.connectTimeMillis) / 1000L)
        } else 0L
        mutableLongStateOf(initial)
    }

    LaunchedEffect(call.callId, call.state, call.connectTimeMillis) {
        if (call.state == CallLifecycleState.ACTIVE && call.connectTimeMillis > 0) {
            while (true) {
                elapsedSeconds = maxOf(0L, (System.currentTimeMillis() - call.connectTimeMillis) / 1000L)
                delay(1000L)
            }
        } else {
            elapsedSeconds = 0L
        }
    }

    val isRinging = call.state == CallLifecycleState.RINGING

    // Ensure unsaved numbers display the formatted phone number, never blank!
    val callerTitle = remember(call.displayName, call.rawNumber) {
        val name = call.displayName?.trim()?.takeIf { it.isNotBlank() && it != call.rawNumber && it != "null" }
        if (name != null) return@remember name
        val formatted = PhoneNormalizer.formatForDisplay(call.rawNumber).trim()
        if (formatted.isNotBlank()) return@remember formatted
        if (call.rawNumber.isNotBlank()) return@remember call.rawNumber.trim()
        "Unknown Caller"
    }

    val timerText = remember(elapsedSeconds) {
        val m = elapsedSeconds / 60
        val s = elapsedSeconds % 60
        String.format("%02d:%02d", m, s)
    }

    // Dynamic width constraints matching iPhone Dynamic Island
    val targetWidth = when {
        expanded -> 315.dp
        isRinging -> 285.dp
        else -> 190.dp
    }

    val shape = when {
        expanded -> RoundedCornerShape(26.dp)
        isRinging -> RoundedCornerShape(27.dp)
        else -> CircleShape
    }

    Box(
        modifier = modifier.wrapContentSize(Alignment.TopCenter),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            modifier = Modifier
                .width(targetWidth)
                .shadow(
                    elevation = if (expanded) 14.dp else 8.dp,
                    shape = shape,
                    spotColor = IslandGlowLime.copy(alpha = 0.25f),
                    ambientColor = Color.Black
                )
                .clip(shape)
                .background(IslandBlack)
                .border(BorderStroke(1.2.dp, IslandBorder), shape)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                .testTag("dynamic_island_container"),
            color = IslandBlack,
            shape = shape
        ) {
            when {
                // 1. Expanded controls view
                expanded -> {
                    ExpandedIslandContent(
                        call = call,
                        callerTitle = callerTitle,
                        timerText = timerText,
                        context = context,
                        onCollapse = { expanded = false },
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

                // 2. Incoming call pill (compact floating capsule)
                isRinging -> {
                    IncomingIslandContent(
                        callerTitle = callerTitle,
                        onAnswer = { CallSessionController.answerCall(call.callId) },
                        onDecline = { CallSessionController.declineOrHangupCall(call.callId) }
                    )
                }

                // 3. Compact active/dialing call pill (authentic iPhone shape)
                else -> {
                    CompactIslandContent(
                        callerTitle = callerTitle,
                        timerText = timerText,
                        callState = call.state,
                        onClick = { expanded = true }
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactIslandContent(
    callerTitle: String,
    timerText: String,
    callState: CallLifecycleState,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Call glyph
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(IslandSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (callState == CallLifecycleState.ACTIVE) Icons.Default.Call else Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = IslandGlowLime,
                    modifier = Modifier.size(13.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = callerTitle,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right: Live timer & green active dot
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (callState == CallLifecycleState.ACTIVE) IslandAnswerEmerald else Color(0xFFF59E0B))
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = if (callState == CallLifecycleState.ACTIVE) timerText else "Dialing…",
                color = IslandGlowLime,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun IncomingIslandContent(
    callerTitle: String,
    onAnswer: () -> Unit,
    onDecline: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Initial Avatar
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(IslandSurface)
                .border(1.dp, IslandGlowLime.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = callerTitle.take(1).uppercase(),
                color = IslandGlowLime,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Center: Caller Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = callerTitle,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Incoming Call…",
                color = IslandGlowLime,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right: Quick Answer & Decline buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Decline (Red)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(IslandDeclineCrimson)
                    .clickable(onClick = onDecline)
                    .testTag("island_decline_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "Decline",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Answer (Green)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(IslandAnswerEmerald)
                    .clickable(onClick = onAnswer)
                    .testTag("island_answer_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Answer",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ExpandedIslandContent(
    call: VozCallUiModel,
    callerTitle: String,
    timerText: String,
    context: Context,
    onCollapse: () -> Unit,
    onOpenFullScreen: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        // Top row: Identity & window buttons
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
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(IslandSurface)
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
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = callerTitle,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (call.state == CallLifecycleState.ACTIVE) timerText else "Connecting…",
                        color = IslandGlowLime,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onOpenFullScreen,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Full Screen",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ExpandLess,
                        contentDescription = "Collapse",
                        tint = IslandGlowLime,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Bottom row: Interactive controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Speaker
            IslandControlButton(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                label = "Speaker",
                isActive = call.isSpeakerOn,
                activeColor = IslandGlowLime,
                onClick = { CallSessionController.toggleSpeaker() }
            )

            // Mute
            IslandControlButton(
                icon = if (call.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                label = if (call.isMuted) "Muted" else "Mute",
                isActive = call.isMuted,
                activeColor = Color(0xFFF59E0B),
                onClick = { CallSessionController.toggleMute() }
            )

            // Hold
            if (call.isHoldSupported) {
                IslandControlButton(
                    icon = if (call.isOnHold) Icons.Default.PlayArrow else Icons.Default.Pause,
                    label = if (call.isOnHold) "Unhold" else "Hold",
                    isActive = call.isOnHold,
                    activeColor = Color(0xFF38BDF8),
                    onClick = { CallSessionController.toggleHold(call.callId) }
                )
            }

            // End Call
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(IslandDeclineCrimson)
                    .clickable { CallSessionController.declineOrHangupCall(call.callId) }
                    .testTag("island_hangup_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "End Call",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isActive) activeColor else IslandSurface)
                .border(1.dp, if (isActive) activeColor else IslandBorder, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) Color.Black else Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = if (isActive) activeColor else Color.White.copy(alpha = 0.7f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
