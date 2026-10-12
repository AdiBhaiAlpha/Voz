package com.example

import com.example.domain.CallHistoryGrouper
import com.example.domain.CallRecord
import com.example.domain.CallType
import com.example.domain.PhoneNormalizer
import com.example.domain.T9AndBanglaMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun banglaDigitsConvertToAsciiAndBackCorrectly() {
        val banglaInput = "০১৭১১-২২৩৩৪৪"
        val ascii = PhoneNormalizer.convertBanglaDigitsToAscii(banglaInput)
        assertEquals("01711-223344", ascii)

        val backToBangla = PhoneNormalizer.convertAsciiDigitsToBangla("01711")
        assertEquals("০১৭১১", backToBangla)
    }

    @Test
    fun bangladeshiPhoneFormatsNormalizeToCanonicalE164() {
        val expected = "+8801711223344"
        assertEquals(expected, PhoneNormalizer.normalizeForComparison("01711223344"))
        assertEquals(expected, PhoneNormalizer.normalizeForComparison("+880 1711-223344"))
        assertEquals(expected, PhoneNormalizer.normalizeForComparison("8801711223344"))
        assertEquals(expected, PhoneNormalizer.normalizeForComparison("008801711223344"))
        assertEquals(expected, PhoneNormalizer.normalizeForComparison("০১৭১১২২৩৩৪৪"))
    }

    @Test
    fun phoneNumberEquivalenceMatchesDomesticAndInternationalFormats() {
        assertTrue(
            PhoneNormalizer.areNumbersEquivalent("+8801819123456", "01819-123456")
        )
        assertTrue(
            PhoneNormalizer.areNumbersEquivalent("০১৯১১২২৩৩৪৪", "+8801911223344")
        )
        assertFalse(
            PhoneNormalizer.areNumbersEquivalent("01711223344", "01711223399")
        )
    }

    @Test
    fun dialableNumberValidationAcceptsEmergencyShortcodesUssdAndInternational() {
        assertTrue(PhoneNormalizer.isValidDialableNumber("999"))
        assertTrue(PhoneNormalizer.isValidDialableNumber("*121#"))
        assertTrue(PhoneNormalizer.isValidDialableNumber("+8801711223344"))
        assertTrue(PhoneNormalizer.isValidDialableNumber("+442079460958"))
        assertFalse(PhoneNormalizer.isValidDialableNumber("   "))
        assertFalse(PhoneNormalizer.isValidDialableNumber("---"))
    }

    @Test
    fun prefixDescriptorRecognizesEmergencyAndBdMobilePrefixes() {
        assertEquals(
            "Bangladesh National Emergency Service",
            PhoneNormalizer.describeNumberPrefix("999")
        )
        val prefixDesc = PhoneNormalizer.describeNumberPrefix("01711223344")
        assertNotNull(prefixDesc)
        assertTrue(prefixDesc!!.contains("017"))
    }

    @Test
    fun t9AndBanglaMatcherSupportsT9EnglishBanglaAndNormalizedDigits() {
        // T9 match: 72446 -> "Rahim"
        val t9Score = T9AndBanglaMatcher.evaluateContactMatch(
            displayName = "Rahim Uddin",
            phoneNumbers = listOf("01711223344"),
            rawQuery = "72446"
        )
        assertTrue(t9Score.matched)
        assertEquals(T9AndBanglaMatcher.MatchKind.T9_NAME_PREFIX, t9Score.kind)

        // Bangla script match
        val banglaScore = T9AndBanglaMatcher.evaluateContactMatch(
            displayName = "আব্দুর রহিম",
            phoneNumbers = listOf("+8801711223344"),
            rawQuery = "রহিম"
        )
        assertTrue(banglaScore.matched)

        // Bangla digits matching +880 contact number
        val banglaNumberScore = T9AndBanglaMatcher.evaluateContactMatch(
            displayName = "Nusrat Jahan",
            phoneNumbers = listOf("+8801811223344"),
            rawQuery = "০১৮১১২২"
        )
        assertTrue(banglaNumberScore.matched)
    }

    @Test
    fun callHistoryGrouperGroupsConsecutiveSameDayCallsAndSeparatesDifferentContacts() {
        val now = 1_750_000_000_000L
        val records = listOf(
            CallRecord(
                id = 3L,
                rawNumber = "01711223344",
                normalizedNumber = "+8801711223344",
                cachedName = "Tanvir Ahmed",
                callType = CallType.MISSED,
                timestampMillis = now - 60_000L,
                durationSeconds = 0L
            ),
            CallRecord(
                id = 2L,
                rawNumber = "+8801711223344",
                normalizedNumber = "+8801711223344",
                cachedName = "Tanvir Ahmed",
                callType = CallType.INCOMING,
                timestampMillis = now - 120_000L,
                durationSeconds = 95L
            ),
            CallRecord(
                id = 1L,
                rawNumber = "01819556677",
                normalizedNumber = "+8801819556677",
                cachedName = "Farhana Akter",
                callType = CallType.OUTGOING,
                timestampMillis = now - 300_000L,
                durationSeconds = 42L
            )
        )

        val grouped = CallHistoryGrouper.groupConsecutiveCalls(records, nowMillis = now, languageCode = "en")
        assertEquals(2, grouped.size)
        assertEquals(2, grouped[0].callCount)
        assertTrue(grouped[0].hasMissedCall)
        assertEquals("Today", grouped[0].dateHeader)
        assertEquals(1, grouped[1].callCount)
    }

    @Test
    fun callDurationFormatsInEnglishAndBangla() {
        assertEquals("1m 5s", CallHistoryGrouper.formatDuration(65L, "en"))
        val bnDuration = CallHistoryGrouper.formatDuration(65L, "bn")
        assertTrue(bnDuration.contains("১"))
        assertTrue(bnDuration.contains("৫"))
    }
}
