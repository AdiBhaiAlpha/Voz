package com.example.domain

import java.util.Locale

object T9AndBanglaMatcher {

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

    private val CHAR_TO_T9 = buildMap<Char, Char> {
        // English letters
        for (c in 'a'..'c') put(c, '2')
        for (c in 'd'..'f') put(c, '3')
        for (c in 'g'..'i') put(c, '4')
        for (c in 'j'..'l') put(c, '5')
        for (c in 'm'..'o') put(c, '6')
        for (c in 'p'..'s') put(c, '7')
        for (c in 't'..'v') put(c, '8')
        for (c in 'w'..'z') put(c, '9')

        // Common Bengali transliteration T9 mapping
        // ক খ গ ঘ ঙ -> 2
        listOf('ক', 'খ', 'গ', 'ঘ', 'ঙ').forEach { put(it, '2') }
        // চ ছ জ ঝ ঞ -> 3
        listOf('চ', 'ছ', 'জ', 'ঝ', 'ঞ').forEach { put(it, '3') }
        // ট ঠ ড ঢ ণ -> 4
        listOf('ট', 'ঠ', 'ড', 'ঢ', 'ণ').forEach { put(it, '4') }
        // ত থ দ ধ ন -> 5
        listOf('ত', 'থ', 'দ', 'ধ', 'ন').forEach { put(it, '5') }
        // প ফ ব ভ ম -> 6
        listOf('প', 'ফ', 'ব', 'ভ', 'ম').forEach { put(it, '6') }
        // য র ল শ ষ -> 7
        listOf('য', 'র', 'ল', 'শ', 'ষ').forEach { put(it, '7') }
        // স হ ড় ঢ় য় -> 8
        listOf('স', 'হ', 'ড়', 'ঢ়', 'য়').forEach { put(it, '8') }
        // স্বরবর্ণ অ আ ই ঈ উ ঊ ঋ এ ঐ ও ঔ -> 9
        listOf('অ', 'আ', 'ই', 'ঈ', 'উ', 'ঊ', 'ঋ', 'এ', 'ঐ', 'ও', 'ঔ').forEach { put(it, '9') }
    }

    fun nameToT9Digits(name: String): String {
        val lower = name.lowercase(Locale.ROOT)
        val sb = StringBuilder(lower.length)
        for (ch in lower) {
            val mapped = CHAR_TO_T9[ch]
            if (mapped != null) {
                sb.append(mapped)
            } else if (ch.isDigit()) {
                sb.append(PhoneNormalizer.convertBanglaDigitsToAscii(ch.toString()))
            }
        }
        return sb.toString()
    }

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
        val lowerName = displayName.lowercase(Locale.ROOT)
        val lowerQuery = asciiQuery.lowercase(Locale.ROOT)

        // 1. Exact name match (English or Bangla)
        if (lowerName == lowerQuery || displayName == query) {
            return MatchScore(matched = true, kind = MatchKind.EXACT_NAME, rank = 1)
        }

        // 2. Name prefix match
        if (lowerName.startsWith(lowerQuery) || displayName.startsWith(query)) {
            return MatchScore(matched = true, kind = MatchKind.NAME_PREFIX, rank = 2)
        }

        // Word-level prefix match
        val words = lowerName.split(' ', '-', '.')
        if (words.any { it.startsWith(lowerQuery) }) {
            return MatchScore(matched = true, kind = MatchKind.NAME_PREFIX, rank = 3)
        }

        // 3. Name substring match
        if (lowerName.contains(lowerQuery) || displayName.contains(query)) {
            return MatchScore(matched = true, kind = MatchKind.NAME_SUBSTRING, rank = 4)
        }

        // 4. T9 numeric match on name
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

            // 5. Phone number match
            for (phone in phoneNumbers) {
                if (PhoneNormalizer.areNumbersEquivalent(phone, asciiQuery)) {
                    return MatchScore(matched = true, kind = MatchKind.NUMBER_EXACT, rank = 2)
                }
                val phoneDigits = PhoneNormalizer.digitsOnly(phone)
                val normalizedPhone = PhoneNormalizer.normalizeForComparison(phone).removePrefix("+")
                val strippedQuery = if (queryDigits.startsWith("880") && queryDigits.length > 4) {
                    queryDigits.removePrefix("88")
                } else queryDigits

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
