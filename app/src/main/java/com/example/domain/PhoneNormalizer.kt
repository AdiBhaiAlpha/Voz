package com.example.domain

/**
 * Normalizes and validates phone numbers with first-class support for Bangladeshi (+880 / 01...)
 * and international telephone numbers, as well as Bangla numerals (০-৯).
 *
 * Important principle: Normalization is used strictly for comparison, grouping, and blocklist
 * matching. The original contact number or user-entered string is preserved for display and dialing.
 */
object PhoneNormalizer {

    private val banglaToAsciiDigitMap = mapOf(
        '০' to '0',
        '১' to '1',
        '২' to '2',
        '৩' to '3',
        '৪' to '4',
        '৫' to '5',
        '৬' to '6',
        '৭' to '7',
        '৮' to '8',
        '৯' to '9'
    )

    private val asciiToBanglaDigitMap = banglaToAsciiDigitMap.entries.associate { (k, v) -> v to k }

    /**
     * Converts any Bangla numerals (০-৯) in [input] to standard ASCII digits (0-9).
     */
    fun convertBanglaDigitsToAscii(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder(input.length)
        for (ch in input) {
            sb.append(banglaToAsciiDigitMap[ch] ?: ch)
        }
        return sb.toString()
    }

    /**
     * Converts ASCII digits (0-9) to Bangla numerals (০-৯) for localized UI display when needed.
     */
    fun convertAsciiDigitsToBangla(input: String): String {
        if (input.isEmpty()) return input
        val sb = StringBuilder(input.length)
        for (ch in input) {
            sb.append(asciiToBanglaDigitMap[ch] ?: ch)
        }
        return sb.toString()
    }

    /**
     * Sanitizes a user-entered or pasted string into a clean dialable string while preserving
     * valid telephone characters (0-9, +, *, #, comma, semicolon).
     */
    fun sanitizeDialInput(rawInput: String): String {
        val ascii = convertBanglaDigitsToAscii(rawInput.trim())
        val sb = StringBuilder()
        for ((index, ch) in ascii.withIndex()) {
            when {
                ch in '0'..'9' -> sb.append(ch)
                ch == '+' && sb.isEmpty() && index == 0 -> sb.append(ch)
                ch == '*' || ch == '#' || ch == ',' || ch == ';' -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Produces a canonical normalized key for comparing two phone numbers.
     *
     * Rules for Bangladeshi numbers:
     * - "+8801711223344", "8801711223344", "008801711223344", and "01711223344" all normalize
     *   to "+8801711223344".
     * - Domestic landlines like "02..." with "+8802..." also normalize to "+8802...".
     * - Other international numbers keep their leading '+' and digits, or raw digits.
     */
    fun normalizeForComparison(rawNumber: String): String {
        if (rawNumber.isBlank()) return ""
        val ascii = convertBanglaDigitsToAscii(rawNumber.trim())

        // Preserve USSD/shortcodes as-is if they contain * or #
        if (ascii.contains('*') || ascii.contains('#')) {
            return ascii.filter { it in '0'..'9' || it == '*' || it == '#' }
        }

        val hasLeadingPlus = ascii.trimStart().startsWith('+')
        val digitsOnly = ascii.filter { it in '0'..'9' }
        if (digitsOnly.isEmpty()) return ""

        // Handle 00 international prefix (e.g., 0088017... -> +88017...)
        val strippedIntl = if (!hasLeadingPlus && digitsOnly.startsWith("00") && digitsOnly.length > 4) {
            digitsOnly.removePrefix("00")
        } else {
            digitsOnly
        }
        val isExplicitIntl = hasLeadingPlus || (!hasLeadingPlus && digitsOnly.startsWith("00") && digitsOnly.length > 4)

        // Bangladeshi mobile numbers: 11 digits starting with 01[3-9] -> +8801[3-9]...
        if (!isExplicitIntl && strippedIntl.length == 11 && strippedIntl.startsWith("01") && strippedIntl[2] in '3'..'9') {
            return "+88$strippedIntl"
        }

        // Bangladeshi mobile numbers starting with 8801[3-9] (13 digits)
        if (strippedIntl.length == 13 && strippedIntl.startsWith("8801") && strippedIntl[4] in '3'..'9') {
            return "+$strippedIntl"
        }

        // Bangladeshi 10-digit mobile without leading 0 (e.g., 1711223344 -> +8801711223344)
        if (!isExplicitIntl && strippedIntl.length == 10 && strippedIntl[0] == '1' && strippedIntl[1] in '3'..'9') {
            return "+880$strippedIntl"
        }

        return if (isExplicitIntl) {
            "+$strippedIntl"
        } else {
            strippedIntl
        }
    }

    /**
     * Extracts pure ASCII digits for substring and T9 phone number matching.
     */
    fun digitsOnly(rawNumber: String): String {
        val ascii = convertBanglaDigitsToAscii(rawNumber)
        return ascii.filter { it in '0'..'9' }
    }

    /**
     * Checks whether two phone numbers refer to the same destination.
     */
    fun areNumbersEquivalent(first: String, second: String): Boolean {
        val normA = normalizeForComparison(first)
        val normB = normalizeForComparison(second)
        if (normA.isEmpty() || normB.isEmpty()) return false
        if (normA == normB) return true

        // Fallback suffix match for 10+ digit numbers where one has a country code and one is local
        val digitsA = normA.removePrefix("+")
        val digitsB = normB.removePrefix("+")
        if (digitsA.length >= 10 && digitsB.length >= 10) {
            val tailA = digitsA.takeLast(10)
            val tailB = digitsB.takeLast(10)
            if (tailA == tailB && (normA.startsWith("+880") || normB.startsWith("+880"))) {
                return true
            }
        }
        return false
    }

    /**
     * Validates if an entered string can be dialed via Telecom/Intent.
     * Does NOT reject numbers solely because length differs from a local format; allows shortcodes
     * (e.g. 999, 106, *121#), domestic numbers, and international numbers.
     */
    fun isValidDialableNumber(rawInput: String): Boolean {
        val sanitized = sanitizeDialInput(rawInput)
        if (sanitized.isEmpty()) return false
        val hasDigit = sanitized.any { it in '0'..'9' }
        return hasDigit && sanitized.length in 1..32
    }

    /**
     * Returns non-authoritative prefix metadata (such as Bangladeshi carrier prefix or Emergency
     * service indicator) to help users avoid calling the wrong number or SIM.
     */
    fun describeNumberPrefix(rawNumber: String): String? {
        val normalized = normalizeForComparison(rawNumber)
        if (normalized == "999") return "Bangladesh National Emergency Service"
        if (normalized == "333") return "National Information & Services"
        if (normalized == "109") return "Women & Children Helpline"
        if (normalized == "16263") return "Shastho Batayon Health Line"

        if (normalized.startsWith("+8801") && normalized.length == 14) {
            return when (normalized.substring(4, 6)) {
                "17", "13" -> "BD Mobile • 0${normalized.substring(4, 6)} Prefix"
                "18" -> "BD Mobile • 018 Prefix"
                "19", "14" -> "BD Mobile • 0${normalized.substring(4, 6)} Prefix"
                "16" -> "BD Mobile • 016 Prefix"
                "15" -> "BD Mobile • 015 Prefix"
                else -> "Bangladesh (+880)"
            }
        }
        if (normalized.startsWith("+880")) {
            return "Bangladesh (+880)"
        }
        if (normalized.startsWith("+")) {
            return "International Number"
        }
        return null
    }
}
