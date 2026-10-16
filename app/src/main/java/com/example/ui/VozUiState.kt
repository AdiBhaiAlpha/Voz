package com.example.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.R
import com.example.data.local.BlockedNumberEntity
import com.example.data.local.CallReminderEntity
import com.example.data.local.ContactAssistEntity
import com.example.data.preferences.VozUserPreferences
import com.example.domain.CallLogFilter
import com.example.domain.ContactItem
import com.example.domain.GroupedCallSession
import com.example.domain.SimSubscriptionItem

enum class VozDestination(val titleRes: Int, val icon: ImageVector) {
    HOME(R.string.nav_home, Icons.Filled.Call),
    CONTACTS(R.string.nav_contacts, Icons.Filled.Contacts),
    ACTIVITY(R.string.nav_activity, Icons.Filled.History),
    TOOLS(R.string.nav_tools, Icons.Filled.Settings)
}

data class PendingCallConfirmation(
    val displayName: String,
    val rawNumber: String
)

data class PendingSimSelection(
    val rawNumber: String,
    val availableSims: List<SimSubscriptionItem>
)

sealed interface DetailSheetTarget {
    data class Contact(val contact: ContactItem) : DetailSheetTarget
    data class CallRecordDetail(
        val rawNumber: String,
        val normalizedNumber: String,
        val displayName: String?,
        val photoUri: String?,
        val note: String?,
        val customLabel: String?,
        val isFavorite: Boolean,
        val isImportant: Boolean,
        val isBlocked: Boolean
    ) : DetailSheetTarget
}

data class VozUiState(
    val currentDestination: VozDestination = VozDestination.HOME,
    val isDialpadVisible: Boolean = false,
    val homeSearchOrDialInput: String = "",
    val contactsSearchQuery: String = "",
    val activitySearchQuery: String = "",
    val activityFilter: CallLogFilter = CallLogFilter.ALL,
    val preferences: VozUserPreferences = VozUserPreferences(),
    val hasContactsPermission: Boolean = false,
    val hasCallLogPermission: Boolean = false,
    val hasWriteCallLogPermission: Boolean = false,
    val hasCallPhonePermission: Boolean = false,
    val isDefaultDialer: Boolean = false,
    val isCallScreeningRoleHeld: Boolean = false,
    val allContacts: List<ContactItem> = emptyList(),
    val filteredContacts: List<ContactItem> = emptyList(),
    val groupedCalls: List<GroupedCallSession> = emptyList(),
    val filteredGroupedCalls: List<GroupedCallSession> = emptyList(),
    val blockedNumbers: List<BlockedNumberEntity> = emptyList(),
    val reminders: List<CallReminderEntity> = emptyList(),
    val assistMap: Map<String, ContactAssistEntity> = emptyMap(),
    val availableSims: List<SimSubscriptionItem> = emptyList(),
    val pendingConfirmation: PendingCallConfirmation? = null,
    val pendingSimSelection: PendingSimSelection? = null,
    val detailSheetTarget: DetailSheetTarget? = null,
    val feedbackMessage: String? = null,
    val isLoadingContacts: Boolean = false,
    val isLoadingCallHistory: Boolean = false
)
