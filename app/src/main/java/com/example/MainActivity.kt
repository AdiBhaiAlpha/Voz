package com.example

import androidx.compose.ui.unit.dp

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.PhoneNormalizer
import com.example.ui.DetailSheetTarget
import com.example.ui.VozDestination
import com.example.ui.VozMainViewModel
import com.example.ui.components.CallRecordDetailBottomSheet
import com.example.ui.components.ConfirmBeforeCallDialog
import com.example.ui.components.ContactDetailBottomSheet
import com.example.ui.components.SimSelectionDialog
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import android.util.Log
import com.example.telecom.CallLifecycleState
import com.example.telecom.CallSessionController
import com.example.telecom.InCallActivity
import com.example.ui.components.DynamicIslandCallPill
import com.example.ui.screens.ActivityScreen
import com.example.ui.screens.ContactsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ToolsScreen
import com.example.ui.theme.VOZTheme

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<VozMainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleDialIntent(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            VOZTheme(themeMode = uiState.preferences.themeMode) {
                VozAppRoot(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDialIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        com.example.telecom.CallPresentationPolicy.mainVisible = true
        com.example.telecom.VozCallOverlayService.stop(this)
        viewModel.refreshPermissionsAndSystemData()
    }

    override fun onStop() {
        com.example.telecom.CallPresentationPolicy.mainVisible = false
        super.onStop()
        if (!com.example.telecom.CallPresentationPolicy.fullScreenVisible &&
            CallSessionController.activeCalls.value.any { it.state != CallLifecycleState.DISCONNECTED } &&
            viewModel.preferencesRepository.loadPreferences().floatingIslandOverlayEnabled) {
            com.example.telecom.VozCallOverlayService.startIfPermitted(this)
        }
    }

    private fun handleDialIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val data: Uri? = intent.data

        if (action == Intent.ACTION_DIAL || action == Intent.ACTION_VIEW) {
            val schemeSpecific = data?.schemeSpecificPart
            if (!schemeSpecific.isNullOrBlank()) {
                val sanitized = PhoneNormalizer.sanitizeDialInput(schemeSpecific)
                viewModel.onHomeSearchOrDialChange(sanitized)
                viewModel.setDialpadVisible(true)
                viewModel.selectDestination(VozDestination.HOME)
            }
        }
    }
}

