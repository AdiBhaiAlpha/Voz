package com.example.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.CallHistoryGrouper
import com.example.domain.CallRecord
import com.example.domain.CallType
import com.example.domain.ContactItem
import com.example.domain.GroupedCallSession
import com.example.domain.PhoneNormalizer
import com.example.telecom.CallLifecycleState
import com.example.telecom.CallManager
import com.example.telecom.InCallActivity
import com.example.ui.VozUiState
import com.example.ui.components.SmartDialpad

@Composable
fun HomeScreen(
    uiState: VozUiState,
    onSearchOrDialChange: (String) -> Unit,
    onDigitPressed: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClearAll: () -> Unit,
    onToggleDialpad: (Boolean) -> Unit,
    onPlaceCall: (rawNumber: String, displayName: String?, explicitSimId: String?) -> Unit,
    onContactClick: (ContactItem) -> Unit,
    onRecordClick: (CallRecord) -> Unit,
    onGrantPermissionsClick: () -> Unit,
    onRequestDefaultDialer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeCallModel by CallManager.activeCall.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Active Call Banner (if a call is ongoing, shows tap to return)
            if (activeCallModel != null) {
                val call = activeCallModel!!
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable {
                            val intent = Intent(context, InCallActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            context.startActivity(intent)
                        }
                        .testTag("active_call_banner"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (call.state == CallLifecycleState.ACTIVE) Color(0xFF142416) else Color(0xFF131A20)
                    ),
                    border = BorderStroke(1.dp, if (call.state == CallLifecycleState.ACTIVE) Color(0xFFA3E635) else Color(0xFF202A32)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFA3E635).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Call,
                                    contentDescription = null,
                                    tint = Color(0xFFA3E635),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = call.displayName ?: PhoneNormalizer.formatForDisplay(call.rawNumber),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (call.state == CallLifecycleState.ACTIVE) "Active Carrier Call • Tap to view"
                                    else "Dialing… • Tap to view",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }

                        IconButton(
                            onClick = { CallManager.declineOrHangup() },
                            modifier = Modifier.testTag("banner_hangup_btn")
                        ) {
                            Icon(Icons.Filled.CallEnd, contentDescription = "Hang Up", tint = Color(0xFFEF4444))
                        }
                    }
                }
            } else if (!uiState.isDefaultDialer) {
                // Setup Default Phone App Role banner for Truecaller-style caller screen capability
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable { onRequestDefaultDialer() }
                        .testTag("home_default_dialer_banner"),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131A20)),
                    border = BorderStroke(1.dp, Color(0xFFA3E635).copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = Color(0xFFA3E635),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Enable Custom Caller Screen",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Android routes incoming calls to your default phone app. Tap SET to activate VOZ incoming caller screen.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                            }
                        }
                        Text(
                            text = "SET",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFA3E635),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            // Top Search / Dialbar
            OutlinedTextField(
                value = uiState.homeSearchOrDialInput,
                onValueChange = onSearchOrDialChange,
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (uiState.homeSearchOrDialInput.isNotEmpty()) {
                        IconButton(onClick = onClearAll) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_search_input"),
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Main Content: If search/dial input is active, show matching contacts; otherwise show pinned & recent calls
            if (uiState.homeSearchOrDialInput.isNotEmpty()) {
                // Matching contacts / Direct call header
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = if (uiState.isDialpadVisible) 360.dp else 90.dp)
                ) {
                    val sanitized = PhoneNormalizer.sanitizeDialInput(uiState.homeSearchOrDialInput)
                    if (sanitized.isNotEmpty()) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { onPlaceCall(sanitized, null, null) }
                                    .testTag("direct_dial_card"),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Filled.Call,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "Direct Call",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = PhoneNormalizer.formatForDisplay(sanitized),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }

                                    Icon(
                                        Icons.AutoMirrored.Filled.CallMade,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    if (uiState.filteredContacts.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.section_matched_contacts),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }

                        items(uiState.filteredContacts, key = { it.contactId }) { contact ->
                            ContactRowItem(
                                contact = contact,
                                onClick = { onContactClick(contact) },
                                onCall = { onPlaceCall(contact.primaryNumber, contact.displayName, null) }
                            )
                        }
                    } else if (sanitized.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No matching contacts found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                // Normal Home Screen (Favorites + Recent Calls)
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = if (uiState.isDialpadVisible) 360.dp else 90.dp)
                ) {
                    // Pinned / Favorites row
                    val favorites = uiState.allContacts.filter { it.isFavorite || it.isImportant }
                    if (favorites.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.section_favorites),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(favorites, key = { it.contactId }) { fav ->
                                    FavoriteContactBubble(
                                        contact = fav,
                                        onClick = { onContactClick(fav) },
                                        onCall = { onPlaceCall(fav.primaryNumber, fav.displayName, null) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    // Recent Calls section
                    item {
                        Text(
                            text = stringResource(R.string.section_recent_calls),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }

                    if (!uiState.hasCallLogPermission) {
                        item {
                            PermissionPromptCard(
                                title = stringResource(R.string.call_log_permission_title),
                                body = stringResource(R.string.call_log_permission_body),
                                onGrant = onGrantPermissionsClick
                            )
                        }
                    } else if (uiState.groupedCalls.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Filled.Phone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = stringResource(R.string.call_log_empty_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.call_log_empty_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(uiState.groupedCalls, key = { it.sessionKey }) { session ->
                            RecentCallRowItem(
                                session = session,
                                onClick = { onRecordClick(session.primaryRecord) },
                                onCall = { onPlaceCall(session.primaryRecord.rawNumber, session.primaryRecord.displayName, null) }
                            )
                        }
                    }
                }
            }
        }

        // Floating Action Button for Dialpad (when keypad sheet is closed)
        if (!uiState.isDialpadVisible) {
            FloatingActionButton(
                onClick = { onToggleDialpad(true) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 24.dp)
                    .testTag("fab_open_dialpad"),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Filled.Dialpad,
                    contentDescription = stringResource(R.string.open_dialpad)
                )
            }
        }

        // Bottom Smart Keypad Sheet
        AnimatedVisibility(
            visible = uiState.isDialpadVisible,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            SmartDialpad(
                currentInput = uiState.homeSearchOrDialInput,
                availableSims = uiState.availableSims,
                isSoundEnabled = uiState.preferences.dialpadSoundEnabled,
                isHapticEnabled = uiState.preferences.hapticFeedbackEnabled,
                onDigitPressed = onDigitPressed,
                onBackspace = onBackspace,
                onClearAll = onClearAll,
                onPlaceCall = { explicitSimId ->
                    val num = uiState.homeSearchOrDialInput.ifBlank { "" }
                    if (num.isNotBlank()) {
                        onPlaceCall(num, null, explicitSimId)
                    }
                }
            )
        }
    }
}

