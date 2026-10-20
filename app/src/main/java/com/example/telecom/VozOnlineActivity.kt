package com.example.telecom

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.domain.VozAddress
import com.example.online.OnlineCalls
import com.example.online.OnlineCallService
import com.example.ui.theme.VOZTheme

/** Native registration + audio client. Credentials never enter a WebView or a SIM intent. */
class VozOnlineActivity : ComponentActivity() {
    private val invitation = mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); OnlineCalls.initialize(this)
        invitation.value = intent.getStringExtra("callId")
        if (android.os.Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        setContent { VOZTheme { OnlineScreen(intent.getStringExtra("target").orEmpty(), invitation.value) } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); invitation.value = intent.getStringExtra("callId") }
    override fun onResume() { super.onResume(); CallPresentationPolicy.setFullScreenVisible(true, this); VozCallOverlayService.stop(this); OnlineCalls.ui(true, this) }
    override fun onStop() {
        CallPresentationPolicy.setFullScreenVisible(false, this); OnlineCalls.ui(false, this)
        if (OnlineCalls.isOnlineActive()) VozCallOverlayService.startIfPermitted(this)
        super.onStop()
    }
    companion object {
        fun open(context: Context, target: String = ""): Boolean {
            if (!BuildConfig.VOZ_SERVICE_URL.startsWith("https://")) return false
            context.startActivity(Intent(context, VozOnlineActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("target", target)); return true
        }
    }
}

@Composable
internal fun OnlineScreen(initialTarget: String, invitation: String?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val ui by OnlineCalls.state.collectAsStateWithLifecycle()
    var username by rememberSaveable { mutableStateOf("") }
    // Password deliberately not saved across Activity/process recreation.
    var password by remember { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf(initialTarget) }
    var create by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf<String?>(null) }
    var pendingMicAction by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun execute(action: String) { OnlineCalls.run {
        busy = true
        try { when (action) { "answer" -> OnlineCalls.accept(); "resume" -> OnlineCalls.resumeMedia(); else -> OnlineCalls.start(VozAddress.normalize(target) ?: target) } }
        finally { busy = false; if (!OnlineCalls.isOnlineActive()) OnlineCallService.stop(context) }
    } }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingMicAction; pendingMicAction = null
        if (granted && action != null) execute(action)
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(ui.signedIn) { if (ui.signedIn && android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
    LaunchedEffect(invitation, ui.signedIn) { if (invitation != null && ui.signedIn) OnlineCalls.run { OnlineCalls.openInvitation(invitation) } }
    if (consent != null) AlertDialog(onDismissRequest = { consent = null }, title = { Text("Enable call microphone?") },
        text = { Text("Your microphone is shared only with the other call participant. Internet data charges may apply.") },
        confirmButton = { TextButton(onClick = { pendingMicAction = consent; consent = null; mic.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { consent = null }) { Text("Cancel") } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete account and Caller ID?") },
        text = { Text("This permanently revokes your account, devices and sessions.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; OnlineCalls.run { OnlineCalls.signOut(true) } }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("VOZ online", style = MaterialTheme.typography.headlineLarge)
            Text(ui.state, style = MaterialTheme.typography.titleMedium)
            if (!ui.signedIn) {
                OutlinedTextField(username, { username = it }, modifier = Modifier.testTag("online-username"), label = { Text("Username") }, singleLine = true)
                OutlinedTextField(password, { password = it }, modifier = Modifier.testTag("online-password"), label = { Text("Password · 12+ characters") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(name, { name = it }, modifier = Modifier.testTag("online-display-name"), label = { Text("Display name · required for new Caller ID") }, singleLine = true)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(create, { create = it }, modifier = Modifier.testTag("online-create-account")); Text("Create a new account") }
                Button(modifier = Modifier.testTag("online-submit"), enabled = !busy && password.length >= 12 && username.matches(Regex("[a-z0-9_]{3,40}")) && (!create || com.example.domain.VozDisplayName.isValid(name)), onClick = {
                    OnlineCalls.run { busy = true; try { OnlineCalls.signIn(username, password, name, create); password = "" } finally { busy = false } }
                }) { Text(if (create) "Register Caller ID" else "Sign in") }
            } else {
                Text(ui.displayName); Text(ui.identity, style = MaterialTheme.typography.headlineSmall)
                if (ui.callId != null) Text(ui.remoteName, style = MaterialTheme.typography.titleLarge)
                if (ui.identity.isBlank()) {
                    OutlinedTextField(name, { name = it }, modifier = Modifier.testTag("online-display-name"), label = { Text("Display name") }, singleLine = true)
                    Button(enabled = com.example.domain.VozDisplayName.isValid(name), onClick = { OnlineCalls.run { OnlineCalls.finishIdentity(name) } }) { Text("Complete Caller ID registration") }
                }
                if (ui.callId == null) {
                    OutlinedTextField(target, { target = it }, label = { Text("VOZ-302-8671") }, singleLine = true)
                    Button(enabled = !busy && ui.identity.isNotBlank() && VozAddress.isComplete(target), onClick = { consent = "call" }) { Text("Call") }
                    TextButton(onClick = { OnlineCalls.run { OnlineCalls.signOut(false) } }) { Text("Sign out") }
                    TextButton(onClick = { confirmDelete = true }) { Text("Delete account") }
                } else {
                    if (ui.incoming && ui.state == "Incoming call") Button(enabled = !busy, onClick = { consent = "answer" }) { Text("Answer") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { OnlineCalls.mute() }) { Text(if (ui.muted) "Unmute" else "Mute") }
                        Button(onClick = { OnlineCalls.speaker() }) { Text(if (ui.speaker) "Earpiece" else "Speaker") }
                    }
                    if (ui.state == "Connecting…" || ui.state == "Reconnecting…") TextButton(enabled = !busy, onClick = { consent = "resume" }) { Text("Reconnect audio") }
                    Button(onClick = { OnlineCalls.endFromUi() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text(if (ui.state == "Incoming call") "Decline" else "End call") }
                }
                if (!ui.pushReady) Text("Push is not configured: open this screen to receive calls.", style = MaterialTheme.typography.bodySmall)
                if (!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                    TextButton(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("Enable call notifications") }
                }
                if (android.os.Build.VERSION.SDK_INT >= 34 && !context.getSystemService(android.app.NotificationManager::class.java).canUseFullScreenIntent()) {
                    TextButton(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, android.net.Uri.parse("package:${context.packageName}"))) }) { Text("Allow lock-screen calls") }
                }

            }
            ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { OnlineCalls.clearError() }) { Text("Dismiss") } }
        }
    }
}
