package com.example.domain

/** VOZ identities live in a separate namespace; never strip their prefix for Telecom. */
object VozAddress {
    const val PREFIX = "VOZ-"
    private val complete = Regex("^VOZ-[0-9]{3}-[0-9]{4}$")
    private val editable = Regex("^VOZ-?[0-9]{0,3}-?[0-9]{0,4}$", RegexOption.IGNORE_CASE)

    fun isVoz(input: String): Boolean = input.trim().startsWith("VOZ", ignoreCase = true)

    fun normalize(input: String): String? {
        val ascii = PhoneNormalizer.convertBanglaDigitsToAscii(input.trim())
        if (!editable.matches(ascii)) return null
        return formatDigits(ascii.filter { it in '0'..'9' })
    }

    fun isComplete(input: String): Boolean = normalize(input)?.let { complete.matches(it) } == true

    private fun formatDigits(digits: String): String =
        PREFIX + digits.take(3) + if (digits.length > 3) "-" + digits.drop(3) else ""

    fun append(input: String, digit: Char): String {
        val base = normalize(input) ?: return input
        val ascii = PhoneNormalizer.convertBanglaDigitsToAscii(digit.toString()).singleOrNull()
        val digits = base.filter { it in '0'..'9' }
        return if (ascii != null && ascii in '0'..'9' && digits.length < 7)
            formatDigits(digits + ascii) else base
    }

    fun backspace(input: String): String {
        val base = normalize(input) ?: return ""
        val digits = base.filter { it in '0'..'9' }
        return if (digits.isEmpty()) "" else formatDigits(digits.dropLast(1))
    }

    /** A numeric draft may become a VOZ address only through an explicit user action. */
    fun enable(input: String): String {
        if (isVoz(input)) return normalize(input) ?: PREFIX
        val ascii = PhoneNormalizer.convertBanglaDigitsToAscii(input.trim())
        val digits = ascii.filter { it in '0'..'9' }
        return if (ascii.all { it in '0'..'9' || it == '-' || it == ' ' } && digits.length <= 7)
            formatDigits(digits) else PREFIX
    }
}

object VozDisplayName {
    fun clean(input: String): String = input.trim().replace(Regex("\\s+"), " ")
    fun isValid(input: String): Boolean {
        val name = clean(input)
        return name.length in 2..50 && name.any { it.isLetterOrDigit() } &&
            name.none { Character.isISOControl(it) }
    }
}
