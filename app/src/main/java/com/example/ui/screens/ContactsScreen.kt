package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.ContactItem
import com.example.domain.PhoneNormalizer
import com.example.ui.VozUiState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen(
    uiState: VozUiState,
    onSearchQueryChange: (String) -> Unit,
    onFavoritesOnlyChange: (Boolean) -> Unit,
    onContactClick: (ContactItem) -> Unit,
    onPlaceCall: (rawNumber: String, displayName: String?) -> Unit,
    onGrantPermissionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.contacts_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // Search Bar
        OutlinedTextField(
            value = uiState.contactsSearchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text(stringResource(R.string.contacts_search_placeholder)) },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (uiState.contactsSearchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("contacts_search_input"),
            singleLine = true,
            shape = RoundedCornerShape(24.dp)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !uiState.favoritesOnly,
                onClick = { onFavoritesOnlyChange(false) },
                label = { Text(stringResource(R.string.filter_all)) }
            )
            FilterChip(
                selected = uiState.favoritesOnly,
                onClick = { onFavoritesOnlyChange(true) },
                label = { Text(stringResource(R.string.contacts_favorites)) },
                modifier = Modifier.testTag("contacts_favorites_filter")
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (!uiState.hasContactsPermission) {
            PermissionPromptCard(
                title = stringResource(R.string.contacts_permission_title),
                body = stringResource(R.string.contacts_permission_body),
                onGrant = onGrantPermissionsClick
            )
        } else if (uiState.isLoadingContacts) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.filteredContacts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Contacts,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(if (uiState.contactsSearchQuery.isNotBlank() || uiState.favoritesOnly) R.string.contacts_no_matches else R.string.contacts_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(if (uiState.favoritesOnly) R.string.contacts_favorites_hint else if (uiState.contactsSearchQuery.isNotBlank()) R.string.contacts_search_hint else R.string.contacts_empty_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Group contacts by first letter
            val groupedByChar = uiState.filteredContacts.groupBy {
                val first = it.displayName.firstOrNull()?.uppercaseChar() ?: '#'
                if (first.isLetter()) first else '#'
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                groupedByChar.forEach { (char, contacts) ->
                    stickyHeader {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = char.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    items(contacts, key = { it.contactId }) { contact ->
                        ContactListItem(
                            contact = contact,
                            onClick = { onContactClick(contact) },
                            onCall = { onPlaceCall(contact.primaryNumber, contact.displayName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContactListItem(
    contact: ContactItem,
    onClick: () -> Unit,
    onCall: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("contact_card_${contact.contactId}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
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
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (contact.displayName.firstOrNull() ?: '?').uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = contact.displayName,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (contact.isFavorite) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFFE91E63), modifier = Modifier.size(14.dp))
                        }
                        if (contact.isImportant) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(14.dp))
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
                modifier = Modifier.testTag("call_btn_${contact.contactId}")
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
