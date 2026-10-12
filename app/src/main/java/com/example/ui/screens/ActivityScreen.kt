package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.CallLogFilter
import com.example.domain.CallRecord
import com.example.ui.VozUiState
import com.example.ui.components.GroupedCallSessionCard

@Composable
fun ActivityScreen(
    uiState: VozUiState,
    onSearchQueryChange: (String) -> Unit,
    onFilterChange: (CallLogFilter) -> Unit,
    onCallBack: (CallRecord) -> Unit,
    onOpenRecordDetail: (CallRecord) -> Unit,
    onDeleteSessionRecords: (List<Long>) -> Unit,
    onClearAllHistory: () -> Unit,
    onRequestCallLogPermission: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onRefreshCallLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearAllDialog by remember { mutableStateOf(false) }
    var pendingDeleteIds by remember { mutableStateOf<List<Long>?>(null) }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text(stringResource(R.string.clear_call_history_confirm_title)) },
            text = { Text(stringResource(R.string.clear_call_history_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showClearAllDialog = false
                        onClearAllHistory()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_clear_history_button")
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    pendingDeleteIds?.let { idsToDelete ->
        AlertDialog(
            onDismissRequest = { pendingDeleteIds = null },
            title = { Text(stringResource(R.string.delete_call_entry_confirm_title)) },
            text = { Text(stringResource(R.string.delete_call_entry_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteIds = null
                        onDeleteSessionRecords(idsToDelete)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_session_button")
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteIds = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    val groupedByDateHeader = remember(uiState.activityGroupedSessions) {
        uiState.activityGroupedSessions.groupBy { it.dateHeader }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("activity_screen")
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(R.string.activity_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${uiState.rawCallRecords.size} system call records",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row {
                if (uiState.hasCallLogPermission) {
                    IconButton(
                        onClick = onRefreshCallLog,
                        modifier = Modifier.testTag("activity_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh call log",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (uiState.hasWriteCallLogPermission && uiState.rawCallRecords.isNotEmpty()) {
                    IconButton(
                        onClick = { showClearAllDialog = true },
                        modifier = Modifier.testTag("activity_clear_all_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = stringResource(R.string.clear_call_history),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = uiState.activitySearchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                if (uiState.activitySearchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.testTag("activity_search_clear_button")
                    ) {
                        Icon(imageVector = Icons.Filled.Clear, contentDescription = "Clear search")
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
                .testTag("activity_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filter Chips (All, Missed, Incoming, Outgoing)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val filters = listOf(
                CallLogFilter.ALL to R.string.filter_all,
                CallLogFilter.MISSED to R.string.filter_missed,
                CallLogFilter.INCOMING to R.string.filter_incoming,
                CallLogFilter.OUTGOING to R.string.filter_outgoing
            )
            items(filters, key = { it.first.name }) { (filterType, labelRes) ->
                FilterChip(
                    selected = uiState.activityFilter == filterType,
                    onClick = { onFilterChange(filterType) },
                    label = { Text(stringResource(labelRes)) },
                    modifier = Modifier.testTag("activity_filter_${filterType.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when {
            !uiState.hasCallLogPermission -> {
                PermissionInlineCard(
                    title = stringResource(R.string.call_log_permission_title),
                    body = stringResource(R.string.call_log_permission_body),
                    buttonLabel = stringResource(R.string.grant_permission),
                    testTag = "grant_call_log_permission_button",
                    onAction = onRequestCallLogPermission,
                    onSecondarySettingsAction = onOpenSystemSettings
                )
            }
            uiState.isLoadingCallLog -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.activityGroupedSessions.isEmpty() -> {
                EmptyStateCard(
                    title = stringResource(R.string.call_log_empty_title),
                    subtitle = stringResource(R.string.call_log_empty_subtitle)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("activity_lazy_column"),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupedByDateHeader.forEach { (dateHeader, sessions) ->
                        item(key = "date_$dateHeader") {
                            Text(
                                text = dateHeader,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }

                        items(
                            items = sessions,
                            key = { "act_session_${it.sessionKey}" }
                        ) { session ->
                            GroupedCallSessionCard(
                                session = session,
                                languageCode = uiState.preferences.languageCode,
                                onCallBackClick = onCallBack,
                                onSessionDetailClick = onOpenRecordDetail,
                                onDeleteSessionClick = if (uiState.hasWriteCallLogPermission) {
                                    { ids -> pendingDeleteIds = ids }
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
