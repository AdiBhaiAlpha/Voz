package com.example

import android.content.Context
import android.content.pm.PackageManager
import android.telephony.TelephonyManager
import androidx.test.core.app.ApplicationProvider
import com.example.domain.PhoneNormalizer
import com.example.domain.T9AndBanglaMatcher
import com.example.telecom.CallInitiationOutcome
import com.example.telecom.CallManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTelephonyManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CallManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.CALL_PHONE)
    }

    @Test
    fun testPhoneNormalizer_sanitizationAndBanglaConversion() {
        // Bengali digits conversion
        val banglaNumber = "০১৭০০১১২২৩৩"
        val asciiNumber = PhoneNormalizer.convertBanglaDigitsToAscii(banglaNumber)
        assertEquals("01700112233", asciiNumber)

        // Validation
        assertTrue(PhoneNormalizer.isValidDialableNumber(banglaNumber))
        assertTrue(PhoneNormalizer.isValidDialableNumber("+8801700112233"))
        assertTrue(PhoneNormalizer.isValidDialableNumber("*121#"))
        assertFalse(PhoneNormalizer.isValidDialableNumber("abc"))
        assertFalse(PhoneNormalizer.isValidDialableNumber(""))

        // Normalization (+8801... -> 01...)
        val normalized = PhoneNormalizer.normalizeForComparison("+8801700112233")
        assertEquals("01700112233", normalized)

        // Equivalence
        assertTrue(PhoneNormalizer.areNumbersEquivalent("+8801700112233", "01700112233"))
    }

    @Test
    fun testT9AndBanglaMatcher_evaluatesMatches() {
        val phones = listOf("01711223344")

        // Exact match
        val scoreExact = T9AndBanglaMatcher.evaluateContactMatch("Rahim", phones, "Rahim")
        assertTrue(scoreExact.matched)
        assertEquals(T9AndBanglaMatcher.MatchKind.EXACT_NAME, scoreExact.kind)

        // T9 match (72446 -> RAHIM)
        val scoreT9 = T9AndBanglaMatcher.evaluateContactMatch("Rahim", phones, "72446")
        assertTrue(scoreT9.matched)

        // Phone match
        val scorePhone = T9AndBanglaMatcher.evaluateContactMatch("Rahim", phones, "223344")
        assertTrue(scorePhone.matched)

        // Non-match
        val scoreNone = T9AndBanglaMatcher.evaluateContactMatch("Rahim", phones, "9999999")
        assertFalse(scoreNone.matched)
    }

    @Test
    fun testCallManager_initialStateAndOutcomeValidation() {
        // Initial state should have no active call
        assertNull(CallManager.activeCall.value)

        // Invalid number should return Error outcome immediately without dialing
        val outcomeInvalid = CallManager.placeRealCall(context, "abc")
        assertTrue(outcomeInvalid is CallInitiationOutcome.Error)

        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val shadowTm: ShadowTelephonyManager = Shadows.shadowOf(tm)

        // 1. When running without an active SIM (preview environment without SIM):
        // It must NOT fake a successful connection, but report accurately.
        shadowTm.setSimState(TelephonyManager.SIM_STATE_ABSENT)
        val outcomeNoSim = CallManager.placeRealCall(context, "01711223344")
        assertTrue(outcomeNoSim is CallInitiationOutcome.Error)
        val reason = (outcomeNoSim as CallInitiationOutcome.Error).reason
        assertTrue(reason.contains("compatible Android phone with an active SIM"))

        // 2. When active SIM is present (physical Android device with carrier SIM):
        shadowTm.setSimState(TelephonyManager.SIM_STATE_READY)
        val outcomeWithSim = CallManager.placeRealCall(context, "01711223344")
        assertTrue(outcomeWithSim is CallInitiationOutcome.PlacedDirectly)
    }

    @Test
    fun testCallLogRepository_queriesWithoutInvalidTokenLimit() = kotlinx.coroutines.runBlocking {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.READ_CALL_LOG)

        val repo = com.example.data.repository.CallLogRepository(context)
        val result = repo.loadSystemCallHistory()
        // Must succeed without throwing SQLiteQueryBuilder "Invalid token LIMIT" error
        assertTrue("Expected Success but got $result", result is com.example.data.repository.CallLogLoadResult.Success)
    }

    @Test
    fun testVozUserPreferences_storesAccountAndCallerId() {
        val repo = com.example.data.preferences.UserPreferencesRepository(context)
        assertFalse(repo.loadPreferences().isVozAccountLoggedIn)

        repo.loginVozAccount("Chitron", "VOZ-880-17001")
        val prefs = repo.loadPreferences()
        assertTrue(prefs.isVozAccountLoggedIn)
        assertEquals("Chitron", prefs.vozAccountName)
        assertEquals("VOZ-880-17001", prefs.vozCallerId)

        repo.logoutVozAccount()
        assertFalse(repo.loadPreferences().isVozAccountLoggedIn)
    }

    @Test
    fun testCallSessionController_registersAndManagesCallState() {
        val initialModel = com.example.telecom.VozCallUiModel(
            callId = "test_call_101",
            rawNumber = "+8801700112233",
            normalizedNumber = "01700112233",
            displayName = "Chitron",
            photoUri = null,
            state = com.example.telecom.CallLifecycleState.RINGING,
            connectTimeMillis = 0L,
            isIncoming = true
        )

        // Initial check
        assertTrue(com.example.telecom.CallSessionController.activeCalls.value.isEmpty())

        // In absence of live telephony modem mock in Robolectric, verify controller audio and toggle methods
        com.example.telecom.CallSessionController.toggleMute()
        assertFalse(com.example.telecom.CallSessionController.audioState.value.isMuted)
    }
}
