package com.example

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.VozDatabase
import com.example.data.repository.VozAssistRepository
import com.example.ui.components.SmartDialpadPanel
import com.example.ui.theme.VozTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var inMemoryDb: VozDatabase
    private lateinit var assistRepository: VozAssistRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        inMemoryDb = Room.inMemoryDatabaseBuilder(context, VozDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        assistRepository = VozAssistRepository(inMemoryDb.vozAssistDao())
    }

    @After
    fun tearDown() {
        inMemoryDb.close()
    }

    @Test
    fun verifyAppNameAndBengaliLocalizationResources() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("VOZ", context.getString(R.string.app_name))

        val bnConfig = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag("bn-BD"))
        }
        val bnContext = context.createConfigurationContext(bnConfig)
        assertEquals("কন্টাক্টস", bnContext.getString(R.string.nav_contacts))
        assertEquals("কল লগ", bnContext.getString(R.string.nav_activity))
    }

    @Test
    fun roomRepositoryPersistsCallAssistNotesRemindersAndBlocklistWithBdNormalization() = runBlocking {
        // 1. Save Call Assist Note with domestic 017 number
        assistRepository.saveContactAssist(
            rawNumber = "01711223344",
            displayNameHint = "Dr. Mahmud",
            note = "Call after 7 PM regarding lab results",
            customLabel = "Doctor",
            isFavorite = true,
            isImportant = true
        )

        val savedNotes = assistRepository.assistMetadataFlow.first()
        assertEquals(1, savedNotes.size)
        assertEquals("+8801711223344", savedNotes.first().normalizedNumber)
        assertEquals("Doctor", savedNotes.first().customLabel)

        // 2. Block number using domestic format and verify +880 format is recognized as blocked
        val blockedOk = assistRepository.blockNumber("01911998877", "Spam caller")
        assertTrue(blockedOk)
        assertTrue(assistRepository.isBlocked("+880 1911-998877"))
        assertTrue(assistRepository.isBlocked("০১৯১১৯৯৮৮৭৭"))

        assistRepository.unblockNumber("+8801911998877")
        assertFalse(assistRepository.isBlocked("01911998877"))

        // 3. Schedule and complete Call-Back Reminder
        val reminderId = assistRepository.addCallReminder(
            rawNumber = "01711223344",
            contactName = "Dr. Mahmud",
            reason = "Follow up",
            dueInMinutes = 30
        )
        assertTrue(reminderId > 0L)
        assertEquals(1, assistRepository.activeRemindersFlow.first().size)

        assistRepository.completeReminder(reminderId)
        assertEquals(0, assistRepository.activeRemindersFlow.first().size)
    }

    @Test
    fun smartDialpadPanelKeyInputDeletionAndCallActionWork() {
        var enteredText by mutableStateOf("")

        composeTestRule.setContent {
            VozTheme {
                SmartDialpadPanel(
                    enteredText = enteredText,
                    hapticsEnabled = false,
                    oneHandedMode = false,
                    onDigitPress = { enteredText += it },
                    onBackspace = { if (enteredText.isNotEmpty()) enteredText = enteredText.dropLast(1) },
                    onClearAll = { enteredText = "" },
                    onPasteText = { enteredText = it },
                    onPlaceCall = {},
                    onCollapse = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // Dialpad panel is displayed
        composeTestRule.onNodeWithTag("smart_dialpad_panel").assertIsDisplayed()

        // Press digits 0, 1, 7
        composeTestRule.onNodeWithTag("dialpad_key_0").performClick()
        composeTestRule.onNodeWithTag("dialpad_key_1").performClick()
        composeTestRule.onNodeWithTag("dialpad_key_7").performClick()
        composeTestRule.waitForIdle()

        assertEquals("017", enteredText)
        composeTestRule.onNodeWithTag("dialpad_entered_display").assertIsDisplayed()

        // Backspace removes last digit
        composeTestRule.onNodeWithTag("dialpad_backspace_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals("01", enteredText)

        // Call button is present
        composeTestRule.onNodeWithTag("dialpad_call_button").assertIsDisplayed()
    }
}
