package com.example.telecom

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.PhoneNormalizer
import kotlinx.coroutines.delay

class InCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Configure lock-screen and turn-on display
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            km?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            VozCallerRoot(onDismiss = { finish() })
        }
    }

    override fun onResume() {
        super.onResume()
        CallPresentationPolicy.setFullScreenVisible(true, this)
        VozCallOverlayService.stop(this)
    }

    override fun onStop() {
        CallPresentationPolicy.setFullScreenVisible(false, this)
        super.onStop()
        if (CallSessionController.activeCalls.value.any {
            it.state != CallLifecycleState.DISCONNECTED && it.state != CallLifecycleState.DISCONNECTING
        } && com.example.data.preferences.UserPreferencesRepository(this)
                .loadPreferences().floatingIslandOverlayEnabled) {
            VozCallOverlayService.startIfPermitted(this)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

// Brand palette: Obsidian Black & Electric Neon Lime
private val VozObsidianBg = Color(0xFF090D10)
private val VozCardSurface = Color(0xFF131A20)
private val VozCardBorder = Color(0xFF202A32)
private val VozLimeGreen = Color(0xFFA3E635)
private val VozLimeGlow = Color(0xFFBEF264)
private val VozEmeraldAnswer = Color(0xFF22C55E)
private val VozCrimsonEnd = Color(0xFFEF4444)
private val VozCrimsonDark = Color(0xFFDC2626)

@Composable
fun VozCallerRoot(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activeCalls by CallSessionController.activeCalls.collectAsStateWithLifecycle()
    val audioState by CallSessionController.audioState.collectAsStateWithLifecycle()

    // Prioritize ringing incoming call (crucial for incoming calls and Call Waiting)
    val ringingCall = activeCalls.firstOrNull { it.state == CallLifecycleState.RINGING }
    val currentCall = ringingCall ?: activeCalls.firstOrNull()

    var recordingConsent by remember { mutableStateOf(false) }
    val micPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && currentCall != null) CallSessionController.toggleRecord(context, currentCall.callId)
        else if (!granted) android.widget.Toast.makeText(context, "Microphone permission was not granted.", android.widget.Toast.LENGTH_SHORT).show()
    }
    if (recordingConsent && currentCall != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { recordingConsent = false },
            title = { Text("Record microphone?") },
            text = { Text("Get consent from everyone first. Android may block capture during a call. This records the microphone, not guaranteed two-sided call audio.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                recordingConsent = false
                micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
            }) { Text("I have consent") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { recordingConsent = false }) { Text("Cancel") } }
        )
    }
    currentCall?.recordingError?.let { error ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { CallSessionController.clearRecordingError(currentCall.callId) },
            title = { Text("Recording unavailable") }, text = { Text(error) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = {
                CallSessionController.clearRecordingError(currentCall.callId)
            }) { Text("OK") } }
        )
    }

    // Dismiss activity when all calls have ended
    LaunchedEffect(activeCalls) {
        if (activeCalls.isEmpty()) {
            delay(1000)
            onDismiss()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = VozObsidianBg
    ) {
        if (currentCall == null) {
            VozCallEndedScreen(durationSeconds = 0L)
        } else {
            when {
                // Incoming Call Screen (or Call Waiting)
                (currentCall.isIncoming || currentCall.state == CallLifecycleState.RINGING) && currentCall.state == CallLifecycleState.RINGING -> {
                    VozIncomingCallScreen(
                        call = currentCall,
                        isCallWaiting = activeCalls.size > 1,
                        onAnswer = { CallSessionController.answerCall(currentCall.callId) },
                        onDecline = { CallSessionController.declineOrHangupCall(currentCall.callId) }
                    )
                }
                // Outgoing Call Screen (Dialing/Ringing before remote answer)
                !currentCall.isIncoming && (currentCall.state == CallLifecycleState.DIALING || currentCall.state == CallLifecycleState.RINGING) -> {
                    VozOutgoingCallScreen(
                        call = currentCall,
                        audioState = audioState,
                        onCancelCall = { CallSessionController.declineOrHangupCall(currentCall.callId) },
                        onToggleMute = { CallSessionController.toggleMute() },
                        onToggleSpeaker = { CallSessionController.toggleSpeaker() }
                    )
                }
                // Active Call Screen (Connected call with live duration timer)
                currentCall.state == CallLifecycleState.ACTIVE || currentCall.state == CallLifecycleState.HOLDING -> {
                    VozActiveCallScreen(
                        call = currentCall,
                        audioState = audioState,
                        onEndCall = { CallSessionController.declineOrHangupCall(currentCall.callId) },
                        onToggleMute = { CallSessionController.toggleMute() },
                        onToggleSpeaker = { CallSessionController.toggleSpeaker() },
                        onToggleHold = { CallSessionController.toggleHold(currentCall.callId) },
                        onToggleRecord = {
                            if (currentCall.isRecording) CallSessionController.toggleRecord(context, currentCall.callId)
                            else if (!currentCall.isVozOnlineCall) recordingConsent = true
                        },
                        onPlayDtmf = { digit -> CallSessionController.playDtmf(currentCall.callId, digit) },
                        onStopDtmf = { CallSessionController.stopDtmf(currentCall.callId) },
                        onBack = onDismiss
                    )
                }
                // Disconnecting or Disconnected
                else -> {
                    val isMissed = currentCall.isIncoming && currentCall.durationSeconds == 0L
                    VozCallEndedScreen(
                        durationSeconds = currentCall.durationSeconds,
                        isMissed = isMissed
                    )
                }
            }
        }
    }
}