@Composable
fun ContactRowItem(
    contact: ContactItem,
    onClick: () -> Unit,
    onCall: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
            .testTag("contact_item_${contact.contactId}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (contact.displayName.firstOrNull() ?: '?').uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = contact.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (contact.isFavorite) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFFE91E63), modifier = Modifier.size(14.dp))
                        }
                    }
                    Text(
                        text = PhoneNormalizer.formatForDisplay(contact.primaryNumber),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!contact.customNote.isNullOrBlank()) {
                        Text(
                            text = "Note: ${contact.customNote}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }
            }

            IconButton(
                onClick = onCall,
                modifier = Modifier.testTag("call_contact_btn_${contact.contactId}")
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

@Composable
fun FavoriteContactBubble(
    contact: ContactItem,
    onClick: () -> Unit,
    onCall: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick)
            .testTag("fav_bubble_${contact.contactId}")
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
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
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = contact.displayName.split(" ").firstOrNull() ?: contact.displayName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
fun RecentCallRowItem(
    session: GroupedCallSession,
    onClick: () -> Unit,
    onCall: () -> Unit
) {
    val record = session.primaryRecord
    val isMissed = record.callType == CallType.MISSED || record.callType == CallType.REJECTED

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
            .testTag("recent_call_item_${record.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                // Call type icon
                val icon = when (record.callType) {
                    CallType.INCOMING -> Icons.AutoMirrored.Filled.CallReceived
                    CallType.OUTGOING -> Icons.AutoMirrored.Filled.CallMade
                    CallType.MISSED -> Icons.AutoMirrored.Filled.CallMissed
                    CallType.REJECTED -> Icons.AutoMirrored.Filled.CallMissed
                    CallType.BLOCKED -> Icons.Filled.Clear
                }
                val iconColor = when (record.callType) {
                    CallType.MISSED, CallType.REJECTED -> Color(0xFFD32F2F)
                    CallType.OUTGOING -> Color(0xFF1976D2)
                    CallType.INCOMING -> Color(0xFF388E3C)
                    CallType.BLOCKED -> Color(0xFF757575)
                }

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(iconColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = record.displayName ?: PhoneNormalizer.formatForDisplay(record.rawNumber),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isMissed) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isMissed) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurface
                        )
                        if (session.totalAttempts > 1) {
                            Text(
                                text = " (${session.totalAttempts})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isMissed) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = CallHistoryGrouper.formatCallTime(record.timestampMillis),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (record.durationSeconds > 0) {
                            Text(
                                text = " • ${CallHistoryGrouper.formatDuration(record.durationSeconds)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!record.assistNote.isNullOrBlank()) {
                            Text(
                                text = " • Note: ${record.assistNote}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onCall,
                modifier = Modifier.testTag("call_back_btn_${record.id}")
            ) {
                Icon(Icons.Filled.Call, contentDescription = "Call Back", tint = Color(0xFF2E7D32))
            }
        }
    }
}

@Composable
fun PermissionPromptCard(
    title: String,
    body: String,
    onGrant: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = body, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(12.dp))
            androidx.compose.material3.Button(onClick = onGrant) {
                Text(stringResource(R.string.grant_permission))
            }
        }
    }
}