@Composable
fun VozAppRoot(
    viewModel: VozMainViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeCalls by CallSessionController.activeCalls.collectAsStateWithLifecycle()
    val ringingCall = activeCalls.firstOrNull { it.state == CallLifecycleState.RINGING }
    val activeCall = ringingCall ?: activeCalls.firstOrNull()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Launcher for RoleManager.ROLE_DIALER
    val dialerRoleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshPermissionsAndSystemData()
        if (viewModel.telecomDialerManager.isDefaultDialer()) {
            viewModel.showFeedback("VOZ is now your default phone app")
        } else {
            viewModel.showFeedback("Default phone app not set")
        }
    }

    // Launcher for RoleManager.ROLE_CALL_SCREENING
    val callScreeningLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshPermissionsAndSystemData()
        if (viewModel.telecomDialerManager.isCallScreeningRoleHeld()) {
            viewModel.showFeedback("Call screening active")
        }
    }

    val onRequestDialerRole = {
        val intent = viewModel.telecomDialerManager.createRequestDefaultDialerIntent()
        if (intent != null) {
            try {
                dialerRoleLauncher.launch(intent)
            } catch (_: Exception) {
                viewModel.showFeedback("Unable to launch default dialer settings")
            }
        } else {
            viewModel.showFeedback("Default dialer role not available on this device")
        }
    }

    val onRequestScreeningRole = {
        val intent = viewModel.telecomDialerManager.createRequestCallScreeningIntent()
        if (intent != null) {
            try {
                callScreeningLauncher.launch(intent)
            } catch (_: Exception) {
                viewModel.showFeedback("Unable to launch call screening settings")
            }
        } else {
            viewModel.showFeedback("Call screening role not available on this device")
        }
    }

    // Runtime Permission Launcher for phone, contacts, call log, notifications
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshPermissionsAndSystemData()
    }

    fun requestRequiredPermissions() {
        val perms = mutableListOf(
            android.Manifest.permission.CALL_PHONE,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.WRITE_CALL_LOG,
            android.Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    // Auto-request core permissions on launch if not granted
    LaunchedEffect(Unit) {
        if (!uiState.hasCallPhonePermission || !uiState.hasContactsPermission || !uiState.hasCallLogPermission) {
            requestRequiredPermissions()
        }
    }

    // Feedback message handler
    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissFeedbackBanner()
        }
    }

    // Back button behavior
    BackHandler(enabled = uiState.isDialpadVisible || uiState.currentDestination != VozDestination.HOME) {
        if (uiState.isDialpadVisible) {
            viewModel.setDialpadVisible(false)
        } else if (uiState.currentDestination != VozDestination.HOME) {
            viewModel.selectDestination(VozDestination.HOME)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(modifier = Modifier.testTag("main_bottom_nav")) {
                VozDestination.entries.forEach { destination ->
                    val selected = uiState.currentDestination == destination
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            viewModel.selectDestination(destination)
                            if (destination != VozDestination.HOME) {
                                viewModel.setDialpadVisible(false)
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = stringResource(destination.titleRes)
                            )
                        },
                        label = { Text(stringResource(destination.titleRes)) },
                        modifier = Modifier.testTag("nav_item_${destination.name.lowercase()}")
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = if (activeCall != null) 58.dp else 0.dp)
            ) {
                when (uiState.currentDestination) {
                    VozDestination.HOME -> HomeScreen(
                        uiState = uiState,
                        onSearchOrDialChange = { viewModel.onHomeSearchOrDialChange(it) },
                        onDigitPressed = { viewModel.onDialpadDigitPressed(it) },
                        onBackspace = { viewModel.onDialpadBackspace() },
                        onClearAll = { viewModel.onDialpadClearAll() },
                        onToggleDialpad = { viewModel.setDialpadVisible(it) },
                        onPlaceCall = { rawNumber, name, explicitSim ->
                            viewModel.placeRealCall(rawNumber, name, explicitSim)
                        },
                        onContactClick = { viewModel.openDetailForContact(it) },
                        onRecordClick = { viewModel.openDetailForCallRecord(it) },
                        onGrantPermissionsClick = { requestRequiredPermissions() },
                        onRequestDefaultDialer = onRequestDialerRole
                    )

                    VozDestination.CONTACTS -> ContactsScreen(
                        uiState = uiState,
                        onSearchQueryChange = { viewModel.onContactsSearchQueryChange(it) },
                        onFavoritesOnlyChange = { viewModel.setFavoritesOnly(it) },
                        onContactClick = { viewModel.openDetailForContact(it) },
                        onPlaceCall = { rawNumber, name ->
                            viewModel.placeRealCall(rawNumber, name)
                        },
                        onGrantPermissionsClick = { requestRequiredPermissions() }
                    )

                    VozDestination.ACTIVITY -> ActivityScreen(
                        uiState = uiState,
                        onFilterChange = { viewModel.onActivityFilterChange(it) },
                        onSearchQueryChange = { viewModel.onActivitySearchQueryChange(it) },
                        onRecordClick = { viewModel.openDetailForCallRecord(it) },
                        onPlaceCall = { rawNumber, name ->
                            viewModel.placeRealCall(rawNumber, name)
                        },
                        onClearAllHistory = { viewModel.clearAllCallHistory() },
                        onGrantPermissionsClick = { requestRequiredPermissions() }
                    )

                    VozDestination.TOOLS -> ToolsScreen(
                        uiState = uiState,
                        onRequestDefaultDialer = onRequestDialerRole,
                        onRequestCallScreening = onRequestScreeningRole,
                        onThemeChange = { viewModel.setThemeMode(it) },
                        onLanguageChange = { viewModel.setLanguageCode(it) },
                        onHapticChange = { viewModel.setHapticFeedback(it) },
                        onSoundChange = { viewModel.setDialpadSound(it) },
                        onAutoSpeakerChange = { viewModel.preferencesRepository.setAutoSpeaker(it) },
                        onConfirmBeforeCallChange = { viewModel.setConfirmBeforeCall(it) },
                        onPreferredSimChange = { viewModel.setPreferredSimId(it) },
                        onBlockNumber = { num, reason -> viewModel.blockNumber(num, reason) },
                        onUnblockNumber = { viewModel.unblockNumber(it) },
                        onCompleteReminder = { viewModel.completeReminder(it) },
                        onDeleteReminder = { viewModel.deleteReminder(it) },
                        onLogoutVozAccount = { viewModel.logoutVozAccount() },
                        onToggleFloatingOverlay = { viewModel.setFloatingIslandOverlayEnabled(it) }
                    )
                }
            }

            // Dynamic Island Floating Call Pill: Sits cleanly below system status bar / time & date
            activeCall?.let { call ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .zIndex(100f),
                    contentAlignment = Alignment.TopCenter
                ) {
                    DynamicIslandCallPill(
                        call = call,
                        onOpenFullScreen = {
                            val intent = Intent(context, InCallActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    )
                }
            }
        }

        // Contact Detail Bottom Sheet
        val detailTarget = uiState.detailSheetTarget
        if (detailTarget is DetailSheetTarget.Contact) {
            ContactDetailBottomSheet(
                contact = detailTarget.contact,
                onDismiss = { viewModel.closeDetailSheet() },
                onCallNumber = { num ->
                    viewModel.closeDetailSheet()
                    viewModel.placeRealCall(num, detailTarget.contact.displayName)
                },
                onSaveAssist = { note, label, fav, imp ->
                    viewModel.saveCallAssist(
                        phoneOrKey = detailTarget.contact.primaryNormalizedNumber,
                        note = note,
                        customLabel = label,
                        isFavorite = fav,
                        isImportant = imp
                    )
                },
                onAddReminder = { reason, mins ->
                    viewModel.addCallReminder(detailTarget.contact.displayName, detailTarget.contact.primaryNumber, mins, reason)
                },
                onBlockNumber = { num ->
                    viewModel.blockNumber(num, "Blocked from contact card")
                }
            )
        } else if (detailTarget is DetailSheetTarget.CallRecordDetail) {
            CallRecordDetailBottomSheet(
                detail = detailTarget,
                onDismiss = { viewModel.closeDetailSheet() },
                onCallNumber = { num ->
                    viewModel.closeDetailSheet()
                    viewModel.placeRealCall(num, detailTarget.displayName)
                },
                onSaveNote = { note ->
                    viewModel.saveCallAssist(
                        phoneOrKey = detailTarget.normalizedNumber,
                        note = note,
                        customLabel = detailTarget.customLabel ?: "",
                        isFavorite = detailTarget.isFavorite,
                        isImportant = detailTarget.isImportant
                    )
                },
                onBlockToggle = { num, shouldBlock ->
                    if (shouldBlock) {
                        viewModel.blockNumber(num, "Blocked from call history")
                    } else {
                        viewModel.unblockNumber(detailTarget.normalizedNumber)
                    }
                },
                onDeleteRecord = {
                    viewModel.closeDetailSheet()
                    viewModel.deleteCallRecord(detailTarget.recordId)
                }
            )
        }

        // Confirm Before Call Dialog
        uiState.pendingConfirmation?.let { conf ->
            ConfirmBeforeCallDialog(
                confirmation = conf,
                onConfirm = {
                    viewModel.placeRealCall(
                        rawNumber = conf.rawNumber,
                        displayName = conf.displayName,
                        explicitSimId = conf.explicitSimId,
                        bypassConfirmation = true
                    )
                },
                onDismiss = { viewModel.dismissCallConfirmation() }
            )
        }

        // Multi-SIM selection dialog
        uiState.pendingSimSelection?.let { selection ->
            SimSelectionDialog(
                selection = selection,
                onSimChosen = { simId ->
                    viewModel.dismissSimSelection()
                    viewModel.placeRealCall(
                        rawNumber = selection.rawNumber,
                        explicitSimId = simId,
                        bypassConfirmation = true
                    )
                },
                onDismiss = { viewModel.dismissSimSelection() }
            )
        }
    }
}
