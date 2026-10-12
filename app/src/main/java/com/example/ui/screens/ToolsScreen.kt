package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddModerator
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.domain.PhoneNormalizer
import com.example.ui.VozUiState
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ThemeMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolsScreen(
    uiState: VozUiState,
    onRequestDefaultDialerRole: () -> Unit,
    onRequestCallScreeningRole: () -> Unit,
    onBlockNumber: (number: String, reason: String) -> Unit,
    onUnblockNumber: (String) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onLanguageChange: (String) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onSoundChange: (Boolean) -> Unit,
    onOneHandedChange: (Boolean) -> Unit,
    onReduceMotionChange: (Boolean) -> Unit,
    onConfirmBeforeCallChange: (Boolean) -> Unit,
    onPreferredSimChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var blockInputNumber by rememberSaveable { mutableStateOf("") }
    var blockInputReason by rememberSaveable { mutableStateOf("") }
    var pendingBlockConfirmation by remember { mutableStateOf<Pair<String, String>?>(null) }

    pendingBlockConfirmation?.let { (num, reason) ->
        AlertDialog(
            onDismissRequest = { pendingBlockConfirmation = null },
            title = { Text(stringResource(R.string.block_confirm_title, num)) },
            text = { Text(stringResource(R.string.block_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        pendingBlockConfirmation = null
                        onBlockNumber(num, reason)
                        blockInputNumber = ""
                        blockInputReason = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("tools_confirm_block_button")
                ) {
                    Text(stringResource(R.string.block_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingBlockConfirmation = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("tools_screen"),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "tools_header") {
            Text(
                text = stringResource(R.string.tools_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // 1. Default Dialer & Call Screening Role Setup Card
        item(key = "role_management_card") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tools_role_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (uiState.isDefaultDialer) Icons.Filled.CheckCircle else Icons.Filled.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.role_dialer_card_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (uiState.isDefaultDialer) {
                            stringResource(R.string.role_dialer_active)
                        } else {
                            stringResource(R.string.role_dialer_inactive)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!uiState.isDefaultDialer) {
                            Button(
                                onClick = onRequestDefaultDialerRole,
                                modifier = Modifier.testTag("request_default_dialer_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Text(
                                    text = stringResource(R.string.role_dialer_request_btn),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (!uiState.isCallScreeningEnabled) {
                            OutlinedButton(
                                onClick = onRequestCallScreeningRole,
                                modifier = Modifier.testTag("request_call_screening_button")
                            ) {
                                Text(text = stringResource(R.string.role_screening_request_btn))
                            }
                        }
                    }
                }
            }
        }

        // 2. User-Managed Blocked Numbers (Spam & Unwanted Callers)
        item(key = "blocklist_management_card") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tools_blocklist_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.AddModerator,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "${stringResource(R.string.blocklist_title)} (${uiState.blockedNumbers.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.block_confirm_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = blockInputNumber,
                        onValueChange = { blockInputNumber = it },
                        label = { Text(stringResource(R.string.blocklist_number_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("blocklist_number_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = blockInputReason,
                        onValueChange = { blockInputReason = it },
                        label = { Text(stringResource(R.string.blocklist_reason_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("blocklist_reason_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val norm = PhoneNormalizer.normalizeForComparison(blockInputNumber)
                            if (norm.isNotEmpty()) {
                                pendingBlockConfirmation = blockInputNumber.trim() to blockInputReason.trim()
                            }
                        },
                        enabled = PhoneNormalizer.normalizeForComparison(blockInputNumber).isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("blocklist_add_button")
                    ) {
                        Icon(imageVector = Icons.Filled.Block, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.blocklist_add_number))
                    }
                }
            }
        }

        if (uiState.blockedNumbers.isNotEmpty()) {
            items(
                items = uiState.blockedNumbers,
                key = { "blocked_${it.normalizedNumber}" }
            ) { blocked ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("blocked_item_${blocked.normalizedNumber}"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
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
                                text = blocked.originalNumber,
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = buildString {
                                    append("Normalized: ${blocked.normalizedNumber}")
                                    if (blocked.reason.isNotBlank()) append(" • ${blocked.reason}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { onUnblockNumber(blocked.normalizedNumber) },
                            modifier = Modifier.testTag("unblock_btn_${blocked.normalizedNumber}")
                        ) {
                            Text(text = stringResource(R.string.unblock_action))
                        }
                    }
                }
            }
        }

        // 3. Appearance & Localization Settings Card
        item(key = "appearance_and_language_card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = stringResource(R.string.theme_setting_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = uiState.preferences.themeMode == ThemeMode.SYSTEM,
                            onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
                            label = { Text(stringResource(R.string.theme_system)) },
                            modifier = Modifier.testTag("theme_chip_system")
                        )
                        FilterChip(
                            selected = uiState.preferences.themeMode == ThemeMode.LIGHT,
                            onClick = { onThemeModeChange(ThemeMode.LIGHT) },
                            label = { Text(stringResource(R.string.theme_light)) },
                            modifier = Modifier.testTag("theme_chip_light")
                        )
                        FilterChip(
                            selected = uiState.preferences.themeMode == ThemeMode.DARK,
                            onClick = { onThemeModeChange(ThemeMode.DARK) },
                            label = { Text(stringResource(R.string.theme_dark)) },
                            modifier = Modifier.testTag("theme_chip_dark")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.language_setting_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = uiState.preferences.languageCode == "en",
                            onClick = { onLanguageChange("en") },
                            label = { Text(stringResource(R.string.lang_english)) },
                            modifier = Modifier.testTag("lang_chip_en")
                        )
                        FilterChip(
                            selected = uiState.preferences.languageCode == "bn",
                            onClick = { onLanguageChange("bn") },
                            label = { Text(stringResource(R.string.lang_bangla)) },
                            modifier = Modifier.testTag("lang_chip_bn")
                        )
                    }
                }
            }
        }

        // 4. Multi-SIM Selection Preference (shown when device exposes SIM accounts)
        item(key = "sim_preference_card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.SimCard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "SIM & Calling Preferences",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (uiState.availableSims.isEmpty()) {
                        Text(
                            text = "Single SIM or default system SIM routing active. On dual-SIM devices with Phone State permission granted, VOZ lets you pin a preferred SIM or ask before every call.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = uiState.preferences.preferredSimId.isEmpty(),
                                onClick = { onPreferredSimChange("") },
                                label = { Text("Ask Every Time") }
                            )
                            uiState.availableSims.forEach { sim ->
                                FilterChip(
                                    selected = uiState.preferences.preferredSimId == sim.id,
                                    onClick = { onPreferredSimChange(sim.id) },
                                    label = { Text("${sim.label} (${sim.carrierName})") }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    PreferenceToggleRow(
                        title = stringResource(R.string.confirm_before_call_title),
                        subtitle = stringResource(R.string.confirm_before_call_subtitle),
                        checked = uiState.preferences.confirmBeforeCallEnabled,
                        testTag = "toggle_confirm_before_call",
                        onCheckedChange = onConfirmBeforeCallChange
                    )

                    PreferenceToggleRow(
                        title = stringResource(R.string.haptics_setting_title),
                        subtitle = "Tactile vibration feedback when pressing dialpad keys",
                        checked = uiState.preferences.hapticFeedbackEnabled,
                        testTag = "toggle_haptics",
                        onCheckedChange = onHapticsChange
                    )

                    PreferenceToggleRow(
                        title = stringResource(R.string.sound_setting_title),
                        subtitle = "Play standard DTMF audio tones on keypad input",
                        checked = uiState.preferences.dialpadSoundEnabled,
                        testTag = "toggle_dtmf_sound",
                        onCheckedChange = onSoundChange
                    )

                    PreferenceToggleRow(
                        title = stringResource(R.string.one_handed_setting_title),
                        subtitle = "Shift dialpad to a thumb-friendly compact width",
                        checked = uiState.preferences.oneHandedModeEnabled,
                        testTag = "toggle_one_handed",
                        onCheckedChange = onOneHandedChange
                    )

                    PreferenceToggleRow(
                        title = stringResource(R.string.reduce_motion_setting_title),
                        subtitle = "Minimize screen transitions for accessibility comfort",
                        checked = uiState.preferences.reduceMotionEnabled,
                        testTag = "toggle_reduce_motion",
                        onCheckedChange = onReduceMotionChange
                    )
                }
            }
        }

        // 5. Privacy Policy & Diagnostic Information (Excludes sensitive personal data)
        item(key = "privacy_and_diagnostics_card") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("privacy_diagnostics_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.privacy_policy_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.privacy_policy_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.diagnostics_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val diagText = buildString {
                        appendLine("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        appendLine("Android SDK: API ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
                        appendLine("Default Dialer Role: ${if (uiState.isDefaultDialer) "GRANTED" else "NOT HELD"}")
                        appendLine("Call Screening Role: ${if (uiState.isCallScreeningEnabled) "GRANTED" else "NOT HELD"}")
                        appendLine("Contacts Access: ${if (uiState.hasContactsPermission) "GRANTED" else "DENIED"}")
                        appendLine("Call Log Access: ${if (uiState.hasCallLogPermission) "GRANTED" else "DENIED"}")
                        appendLine("Network Permission: NONE (Offline-By-Design)")
                    }
                    Text(
                        text = diagText.trimEnd(),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PreferenceToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
