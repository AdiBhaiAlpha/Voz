package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.VozAddress
import com.example.domain.PhoneNormalizer
import com.example.domain.SimSubscriptionItem

data class DialpadKeySpec(
    val digit: Char,
    val subLabel: String,
    val longPressChar: Char? = null
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SmartDialpad(
    currentInput: String,
    availableSims: List<SimSubscriptionItem>,
    isSoundEnabled: Boolean,
    isHapticEnabled: Boolean,
    onDigitPressed: (Char) -> Unit,
    onInputChange: (String) -> Unit,
    onEnableVoz: () -> Unit,
    onBackspace: () -> Unit,
    onClearAll: () -> Unit,
    onPlaceCall: (explicitSimId: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.72f

    val toneGen = remember(isSoundEnabled) {
        if (isSoundEnabled) {
            try {
                ToneGenerator(AudioManager.STREAM_DTMF, 80)
            } catch (_: Exception) {
                null
            }
        } else null
    }

    DisposableEffect(toneGen) {
        onDispose { toneGen?.release() }
    }
    val isVoz = VozAddress.isVoz(currentInput)
    val canCall = if (isVoz) VozAddress.isComplete(currentInput)
        else PhoneNormalizer.isValidDialableNumber(currentInput)

    fun playToneAndHaptic(char: Char) {
        if (isHapticEnabled) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vm?.defaultVibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                } else {
                    @Suppress("DEPRECATION")
                    val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    @Suppress("DEPRECATION")
                    v?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            } catch (_: Exception) {}
        }

        if (isSoundEnabled && toneGen != null) {
            val tone = when (char) {
                '0' -> ToneGenerator.TONE_DTMF_0
                '1' -> ToneGenerator.TONE_DTMF_1
                '2' -> ToneGenerator.TONE_DTMF_2
                '3' -> ToneGenerator.TONE_DTMF_3
                '4' -> ToneGenerator.TONE_DTMF_4
                '5' -> ToneGenerator.TONE_DTMF_5
                '6' -> ToneGenerator.TONE_DTMF_6
                '7' -> ToneGenerator.TONE_DTMF_7
                '8' -> ToneGenerator.TONE_DTMF_8
                '9' -> ToneGenerator.TONE_DTMF_9
                '*' -> ToneGenerator.TONE_DTMF_S
                '#' -> ToneGenerator.TONE_DTMF_P
                else -> ToneGenerator.TONE_PROP_BEEP
            }
            try {
                toneGen.startTone(tone, 100)
            } catch (_: Exception) {}
        }
    }

    val rows = listOf(
        listOf(
            DialpadKeySpec('1', ""),
            DialpadKeySpec('2', "ABC"),
            DialpadKeySpec('3', "DEF")
        ),
        listOf(
            DialpadKeySpec('4', "GHI"),
            DialpadKeySpec('5', "JKL"),
            DialpadKeySpec('6', "MNO")
        ),
        listOf(
            DialpadKeySpec('7', "PQRS"),
            DialpadKeySpec('8', "TUV"),
            DialpadKeySpec('9', "WXYZ")
        ),
        listOf(
            DialpadKeySpec('*', ""),
            DialpadKeySpec('0', "+", longPressChar = '+'),
            DialpadKeySpec('#', "")
        )
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxSheetHeight)
            .testTag("smart_dialpad_sheet"),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Mode selector: Cellular SIM vs VOZ ID
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { if (isVoz) onClearAll() },
                        shape = RoundedCornerShape(16.dp),
                        color = if (!isVoz) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier.testTag("dialpad_cellular_mode")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Call,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (!isVoz) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.voz_cellular_mode),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (!isVoz) FontWeight.Bold else FontWeight.Medium,
                                color = if (!isVoz) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        onClick = onEnableVoz,
                        shape = RoundedCornerShape(16.dp),
                        color = if (isVoz) Color(0xFF6750A4) else Color.Transparent,
                        modifier = Modifier.testTag("dialpad_voz_mode")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isVoz) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "VOZ",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isVoz) FontWeight.Bold else FontWeight.Medium,
                                color = if (isVoz) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (isVoz) {
                Text(
                    stringResource(if (canCall) R.string.voz_address_detected else R.string.voz_address_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Display area with formatted number and paste/backspace buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Paste remains available immediately after selecting VOZ mode.
                if (currentInput.isEmpty() || currentInput == VozAddress.PREFIX) {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clipText = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                            if (!clipText.isNullOrBlank()) {
                                // Keep the VOZ namespace intact; do not paste its digits as a SIM number.
                                onInputChange(if (VozAddress.isVoz(clipText))
                                    VozAddress.normalize(clipText) ?: clipText.trim()
                                else PhoneNormalizer.sanitizeDialInput(clipText))
                            }
                        },
                        modifier = Modifier.testTag("dialpad_paste_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = stringResource(R.string.dialpad_paste_desc),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }

                // Dialed Digits
                Text(
                    text = if (currentInput.isEmpty()) stringResource(R.string.dialpad_empty_hint)
                    else PhoneNormalizer.formatForDisplay(currentInput),
                    style = if (currentInput.length > 12) MaterialTheme.typography.titleLarge
                    else MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (currentInput.isEmpty()) MaterialTheme.colorScheme.outline
                    else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dialpad_input_display")
                )

                // Backspace button (with combined clickable: tap = 1 char, long press = clear all)
                if (currentInput.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .combinedClickable(
                                onClick = {
                                    playToneAndHaptic('0')
                                    onBackspace()
                                },
                                onLongClick = {
                                    playToneAndHaptic('0')
                                    onClearAll()
                                }
                            )
                            .testTag("dialpad_backspace_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = stringResource(R.string.dialpad_backspace_desc),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Keypad Grid
            for (row in rows) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (spec in row) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .combinedClickable(
                                    onClick = {
                                        if (!isVoz || spec.digit in '0'..'9') {
                                            playToneAndHaptic(spec.digit)
                                            onDigitPressed(spec.digit)
                                        }
                                    },
                                    onLongClick = {
                                        val longChar = if (isVoz) spec.digit else spec.longPressChar ?: spec.digit
                                        if (!isVoz || longChar in '0'..'9') {
                                            playToneAndHaptic(longChar)
                                            onDigitPressed(longChar)
                                        }
                                    }
                                )
                                .testTag("dialpad_key_${spec.digit}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = spec.digit.toString(),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (spec.subLabel.isNotEmpty()) {
                                    Text(
                                        text = spec.subLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        letterSpacing = 1.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Action Row: Standard Carrier Call + Dedicated VOZ Button
            if (!isVoz) {
                if (availableSims.size > 1) {
                    // Multi-SIM quick call buttons + dedicated VOZ button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (sim in availableSims.take(2)) {
                            Button(
                                onClick = { onPlaceCall(sim.subscriptionId.toString()) },
                                enabled = canCall,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp)
                                    .testTag("call_button_sim_${sim.slotIndex}"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                shape = RoundedCornerShape(27.dp)
                            ) {
                                Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "SIM ${sim.slotIndex + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Dedicated VOZ button right in the dialer action row
                        Button(
                            onClick = onEnableVoz,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .testTag("dialpad_voz_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                            shape = RoundedCornerShape(27.dp)
                        ) {
                            Icon(Icons.Filled.Bolt, contentDescription = "VOZ", modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "VOZ",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    // Single SIM Call button + Dedicated VOZ Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onPlaceCall(null) },
                            enabled = canCall,
                            modifier = Modifier
                                .weight(1.3f)
                                .height(56.dp)
                                .testTag("dialpad_call_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Call,
                                contentDescription = stringResource(R.string.dialpad_call_desc),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.voz_cellular_mode),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Dedicated VOZ Button right on dialer keyboard
                        Button(
                            onClick = onEnableVoz,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag("dialpad_voz_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = "VOZ",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "VOZ",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                // In VOZ mode: Return to SIM button + VOZ Call button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onClearAll,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .testTag("dialpad_switch_sim_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SIM Mode",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Button(
                        onClick = { onPlaceCall(null) },
                        enabled = canCall,
                        modifier = Modifier
                            .weight(1.8f)
                            .height(56.dp)
                            .testTag("dialpad_call_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = stringResource(R.string.dialpad_call_desc),
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.voz_online_call),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
