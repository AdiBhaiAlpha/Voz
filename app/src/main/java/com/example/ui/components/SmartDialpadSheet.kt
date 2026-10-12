package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.PhoneNormalizer
import com.example.ui.theme.JetBrainsMonoFontFamily

private data class DialpadKeySpec(
    val digit: Char,
    val subLabel: String,
    val longPressChar: Char? = null
)

private val dialpadRows = listOf(
    listOf(
        DialpadKeySpec('1', "VOZ"),
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
        DialpadKeySpec('*', ","),
        DialpadKeySpec('0', "+", longPressChar = '+'),
        DialpadKeySpec('#', ";")
    )
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SmartDialpadPanel(
    enteredText: String,
    hapticsEnabled: Boolean,
    oneHandedMode: Boolean,
    onDigitPress: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClearAll: () -> Unit,
    onPasteText: (String) -> Unit,
    onPlaceCall: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    val prefixHint = remember(enteredText) {
        PhoneNormalizer.describeNumberPrefix(enteredText)
    }
    val canDial = remember(enteredText) {
        PhoneNormalizer.isValidDialableNumber(enteredText)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("smart_dialpad_panel"),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (oneHandedMode) 36.dp else 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 14.dp
                ),
            horizontalAlignment = if (oneHandedMode) Alignment.End else Alignment.CenterHorizontally
        ) {
            // Display Header with Paste, Number Display, and Backspace
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = if (oneHandedMode) 340.dp else 500.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        val clip = clipboardManager.getText()?.text.orEmpty()
                        val sanitized = PhoneNormalizer.sanitizeDialInput(clip)
                        if (sanitized.isNotEmpty()) {
                            onPasteText(sanitized)
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("dialpad_paste_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentPaste,
                        contentDescription = stringResource(R.string.dialpad_paste_desc),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = enteredText.ifBlank { stringResource(R.string.dialpad_empty_hint) },
                        modifier = Modifier.testTag("dialpad_entered_display"),
                        style = if (enteredText.isBlank()) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.headlineMedium.copy(fontFamily = JetBrainsMonoFontFamily)
                        },
                        color = if (enteredText.isBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    if (prefixHint != null) {
                        Text(
                            text = prefixHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                val backspaceDesc = stringResource(R.string.dialpad_backspace_desc)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            enabled = enteredText.isNotEmpty(),
                            role = Role.Button,
                            onClick = {
                                if (hapticsEnabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                onBackspace()
                            },
                            onLongClick = {
                                if (hapticsEnabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                onClearAll()
                            }
                        )
                        .semantics { contentDescription = backspaceDesc }
                        .testTag("dialpad_backspace_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Backspace,
                        contentDescription = null,
                        tint = if (enteredText.isNotEmpty()) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4x3 Keypad Grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = if (oneHandedMode) 340.dp else 460.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                dialpadRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { keySpec ->
                            DialpadKeyButton(
                                keySpec = keySpec,
                                hapticsEnabled = hapticsEnabled,
                                onPress = onDigitPress,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Row: Hide Keypad & Direct Call Action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = if (oneHandedMode) 340.dp else 460.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("dialpad_hide_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.hide_dialpad),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val callDesc = stringResource(R.string.dialpad_call_desc)
                Button(
                    onClick = {
                        if (hapticsEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onPlaceCall()
                    },
                    enabled = canDial,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .height(56.dp)
                        .semantics { contentDescription = callDesc }
                        .testTag("dialpad_call_button"),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.dialpad_call_desc),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.size(48.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialpadKeyButton(
    keySpec: DialpadKeySpec,
    hapticsEnabled: Boolean,
    onPress: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val talkBackDesc = if (keySpec.longPressChar != null) {
        "Digit ${keySpec.digit}, ${keySpec.subLabel}, long press for plus"
    } else {
        "Digit ${keySpec.digit}, ${keySpec.subLabel}"
    }

    Box(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .combinedClickable(
                role = Role.Button,
                onClick = {
                    if (hapticsEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onPress(keySpec.digit)
                },
                onLongClick = {
                    if (keySpec.longPressChar != null) {
                        if (hapticsEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onPress(keySpec.longPressChar)
                    }
                }
            )
            .semantics { contentDescription = talkBackDesc }
            .testTag("dialpad_key_${keySpec.digit}"),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = keySpec.digit.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = keySpec.subLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
