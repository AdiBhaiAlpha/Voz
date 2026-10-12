package com.example.telecom

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.CallHistoryGrouper
import com.example.domain.PhoneNormalizer
import com.example.ui.theme.CallAnswerGreen
import com.example.ui.theme.CallDeclineRed
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.VozTheme
import kotlinx.coroutines.delay

class InCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureLockScreenPresentation()
        enableEdgeToEdge()

        setContent {
            val activeCalls by CallSessionController.activeCalls.collectAsStateWithLifecycle()
            val audioState by CallSessionController.audioState.collectAsStateWithLifecycle()

            // Close InCallActivity automatically when all active calls have ended
            LaunchedEffect(activeCalls.size) {
                if (activeCalls.isEmpty()) {
                    delay(600L)
                    if (CallSessionController.activeCalls.value.isEmpty()) {
                        finish()
                    }
                }
            }

            VozTheme(themeMode = ThemeMode.DARK) {
                val primaryCall = activeCalls.firstOrNull()
                val secondaryCall = activeCalls.getOrNull(1)

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (primaryCall != null) {
                        InCallScreenContent(
                            call = primaryCall,
                            secondaryCall = secondaryCall,
                            audioState = audioState,
                            onAnswer = { CallSessionController.answerCall(it) },
                            onDeclineOrHangup = { CallSessionController.declineOrHangupCall(it) },
                            onToggleMute = { CallSessionController.toggleMute() },
                            onToggleSpeaker = { CallSessionController.toggleSpeaker() },
                            onToggleHold = { CallSessionController.toggleHold(it) },
                            onDtmfDigit = { callId, digit ->
                                CallSessionController.playDtmf(callId, digit)
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.call_state_disconnected),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }
        }
    }

    private fun configureLockScreenPresentation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }
}

