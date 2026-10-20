package com.example.telecom

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.online.OnlineCalls
import com.example.ui.theme.VOZTheme
import org.junit.Rule
import org.junit.Test

/** UI-only validation test: does not pretend to register or establish media. */
class OnlineRegistrationUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun newIdentityRequiresDisplayNameAndCredentials() {
        OnlineCalls.initialize(ApplicationProvider.getApplicationContext())
        compose.setContent { VOZTheme { OnlineScreen("", null) } }
        compose.onNodeWithTag("online-submit").assertIsNotEnabled()
        compose.onNodeWithTag("online-username").performTextInput("testuser")
        compose.onNodeWithTag("online-password").performTextInput("correct long password")
        compose.onNodeWithTag("online-submit").assertIsEnabled()
        compose.onNodeWithTag("online-create-account").performClick()
        compose.onNodeWithTag("online-submit").assertIsNotEnabled()
        compose.onNodeWithTag("online-display-name").performTextInput("Rahim")
        compose.onNodeWithTag("online-submit").assertIsEnabled()
    }
}
