package com.example.ui

import android.app.Application
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BlockedNumberEntity
import com.example.data.local.CallReminderEntity
import com.example.data.local.ContactAssistEntity
import com.example.data.local.VozDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.preferences.VozUserPreferences
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
import com.example.domain.ContactNumber
import com.example.domain.GroupedCallSession
import com.example.domain.PhoneNormalizer
import com.example.domain.SimSubscriptionItem
import com.example.domain.T9AndBanglaMatcher
import com.example.telecom.CallInitiationOutcome
import com.example.telecom.TelecomDialerManager
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class VozDestination {
    HOME,
    CONTACTS,
    ACTIVITY,
    TOOLS
}

data class PendingCallConfirmation(
    val displayName: String,
    val rawNumber: String
)

data class PendingSimSelection(
    val rawNumber: String,
    val availableSims: List<SimSubscriptionItem>
)

data class DetailSheetTarget(
    val displayName: String,
    val rawNumber: String,
    val normalizedNumber: String,
    val numbers: List<ContactNumber>,
    val photoUri: String? = null,
    val note: String = "",
    val customLabel: String = "",
    val isFavorite: Boolean = false,
    val isImportant: Boolean = false,
    val isBlocked: Boolean = false
)

data class VozUiState(
    val currentDestination: VozDestination = VozDestination.HOME,
    val preferences: VozUserPreferences = VozUserPreferences(),
    val isDefaultDialer: Boolean = false,
    val isCallScreeningEnabled: Boolean = false,
    val hasContactsPermission: Boolean = false,
    val hasCallLogPermission: Boolean = false,
    val hasWriteCallLogPermission: Boolean = false,
    val hasCallPhonePermission: Boolean = false,
    val availableSims: List<SimSubscriptionItem> = emptyList(),
    val isLoadingContacts: Boolean = false,
    val isLoadingCallLog: Boolean = false,
    val contactsError: String? = null,
    val callLogError: String? = null,
    // Dialpad & Home Search
    val homeSearchOrDialInput: String = "",
    val isDialpadVisible: Boolean = false,
    // Contacts Directory
    val contactsSearchQuery: String = "",
    val allContacts: List<ContactItem> = emptyList(),
    val homeMatchedContacts: List<ContactItem> = emptyList(),
    val directoryFilteredContacts: List<ContactItem> = emptyList(),
    val pinnedAndImportantContacts: List<ContactItem> = emptyList(),
    // Call History / Activity
    val activitySearchQuery: String = "",
    val activityFilter: CallLogFilter = CallLogFilter.ALL,
    val rawCallRecords: List<CallRecord> = emptyList(),
    val recentGroupedSessions: List<GroupedCallSession> = emptyList(),
    val activityGroupedSessions: List<GroupedCallSession> = emptyList(),
    val missedCallCount: Int = 0,
    // Call Assist & Blocklist
    val assistMetadataList: List<ContactAssistEntity> = emptyList(),
    val blockedNumbers: List<BlockedNumberEntity> = emptyList(),
    val activeReminders: List<CallReminderEntity> = emptyList(),
    // Modals & Confirmations
    val pendingCallConfirmation: PendingCallConfirmation? = null,
    val pendingSimSelection: PendingSimSelection? = null,
    val selectedDetailTarget: DetailSheetTarget? = null,
    val feedbackBannerMessage: String? = null
)

class VozMainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = VozDatabase.getInstance(application)
    private val assistRepository = VozAssistRepository(database.vozAssistDao())
    private val preferencesRepository = UserPreferencesRepository(application)
    private val contactsRepository = ContactsRepository(application)
    private val callLogRepository = CallLogRepository(application)
    val telecomManager = TelecomDialerManager(application)

    private val _internalState = MutableStateFlow(VozUiState())

    val uiState: StateFlow<VozUiState> = combine(
        _internalState,
        preferencesRepository.preferencesFlow,
        assistRepository.assistMetadataFlow,
        assistRepository.blockedNumbersFlow,
        assistRepository.activeRemindersFlow
    ) { base, prefs, assistList, blockedList, reminders ->
        buildDerivedState(
            base = base,
            prefs = prefs,
            assistList = assistList,
            blockedList = blockedList,
            reminders = reminders
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
        val hasContacts = contactsRepository.hasContactsPermission()
        val hasCallLog = callLogRepository.hasReadCallLogPermission()
        val hasWriteCallLog = callLogRepository.hasWriteCallLogPermission()
        val hasCallPhone = telecomManager.hasCallPhonePermission()
        val isDefault = telecomManager.isDefaultDialer()
        val isScreening = telecomManager.isCallScreeningRoleHeld()
        val sims = telecomManager.getAvailableSimAccounts()

        _internalState.update {
            it.copy(
                hasContactsPermission = hasContacts,
                hasCallLogPermission = hasCallLog,
                hasWriteCallLogPermission = hasWriteCallLog,
                hasCallPhonePermission = hasCallPhone,
                isDefaultDialer = isDefault,
                isCallScreeningEnabled = isScreening,
                availableSims = sims
            )
        }

        if (hasContacts) {
            loadContacts()
        }
        if (hasCallLog) {
            loadCallHistory()
        }
    }

    fun loadContacts() {
        viewModelScope.launch {
            _internalState.update { it.copy(isLoadingContacts = true, contactsError = null) }
            when (val res = contactsRepository.loadAuthorizedContacts()) {
                is ContactsLoadResult.Success -> {
                    _internalState.update {
                        it.copy(
                            isLoadingContacts = false,
                            hasContactsPermission = true,
                            allContacts = res.contacts,
                            contactsError = null
                        )
                    }
                }
                is ContactsLoadResult.PermissionDenied -> {
                    _internalState.update {
                        it.copy(
                            isLoadingContacts = false,
                            hasContactsPermission = false,
                            allContacts = emptyList()
                        )
                    }
                }
                is ContactsLoadResult.Error -> {
                    _internalState.update {
                        it.copy(
                            isLoadingContacts = false,
                            contactsError = res.message
                        )
                    }
                }
            }
        }
    }

    fun loadCallHistory() {
        viewModelScope.launch {
            _internalState.update { it.copy(isLoadingCallLog = true, callLogError = null) }
            when (val res = callLogRepository.loadSystemCallHistory()) {
                is CallLogLoadResult.Success -> {
                    _internalState.update {
                        it.copy(
                            isLoadingCallLog = false,
                            hasCallLogPermission = true,
                            rawCallRecords = res.records,
                            callLogError = null
                        )
                    }
                }
                is CallLogLoadResult.PermissionDenied -> {
                    _internalState.update {
                        it.copy(
                            isLoadingCallLog = false,
                            hasCallLogPermission = false,
                            rawCallRecords = emptyList()
                        )
                    }
                }
                is CallLogLoadResult.Error -> {
                    _internalState.update {
                        it.copy(
                            isLoadingCallLog = false,
                            callLogError = res.message
                        )
                    }
                }
            }
        }
    }

    fun selectDestination(destination: VozDestination) {
        _internalState.update { it.copy(currentDestination = destination) }
    }

    fun setDialpadVisible(visible: Boolean) {
        _internalState.update { it.copy(isDialpadVisible = visible) }
    }

    fun onHomeSearchOrDialChange(newValue: String) {
        _internalState.update { it.copy(homeSearchOrDialInput = newValue) }
    }

    fun onDialpadDigitPressed(digit: Char) {
        val currentPrefs = uiState.value.preferences
        if (currentPrefs.dialpadSoundEnabled) {
            playDialpadTone(digit)
        }
        _internalState.update { state ->
            state.copy(
                homeSearchOrDialInput = state.homeSearchOrDialInput + digit,
                isDialpadVisible = true
            )
        }
    }

    fun onDialpadBackspace() {
        _internalState.update { state ->
            if (state.homeSearchOrDialInput.isNotEmpty()) {
                state.copy(homeSearchOrDialInput = state.homeSearchOrDialInput.dropLast(1))
            } else {
                state
            }
        }
    }

    fun onDialpadClearAll() {
        _internalState.update { it.copy(homeSearchOrDialInput = "") }
    }

    fun handleIncomingDialIntentNumber(rawSchemePart: String?) {
        val cleaned = rawSchemePart?.trim().orEmpty()
        if (cleaned.isNotEmpty()) {
            _internalState.update {
                it.copy(
                    currentDestination = VozDestination.HOME,
                    homeSearchOrDialInput = cleaned,
                    isDialpadVisible = true
                )
            }
        }
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
     * Requests a call to [rawNumber].
     * If [requireConfirmation] is true or user enabled "Confirm Before Calling", displays a clear
     * confirmation dialog before placing the call to prevent accidental calls.
     */
    fun requestCall(
        rawNumber: String,
        displayName: String = "",
        bypassConfirmation: Boolean = false,
        explicitSimId: String? = null
    ) {
        val sanitized = PhoneNormalizer.sanitizeDialInput(rawNumber)
        if (!PhoneNormalizer.isValidDialableNumber(sanitized)) {
            showFeedback("Enter a valid phone number before calling.")
            return
        }

        val state = uiState.value
        if (!bypassConfirmation && state.preferences.confirmBeforeCallEnabled) {
            _internalState.update {
                it.copy(
                    pendingCallConfirmation = PendingCallConfirmation(
                        displayName = displayName.ifBlank { sanitized },
                        rawNumber = rawNumber
                    )
                )
            }
            return
        }

        _internalState.update {
            it.copy(pendingCallConfirmation = null, pendingSimSelection = null)
        }

        val outcome = telecomManager.initiateCall(
            rawInput = sanitized,
            preferredSimId = state.preferences.preferredSimId,
            explicitSimId = explicitSimId
        )

        when (outcome) {
            is CallInitiationOutcome.PlacedDirectly -> {
                showFeedback("Calling ${displayName.ifBlank { rawNumber }}…")
            }
            is CallInitiationOutcome.OpenedSystemDialerFallback -> {
                showFeedback("Opened dialer for ${displayName.ifBlank { rawNumber }}")
            }
            is CallInitiationOutcome.RequiresSimSelection -> {
                _internalState.update {
                    it.copy(
                        pendingSimSelection = PendingSimSelection(
                            rawNumber = outcome.sanitizedNumber,
                            availableSims = outcome.availableSims
                        )
                    )
                }
            }
            is CallInitiationOutcome.Error -> {
                showFeedback(outcome.reason)
            }
        }
    }

    fun dismissCallConfirmation() {
        _internalState.update { it.copy(pendingCallConfirmation = null) }
    }

    fun dismissSimSelection() {
        _internalState.update { it.copy(pendingSimSelection = null) }
    }

    // --- Detail Sheet & Call Assist Actions ---
    fun openDetailForContact(contact: ContactItem) {
        _internalState.update {
            it.copy(
                selectedDetailTarget = DetailSheetTarget(
                    displayName = contact.displayName,
                    rawNumber = contact.primaryNumber,
                    normalizedNumber = contact.primaryNormalizedNumber,
                    numbers = contact.numbers,
                    photoUri = contact.photoUri,
                    note = contact.customNote.orEmpty(),
                    customLabel = contact.customLabel.orEmpty(),
                    isFavorite = contact.isFavorite,
                    isImportant = contact.isImportant,
                    isBlocked = contact.isBlocked
                )
            )
        }
    }

    fun openDetailForCallRecord(record: CallRecord) {
        val state = uiState.value
        val matchedContact = state.allContacts.firstOrNull { c ->
            c.numbers.any { n ->
                n.normalizedNumber == record.normalizedNumber ||
                    PhoneNormalizer.areNumbersEquivalent(n.rawNumber, record.rawNumber)
            }
        }
        if (matchedContact != null) {
            openDetailForContact(matchedContact)
            return
        }

        val norm = record.normalizedNumber.ifBlank { PhoneNormalizer.normalizeForComparison(record.rawNumber) }
        val assist = state.assistMetadataList.firstOrNull { it.normalizedNumber == norm }
        val blocked = state.blockedNumbers.any {
            it.normalizedNumber == norm || PhoneNormalizer.areNumbersEquivalent(it.normalizedNumber, norm)
        }

        _internalState.update {
            it.copy(
                selectedDetailTarget = DetailSheetTarget(
                    displayName = record.displayIdentity.ifBlank { record.rawNumber },
                    rawNumber = record.rawNumber,
                    normalizedNumber = norm,
                    numbers = if (record.rawNumber.isNotBlank()) {
                        listOf(
                            ContactNumber(
                                rawNumber = record.rawNumber,
                                normalizedNumber = norm,
                                typeLabel = "Recent"
                            )
                        )
                    } else {
                        emptyList()
                    },
                    photoUri = record.photoUri,
                    note = assist?.note.orEmpty(),
                    customLabel = assist?.customLabel.orEmpty(),
                    isFavorite = assist?.isFavorite ?: false,
                    isImportant = assist?.isImportant ?: false,
                    isBlocked = blocked
                )
            )
        }
    }

    fun closeDetailSheet() {
        _internalState.update { it.copy(selectedDetailTarget = null) }
    }

    fun saveCallAssistMetadata(
        rawNumber: String,
        displayName: String,
        note: String,
        customLabel: String,
        isFavorite: Boolean,
        isImportant: Boolean
    ) {
        viewModelScope.launch {
            assistRepository.saveContactAssist(
                rawNumber = rawNumber,
                displayNameHint = displayName,
                note = note,
                customLabel = customLabel,
                isFavorite = isFavorite,
                isImportant = isImportant
            )
            _internalState.update { state ->
                val cur = state.selectedDetailTarget
                if (cur != null && cur.rawNumber == rawNumber) {
                    state.copy(
                        selectedDetailTarget = cur.copy(
                            note = note.trim(),
                            customLabel = customLabel.trim(),
                            isFavorite = isFavorite,
                            isImportant = isImportant
                        )
                    )
                } else {
                    state
                }
            }
            showFeedback("Call Assist note saved locally.")
        }
    }

    fun deleteCallAssistMetadata(rawOrNormalizedNumber: String) {
        viewModelScope.launch {
            assistRepository.deleteContactAssist(rawOrNormalizedNumber)
            _internalState.update { state ->
                val cur = state.selectedDetailTarget
                if (cur != null) {
                    state.copy(
                        selectedDetailTarget = cur.copy(
                            note = "",
                            customLabel = "",
                            isFavorite = false,
                            isImportant = false
                        )
                    )
                } else {
                    state
                }
            }
            showFeedback("Call Assist note removed.")
        }
    }

    fun addCallReminder(rawNumber: String, contactName: String, reason: String, minutesFromNow: Int = 30) {
        if (rawNumber.isBlank()) return
        viewModelScope.launch {
            val id = assistRepository.addCallReminder(
                rawNumber = rawNumber,
                contactName = contactName,
                reason = reason.ifBlank { "Return call" },
                dueInMinutes = minutesFromNow
            )
            if (id > 0) {
                showFeedback("Call-back reminder saved for $contactName.")
            }
        }
    }

    fun completeReminder(reminderId: Long) {
        viewModelScope.launch {
            assistRepository.completeReminder(reminderId)
            showFeedback("Reminder marked complete.")
        }
    }

    fun deleteReminder(reminderId: Long) {
        viewModelScope.launch {
            assistRepository.deleteReminder(reminderId)
        }
    }

    // --- Blocklist Management ---
    fun blockNumber(rawNumber: String, reason: String = "") {
        viewModelScope.launch {
            val ok = assistRepository.blockNumber(rawNumber, reason)
            if (ok) {
                _internalState.update { state ->
                    val cur = state.selectedDetailTarget
                    if (cur != null && PhoneNormalizer.areNumbersEquivalent(cur.rawNumber, rawNumber)) {
                        state.copy(selectedDetailTarget = cur.copy(isBlocked = true))
                    } else {
                        state
                    }
                }
                showFeedback("Added $rawNumber to local blocklist.")
            } else {
                showFeedback("Enter a valid phone number to block.")
            }
        }
    }

    fun unblockNumber(rawOrNormalizedNumber: String) {
        viewModelScope.launch {
            assistRepository.unblockNumber(rawOrNormalizedNumber)
            _internalState.update { state ->
                val cur = state.selectedDetailTarget
                if (cur != null &&
                    (cur.normalizedNumber == rawOrNormalizedNumber ||
                        PhoneNormalizer.areNumbersEquivalent(cur.rawNumber, rawOrNormalizedNumber))
                ) {
                    state.copy(selectedDetailTarget = cur.copy(isBlocked = false))
                } else {
                    state
                }
            }
            showFeedback("Removed from blocklist.")
        }
    }

    // --- System Call Log Deletion ---
    fun deleteCallSessionRecords(ids: List<Long>) {
        viewModelScope.launch {
            val deleted = callLogRepository.deleteCallRecordsByIds(ids)
            if (deleted) {
                loadCallHistory()
                showFeedback("Call record deleted from system log.")
            } else {
                showFeedback("Deleting system call records requires Write Call Log permission.")
            }
        }
    }

    fun clearAllSystemCallHistory() {
        viewModelScope.launch {
            val cleared = callLogRepository.clearAllCallHistory()
            if (cleared) {
                loadCallHistory()
                showFeedback("System call history cleared.")
            } else {
                showFeedback("Clearing call history requires Write Call Log permission.")
            }
        }
    }

    // --- Preferences Updates ---
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setLanguageCode(code: String) {
        viewModelScope.launch { preferencesRepository.setLanguageCode(code) }
    }

    fun setHapticFeedback(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setHapticFeedback(enabled) }
    }

    fun setDialpadSound(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setDialpadSound(enabled) }
    }

    fun setPreferredSimId(simId: String) {
        viewModelScope.launch { preferencesRepository.setPreferredSimId(simId) }
    }

    fun setOneHandedMode(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setOneHandedMode(enabled) }
    }

    fun setReduceMotion(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setReduceMotion(enabled) }
    }

    fun setConfirmBeforeCall(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setConfirmBeforeCall(enabled) }
    }

    fun dismissFeedbackBanner() {
        _internalState.update { it.copy(feedbackBannerMessage = null) }
    }

    private fun showFeedback(message: String) {
        _internalState.update { it.copy(feedbackBannerMessage = message) }
    }

    private fun playDialpadTone(digit: Char) {
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                val toneType = when (digit) {
                    '0' -> ToneGenerator.TONE_DTMF_0
                    '1' -> ToneGenerator.TONE_DTMF_1
                    '2' -> ToneGenerator.TONE_DTMF_2
                    '3' -> ToneGenerator.TONE_DTMF_3
                    '4' -> ToneGenerator.TONE_DTMF_4
                    '5' -> ToneGenerator.TONE_DTMF_5
                    '6' -> ToneGenerator.TONE_DTMF_6
                    '7' -> ToneGenerator.TONE_DTMF_7
                    '8' -> ToneGenerator.TONE_DTMF_8
                    '9' -> ToneGenerator.TONE_DTMF_9
                    '*' -> ToneGenerator.TONE_DTMF_S
                    '#' -> ToneGenerator.TONE_DTMF_P
                    else -> return@launch
                }
                val toneGen = ToneGenerator(AudioManager.STREAM_DTMF, 65)
                toneGen.startTone(toneType, 90)
                kotlinx.coroutines.delay(100L)
                toneGen.release()
            }
        }
    }

    private fun buildDerivedState(
        base: VozUiState,
        prefs: VozUserPreferences,
        assistList: List<ContactAssistEntity>,
        blockedList: List<BlockedNumberEntity>,
        reminders: List<CallReminderEntity>
    ): VozUiState {
        val assistMap = assistList.associateBy { it.normalizedNumber }
        val blockedSet = blockedList.map { it.normalizedNumber }.toSet()

        // 1. Enrich contacts with local Call Assist notes, labels, favorites, and blocklist status
        val enrichedContacts = base.allContacts.map { contact ->
            val matchedAssist = contact.numbers.firstNotNullOfOrNull { num ->
                assistMap[num.normalizedNumber]
            }
            val isBlocked = contact.numbers.any { num ->
                num.normalizedNumber in blockedSet ||
                    blockedList.any { b -> PhoneNormalizer.areNumbersEquivalent(b.normalizedNumber, num.rawNumber) }
            }
            contact.copy(
                customNote = matchedAssist?.note?.takeIf { it.isNotBlank() },
                customLabel = matchedAssist?.customLabel?.takeIf { it.isNotBlank() },
                isFavorite = matchedAssist?.isFavorite ?: false,
                isImportant = matchedAssist?.isImportant ?: false,
                isBlocked = isBlocked
            )
        }

        // Also include standalone Call Assist entries (for numbers not in system contacts) in Pinned/Important
        val contactNormSet = enrichedContacts.flatMap { c -> c.numbers.map { it.normalizedNumber } }.toSet()
        val standalonePinned = assistList
            .filter { (it.isFavorite || it.isImportant) && it.normalizedNumber !in contactNormSet }
            .map { entity ->
                ContactItem(
                    contactId = -entity.updatedAtMillis,
                    lookupKey = "local_${entity.normalizedNumber}",
                    displayName = entity.displayNameHint.ifBlank { entity.rawNumber },
                    photoUri = null,
                    numbers = listOf(
                        ContactNumber(
                            rawNumber = entity.rawNumber,
                            normalizedNumber = entity.normalizedNumber,
                            typeLabel = entity.customLabel.ifBlank { "Pinned" }
                        )
                    ),
                    customNote = entity.note.takeIf { it.isNotBlank() },
                    customLabel = entity.customLabel.takeIf { it.isNotBlank() },
                    isFavorite = entity.isFavorite,
                    isImportant = entity.isImportant,
                    isBlocked = entity.normalizedNumber in blockedSet
                )
            }

        val pinnedAndImportant = (enrichedContacts.filter { it.isFavorite || it.isImportant } + standalonePinned)
            .sortedWith(compareByDescending<ContactItem> { it.isImportant }.thenBy { it.displayName })

        // 2. Match contacts for Home Search / T9 Dialpad
        val homeQuery = base.homeSearchOrDialInput.trim()
        val homeMatched = if (homeQuery.isEmpty()) {
            emptyList()
        } else {
            enrichedContacts.mapNotNull { contact ->
                val score = T9AndBanglaMatcher.evaluateContactMatch(
                    displayName = contact.displayName,
                    phoneNumbers = contact.numbers.map { it.rawNumber },
                    rawQuery = homeQuery
                )
                if (score.matched) Pair(contact, score.rank) else null
            }
                .sortedWith(compareBy<Pair<ContactItem, Int>> { it.second }.thenBy { it.first.displayName })
                .map { it.first }
                .take(30)
        }

        // 3. Filter contacts for Contacts Directory tab
        val dirQuery = base.contactsSearchQuery.trim()
        val dirFiltered = if (dirQuery.isEmpty()) {
            enrichedContacts
        } else {
            enrichedContacts.mapNotNull { contact ->
                val score = T9AndBanglaMatcher.evaluateContactMatch(
                    displayName = contact.displayName,
                    phoneNumbers = contact.numbers.map { it.rawNumber },
                    rawQuery = dirQuery
                )
                if (score.matched) Pair(contact, score.rank) else null
            }
                .sortedWith(compareBy<Pair<ContactItem, Int>> { it.second }.thenBy { it.first.displayName })
                .map { it.first }
        }

        // 4. Enrich call records with contact names, photos, Call Assist notes, and blocklist status
        val contactByNormalizedNumber = HashMap<String, ContactItem>()
        for (c in enrichedContacts) {
            for (n in c.numbers) {
                contactByNormalizedNumber[n.normalizedNumber] = c
            }
        }

        val enrichedCallRecords = base.rawCallRecords.map { rec ->
            val matchedContact = contactByNormalizedNumber[rec.normalizedNumber]
            val assist = assistMap[rec.normalizedNumber]
            val isBlocked = rec.normalizedNumber in blockedSet
            rec.copy(
                resolvedContactName = matchedContact?.displayName
                    ?: assist?.displayNameHint?.takeIf { it.isNotBlank() }
                    ?: rec.cachedName,
                resolvedContactId = matchedContact?.contactId,
                photoUri = matchedContact?.photoUri ?: rec.photoUri,
                isBlocked = isBlocked,
                assistNote = assist?.note?.takeIf { it.isNotBlank() },
                assistLabel = assist?.customLabel?.takeIf { it.isNotBlank() }
            )
        }

        val missedCount = enrichedCallRecords.count { it.callType == CallType.MISSED }

        val recentSessions = CallHistoryGrouper.groupConsecutiveCalls(
            records = enrichedCallRecords.take(60),
            languageCode = prefs.languageCode
        ).take(15)

        // 5. Filter and group Call History for Activity tab
        val filteredRecords = enrichedCallRecords.filter { rec ->
            val matchesFilter = when (base.activityFilter) {
                CallLogFilter.ALL -> true
                CallLogFilter.MISSED -> rec.callType == CallType.MISSED
                CallLogFilter.INCOMING -> rec.callType == CallType.INCOMING
                CallLogFilter.OUTGOING -> rec.callType == CallType.OUTGOING
            }
            if (!matchesFilter) return@filter false

            val actQuery = base.activitySearchQuery.trim()
            if (actQuery.isEmpty()) return@filter true

            val score = T9AndBanglaMatcher.evaluateContactMatch(
                displayName = rec.displayIdentity,
                phoneNumbers = listOf(rec.rawNumber),
                rawQuery = actQuery
            )
            score.matched
        }

        val activitySessions = CallHistoryGrouper.groupConsecutiveCalls(
            records = filteredRecords,
            languageCode = prefs.languageCode
        )

        return base.copy(
            preferences = prefs,
            allContacts = enrichedContacts,
            pinnedAndImportantContacts = pinnedAndImportant,
            homeMatchedContacts = homeMatched,
            directoryFilteredContacts = dirFiltered,
            rawCallRecords = enrichedCallRecords,
            recentGroupedSessions = recentSessions,
            activityGroupedSessions = activitySessions,
            missedCallCount = missedCount,
            assistMetadataList = assistList,
            blockedNumbers = blockedList,
            activeReminders = reminders
        )
    }
}