/**
 * 1. VOZ Premium Incoming Call Screen (Truecaller-style caller verification & swipe/tap answer)
 */
@Composable
fun VozIncomingCallScreen(
    call: VozCallUiModel,
    isCallWaiting: Boolean = false,
    onAnswer: () -> Unit,
    onDecline: () -> Unit
) {
    val context = LocalContext.current

    // Trigger ringtone vibration pattern for incoming ringing call
    DisposableEffect(Unit) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        val pattern = longArrayOf(0, 800, 1000)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }

        onDispose {
            vibrator?.cancel()
        }
    }

    // Concentric pulsating rings animation
    val infiniteTransition = rememberInfiniteTransition(label = "incoming_pulse")
    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse1"
    )
    val pulseAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha1"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top VOZ Branding Badge / Call Waiting Alert
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isCallWaiting) Color(0xFF332005) else VozCardSurface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isCallWaiting) Color(0xFFF59E0B) else VozLimeGreen.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isCallWaiting) Icons.Default.Pause else Icons.Default.Shield,
                        contentDescription = null,
                        tint = if (isCallWaiting) Color(0xFFF59E0B) else VozLimeGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isCallWaiting) "CALL WAITING • CURRENT CALL WILL HOLD" else "VOZ CALL PROTECTION",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isCallWaiting) Color(0xFFF59E0B) else VozLimeGreen,
                        letterSpacing = 1.2.sp
                    )
                }
            }
        }

        // Center Truecaller-style Caller Card
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Pulsing Avatar
            Box(contentAlignment = Alignment.Center) {
                // Outer expanding glow ring
                Box(
                    modifier = Modifier
                        .size(116.dp)
                        .scale(pulseScale1)
                        .clip(CircleShape)
                        .background(VozLimeGreen.copy(alpha = pulseAlpha1))
                )

                // Solid avatar container
                Box(
                    modifier = Modifier
                        .size(116.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF22351E), Color(0xFF131F10))
                            )
                        )
                        .border(2.dp, VozLimeGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val hasName = call.hasContactName
                    val initialChar = if (hasName) (call.displayName?.firstOrNull() ?: '?') else (call.rawNumber.firstOrNull() ?: '?')
                    val initial = initialChar.uppercaseChar().toString()
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = VozLimeGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Caller Name (guaranteed never blank for unsaved numbers)
            val displayTitle = call.displayTitle
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = Color.White
            )

            // Number line if display name is present
            if (call.hasContactName && call.rawNumber.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = PhoneNormalizer.formatForDisplay(call.rawNumber),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Truecaller Trust & Verification Badge
            val isVerified = call.hasContactName
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isVerified) Color(0xFF162A14) else VozCardSurface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isVerified) VozLimeGreen.copy(alpha = 0.5f) else VozCardBorder
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isVerified) Icons.Default.CheckCircle else Icons.Default.Security,
                        contentDescription = null,
                        tint = if (isVerified) VozLimeGreen else Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isVerified) "Verified Contact • Local SIM" else "Unsaved Incoming Number",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isVerified) VozLimeGreen else Color(0xFF94A3B8)
                    )
                }
            }

            // Local Call Assist note if exists
            if (!call.assistNote.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1C3E)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "📌 Note: ${call.assistNote}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE9D5FF),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // State label
            Text(
                text = "INCOMING CARRIER CALL",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = VozLimeGlow,
                letterSpacing = 2.sp
            )
        }

        // Bottom Answer & Decline Buttons (Prominent Truecaller-style circular controls)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Decline Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = onDecline,
                    modifier = Modifier
                        .size(76.dp)
                        .testTag("incoming_decline_button"),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = VozCrimsonEnd),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CallEnd,
                        contentDescription = stringResource(R.string.action_decline),
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.action_decline),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            // Answer Button
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = onAnswer,
                    modifier = Modifier
                        .size(76.dp)
                        .testTag("incoming_answer_button"),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = VozEmeraldAnswer),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = stringResource(R.string.action_answer),
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.action_answer),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = VozLimeGreen
                )
            }
        }
    }
}

