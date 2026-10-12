package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneCallback
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.CallReminderEntity
import com.example.domain.CallRecord
import com.example.domain.ContactItem
import com.example.domain.PhoneNormalizer
import com.example.ui.VozUiState
import com.example.ui.components.ContactRowCard
import com.example.ui.components.GroupedCallSessionCard
import com.example.ui.components.SmartDialpadPanel
import com.example.ui.theme.CallMissedAmber
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun HomeScreen(
    uiState: VozUiState,
    onSearchOrDialChange: (String) -> Unit,
    onDialpadDigit: (Char) -> Unit,
    onDialpadBackspace: () -> Unit,
    onDialpadClearAll: () -> Unit,
    onToggleDialpad: (Boolean) -> Unit,
    onRequestCall: (number: String, name: String) -> Unit,
    onOpenContactDetail: (ContactItem) -> Unit,
    onOpenCallRecordDetail: (CallRecord) -> Unit,
    onCompleteReminder: (Long) -> Unit,
    onRequestPermissionsClick: () -> Unit,
    onRequestDefaultDialerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val query = uiState.homeSearchOrDialInput.trim()
    val isDialableQuery = remember(query) {
        PhoneNormalizer.isValidDialableNumber(query)
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("home_lazy_column"),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = if (uiState.isDialpadVisible) 410.dp else 100.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Editorial VOZ Brand Header & Trust Status
            item(key = "home_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.testTag("voz_brand_header")
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(50)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "OFFLINE-FIRST",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        Text(
                            text = stringResource(R.string.app_tagline),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (uiState.missedCallCount > 0) {
                        Surface(
                            color = CallMissedAmber.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "${uiState.missedCallCount} Missed",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = CallMissedAmber,
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("home_missed_badge")
                            )
                        }
                    }
                }
            }

            // 2. Fast Intent-First Search Field (Name, বাংলা, or T9 Digits)
            item(key = "home_search_bar") {
                OutlinedTextField(
                    value = uiState.homeSearchOrDialInput,
                    onValueChange = onSearchOrDialChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_placeholder),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (uiState.homeSearchOrDialInput.isNotEmpty()) {
                                IconButton(
                                    onClick = onDialpadClearAll,
                                    modifier = Modifier.testTag("home_search_clear_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Clear,
                                        contentDescription = "Clear search"
                                    )
                                }
                            }
                            IconButton(
                                onClick = { onToggleDialpad(!uiState.isDialpadVisible) },
                                modifier = Modifier.testTag("home_toggle_dialpad_icon")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Dialpad,
                                    contentDescription = stringResource(R.string.open_dialpad),
                                    tint = if (uiState.isDialpadVisible) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_search_input")
                )
            }

            // 3. Direct Dial Prompt when user entered a valid phone number
            if (query.isNotEmpty() && isDialableQuery) {
                item(key = "direct_dial_card") {
                    val prefixDesc = PhoneNormalizer.describeNumberPrefix(query)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRequestCall(query, "") }
                            .testTag("direct_dial_action_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.direct_dial_prompt, query),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (prefixDesc != null) {
                                    Text(
                                        text = prefixDesc,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Filled.Call,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // 4. Matching Contacts when searching or typing on T9 keypad
            if (query.isNotEmpty()) {
                if (uiState.homeMatchedContacts.isNotEmpty()) {
                    item(key = "matched_contacts_header") {
                        Text(
                            text = "${stringResource(R.string.section_matched_contacts)} (${uiState.homeMatchedContacts.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(
                        items = uiState.homeMatchedContacts,
                        key = { "match_${it.contactId}_${it.primaryNormalizedNumber}" }
                    ) { contact ->
                        ContactRowCard(
                            contact = contact,
                            onCallClick = { onRequestCall(it.primaryNumber, it.displayName) },
                            onDetailClick = onOpenContactDetail
                        )
                    }
                } else if (!uiState.hasContactsPermission) {
                    item(key = "search_contacts_perm_prompt") {
                        PermissionInlineCard(
                            title = stringResource(R.string.contacts_permission_title),
                            body = stringResource(R.string.contacts_permission_body),
                            buttonLabel = stringResource(R.string.grant_permission),
                            testTag = "home_grant_contacts_button",
                            onAction = onRequestPermissionsClick
                        )
                    }
                }
            } else {
                // 5. Default Dialer Role Banner if not yet Default Dialer
                if (!uiState.isDefaultDialer) {
                    item(key = "default_dialer_banner") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_default_dialer_card"),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.VerifiedUser,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.role_dialer_card_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.role_dialer_inactive),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = onRequestDefaultDialerClick,
                                    modifier = Modifier.testTag("home_set_default_dialer_button")
                                ) {
                                    Text(text = "Enable")
                                }
                            }
                        }
                    }
                }

                // 6. Active Call-Back Reminders (VOZ Call Assist)
                if (uiState.activeReminders.isNotEmpty()) {
                    item(key = "reminders_header") {
                        Text(
                            text = stringResource(R.string.section_reminders),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(
                        items = uiState.activeReminders,
                        key = { "reminder_${it.id}" }
                    ) { reminder ->
                        ReminderRowCard(
                            reminder = reminder,
                            onCallNow = { onRequestCall(reminder.rawNumber, reminder.contactName) },
                            onComplete = { onCompleteReminder(reminder.id) }
                        )
                    }
                }

                // 7. Pinned & Important Contacts (VOZ Call Assist)
                if (uiState.pinnedAndImportantContacts.isNotEmpty()) {
                    item(key = "favorites_header") {
                        Text(
                            text = stringResource(R.string.section_favorites),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    item(key = "favorites_row") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("home_pinned_contacts_row")
                        ) {
                            items(
                                items = uiState.pinnedAndImportantContacts,
                                key = { "pin_${it.contactId}_${it.primaryNormalizedNumber}" }
                            ) { pinned ->
                                PinnedContactChipCard(
                                    contact = pinned,
                                    onCall = { onRequestCall(pinned.primaryNumber, pinned.displayName) },
                                    onDetail = { onOpenContactDetail(pinned) }
                                )
                            }
                        }
                    }
                }

                // 8. Recent Communication Timeline
                if (uiState.recentGroupedSessions.isNotEmpty()) {
                    item(key = "recent_calls_header") {
                        Text(
                            text = stringResource(R.string.section_recent_calls),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(
                        items = uiState.recentGroupedSessions,
                        key = { "home_session_${it.sessionKey}" }
                    ) { session ->
                        GroupedCallSessionCard(
                            session = session,
                            languageCode = uiState.preferences.languageCode,
                            onCallBackClick = { rec ->
                                onRequestCall(rec.rawNumber, rec.displayIdentity)
                            },
                            onSessionDetailClick = onOpenCallRecordDetail
                        )
                    }
                } else if (!uiState.hasContactsPermission || !uiState.hasCallLogPermission) {
                    item(key = "home_permissions_card") {
                        PermissionInlineCard(
                            title = stringResource(R.string.empty_home_title),
                            body = stringResource(R.string.empty_home_subtitle),
                            buttonLabel = stringResource(R.string.grant_permission),
                            testTag = "home_grant_permissions_button",
                            onAction = onRequestPermissionsClick
                        )
                    }
                } else {
                    item(key = "home_empty_state") {
                        EmptyStateCard(
                            title = stringResource(R.string.empty_home_title),
                            subtitle = stringResource(R.string.call_log_empty_subtitle)
                        )
                    }
                }
            }
        }

        // Bottom Smart Dialpad or Floating Keypad Action Button
        if (uiState.isDialpadVisible) {
            SmartDialpadPanel(
                enteredText = uiState.homeSearchOrDialInput,
                hapticsEnabled = uiState.preferences.hapticFeedbackEnabled,
                oneHandedMode = uiState.preferences.oneHandedModeEnabled,
                onDigitPress = onDialpadDigit,
                onBackspace = onDialpadBackspace,
                onClearAll = onDialpadClearAll,
                onPasteText = onSearchOrDialChange,
                onPlaceCall = {
                    val matchedName = uiState.homeMatchedContacts.firstOrNull()?.displayName.orEmpty()
                    onRequestCall(uiState.homeSearchOrDialInput, matchedName)
                },
                onCollapse = { onToggleDialpad(false) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        } else {
            ExtendedFloatingActionButton(
                onClick = { onToggleDialpad(true) },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Dialpad,
                        contentDescription = null
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.open_dialpad),
                        fontWeight = FontWeight.Bold
                    )
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .align(
                        if (uiState.preferences.oneHandedModeEnabled) {
                            Alignment.BottomEnd
                        } else {
                            Alignment.BottomEnd
                        }
                    )
                    .padding(18.dp)
                    .testTag("open_dialpad_fab")
            )
        }
    }
}

@Composable
private fun PinnedContactChipCard(
    contact: ContactItem,
    onCall: () -> Unit,
    onDetail: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(156.dp)
            .clickable { onDetail() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = contact.initials,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                IconButton(
                    onClick = onCall,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = "Call ${contact.displayName}",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = contact.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = contact.customLabel ?: contact.primaryNumber,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ReminderRowCard(
    reminder: CallReminderEntity,
    onCallNow: () -> Unit,
    onComplete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reminder_card_${reminder.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.contactName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "${reminder.reason} • ${reminder.rawNumber}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
            IconButton(
                onClick = onComplete,
                modifier = Modifier.testTag("complete_reminder_${reminder.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Mark reminder done",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = onCallNow,
                modifier = Modifier.testTag("call_reminder_${reminder.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = "Call ${reminder.contactName}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun PermissionInlineCard(
    title: String,
    body: String,
    buttonLabel: String,
    testTag: String,
    onAction: () -> Unit,
    onSecondarySettingsAction: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("${testTag}_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onAction,
                    modifier = Modifier.testTag(testTag),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Text(text = buttonLabel, fontWeight = FontWeight.Bold)
                }
                if (onSecondarySettingsAction != null) {
                    OutlinedButton(onClick = onSecondarySettingsAction) {
                        Text(text = stringResource(R.string.open_system_settings))
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("empty_state_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.PhoneCallback,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