@Composable
fun InCallScreenContent(
    call: VozCallUiModel,
    secondaryCall: VozCallUiModel?,
    audioState: VozAudioState,
    onAnswer: (String) -> Unit,
    onDeclineOrHangup: (String) -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleHold: (String) -> Unit,
    onDtmfDigit: (String, Char) -> Unit
) {
    var showDtmfPad by rememberSaveable { mutableStateOf(false) }
    var dtmfSequence by rememberSaveable { mutableStateOf("") }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(call.state, call.connectTimeMillis) {
        if (call.state == CallLifecycleState.ACTIVE) {
            while (true) {
                val base = if (call.connectTimeMillis > 0L) {
                    call.connectTimeMillis
                } else {
                    System.currentTimeMillis()
                }
                elapsedSeconds = ((System.currentTimeMillis() - base) / 1000L).coerceAtLeast(0L)
                delay(1000L)
            }
        }
    }

    val stateLabel = when (call.state) {
        CallLifecycleState.RINGING -> stringResource(R.string.call_state_ringing)
        CallLifecycleState.DIALING, CallLifecycleState.CONNECTING -> stringResource(R.string.call_state_dialing)
        CallLifecycleState.ACTIVE -> CallHistoryGrouper.formatDuration(elapsedSeconds)
        CallLifecycleState.HOLDING -> stringResource(R.string.call_state_holding)
        CallLifecycleState.DISCONNECTING -> stringResource(R.string.call_state_disconnecting)
        CallLifecycleState.DISCONNECTED -> stringResource(R.string.call_state_disconnected)
    }

    val prefixInfo = remember(call.rawNumber) {
        PhoneNormalizer.describeNumberPrefix(call.rawNumber)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Call Waiting Banner if a second call is present
        if (secondaryCall != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = secondaryCall.displayName ?: secondaryCall.rawNumber.ifBlank {
                                stringResource(R.string.unknown_caller)
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (secondaryCall.state == CallLifecycleState.RINGING) {
                                "Call Waiting • Incoming"
                            } else {
                                "Second Call • On Hold"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (secondaryCall.state == CallLifecycleState.RINGING) {
                        Button(
                            onClick = { onAnswer(secondaryCall.callId) },
                            colors = ButtonDefaults.buttonColors(containerColor = CallAnswerGreen)
                        ) {
                            Text(text = stringResource(R.string.action_answer), color = Color.White)
                        }
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Caller Identity & Confidence Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!call.photoUri.isNullOrBlank()) {
                    AsyncImage(
                        model = call.photoUri,
                        contentDescription = call.displayName ?: call.rawNumber,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val initials = (call.displayName ?: call.rawNumber).trim().take(2).uppercase().ifEmpty { "#" }
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // State Pill
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = stateLabel,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("incall_state_label"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Primary Name or Number
            Text(
                text = call.displayName ?: call.rawNumber.ifBlank { stringResource(R.string.unknown_caller) },
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            if (call.displayName != null && call.rawNumber.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = call.rawNumber,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Honest Caller Confidence Badge (never claims unverified caller identity is authoritative)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (call.isKnownContact) {
                    Icon(
                        imageVector = Icons.Outlined.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Saved in Device Contacts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (prefixInfo != null) {
                    Text(
                        text = "Unsaved Caller • $prefixInfo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Unsaved Number",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Local VOZ Call Assist Context Note (if saved by user)
            if (!call.assistNote.isNullOrBlank() || !call.assistLabel.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        if (!call.assistLabel.isNullOrBlank()) {
                            Text(
                                text = call.assistLabel.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        if (!call.assistNote.isNullOrBlank()) {
                            Text(
                                text = call.assistNote,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Optional In-Call DTMF Keypad
            if (showDtmfPad && call.state != CallLifecycleState.RINGING) {
                Spacer(modifier = Modifier.height(16.dp))
                if (dtmfSequence.isNotEmpty()) {
                    Text(
                        text = dtmfSequence,
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                val rows = listOf(
                    listOf('1', '2', '3'),
                    listOf('4', '5', '6'),
                    listOf('7', '8', '9'),
                    listOf('*', '0', '#')
                )
                rows.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        row.forEach { digit ->
                            FilledTonalIconButton(
                                onClick = {
                                    dtmfSequence += digit
                                    onDtmfDigit(call.callId, digit)
                                },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Text(
                                    text = digit.toString(),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Controls: Incoming (Answer / Decline) vs Ongoing (Mute / Speaker / Hold / Keypad / Hangup)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (call.state == CallLifecycleState.RINGING) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val declineDesc = stringResource(R.string.action_decline)
                    Button(
                        onClick = { onDeclineOrHangup(call.callId) },
                        modifier = Modifier
                            .height(64.dp)
                            .weight(1f)
                            .padding(end = 12.dp)
                            .semantics { contentDescription = declineDesc }
                            .testTag("incall_decline_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = CallDeclineRed),
                        shape = RoundedCornerShape(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CallEnd,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = declineDesc,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }

                    val answerDesc = stringResource(R.string.action_answer)
                    Button(
                        onClick = { onAnswer(call.callId) },
                        modifier = Modifier
                            .height(64.dp)
                            .weight(1f)
                            .padding(start = 12.dp)
                            .semantics { contentDescription = answerDesc }
                            .testTag("incall_answer_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = CallAnswerGreen),
                        shape = RoundedCornerShape(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = answerDesc,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                    }
                }
            } else {
                // Active Call Controls Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    InCallControlToggle(
                        icon = if (audioState.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                        label = stringResource(R.string.action_mute),
                        active = audioState.isMuted,
                        testTag = "incall_mute_button",
                        onClick = onToggleMute
                    )

                    InCallControlToggle(
                        icon = Icons.Filled.VolumeUp,
                        label = stringResource(R.string.action_speaker),
                        active = audioState.isSpeakerOn,
                        testTag = "incall_speaker_button",
                        onClick = onToggleSpeaker
                    )

                    InCallControlToggle(
                        icon = if (call.state == CallLifecycleState.HOLDING) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        label = stringResource(R.string.action_hold),
                        active = call.state == CallLifecycleState.HOLDING,
                        testTag = "incall_hold_button",
                        onClick = { onToggleHold(call.callId) }
                    )

                    InCallControlToggle(
                        icon = Icons.Filled.Dialpad,
                        label = stringResource(R.string.action_keypad),
                        active = showDtmfPad,
                        testTag = "incall_keypad_button",
                        onClick = { showDtmfPad = !showDtmfPad }
                    )
                }

                // End Call Button
                val hangupDesc = stringResource(R.string.action_hangup)
                Button(
                    onClick = { onDeclineOrHangup(call.callId) },
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(64.dp)
                        .semantics { contentDescription = hangupDesc }
                        .testTag("incall_hangup_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = CallDeclineRed),
                    shape = RoundedCornerShape(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CallEnd,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = hangupDesc,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun InCallControlToggle(
    icon: ImageVector,
    label: String,
    active: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier
                .size(60.dp)
                .testTag(testTag),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (active) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        ) {
            Icon(imageVector = icon, contentDescription = label)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
