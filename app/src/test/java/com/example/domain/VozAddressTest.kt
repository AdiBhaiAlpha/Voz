package com.example.domain

import org.junit.Assert.*
import org.junit.Test

class VozAddressTest {
    @Test fun keypadFormatsSevenDigits() {
        val result = "3028671".fold(VozAddress.PREFIX) { value, digit -> VozAddress.append(value, digit) }
        assertEquals("VOZ-302-8671", result)
        assertTrue(VozAddress.isComplete(result))
        assertEquals(result, VozAddress.append(result, '9'))
    }

    @Test fun keypadRejectsServiceKeysAndPlus() {
        for (digit in "*#+,") assertEquals("VOZ-302", VozAddress.append("VOZ-302", digit))
    }

    @Test fun backspaceDoesNotGetStuckOnHyphens() {
        assertEquals("VOZ-302", VozAddress.backspace("VOZ-302-8"))
        assertEquals("VOZ-30", VozAddress.backspace("VOZ-302"))
        assertEquals("VOZ-", VozAddress.backspace("VOZ-3"))
        assertEquals("", VozAddress.backspace("VOZ-"))
    }

    @Test fun pastedBanglaAndLowercaseNormalize() {
        assertEquals("VOZ-302-8671", VozAddress.normalize(" voz-৩০২-৮৬৭১ "))
        assertEquals("VOZ-302-8671", VozAddress.normalize("voz3028671"))
        assertEquals("VOZ-000-0001", VozAddress.normalize("VOZ-000-0001"))
    }

    @Test fun malformedIdsAreNotSilentlyTruncated() {
        for (bad in listOf("VOZ-302-86712", "VOZ-30x-8671", "VOZ-302-8671*", "VOZ-302--8671")) {
            assertTrue(VozAddress.isVoz(bad))
            assertNull(VozAddress.normalize(bad))
            assertFalse(VozAddress.isComplete(bad))
            assertEquals("", PhoneNormalizer.sanitizeDialInput(bad))
        }
    }

    @Test fun incompleteAddressesAreNotCallable() {
        for (value in listOf("VOZ-", "VOZ-302", "VOZ-302-867")) {
            assertFalse(VozAddress.isComplete(value))
            assertFalse(PhoneNormalizer.isValidDialableNumber(value))
        }
    }

    @Test fun namespacesStaySeparate() {
        assertFalse(PhoneNormalizer.areNumbersEquivalent("VOZ-302-8671", "3028671"))
        assertEquals("VOZ-302-8671", PhoneNormalizer.formatForDisplay("voz3028671"))
        assertFalse(T9AndBanglaMatcher.evaluateContactMatch("Cellular contact", listOf("3028671"), "VOZ-302-8671").matched)
    }

    @Test fun modeSwitchDoesNotReinterpretLongCellularNumbers() {
        assertEquals("VOZ-302-8671", VozAddress.enable("3028671"))
        assertEquals("VOZ-", VozAddress.enable("01700112233"))
        assertEquals("VOZ-", VozAddress.enable("Rahim"))
    }

    @Test fun displayNameRequiredAndBounded() {
        for (bad in listOf("", "  ", "A", "---", "x".repeat(51)))
            assertFalse(VozDisplayName.isValid(bad))
        assertTrue(VozDisplayName.isValid("রহিম"))
        assertEquals("Rahim Ahmed", VozDisplayName.clean(" Rahim   Ahmed "))
    }
}