/**
 * 2. VOZ Premium Outgoing Call Screen (Calling / Ringing before recipient answers)
 */
@Composable
fun VozOutgoingCallScreen(
    call: VozCallUiModel,
    audioState: VozAudioState,
    onCancelCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit
) {
    // Breathing animation for outgoing ring
    val transition = rememberInfiniteTransition(label = "outgoing_breath")
    val breathScale by transition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top VOZ Branding Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = VozCardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, VozLimeGreen.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        tint = VozLimeGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "VOZ OUTGOING CALL",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = VozLimeGreen,
                        letterSpacing = 1.2.sp
                    )
                }
            }
        }

        // Center Recipient Info
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(breathScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF192C15), Color(0xFF0F1B0D))
                        )
                    )
                    .border(2.dp, VozLimeGreen.copy(alpha = 0.8f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val hasName = call.hasContactName
                val initialChar = if (hasName) (call.displayName?.firstOrNull() ?: '?') else (call.rawNumber.firstOrNull() ?: '?')
                val initial = initialChar.uppercaseChar().toString()
                Text(
                    text = initial,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black,
                    color = VozLimeGreen
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            val displayTitle = call.displayTitle
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = Color.White
            )

            if (call.hasContactName && call.rawNumber.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = PhoneNormalizer.formatForDisplay(call.rawNumber),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Real Telecom Outgoing State Indicator
            val statusText = when (call.state) {
                CallLifecycleState.RINGING -> "Ringing recipient phone…"
                CallLifecycleState.DIALING -> "Connecting through cellular network…"
                else -> "Connecting…"
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = VozLimeGlow
            )
        }

        // Quick Controls & Cancel Button
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VozCallRoundToggle(
                    icon = if (audioState.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    label = stringResource(R.string.action_mute),
                    isActive = audioState.isMuted,
                    onClick = onToggleMute,
                    testTag = "outgoing_mute_button"
                )

                VozCallRoundToggle(
                    icon = if (audioState.isSpeaker) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeDown,
                    label = stringResource(R.string.action_speaker),
                    isActive = audioState.isSpeaker,
                    onClick = onToggleSpeaker,
                    testTag = "outgoing_speaker_button"
                )
            }

            // Cancel / End Call Button
            Button(
                onClick = onCancelCall,
                modifier = Modifier
                    .height(64.dp)
                    .fillMaxWidth(0.75f)
                    .testTag("outgoing_end_call_button"),
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VozCrimsonEnd)
            ) {
                Icon(
                    imageVector = Icons.Filled.CallEnd,
                    contentDescription = stringResource(R.string.action_hangup),
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Cancel Call",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * 3. VOZ Premium Active Call Screen (Active Telecom session with real duration timer)
 */
@Composable
fun VozActiveCallScreen(
    call: VozCallUiModel,
    audioState: VozAudioState,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleHold: () -> Unit,
    onToggleRecord: () -> Unit,
    onPlayDtmf: (Char) -> Unit,
    onStopDtmf: () -> Unit,
    onBack: () -> Unit
) {
    var showDtmfKeypad by remember { mutableStateOf(false) }

    // Live Duration Timer - only ticks when call is genuinely ACTIVE
    var elapsedSeconds by remember(call.connectTimeMillis) {
        val initial = if (call.connectTimeMillis > 0) {
            (System.currentTimeMillis() - call.connectTimeMillis) / 1000L
        } else call.durationSeconds
        mutableLongStateOf(maxOf(0L, initial))
    }

    LaunchedEffect(call.state, call.connectTimeMillis) {
        if (call.state == CallLifecycleState.ACTIVE && call.connectTimeMillis > 0) {
            while (true) {
                val current = (System.currentTimeMillis() - call.connectTimeMillis) / 1000L
                elapsedSeconds = maxOf(0L, current)
                delay(1000)
            }
        }
    }

    val timerFormatted = remember(elapsedSeconds) {
        val hrs = elapsedSeconds / 3600
        val mins = (elapsedSeconds % 3600) / 60
        val secs = elapsedSeconds % 60
        if (hrs > 0) {
            String.format("%02d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Navigation & Security Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("active_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.close_drawer),
                    tint = Color.White
                )
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = VozCardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, VozLimeGreen.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(VozLimeGreen)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (call.isOnHold) "CALL ON HOLD" else "ACTIVE TELECOM CALL",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (call.isOnHold) Color(0xFFFBBF24) else VozLimeGreen,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(modifier = Modifier.size(48.dp))
        }

        // Center Caller Showcase & Duration
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(108.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF192C15), Color(0xFF0F1B0D))
                        )
                    )
                    .border(2.dp, if (call.isOnHold) Color(0xFFFBBF24) else VozLimeGreen, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val hasName = call.hasContactName
                val initialChar = if (hasName) (call.displayName?.firstOrNull() ?: '?') else (call.rawNumber.firstOrNull() ?: '?')
                val initial = initialChar.uppercaseChar().toString()
                Text(
                    text = initial,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = if (call.isOnHold) Color(0xFFFBBF24) else VozLimeGreen
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            val displayTitle = call.displayTitle
            Text(
                text = displayTitle,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = Color.White
            )

            if (call.hasContactName && call.rawNumber.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = PhoneNormalizer.formatForDisplay(call.rawNumber),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.65f),
                    fontFamily = FontFamily.Monospace
                )
            }

            if (!call.assistNote.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1C3E)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "📌 ${call.assistNote}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE9D5FF),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Real Call Duration Timer
            Text(
                text = if (call.isOnHold) "Paused on Hold" else timerFormatted,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (call.isOnHold) Color(0xFFFBBF24) else VozLimeGreen
            )

            if (!call.isOnHold && call.state == CallLifecycleState.ACTIVE) {
                VozAudioEqualizerWave(isActive = true)
            }
        }

        // DTMF In-Call Keypad (Overlay Card)
        AnimatedVisibility(
            visible = showDtmfKeypad,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = VozCardSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, VozCardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val keypadRows = listOf(
                        listOf('1', '2', '3'),
                        listOf('4', '5', '6'),
                        listOf('7', '8', '9'),
                        listOf('*', '0', '#')
                    )
                    for (row in keypadRows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (digit in row) {
                                Button(
                                    onClick = {
                                        onPlayDtmf(digit)
                                        onStopDtmf()
                                    },
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(54.dp),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF1F2933)
                                    ),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                ) {
                                    Text(
                                        text = digit.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4-Button Truecaller-style In-Call Grid
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VozCallRoundToggle(
                    icon = if (audioState.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    label = stringResource(R.string.action_mute),
                    isActive = audioState.isMuted,
                    onClick = onToggleMute,
                    testTag = "active_mute_button"
                )

                VozCallRoundToggle(
                    icon = if (audioState.isSpeaker) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeDown,
                    label = stringResource(R.string.action_speaker),
                    isActive = audioState.isSpeaker,
                    onClick = onToggleSpeaker,
                    testTag = "active_speaker_button"
                )

                VozCallRoundToggle(
                    icon = Icons.Filled.Dialpad,
                    label = stringResource(R.string.action_keypad),
                    isActive = showDtmfKeypad,
                    onClick = { showDtmfKeypad = !showDtmfKeypad },
                    testTag = "active_keypad_button"
                )

                VozCallRoundToggle(
                    icon = Icons.Filled.FiberManualRecord,
                    label = if (call.isRecording) "Stop rec" else "Mic rec",
                    isActive = call.isRecording,
                    activeColor = VozCrimsonEnd,
                    onClick = onToggleRecord,
                    testTag = "active_record_button"
                )

                if (call.isHoldSupported) {
                    VozCallRoundToggle(
                        icon = if (call.isOnHold) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        label = stringResource(R.string.action_hold),
                        isActive = call.isOnHold,
                        onClick = onToggleHold,
                        testTag = "active_hold_button"
                    )
                }
            }

            // End Call Button
            Button(
                onClick = onEndCall,
                modifier = Modifier
                    .height(64.dp)
                    .fillMaxWidth(0.8f)
                    .testTag("active_end_call_button"),
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VozCrimsonDark)
            ) {
                Icon(
                    imageVector = Icons.Filled.CallEnd,
                    contentDescription = stringResource(R.string.action_hangup),
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.action_hangup),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * 4. Call Disconnected / Ended Screen
 */
@Composable
fun VozCallEndedScreen(
    durationSeconds: Long,
    isMissed: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(VozCardSurface)
                    .border(
                        1.dp,
                        if (isMissed) Color(0xFFF59E0B) else VozCrimsonEnd.copy(alpha = 0.5f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isMissed) Icons.Filled.Call else Icons.Filled.CallEnd,
                    contentDescription = null,
                    tint = if (isMissed) Color(0xFFF59E0B) else VozCrimsonEnd,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isMissed) stringResource(R.string.call_type_missed)
                else stringResource(R.string.call_state_disconnected),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (isMissed) Color(0xFFFBBF24) else Color.White
            )
            if (durationSeconds > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                val mins = durationSeconds / 60
                val secs = durationSeconds % 60
                Text(
                    text = String.format("Call duration: %02d:%02d", mins, secs),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun VozCallRoundToggle(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    testTag: String,
    activeColor: Color = VozLimeGreen,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp)
    ) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier
                .size(60.dp)
                .testTag(testTag),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isActive) activeColor else VozCardSurface,
                contentColor = if (isActive) Color.Black else Color.White
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = if (isActive) VozLimeGreen else Color.White.copy(alpha = 0.75f)
        )
    }
}

@Composable
private fun VozAudioEqualizerWave(isActive: Boolean) {
    if (!isActive) return
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")
    val heights = (1..5).map { index ->
        infiniteTransition.animateFloat(
            initialValue = 10f,
            targetValue = (20 + (index * 6)) % 36 + 10f,
            animationSpec = infiniteRepeatable(
                animation = tween(280 + index * 90, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "eq_$index"
        )
    }

    Row(
        modifier = Modifier
            .padding(top = 12.dp)
            .height(32.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (h in heights) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .width(4.dp)
                    .height(h.value.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(VozLimeGreen)
            )
        }
    }
}
