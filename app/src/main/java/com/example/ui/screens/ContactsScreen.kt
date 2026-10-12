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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.ContactItem
import com.example.ui.VozUiState
import com.example.ui.components.ContactRowCard

@Composable
fun ContactsScreen(
    uiState: VozUiState,
    onSearchQueryChange: (String) -> Unit,
    onCallContact: (ContactItem) -> Unit,
    onOpenContactDetail: (ContactItem) -> Unit,
    onRequestContactsPermission: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onRefreshContacts: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Group contacts alphabetically by first character (handles Bangla & English characters cleanly)
    val groupedContacts = remember(uiState.directoryFilteredContacts) {
        uiState.directoryFilteredContacts.groupBy { contact ->
            val firstChar = contact.displayName.trim().firstOrNull()?.uppercaseChar()
            when {
                firstChar == null -> "#"
                firstChar.isLetter() -> firstChar.toString()
                else -> "#"
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("contacts_screen")
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(R.string.contacts_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${uiState.allContacts.size} authorized device contacts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (uiState.hasContactsPermission) {
                IconButton(
                    onClick = onRefreshContacts,
                    modifier = Modifier.testTag("contacts_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh contacts",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = uiState.contactsSearchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text(stringResource(R.string.contacts_search_placeholder)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                if (uiState.contactsSearchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.testTag("contacts_search_clear_button")
                    ) {
                        Icon(imageVector = Icons.Filled.Clear, contentDescription = "Clear filter")
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
                .testTag("contacts_search_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        when {
            !uiState.hasContactsPermission -> {
                PermissionInlineCard(
                    title = stringResource(R.string.contacts_permission_title),
                    body = stringResource(R.string.contacts_permission_body),
                    buttonLabel = stringResource(R.string.grant_permission),
                    testTag = "grant_contacts_permission_button",
                    onAction = onRequestContactsPermission,
                    onSecondarySettingsAction = onOpenSystemSettings
                )
            }
            uiState.isLoadingContacts -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            uiState.directoryFilteredContacts.isEmpty() -> {
                EmptyStateCard(
                    title = stringResource(R.string.contacts_empty_title),
                    subtitle = stringResource(R.string.contacts_empty_subtitle)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("contacts_lazy_column"),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupedContacts.forEach { (letter, contactsInGroup) ->
                        item(key = "header_$letter") {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                            ) {
                                Text(
                                    text = letter,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }

                        items(
                            items = contactsInGroup,
                            key = { "dir_contact_${it.contactId}" }
                        ) { contact ->
                            ContactRowCard(
                                contact = contact,
                                onCallClick = onCallContact,
                                onDetailClick = onOpenContactDetail
                            )
                        }
                    }
                }
            }
        }
    }
}
