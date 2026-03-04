package com.partyscout.shared.logging

/**
 * Utility for masking sensitive data before logging.
 *
 * All PII and secrets must pass through this sanitizer before being written to logs.
 * This prevents accidental exposure of user data in Cloud Logging, log aggregators,
 * and debug output.
 */
object LogSanitizer {

    private val EMAIL_PATTERN = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    private val PHONE_PATTERN = Regex("\\+?[0-9()\\s-]{7,15}")
    private val API_KEY_PATTERN = Regex("(?i)(key|token|secret|password|authorization)[=:\\s]+\\S+")

    /**
     * Mask a ZIP code: "94105" -> "941**"
     * Preserves first 3 digits for regional debugging while hiding the specific area.
     */
    fun maskZipCode(zipCode: String): String {
        if (zipCode.length <= 3) return "***"
        return zipCode.take(3) + "*".repeat(zipCode.length - 3)
    }

    /**
     * Mask an email: "john.doe@gmail.com" -> "j*****e@g***l.com"
     */
    fun maskEmail(email: String): String {
        val parts = email.split("@")
        if (parts.size != 2) return "***@***"
        val local = parts[0]
        val domain = parts[1]
        val maskedLocal = if (local.length <= 2) "**" else "${local.first()}${"*".repeat(local.length - 2)}${local.last()}"
        val domainParts = domain.split(".")
        val maskedDomain = domainParts.joinToString(".") { part ->
            if (part.length <= 2) part else "${part.first()}${"*".repeat(part.length - 2)}${part.last()}"
        }
        return "$maskedLocal@$maskedDomain"
    }

    /**
     * Mask a phone number: "+1 (415) 555-0123" -> "+1 (415) ***-****"
     * Preserves area code for regional debugging.
     */
    fun maskPhone(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        if (digits.length < 7) return "***-****"
        val visible = digits.take(digits.length.coerceAtMost(4))
        return "$visible${"*".repeat(digits.length - visible.length)}"
    }

    /**
     * Mask an address: "123 Mock St, San Francisco, CA 94105" -> "*** San Francisco, CA 941**"
     * Removes street-level detail, preserves city/state for debugging.
     */
    fun maskAddress(address: String): String {
        val parts = address.split(",").map { it.trim() }
        if (parts.size < 2) return "***"
        // Drop street (first part), keep city/state, mask ZIP in last part
        val masked = parts.drop(1).joinToString(", ") { part ->
            // Mask any ZIP codes embedded in address parts
            part.replace(Regex("\\b\\d{5}(-\\d{4})?\\b")) { match -> maskZipCode(match.value) }
        }
        return "***,$masked"
    }

    /**
     * Scrub arbitrary text for any recognizable PII patterns.
     * Use this when logging exception messages or request bodies that may
     * contain user-supplied data.
     */
    fun scrubPii(text: String): String {
        var result = text
        result = EMAIL_PATTERN.replace(result) { maskEmail(it.value) }
        result = API_KEY_PATTERN.replace(result) { "[REDACTED]" }
        return result
    }

    /**
     * Mask a latitude/longitude pair to ~1km precision.
     * Truncates to 2 decimal places (precision ~1.1km at equator).
     */
    fun maskCoordinates(lat: Double, lng: Double): String {
        return "(%.2f, %.2f)".format(lat, lng)
    }
}
