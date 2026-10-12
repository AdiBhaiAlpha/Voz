package com.example.domain

/**
 * Provides fast, deterministic contact and number matching supporting:
 * - T9 keypad digit-to-letter matching (e.g., "72446" -> "Rahim")
 * - Case-insensitive English name matching
 * - Bangla script (বাংলা) name matching
 * - Bangla and ASCII digit phone number matching with +880 / 01 normalization
 */
object T9AndBanglaMatcher {

    private val charToT9Digit: Map<Char, Char> = buildMap {
        "abcABC".forEach { put(it, '2') }
        "defDEF".forEach { put(it, '3') }
        "ghiGHI".forEach { put(it, '4') }
        "jklJKL".forEach { put(it, '5') }
        "mnoMNO".forEach { put(it, '6') }
        "pqrsPQRS".forEach { put(it, '7') }
        "tuvTUV".forEach { put(it, '8') }
        "wxyzWXYZ".forEach { put(it, '9') }
        put(' ', '0')
    }

    /**
     * Converts a contact name into its T9 digit sequence for Latin characters.
     */
    fun nameToT9Digits(name: String): String {
        if (name.isEmpty()) return ""
        val sb = StringBuilder(name.length)
        for (ch in name) {
            val mapped = charToT9Digit[ch]
            if (mapped != null) {
                sb.append(mapped)
            } else if (ch in '0'..'9') {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    enum class MatchKind {
        NONE,
        EXACT_NAME,
        NAME_PREFIX,
        NAME_SUBSTRING,
        T9_NAME_PREFIX,
        T9_NAME_SUBSTRING,
        NUMBER_EXACT,
        NUMBER_MATCH
    }

    data class MatchScore(
        val matched: Boolean,
        val kind: MatchKind,
        val rank: Int
    )

    /**
     * Evaluates whether a contact with [displayName] and [phoneNumbers] matches [rawQuery].
     * Returns a [MatchScore] with `rank` (lower is higher priority) for sorting.
     */
    fun evaluateContactMatch(
        displayName: String,
        phoneNumbers: List<String>,
        rawQuery: String
    ): MatchScore {
        val query = rawQuery.trim()
        if (query.isEmpty()) {
            return MatchScore(matched = true, kind = MatchKind.NONE, rank = 100)
        }

        val asciiQuery = PhoneNormalizer.convertBanglaDigitsToAscii(query)
        val lowerName = displayName.lowercase()
        val lowerQuery = asciiQuery.lowercase()

        // 1. Direct Name Matching (supports Bangla script and English case-insensitively)
        if (lowerName == lowerQuery || displayName == query) {
            return MatchScore(matched = true, kind = MatchKind.EXACT_NAME, rank = 1)
        }
        if (lowerName.startsWith(lowerQuery) || displayName.startsWith(query)) {
            return MatchScore(matched = true, kind = MatchKind.NAME_PREFIX, rank = 2)
        }
        // Word boundary prefix match (e.g., "Karim" in "Abdul Karim" or "রহিম" in "আব্দুর রহিম")
        val words = lowerName.split(' ', '-', '.')
        if (words.any { it.startsWith(lowerQuery) }) {
            return MatchScore(matched = true, kind = MatchKind.NAME_PREFIX, rank = 3)
        }
        if (lowerName.contains(lowerQuery) || displayName.contains(query)) {
            return MatchScore(matched = true, kind = MatchKind.NAME_SUBSTRING, rank = 4)
        }

        // 2. If query consists of digits/dial characters, test T9 name and normalized phone numbers
        val queryDigits = PhoneNormalizer.digitsOnly(asciiQuery)
        if (queryDigits.isNotEmpty()) {
            val fullT9 = nameToT9Digits(displayName)
            if (fullT9.startsWith(queryDigits)) {
                return MatchScore(matched = true, kind = MatchKind.T9_NAME_PREFIX, rank = 5)
            }
            val wordT9s = words.map { nameToT9Digits(it) }
            if (wordT9s.any { it.startsWith(queryDigits) }) {
                return MatchScore(matched = true, kind = MatchKind.T9_NAME_PREFIX, rank = 6)
            }
            if (fullT9.contains(queryDigits)) {
                return MatchScore(matched = true, kind = MatchKind.T9_NAME_SUBSTRING, rank = 7)
            }

            // 3. Phone Number Matching (including +880 / 01 equivalence)
            for (phone in phoneNumbers) {
                if (PhoneNormalizer.areNumbersEquivalent(phone, asciiQuery)) {
                    return MatchScore(matched = true, kind = MatchKind.NUMBER_EXACT, rank = 2)
                }
                val phoneDigits = PhoneNormalizer.digitsOnly(phone)
                val normalizedPhone = PhoneNormalizer.normalizeForComparison(phone).removePrefix("+")
                // Strip leading 88 from query if user typed +88017... and contact is stored as 017...
                val strippedQuery = if (queryDigits.startsWith("880") && queryDigits.length > 4) {
                    queryDigits.removePrefix("88")
                } else {
                    queryDigits
                }
                if (phoneDigits.contains(queryDigits) ||
                    phoneDigits.contains(strippedQuery) ||
                    normalizedPhone.contains(queryDigits)
                ) {
                    return MatchScore(matched = true, kind = MatchKind.NUMBER_MATCH, rank = 8)
                }
            }
        }

        return MatchScore(matched = false, kind = MatchKind.NONE, rank = Int.MAX_VALUE)
    }
}
