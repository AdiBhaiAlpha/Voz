package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.preferences.ThemeMode
import com.example.domain.CallHistoryGrouper
import com.example.domain.PhoneNormalizer
import com.example.ui.VozUiState

@Composable
fun ToolsScreen(
    uiState: VozUiState,
    onRequestDefaultDialer: () -> Unit,
    onRequestCallScreening: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onLanguageChange: (String) -> Unit,
    onHapticChange: (Boolean) -> Unit,
    onSoundChange: (Boolean) -> Unit,
    onConfirmBeforeCallChange: (Boolean) -> Unit,
    onPreferredSimChange: (String) -> Unit,
    onBlockNumber: (rawNumber: String, reason: String) -> Unit,
    onUnblockNumber: (normalizedNumber: String) -> Unit,
    onCompleteReminder: (Long) -> Unit,
    onDeleteReminder: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddBlockDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.tools_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Default Dialer Role Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dialer_role_card"),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.isDefaultDialer) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (uiState.isDefaultDialer) Icons.Default.CheckCircle else Icons.Default.Phone,
                            contentDescription = null,
                            tint = if (uiState.isDefaultDialer) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.role_dialer_card_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (uiState.isDefaultDialer) stringResource(R.string.role_dialer_active)
                        else stringResource(R.string.role_dialer_inactive),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!uiState.isDefaultDialer) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onRequestDefaultDialer,
                            modifier = Modifier.testTag("set_default_dialer_btn")
                        ) {
                            Text(stringResource(R.string.role_dialer_request_btn))
                        }
                    }
                }
            }
        }

        // Call Screening Role Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("screening_role_card"),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.isCallScreeningRoleHeld) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = if (uiState.isCallScreeningRoleHeld) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Automatic Call Screening",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (uiState.isCallScreeningRoleHeld) "Call screening is active. Blocked numbers will be dropped automatically."
                        else "Enable Call Screening to automatically block unwanted spam and nuisance calls before your phone rings.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!uiState.isCallScreeningRoleHeld) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = onRequestCallScreening,
                            modifier = Modifier.testTag("set_screening_role_btn")
                        ) {
                            Text(stringResource(R.string.role_screening_request_btn))
                        }
                    }
                }
            }
        }

        // Call-Back Reminders
        if (uiState.reminders.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.section_reminders),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            items(uiState.reminders, key = { it.id }) { reminder ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = reminder.contactName, fontWeight = FontWeight.Bold)
                            Text(text = reminder.reason, style = MaterialTheme.typography.bodySmall)
                            Text(
                                text = "Due: ${CallHistoryGrouper.formatCallTime(reminder.scheduledMillis)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Row {
                            IconButton(onClick = { onCompleteReminder(reminder.id) }) {
                                Icon(Icons.Filled.Check, contentDescription = "Done", tint = Color(0xFF2E7D32))
                            }
                            IconButton(onClick = { onDeleteReminder(reminder.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        // Blocklist Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.blocklist_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(
                    onClick = { showAddBlockDialog = true },
                    modifier = Modifier.testTag("add_blocked_number_btn")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
            }
        }

        if (uiState.blockedNumbers.isEmpty()) {
            item {
                Text(
                    text = "No numbers currently blocked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(uiState.blockedNumbers, key = { it.normalizedNumber }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = PhoneNormalizer.formatForDisplay(item.rawNumber),
                                fontWeight = FontWeight.Bold
                            )
                            if (item.labelOrReason.isNotBlank()) {
                                Text(
                                    text = item.labelOrReason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        TextButton(
                            onClick = { onUnblockNumber(item.normalizedNumber) },
                            modifier = Modifier.testTag("unblock_btn_${item.normalizedNumber}")
                        ) {
                            Text(stringResource(R.string.unblock_action))
                        }
                    }
                }
            }
        }

        // Settings / Preferences Section
        item {
            Text(
                text = "Preferences",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Dialpad Sound Toggle
        item {
            SettingSwitchRow(
                title = stringResource(R.string.sound_setting_title),
                checked = uiState.preferences.dialpadSoundEnabled,
                onCheckedChange = onSoundChange
            )
        }

        // Dialpad Haptic Toggle
        item {
            SettingSwitchRow(
                title = stringResource(R.string.haptics_setting_title),
                checked = uiState.preferences.hapticFeedbackEnabled,
                onCheckedChange = onHapticChange
            )
        }

        // Confirm Before Call Toggle
        item {
            SettingSwitchRow(
                title = stringResource(R.string.confirm_before_call_title),
                subtitle = stringResource(R.string.confirm_before_call_subtitle),
                checked = uiState.preferences.confirmBeforeCallEnabled,
                onCheckedChange = onConfirmBeforeCallChange
            )
        }

        // Preferred SIM Selection (if multi-SIM)
        if (uiState.availableSims.size > 1) {
            item {
                Column {
                    Text(
                        text = "Preferred Calling SIM",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onPreferredSimChange("") },
                            colors = if (uiState.preferences.preferredSimId.isEmpty()) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            else ButtonDefaults.outlinedButtonColors()
                        ) {
                            Text("Always Ask")
                        }
                        for (sim in uiState.availableSims) {
                            OutlinedButton(
                                onClick = { onPreferredSimChange(sim.subscriptionId.toString()) },
                                colors = if (uiState.preferences.preferredSimId == sim.subscriptionId.toString()) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                else ButtonDefaults.outlinedButtonColors()
                            ) {
                                Text(sim.displayName)
                            }
                        }
                    }
                }
            }
        }

        // Privacy Guarantee Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.privacy_policy_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.privacy_policy_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showAddBlockDialog) {
        var num by remember { mutableStateOf("") }
        var reason by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddBlockDialog = false },
            title = { Text(stringResource(R.string.blocklist_add_number)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = num,
                        onValueChange = { num = it },
                        label = { Text(stringResource(R.string.blocklist_number_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text(stringResource(R.string.blocklist_reason_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (num.isNotBlank()) {
                            onBlockNumber(num, reason)
                            showAddBlockDialog = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBlockDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
