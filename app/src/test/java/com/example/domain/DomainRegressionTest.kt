package com.example.domain

import org.junit.Assert.*
import org.junit.Test

class DomainRegressionTest {
    @Test fun bangladeshNationalAndInternationalNumbersMatch() {
        assertTrue(PhoneNormalizer.areNumbersEquivalent("+8801700112233", "০১৭০০১১২২৩৩"))
    }

    @Test fun sharedSuffixDoesNotMergeDifferentCallers() {
        assertFalse(PhoneNormalizer.areNumbersEquivalent("01700112233", "01900112233"))
    }

    @Test fun countryPrefixIsNotStrippedFromShortNumbers() {
        assertEquals("88012345678", PhoneNormalizer.normalizeForComparison("88012345678"))
    }

    @Test fun serviceCodesNeedDigits() {
        assertFalse(PhoneNormalizer.isValidDialableNumber("*#"))
        assertTrue(PhoneNormalizer.isValidDialableNumber("*121#"))
        assertTrue(PhoneNormalizer.isValidDialableNumber("999"))
    }

    private fun record(id: Long, number: String, timestamp: Long) = CallRecord(
        id, id.toString(), number, number, null, null, timestamp, 0,
        CallType.MISSED, null, null
    )

    @Test fun historyIsSortedBeforeGrouping() {
        val now = System.currentTimeMillis()
        val groups = CallHistoryGrouper.groupCallRecords(listOf(
            record(1, "01700112233", now - 1000), record(2, "01700112233", now)
        ))
        assertEquals(2L, groups.first().primaryRecord.id)
        assertEquals(2, groups.first().totalAttempts)
    }

    @Test fun simultaneousDifferentCallsHaveUniqueListKeys() {
        val now = System.currentTimeMillis()
        val groups = CallHistoryGrouper.groupCallRecords(listOf(
            record(1, "", now), record(2, "123", now)
        ))
        assertEquals(groups.size, groups.map { it.sessionKey }.toSet().size)
    }
}
