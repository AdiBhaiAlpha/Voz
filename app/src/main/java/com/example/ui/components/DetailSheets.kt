package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.CallHistoryGrouper
import com.example.domain.ContactItem
import com.example.domain.PhoneNormalizer
import com.example.domain.SimSubscriptionItem
import com.example.ui.DetailSheetTarget
import com.example.ui.PendingCallConfirmation
import com.example.ui.PendingSimSelection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailBottomSheet(
    contact: ContactItem,
    onDismiss: () -> Unit,
    onCallNumber: (String) -> Unit,
    onSaveAssist: (note: String, label: String, isFav: Boolean, isImp: Boolean) -> Unit,
    onAddReminder: (reason: String, minutes: Int) -> Unit,
    onBlockNumber: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var noteText by remember { mutableStateOf(contact.customNote ?: "") }
    var labelText by remember { mutableStateOf(contact.customLabel ?: "") }
    var isFav by remember { mutableStateOf(contact.isFavorite) }
    var isImp by remember { mutableStateOf(contact.isImportant) }
    var showReminderDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("contact_detail_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (contact.displayName.firstOrNull() ?: '?').uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = contact.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (labelText.isNotBlank()) {
                        Text(
                            text = labelText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(
                    onClick = {
                        isFav = !isFav
                        onSaveAssist(noteText, labelText, isFav, isImp)
                    },
                    modifier = Modifier.testTag("contact_fav_button")
                ) {
                    Icon(
                        imageVector = if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Pin Favorite",
                        tint = if (isFav) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        isImp = !isImp
                        onSaveAssist(noteText, labelText, isFav, isImp)
                    },
                    modifier = Modifier.testTag("contact_important_button")
                ) {
                    Icon(
                        imageVector = if (isImp) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = "Important",
                        tint = if (isImp) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Phone Numbers
            Text(
                text = "Phone Numbers",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            val numbers = contact.numbers.ifEmpty {
                listOf(com.example.domain.ContactNumber(contact.primaryNumber, contact.primaryNormalizedNumber))
            }

            for (phone in numbers) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onCallNumber(phone.rawNumber) }
                        .testTag("contact_phone_item"),
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
                                text = PhoneNormalizer.formatForDisplay(phone.rawNumber),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = phone.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { onCallNumber(phone.rawNumber) },
                            modifier = Modifier.testTag("call_contact_phone_btn")
                        ) {
                            Icon(
                                Icons.Filled.Call,
                                contentDescription = "Call",
                                tint = Color(0xFF2E7D32)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Call Assist Note Editing
            Text(
                text = stringResource(R.string.assist_notes_title),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.assist_badge_local_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("Call Context Note") },
                placeholder = { Text(stringResource(R.string.assist_note_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("assist_note_input"),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = labelText,
                onValueChange = { labelText = it },
                label = { Text("Custom Label") },
                placeholder = { Text(stringResource(R.string.assist_label_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("assist_label_input"),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = { onSaveAssist(noteText, labelText, isFav, isImp) },
                    modifier = Modifier.testTag("save_assist_btn")
                ) {
                    Text(stringResource(R.string.assist_save))
                }

                OutlinedButton(
                    onClick = { showReminderDialog = true },
                    modifier = Modifier.testTag("add_reminder_btn")
                ) {
                    Icon(Icons.Filled.Alarm, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Call-Back")
                }

                TextButton(
                    onClick = { onBlockNumber(contact.primaryNumber) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("block_contact_btn")
                ) {
                    Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.block_action))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showReminderDialog) {
        var reason by remember { mutableStateOf("Call back ${contact.displayName}") }
        var minutes by remember { mutableStateOf(30) }

        AlertDialog(
            onDismissRequest = { showReminderDialog = false },
            title = { Text(stringResource(R.string.assist_reminder_add)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text(stringResource(R.string.assist_reminder_reason_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Remind in: $minutes minutes")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(15, 30, 60, 120).forEach { m ->
                            OutlinedButton(onClick = { minutes = m }) {
                                Text("${m}m")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onAddReminder(reason, minutes)
                    showReminderDialog = false
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showReminderDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallRecordDetailBottomSheet(
    detail: DetailSheetTarget.CallRecordDetail,
    onDismiss: () -> Unit,
    onCallNumber: (String) -> Unit,
    onSaveNote: (String) -> Unit,
    onBlockToggle: (String, Boolean) -> Unit,
    onDeleteRecord: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var note by remember(detail.recordId) { mutableStateOf(detail.note ?: "") }
    var confirmDelete by remember(detail.recordId) { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_record_title)) },
            text = { Text(stringResource(R.string.delete_record_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDeleteRecord() }) {
                    Text(stringResource(R.string.delete_record_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag("call_record_detail_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text(
                text = detail.displayName ?: PhoneNormalizer.formatForDisplay(detail.rawNumber),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = PhoneNormalizer.formatForDisplay(detail.rawNumber),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Call Back Button
            Button(
                onClick = { onCallNumber(detail.rawNumber) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("record_call_back_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                shape = RoundedCornerShape(25.dp)
            ) {
                Icon(Icons.Filled.Call, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Call Back ${PhoneNormalizer.formatForDisplay(detail.rawNumber)}")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Note field
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.assist_notes_title)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = { onSaveNote(note) }) {
                    Text(stringResource(R.string.assist_save))
                }

                OutlinedButton(
                    onClick = { onBlockToggle(detail.rawNumber, !detail.isBlocked) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (detail.isBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(if (detail.isBlocked) stringResource(R.string.unblock_action) else stringResource(R.string.block_action))
                }

                TextButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ConfirmBeforeCallDialog(
    confirmation: PendingCallConfirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_before_call_title)) },
        text = {
            Text(
                stringResource(
                    R.string.dialpad_confirm_ambiguous_body,
                    confirmation.displayName,
                    PhoneNormalizer.formatForDisplay(confirmation.rawNumber)
                )
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                modifier = Modifier.testTag("confirm_call_button")
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_confirm_call_button")
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun SimSelectionDialog(
    selection: PendingSimSelection,
    onSimChosen: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialpad_sim_select_title)) },
        text = {
            Column {
                Text(
                    text = "Calling ${PhoneNormalizer.formatForDisplay(selection.rawNumber)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                for (sim in selection.availableSims) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onSimChosen(sim.subscriptionId.toString()) }
                            .testTag("sim_select_item_${sim.slotIndex}"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = null, tint = Color(0xFF2E7D32))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = sim.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = sim.carrierName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
