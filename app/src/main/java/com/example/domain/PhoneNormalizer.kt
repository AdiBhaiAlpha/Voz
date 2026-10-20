package com.example.domain

import java.util.Locale

object PhoneNormalizer {

    private val BANGLA_DIGITS = mapOf(
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

    fun convertBanglaDigitsToAscii(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            sb.append(BANGLA_DIGITS[ch] ?: ch)
        }
        return sb.toString()
    }

    fun digitsOnly(input: String): String {
        val ascii = convertBanglaDigitsToAscii(input)
        return ascii.filter { it.isDigit() }
    }

    fun sanitizeDialInput(input: String): String {
        if (VozAddress.isVoz(input)) return ""
        val ascii = convertBanglaDigitsToAscii(input.trim())
        val sb = StringBuilder()
        for (i in ascii.indices) {
            val ch = ascii[i]
            if (ch.isDigit()) {
                sb.append(ch)
            } else if (ch == '+' && sb.isEmpty()) {
                sb.append(ch)
            } else if (ch == '*' || ch == '#' || ch == ',') {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun isValidDialableNumber(input: String): Boolean {
        val sanitized = sanitizeDialInput(input)
        if (sanitized.isEmpty()) return false
        val digitCount = sanitized.count { it.isDigit() }
        // Support USSD (*121#, etc.) or minimum 3 digits (emergency numbers like 999, 911)
        if ((sanitized.startsWith("*") || sanitized.startsWith("#")) && sanitized.any { it in '0'..'9' }) {
            return true
        }
        return digitCount >= 3
    }

    fun normalizeForComparison(input: String): String {
        if (VozAddress.isVoz(input)) return VozAddress.normalize(input).orEmpty()
        val ascii = convertBanglaDigitsToAscii(input)
        var digits = ascii.filter { it.isDigit() }
        if (digits.isEmpty()) return ""

        // Bangladesh standard normalization: +8801... -> 01...
        if (digits.startsWith("8801") && digits.length == 13) {
            digits = "0" + digits.substring(3)
        }
        return digits
    }

    fun areNumbersEquivalent(a: String, b: String): Boolean {
        val normA = normalizeForComparison(a)
        val normB = normalizeForComparison(b)
        if (normA.isEmpty() || normB.isEmpty()) return false
        if (normA == normB) return true

        // Never use suffix matching for identity: different callers can share a suffix.
        return false
    }

    fun formatForDisplay(rawNumber: String): String {
        if (VozAddress.isVoz(rawNumber)) return VozAddress.normalize(rawNumber) ?: rawNumber
        val sanitized = sanitizeDialInput(rawNumber)
        val digits = digitsOnly(sanitized)

        // BD mobile formatting: 01711-223344
        if (digits.length == 11 && digits.startsWith("01")) {
            return "${digits.substring(0, 5)}-${digits.substring(5)}"
        } else if (digits.length == 13 && digits.startsWith("8801")) {
            return "+880 ${digits.substring(3, 7)}-${digits.substring(7)}"
        }
        return sanitized.ifBlank { rawNumber }
    }
}
