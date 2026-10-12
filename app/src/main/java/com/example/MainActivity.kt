package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.telecom.CallSessionController
import com.example.telecom.InCallActivity
import com.example.ui.VozDestination
import com.example.ui.VozMainViewModel
import com.example.ui.VozUiState
import com.example.ui.components.ContactAssistDetailSheet
import com.example.ui.screens.ActivityScreen
import com.example.ui.screens.ContactsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ToolsScreen
import com.example.ui.theme.CallAnswerGreen
import com.example.ui.theme.VozTheme
import kotlinx.coroutines.delay
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: VozMainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        extractAndApplyDialIntent(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val activeCalls by CallSessionController.activeCalls.collectAsStateWithLifecycle()

            val baseContext = LocalContext.current
            val localizedContext = remember(baseContext, uiState.preferences.languageCode) {
                createLocalizedContext(baseContext, uiState.preferences.languageCode)
            }

            CompositionLocalProvider(LocalContext provides localizedContext) {
                VozTheme(themeMode = uiState.preferences.themeMode) {
                    VozAppRoot(
                        uiState = uiState,
                        hasOngoingCall = activeCalls.isNotEmpty(),
                        viewModel = viewModel,
                        onOpenActiveCallScreen = {
                            startActivity(Intent(this, InCallActivity::class.java))
                        },
                        onOpenAppSystemSettings = {
                            val settingsIntent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", packageName, null)
                            )
                            runCatching { startActivity(settingsIntent) }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractAndApplyDialIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissionsAndSystemData()
    }

    private fun extractAndApplyDialIntent(incomingIntent: Intent?) {
        incomingIntent ?: return
        val action = incomingIntent.action
        if (action == Intent.ACTION_DIAL || action == Intent.ACTION_VIEW) {
            val dataUri = incomingIntent.data
            if (dataUri != null && dataUri.scheme.equals("tel", ignoreCase = true)) {
                val numberPart = dataUri.schemeSpecificPart
                viewModel.handleIncomingDialIntentNumber(numberPart)
            }
        }
    }

    private fun createLocalizedContext(base: Context, languageCode: String): Context {
        val locale = if (languageCode.startsWith("bn")) {
            Locale.forLanguageTag("bn-BD")
        } else {
            Locale.US
        }
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        val configContext = base.createConfigurationContext(config)
        return object : android.content.ContextWrapper(base) {
            override fun getResources(): android.content.res.Resources = configContext.resources
        }
    }
}

private data class NavDestinationSpec(
    val destination: VozDestination,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun VozAppRoot(
    uiState: VozUiState,
    hasOngoingCall: Boolean,
    viewModel: VozMainViewModel,
    onOpenActiveCallScreen: () -> Unit,
    onOpenAppSystemSettings: () -> Unit
) {
    // BackHandler for secondary tabs or expanded dialpad
    BackHandler(
        enabled = uiState.isDialpadVisible || uiState.currentDestination != VozDestination.HOME
    ) {
        if (uiState.isDialpadVisible) {
            viewModel.setDialpadVisible(false)
        } else {
            viewModel.selectDestination(VozDestination.HOME)
        }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshPermissionsAndSystemData()
    }

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshPermissionsAndSystemData()
    }

    val requestCorePermissions = {
        val perms = buildList {
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.WRITE_CALL_LOG)
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                add(Manifest.permission.ANSWER_PHONE_CALLS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        permissionsLauncher.launch(perms)
    }

    val requestDefaultDialerRole = {
        val intent = viewModel.telecomManager.createRequestDefaultDialerIntent()
        if (intent != null) {
            runCatching { roleLauncher.launch(intent) }
        }
    }

    val requestCallScreeningRole = {
        val intent = viewModel.telecomManager.createRequestCallScreeningIntent()
        if (intent != null) {
            runCatching { roleLauncher.launch(intent) }
        }
    }

    // Auto-dismiss transient feedback banner
    LaunchedEffect(uiState.feedbackBannerMessage) {
        if (uiState.feedbackBannerMessage != null) {
            delay(3200L)
            viewModel.dismissFeedbackBanner()
        }
    }

    // Ambiguous / Confirm-Before-Call Dialog
    uiState.pendingCallConfirmation?.let { pending ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissCallConfirmation() },
            title = { Text(stringResource(R.string.dialpad_confirm_ambiguous_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.dialpad_confirm_ambiguous_body,
                        pending.displayName,
                        pending.rawNumber
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.requestCall(
                            rawNumber = pending.rawNumber,
                            displayName = pending.displayName,
                            bypassConfirmation = true
                        )
                    },
                    modifier = Modifier.testTag("confirm_call_dialog_button")
                ) {
                    Text(stringResource(R.string.dialpad_call_desc))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCallConfirmation() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Dual-SIM Selection Dialog
    uiState.pendingSimSelection?.let { simPrompt ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissSimSelection() },
            title = { Text(stringResource(R.string.dialpad_sim_select_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    simPrompt.availableSims.forEach { sim ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.requestCall(
                                        rawNumber = simPrompt.rawNumber,
                                        bypassConfirmation = true,
                                        explicitSimId = sim.id
                                    )
                                }
                                .testTag("select_sim_${sim.slotIndex}"),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "SIM ${sim.slotIndex} • ${sim.label}",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = sim.carrierName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.Call,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSimSelection() }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Contact & Call Assist Detail Bottom Sheet
    uiState.selectedDetailTarget?.let { detailTarget ->
        ContactAssistDetailSheet(
            target = detailTarget,
            onDismiss = { viewModel.closeDetailSheet() },
            onCallNumber = { num, name ->
                viewModel.closeDetailSheet()
                viewModel.requestCall(num, name)
            },
            onSaveAssist = { note, label, isFav, isImp ->
                viewModel.saveCallAssistMetadata(
                    rawNumber = detailTarget.rawNumber,
                    displayName = detailTarget.displayName,
                    note = note,
                    customLabel = label,
                    isFavorite = isFav,
                    isImportant = isImp
                )
            },
            onDeleteAssist = {
                viewModel.deleteCallAssistMetadata(detailTarget.normalizedNumber)
            },
            onScheduleReminder = { reason, mins ->
                viewModel.addCallReminder(
                    rawNumber = detailTarget.rawNumber,
                    contactName = detailTarget.displayName,
                    reason = reason,
                    minutesFromNow = mins
                )
            },
            onBlockNumber = { num ->
                viewModel.blockNumber(num)
            },
            onUnblockNumber = { num ->
                viewModel.unblockNumber(num)
            }
        )
    }

    val navItems = remember {
        listOf(
            NavDestinationSpec(
                destination = VozDestination.HOME,
                labelRes = R.string.nav_home,
                selectedIcon = Icons.Filled.Home,
                unselectedIcon = Icons.Outlined.Home,
                testTag = "nav_tab_home"
            ),
            NavDestinationSpec(
                destination = VozDestination.CONTACTS,
                labelRes = R.string.nav_contacts,
                selectedIcon = Icons.Filled.Contacts,
                unselectedIcon = Icons.Outlined.Contacts,
                testTag = "nav_tab_contacts"
            ),
            NavDestinationSpec(
                destination = VozDestination.ACTIVITY,
                labelRes = R.string.nav_activity,
                selectedIcon = Icons.Filled.History,
                unselectedIcon = Icons.Outlined.History,
                testTag = "nav_tab_activity"
            ),
            NavDestinationSpec(
                destination = VozDestination.TOOLS,
                labelRes = R.string.nav_tools,
                selectedIcon = Icons.Filled.Build,
                unselectedIcon = Icons.Outlined.Build,
                testTag = "nav_tab_tools"
            )
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!isExpandedScreen) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp,
                        modifier = Modifier.testTag("voz_bottom_nav")
                    ) {
                        navItems.forEach { item ->
                            val selected = uiState.currentDestination == item.destination
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectDestination(item.destination) },
                                icon = {
                                    if (item.destination == VozDestination.ACTIVITY && uiState.missedCallCount > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge {
                                                    Text(uiState.missedCallCount.toString())
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                                contentDescription = stringResource(item.labelRes)
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = stringResource(item.labelRes)
                                        )
                                    }
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpandedScreen) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .testTag("voz_nav_rail")
                    ) {
                        navItems.forEach { item ->
                            val selected = uiState.currentDestination == item.destination
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectDestination(item.destination) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Return-to-Active-Call Banner so users never lose context during a call
                        AnimatedVisibility(visible = hasOngoingCall) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenActiveCallScreen() }
                                    .testTag("return_to_call_banner"),
                                color = CallAnswerGreen
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Call,
                                        contentDescription = null,
                                        tint = androidx.compose.ui.graphics.Color.White
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Active Call in Progress • Tap to return",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = androidx.compose.ui.graphics.Color.White
                                    )
                                }
                            }
                        }

                        // Primary Destination Content
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            when (uiState.currentDestination) {
                                VozDestination.HOME -> HomeScreen(
                                    uiState = uiState,
                                    onSearchOrDialChange = viewModel::onHomeSearchOrDialChange,
                                    onDialpadDigit = viewModel::onDialpadDigitPressed,
                                    onDialpadBackspace = viewModel::onDialpadBackspace,
                                    onDialpadClearAll = viewModel::onDialpadClearAll,
                                    onToggleDialpad = viewModel::setDialpadVisible,
                                    onRequestCall = { num, name -> viewModel.requestCall(num, name) },
                                    onOpenContactDetail = viewModel::openDetailForContact,
                                    onOpenCallRecordDetail = viewModel::openDetailForCallRecord,
                                    onCompleteReminder = viewModel::completeReminder,
                                    onRequestPermissionsClick = requestCorePermissions,
                                    onRequestDefaultDialerClick = requestDefaultDialerRole
                                )

                                VozDestination.CONTACTS -> ContactsScreen(
                                    uiState = uiState,
                                    onSearchQueryChange = viewModel::onContactsSearchQueryChange,
                                    onCallContact = { viewModel.requestCall(it.primaryNumber, it.displayName) },
                                    onOpenContactDetail = viewModel::openDetailForContact,
                                    onRequestContactsPermission = {
                                        permissionsLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                                    },
                                    onOpenSystemSettings = onOpenAppSystemSettings,
                                    onRefreshContacts = viewModel::loadContacts
                                )

                                VozDestination.ACTIVITY -> ActivityScreen(
                                    uiState = uiState,
                                    onSearchQueryChange = viewModel::onActivitySearchQueryChange,
                                    onFilterChange = viewModel::onActivityFilterChange,
                                    onCallBack = { rec -> viewModel.requestCall(rec.rawNumber, rec.displayIdentity) },
                                    onOpenRecordDetail = viewModel::openDetailForCallRecord,
                                    onDeleteSessionRecords = viewModel::deleteCallSessionRecords,
                                    onClearAllHistory = viewModel::clearAllSystemCallHistory,
                                    onRequestCallLogPermission = {
                                        permissionsLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.READ_CALL_LOG,
                                                Manifest.permission.WRITE_CALL_LOG
                                            )
                                        )
                                    },
                                    onOpenSystemSettings = onOpenAppSystemSettings,
                                    onRefreshCallLog = viewModel::loadCallHistory
                                )

                                VozDestination.TOOLS -> ToolsScreen(
                                    uiState = uiState,
                                    onRequestDefaultDialerRole = requestDefaultDialerRole,
                                    onRequestCallScreeningRole = requestCallScreeningRole,
                                    onBlockNumber = viewModel::blockNumber,
                                    onUnblockNumber = viewModel::unblockNumber,
                                    onThemeModeChange = viewModel::setThemeMode,
                                    onLanguageChange = viewModel::setLanguageCode,
                                    onHapticsChange = viewModel::setHapticFeedback,
                                    onSoundChange = viewModel::setDialpadSound,
                                    onOneHandedChange = viewModel::setOneHandedMode,
                                    onReduceMotionChange = viewModel::setReduceMotion,
                                    onConfirmBeforeCallChange = viewModel::setConfirmBeforeCall,
                                    onPreferredSimChange = viewModel::setPreferredSimId
                                )
                            }
                        }
                    }

                    // Transient Feedback Snackbar
                    uiState.feedbackBannerMessage?.let { msg ->
                        Snackbar(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(16.dp)
                                .testTag("feedback_snackbar"),
                            shape = RoundedCornerShape(14.dp),
                            action = {
                                TextButton(onClick = { viewModel.dismissFeedbackBanner() }) {
                                    Text("OK")
                                }
                            }
                        ) {
                            Text(text = msg)
                        }
                    }
                }
            }
        }
    }
}
