package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.example.domain.CallLogFilter
import com.example.domain.CallRecord
import com.example.ui.VozUiState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActivityScreen(
    uiState: VozUiState,
    onFilterChange: (CallLogFilter) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onRecordClick: (CallRecord) -> Unit,
    onPlaceCall: (rawNumber: String, displayName: String?) -> Unit,
    onClearAllHistory: () -> Unit,
    onGrantPermissionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.activity_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            if (uiState.groupedCalls.isNotEmpty() && uiState.hasWriteCallLogPermission) {
                IconButton(
                    onClick = { showClearConfirmDialog = true },
                    modifier = Modifier.testTag("clear_history_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = stringResource(R.string.clear_call_history),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search Bar
        OutlinedTextField(
            value = uiState.activitySearchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Filter call history…") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (uiState.activitySearchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("activity_search_input"),
            singleLine = true,
            shape = RoundedCornerShape(24.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter Chips Row (All, Missed, Incoming, Outgoing)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = uiState.activityFilter == CallLogFilter.ALL,
                onClick = { onFilterChange(CallLogFilter.ALL) },
                label = { Text(stringResource(R.string.filter_all)) },
                modifier = Modifier.testTag("filter_chip_all")
            )
            FilterChip(
                selected = uiState.activityFilter == CallLogFilter.MISSED,
                onClick = { onFilterChange(CallLogFilter.MISSED) },
                label = { Text(stringResource(R.string.filter_missed)) },
                modifier = Modifier.testTag("filter_chip_missed")
            )
            FilterChip(
                selected = uiState.activityFilter == CallLogFilter.INCOMING,
                onClick = { onFilterChange(CallLogFilter.INCOMING) },
                label = { Text(stringResource(R.string.filter_incoming)) },
                modifier = Modifier.testTag("filter_chip_incoming")
            )
            FilterChip(
                selected = uiState.activityFilter == CallLogFilter.OUTGOING,
                onClick = { onFilterChange(CallLogFilter.OUTGOING) },
                label = { Text(stringResource(R.string.filter_outgoing)) },
                modifier = Modifier.testTag("filter_chip_outgoing")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (!uiState.hasCallLogPermission) {
            PermissionPromptCard(
                title = stringResource(R.string.call_log_permission_title),
                body = stringResource(R.string.call_log_permission_body),
                onGrant = onGrantPermissionsClick
            )
        } else if (uiState.isLoadingCallHistory) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.filteredGroupedCalls.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.History,
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
        } else {
            // Group by date header
            val groupedByDate = uiState.filteredGroupedCalls.groupBy { it.dateHeader }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                groupedByDate.forEach { (dateHeader, sessions) ->
                    stickyHeader {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = dateHeader,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }

                    items(sessions, key = { it.sessionKey }) { session ->
                        RecentCallRowItem(
                            session = session,
                            onClick = { onRecordClick(session.primaryRecord) },
                            onCall = { onPlaceCall(session.primaryRecord.rawNumber, session.primaryRecord.displayName) }
                        )
                    }
                }
            }
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text(stringResource(R.string.clear_call_history_confirm_title)) },
            text = { Text(stringResource(R.string.clear_call_history_confirm_body)) },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        onClearAllHistory()
                        showClearConfirmDialog = false
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
