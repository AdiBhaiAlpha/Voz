package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.local.VozDatabase
import com.example.data.preferences.ThemeMode
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.CallLogLoadResult
import com.example.data.repository.CallLogRepository
import com.example.data.repository.ContactsLoadResult
import com.example.data.repository.ContactsRepository
import com.example.data.repository.VozAssistRepository
import com.example.domain.CallHistoryGrouper
import com.example.domain.CallLogFilter
import com.example.domain.CallRecord
import com.example.domain.CallType
import com.example.domain.ContactItem
import com.example.domain.VozAddress
import com.example.domain.VozDisplayName
import com.example.domain.PhoneNormalizer
import com.example.domain.T9AndBanglaMatcher
import com.example.telecom.CallInitiationOutcome
import com.example.telecom.CallManager
import com.example.telecom.TelecomDialerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VozMainViewModel(application: Application) : AndroidViewModel(application) {

    private val database: VozDatabase = Room.databaseBuilder(
        application,
        VozDatabase::class.java,
        "voz_app_database"
    ).build()

    val assistRepository = VozAssistRepository(database.assistDao())
    val preferencesRepository = UserPreferencesRepository(application)
    val contactsRepository = ContactsRepository(application)
    val callLogRepository = CallLogRepository(application)
    val telecomDialerManager = TelecomDialerManager(application)

    private val _internalState = MutableStateFlow(VozUiState())

    val uiState: StateFlow<VozUiState> = combine(
        _internalState,
        preferencesRepository.preferencesFlow,
        assistRepository.allAssist,
        assistRepository.allBlocked,
        assistRepository.pendingReminders
    ) { baseState, prefs, assistList, blockedList, reminders ->
        val assistMap = assistList.associateBy { it.phoneKey }

        // Enrich contacts with notes and favorites
        val enrichedContacts = baseState.allContacts.map { contact ->
            val norm = contact.primaryNormalizedNumber
            val assist = assistMap[norm] ?: assistMap[contact.lookupKey]
            contact.copy(
                isFavorite = assist?.isFavorite ?: false,
                isImportant = assist?.isImportant ?: false,
                customNote = assist?.note,
                customLabel = assist?.customLabel
            )
        }

        // Filter contacts based on query
        val query = if (baseState.currentDestination == VozDestination.CONTACTS) {
            baseState.contactsSearchQuery
        } else {
            baseState.homeSearchOrDialInput
        }

        val eligibleContacts = enrichedContacts.filter {
            !baseState.favoritesOnly || baseState.currentDestination != VozDestination.CONTACTS || it.isFavorite
        }
        val filteredContacts = if (query.isBlank()) {
            eligibleContacts
        } else {
            eligibleContacts
                .map { contact ->
                    val phones = contact.numbers.map { it.rawNumber }
                    val score = T9AndBanglaMatcher.evaluateContactMatch(contact.displayName, phones, query)
                    contact to score
                }
                .filter { it.second.matched }
                .sortedBy { it.second.rank }
                .map { it.first }
        }

        // Enrich call records with notes and filter
        val blockedNumbersSet = blockedList.map { it.normalizedNumber }.toSet()
        val enrichedGroupedCalls = baseState.groupedCalls.map { group ->
            val norm = group.primaryRecord.normalizedNumber
            val assist = assistMap[norm]
            val isBlk = blockedNumbersSet.contains(norm)
            val updatedPrimary = group.primaryRecord.copy(
                assistNote = assist?.note,
                assistLabel = assist?.customLabel,
                isImportant = assist?.isImportant ?: false,
                isBlocked = isBlk
            )
            group.copy(primaryRecord = updatedPrimary)
        }

        val filteredGroupedCalls = enrichedGroupedCalls.filter { group ->
            val matchesFilter = when (baseState.activityFilter) {
                CallLogFilter.ALL -> true
                CallLogFilter.INCOMING -> group.allCallTypes.contains(CallType.INCOMING)
                CallLogFilter.MISSED -> group.allCallTypes.contains(CallType.MISSED)
                CallLogFilter.OUTGOING -> group.allCallTypes.contains(CallType.OUTGOING)
            }
            val matchesQuery = if (baseState.activitySearchQuery.isBlank()) true else {
                val queryLower = baseState.activitySearchQuery.lowercase()
                val nameLower = group.primaryRecord.displayName?.lowercase() ?: ""
                val num = group.primaryRecord.rawNumber
                nameLower.contains(queryLower) || num.contains(queryLower)
            }
            matchesFilter && matchesQuery
        }

        val refreshedDetail = when (val target = baseState.detailSheetTarget) {
            is DetailSheetTarget.Contact -> DetailSheetTarget.Contact(
                enrichedContacts.firstOrNull { it.contactId == target.contact.contactId } ?: target.contact
            )
            is DetailSheetTarget.CallRecordDetail -> {
                val assist = assistMap[target.normalizedNumber]
                target.copy(
                    note = assist?.note,
                    customLabel = assist?.customLabel,
                    isFavorite = assist?.isFavorite ?: false,
                    isImportant = assist?.isImportant ?: false,
                    isBlocked = target.normalizedNumber in blockedNumbersSet
                )
            }
            null -> null
        }
        baseState.copy(
            detailSheetTarget = refreshedDetail,
            preferences = prefs,
            allContacts = enrichedContacts,
            filteredContacts = filteredContacts,
            groupedCalls = enrichedGroupedCalls,
            filteredGroupedCalls = filteredGroupedCalls,
            blockedNumbers = blockedList,
            reminders = reminders,
            assistMap = assistMap
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VozUiState()
    )

    init {
        refreshPermissionsAndSystemData()
    }

    fun refreshPermissionsAndSystemData() {
        val hasContacts = androidx.core.content.ContextCompat.checkSelfPermission(
            getApplication(),
            android.Manifest.permission.READ_CONTACTS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val hasCallLog = androidx.core.content.ContextCompat.checkSelfPermission(
            getApplication(),
            android.Manifest.permission.READ_CALL_LOG
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val hasWriteLog = androidx.core.content.ContextCompat.checkSelfPermission(
            getApplication(),
            android.Manifest.permission.WRITE_CALL_LOG
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val hasCallPhone = telecomDialerManager.hasCallPhonePermission()
        val isDefault = telecomDialerManager.isDefaultDialer()
        val isScreening = telecomDialerManager.isCallScreeningRoleHeld()
        val sims = telecomDialerManager.getAvailableSimAccounts()

        _internalState.update { it.copy(
            hasContactsPermission = hasContacts,
            hasCallLogPermission = hasCallLog,
            hasWriteCallLogPermission = hasWriteLog,
            hasCallPhonePermission = hasCallPhone,
            isDefaultDialer = isDefault,
            isCallScreeningRoleHeld = isScreening,
            availableSims = sims
        ) }

        _internalState.update { it.copy(
            allContacts = if (hasContacts) it.allContacts else emptyList(),
            groupedCalls = if (hasCallLog) it.groupedCalls else emptyList()
        ) }

        if (hasContacts) {
            loadContacts()
        }
        if (hasCallLog) {
            loadCallHistory()
        }
    }

    fun loadContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            _internalState.update { it.copy(isLoadingContacts = true) }
            when (val result = contactsRepository.loadAuthorizedContacts()) {
                is ContactsLoadResult.Success -> {
                    _internalState.update { it.copy(
                        allContacts = result.contacts,
                        isLoadingContacts = false
                    ) }
                }
                is ContactsLoadResult.Error -> {
                    _internalState.update { it.copy(
                        isLoadingContacts = false,
                        feedbackMessage = result.message
                    ) }
                }
                is ContactsLoadResult.PermissionDenied -> {
                    _internalState.update { it.copy(isLoadingContacts = false) }
                }
            }
        }
    }

    fun loadCallHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            _internalState.update { it.copy(isLoadingCallHistory = true) }
            when (val result = callLogRepository.loadSystemCallHistory()) {
                is CallLogLoadResult.Success -> {
                    val grouped = CallHistoryGrouper.groupCallRecords(result.records)
                    _internalState.update { it.copy(
                        groupedCalls = grouped,
                        isLoadingCallHistory = false
                    ) }
                }
                is CallLogLoadResult.Error -> {
                    _internalState.update { it.copy(
                        isLoadingCallHistory = false,
                        feedbackMessage = result.message
                    ) }
                }
                is CallLogLoadResult.PermissionDenied -> {
                    _internalState.update { it.copy(isLoadingCallHistory = false) }
                }
            }
        }
    }

    fun setFavoritesOnly(enabled: Boolean) {
        _internalState.update { it.copy(favoritesOnly = enabled) }
    }

    override fun onCleared() {
        database.close()
        super.onCleared()
    }

    fun selectDestination(dest: VozDestination) {
        _internalState.update { it.copy(currentDestination = dest) }
    }

    fun setDialpadVisible(visible: Boolean) {
        _internalState.update { it.copy(isDialpadVisible = visible) }
    }

    fun onHomeSearchOrDialChange(input: String) {
        val value = if (VozAddress.isVoz(input)) VozAddress.normalize(input) ?: input else input
        _internalState.update { it.copy(homeSearchOrDialInput = value) }
    }

    fun onDialpadDigitPressed(digit: Char) {
        _internalState.update {
            val current = it.homeSearchOrDialInput
            it.copy(homeSearchOrDialInput = if (VozAddress.isVoz(current))
                VozAddress.append(current, digit) else current + digit)
        }
    }

    fun onDialpadBackspace() {
        _internalState.update {
            val current = it.homeSearchOrDialInput
            it.copy(homeSearchOrDialInput = if (VozAddress.isVoz(current))
                VozAddress.backspace(current) else current.dropLast(1))
        }
    }

    fun onDialpadClearAll() {
        _internalState.update { it.copy(homeSearchOrDialInput = "") }
    }

    fun onContactsSearchQueryChange(query: String) {
        _internalState.update { it.copy(contactsSearchQuery = query) }
    }

    fun onActivitySearchQueryChange(query: String) {
        _internalState.update { it.copy(activitySearchQuery = query) }
    }

    fun onActivityFilterChange(filter: CallLogFilter) {
        _internalState.update { it.copy(activityFilter = filter) }
    }

    /**
     * Genuine Call Placement - executes real telephony call through Android Telecom / Phone Network.
     * Guaranteed NO fake timers or simulation.
     */
    fun placeRealCall(
        rawNumber: String,
        displayName: String? = null,
        explicitSimId: String? = null,
        bypassConfirmation: Boolean = false
    ) {
        // Route before cellular sanitization; a VOZ ID must NEVER become a carrier number.
        if (VozAddress.isVoz(rawNumber)) {
            if (!VozAddress.isComplete(rawNumber)) {
                showFeedback(getApplication<Application>().getString(com.example.R.string.voz_invalid_address))
            } else if (!com.example.telecom.VozOnlineActivity.open(getApplication(), VozAddress.normalize(rawNumber)!!)) {
                showFeedback(getApplication<Application>().getString(com.example.R.string.voz_service_unavailable))
            }
            return
        }
        val sanitized = PhoneNormalizer.sanitizeDialInput(rawNumber)
        if (!PhoneNormalizer.isValidDialableNumber(sanitized)) {
            showFeedback("Please enter a valid phone number.")
            return
        }

        val state = uiState.value
        if (!bypassConfirmation && state.preferences.confirmBeforeCallEnabled) {
            val name = displayName?.ifBlank { sanitized } ?: sanitized
            _internalState.update { it.copy(
                pendingConfirmation = PendingCallConfirmation(name, sanitized, explicitSimId)
            ) }
            return
        }

        _internalState.update { it.copy(pendingConfirmation = null) }

        val outcome = CallManager.placeRealCall(
            context = getApplication(),
            rawNumber = sanitized,
            explicitSimId = explicitSimId ?: state.preferences.preferredSimId.takeIf { it.isNotBlank() }
        )

        when (outcome) {
            is CallInitiationOutcome.PlacedDirectly -> {
                val target = displayName?.ifBlank { sanitized } ?: sanitized
                showFeedback("Placing cellular call to $target…")
            }
            is CallInitiationOutcome.RequiresSimSelection -> {
                _internalState.update { it.copy(
                    pendingSimSelection = PendingSimSelection(outcome.sanitizedNumber, outcome.availableSims)
                ) }
            }
            is CallInitiationOutcome.Error -> {
                showFeedback(outcome.reason)
            }
        }
    }

    fun dismissCallConfirmation() {
        _internalState.update { it.copy(pendingConfirmation = null) }
    }

    fun dismissSimSelection() {
        _internalState.update { it.copy(pendingSimSelection = null) }
    }

    fun openDetailForContact(contact: ContactItem) {
        _internalState.update { it.copy(detailSheetTarget = DetailSheetTarget.Contact(contact)) }
    }

    fun openDetailForCallRecord(record: CallRecord) {
        _internalState.update { it.copy(
            detailSheetTarget = DetailSheetTarget.CallRecordDetail(
                recordId = record.id,
                rawNumber = record.rawNumber,
                normalizedNumber = record.normalizedNumber,
                displayName = record.displayName,
                photoUri = record.photoUri,
                note = record.assistNote,
                customLabel = record.assistLabel,
                isFavorite = uiState.value.assistMap[record.normalizedNumber]?.isFavorite ?: false,
                isImportant = record.isImportant,
                isBlocked = record.isBlocked
            )
        ) }
    }

    fun closeDetailSheet() {
        _internalState.update { it.copy(detailSheetTarget = null) }
    }

    fun saveCallAssist(
        phoneOrKey: String,
        note: String,
        customLabel: String,
        isFavorite: Boolean,
        isImportant: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            assistRepository.saveAssist(phoneOrKey, note, customLabel, isFavorite, isImportant)
            showFeedback("Saved note for contact.")
        }
    }

    fun addCallReminder(contactName: String, rawNumber: String, minutesFromNow: Int, reason: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val scheduled = System.currentTimeMillis() + (minutesFromNow * 60 * 1000L)
            assistRepository.addReminder(contactName, rawNumber, scheduled, reason)
            showFeedback("Reminder set for $contactName.")
        }
    }

    fun completeReminder(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            assistRepository.completeReminder(id)
        }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            assistRepository.deleteReminder(id)
        }
    }

    fun blockNumber(rawNumber: String, reason: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            assistRepository.blockNumber(rawNumber, reason)
            showFeedback("Blocked $rawNumber.")
        }
    }

    fun unblockNumber(normalizedNumber: String) {
        viewModelScope.launch(Dispatchers.IO) {
            assistRepository.unblockNumber(normalizedNumber)
            showFeedback("Unblocked.")
        }
    }

    fun deleteCallRecord(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val deleted = callLogRepository.deleteCallRecordsByIds(listOf(id))
            if (deleted) loadCallHistory()
            showFeedback(if (deleted) "Record removed." else "Could not delete record. Check call log permissions and default phone app settings.")
        }
    }

    fun clearAllCallHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val cleared = callLogRepository.clearAllCallHistory()
            if (cleared) loadCallHistory()
            showFeedback(if (cleared) "Call log cleared." else "Could not clear call log. Check call log permissions and default phone app settings.")
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        preferencesRepository.setThemeMode(mode)
    }

    fun setLanguageCode(code: String) {
        preferencesRepository.setLanguageCode(code)
    }

    fun setHapticFeedback(enabled: Boolean) {
        preferencesRepository.setHapticFeedback(enabled)
    }

    fun setDialpadSound(enabled: Boolean) {
        preferencesRepository.setDialpadSound(enabled)
    }

    fun setPreferredSimId(simId: String) {
        preferencesRepository.setPreferredSimId(simId)
    }

    fun setOneHandedMode(enabled: Boolean) {
        preferencesRepository.setOneHandedMode(enabled)
    }

    fun setReduceMotion(enabled: Boolean) {
        preferencesRepository.setReduceMotion(enabled)
    }

    fun setConfirmBeforeCall(enabled: Boolean) {
        preferencesRepository.setConfirmBeforeCall(enabled)
    }

    fun saveVozDisplayName(userName: String) {
        if (!VozDisplayName.isValid(userName)) {
            showFeedback(getApplication<Application>().getString(com.example.R.string.voz_name_error))
            return
        }
        preferencesRepository.saveVozDisplayName(VozDisplayName.clean(userName))
        showFeedback(getApplication<Application>().getString(com.example.R.string.voz_name_saved))
    }

    fun logoutVozAccount() {
        preferencesRepository.logoutVozAccount()
        showFeedback("Local demo profile cleared.")
    }

    fun setFloatingIslandOverlayEnabled(enabled: Boolean) {
        preferencesRepository.setFloatingIslandOverlayEnabled(enabled)
    }

    fun dismissFeedbackBanner() {
        _internalState.update { it.copy(feedbackMessage = null) }
    }

    fun showFeedback(msg: String) {
        _internalState.update { it.copy(feedbackMessage = msg) }
    }
}
