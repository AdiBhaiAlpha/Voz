package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.CallHistoryGrouper
import com.example.domain.CallRecord
import com.example.domain.CallType
import com.example.domain.ContactItem
import com.example.domain.GroupedCallSession
import com.example.ui.DetailSheetTarget
import com.example.ui.theme.CallAnswerGreen
import com.example.ui.theme.CallDeclineRed
import com.example.ui.theme.CallMissedAmber
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun ContactRowCard(
    contact: ContactItem,
    onCallClick: (ContactItem) -> Unit,
    onDetailClick: (ContactItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onDetailClick(contact) }
            .testTag("contact_card_${contact.contactId}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar or Initials
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!contact.photoUri.isNullOrBlank()) {
                    AsyncImage(
                        model = contact.photoUri,
                        contentDescription = contact.displayName,
                        modifier = Modifier.size(48.dp)
                    )
                } else {
                    Text(
                        text = contact.initials,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = contact.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (contact.isImportant) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = contact.customLabel?.ifBlank { "IMPORTANT" } ?: "IMPORTANT",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (!contact.customLabel.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = contact.customLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = contact.primaryNumber,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (contact.numbers.size > 1) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "+${contact.numbers.size - 1} more",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (contact.isBlocked) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.blocked_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                if (!contact.customNote.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Note: ${contact.customNote}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = { onCallClick(contact) },
                modifier = Modifier
                    .size(48.dp)
                    .testTag("call_contact_${contact.contactId}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = "Call ${contact.displayName}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun GroupedCallSessionCard(
    session: GroupedCallSession,
    languageCode: String,
    onCallBackClick: (CallRecord) -> Unit,
    onSessionDetailClick: (CallRecord) -> Unit,
    onDeleteSessionClick: ((List<Long>) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val rec = session.primaryRecord
    val (typeIcon, typeLabel, typeColor) = when (rec.callType) {
        CallType.MISSED -> Triple(
            Icons.AutoMirrored.Filled.CallMissed,
            stringResource(R.string.call_type_missed),
            CallMissedAmber
        )
        CallType.INCOMING -> Triple(
            Icons.AutoMirrored.Filled.CallReceived,
            stringResource(R.string.call_type_incoming),
            CallAnswerGreen
        )
        CallType.OUTGOING -> Triple(
            Icons.AutoMirrored.Filled.CallMade,
            stringResource(R.string.call_type_outgoing),
            MaterialTheme.colorScheme.primary
        )
        CallType.REJECTED -> Triple(
            Icons.AutoMirrored.Filled.CallMissed,
            stringResource(R.string.call_type_rejected),
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        CallType.BLOCKED -> Triple(
            Icons.Filled.Block,
            stringResource(R.string.call_type_blocked),
            CallDeclineRed
        )
        CallType.UNKNOWN -> Triple(
            Icons.Outlined.Info,
            stringResource(R.string.call_type_incoming),
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    val title = rec.displayIdentity.ifBlank { stringResource(R.string.unknown_caller) }
    val timeFormatted = remember(rec.timestampMillis, languageCode) {
        CallHistoryGrouper.formatTime(rec.timestampMillis, languageCode)
    }
    val durationFormatted = remember(rec.durationSeconds, languageCode) {
        if (rec.durationSeconds > 0L) {
            " • ${CallHistoryGrouper.formatDuration(rec.durationSeconds, languageCode)}"
        } else {
            ""
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onSessionDetailClick(rec) }
            .testTag("call_session_${session.sessionKey}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(typeColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = typeIcon,
                    contentDescription = typeLabel,
                    tint = typeColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (session.hasMissedCall) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (session.hasMissedCall) CallMissedAmber else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (session.callCount > 1) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "×${session.callCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Call Type + Timestamp + Duration (never color-only status!)
                Text(
                    text = "$typeLabel • $timeFormatted$durationFormatted",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (rec.isKnownContact && rec.rawNumber.isNotBlank()) {
                    Text(
                        text = rec.rawNumber,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Clearly distinguish local app-created VOZ Call Assist metadata from system call records
                if (!rec.assistNote.isNullOrBlank() || !rec.assistLabel.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            append("[Call Assist] ")
                            if (!rec.assistLabel.isNullOrBlank()) append("${rec.assistLabel}: ")
                            if (!rec.assistNote.isNullOrBlank()) append(rec.assistNote)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (onDeleteSessionClick != null) {
                IconButton(
                    onClick = { onDeleteSessionClick(session.records.map { it.id }) },
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("delete_call_session_${session.sessionKey}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete call record",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (rec.rawNumber.isNotBlank()) {
                IconButton(
                    onClick = { onCallBackClick(rec) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("callback_session_${session.sessionKey}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = "Call back $title",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ContactAssistDetailSheet(
    target: DetailSheetTarget,
    onDismiss: () -> Unit,
    onCallNumber: (String, String) -> Unit,
    onSaveAssist: (note: String, label: String, isFav: Boolean, isImp: Boolean) -> Unit,
    onDeleteAssist: () -> Unit,
    onScheduleReminder: (reason: String, minutes: Int) -> Unit,
    onBlockNumber: (String) -> Unit,
    onUnblockNumber: (String) -> Unit
) {
    var noteInput by rememberSaveable(target.normalizedNumber) { mutableStateOf(target.note) }
    var labelInput by rememberSaveable(target.normalizedNumber) { mutableStateOf(target.customLabel) }
    var isFav by rememberSaveable(target.normalizedNumber) { mutableStateOf(target.isFavorite) }
    var isImp by rememberSaveable(target.normalizedNumber) { mutableStateOf(target.isImportant) }
    var reminderReason by rememberSaveable(target.normalizedNumber) { mutableStateOf("") }
    var showBlockConfirm by remember { mutableStateOf(false) }

    if (showBlockConfirm) {
        AlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            title = {
                Text(text = stringResource(R.string.block_confirm_title, target.displayName))
            },
            text = {
                Text(text = stringResource(R.string.block_confirm_body))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBlockConfirm = false
                        onBlockNumber(target.rawNumber)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_block_dialog_button")
                ) {
                    Text(text = stringResource(R.string.block_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlockConfirm = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = target.displayName,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (target.normalizedNumber.isNotBlank()) {
                Text(
                    text = "Normalized ID: ${target.normalizedNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // All Phone Numbers for this Contact
            target.numbers.forEachIndexed { idx, num ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = num.rawNumber,
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = num.typeLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { onCallNumber(num.rawNumber, target.displayName) },
                            modifier = Modifier.testTag("detail_call_number_$idx"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Icon(imageVector = Icons.Filled.Call, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(R.string.dialpad_call_desc))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(14.dp))

            // VOZ Call Assist Section (Local App Metadata)
            Text(
                text = stringResource(R.string.assist_notes_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.assist_badge_local_only),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = isFav,
                    onClick = { isFav = !isFav },
                    label = { Text(stringResource(R.string.assist_mark_favorite)) },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isFav) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.testTag("detail_toggle_favorite")
                )

                FilterChip(
                    selected = isImp,
                    onClick = { isImp = !isImp },
                    label = { Text(stringResource(R.string.assist_mark_important)) },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isImp) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.testTag("detail_toggle_important")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = labelInput,
                onValueChange = { labelInput = it },
                label = { Text(stringResource(R.string.assist_label_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("detail_label_input")
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = noteInput,
                onValueChange = { noteInput = it },
                label = { Text(stringResource(R.string.assist_note_hint)) },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("detail_note_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onSaveAssist(noteInput, labelInput, isFav, isImp) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("detail_save_assist_button")
                ) {
                    Text(text = stringResource(R.string.assist_save))
                }
                if (target.note.isNotBlank() || target.customLabel.isNotBlank() || target.isFavorite || target.isImportant) {
                    OutlinedButton(
                        onClick = {
                            noteInput = ""
                            labelInput = ""
                            isFav = false
                            isImp = false
                            onDeleteAssist()
                        },
                        modifier = Modifier.testTag("detail_delete_assist_button")
                    ) {
                        Text(text = stringResource(R.string.assist_delete))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(14.dp))

            // Call-Back Reminder Section
            Text(
                text = stringResource(R.string.assist_reminder_add),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = reminderReason,
                onValueChange = { reminderReason = it },
                label = { Text(stringResource(R.string.assist_reminder_reason_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("detail_reminder_reason_input")
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(15 to "+15m", 30 to "+30m", 60 to "+1h").forEach { (mins, label) ->
                    AssistChip(
                        onClick = {
                            onScheduleReminder(reminderReason, mins)
                            reminderReason = ""
                        },
                        label = { Text(label) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("detail_schedule_reminder_$mins")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(14.dp))

            // Block / Unblock Action
            if (target.isBlocked) {
                FilledTonalButton(
                    onClick = { onUnblockNumber(target.rawNumber) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("detail_unblock_button")
                ) {
                    Text(text = stringResource(R.string.unblock_action))
                }
            } else if (target.rawNumber.isNotBlank()) {
                OutlinedButton(
                    onClick = { showBlockConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("detail_block_button"),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(imageVector = Icons.Filled.Block, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.block_action))
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
