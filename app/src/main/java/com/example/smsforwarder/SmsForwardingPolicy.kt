package com.example.smsforwarder

internal object SmsForwardingPolicy {
    const val FORWARD_PREFIX = "[FWD]"

    private const val LEGACY_FORWARD_PREFIX = "📱"

    fun shouldForward(
        messageBody: String,
        sender: String?,
        forwardNumber: String?,
        keywords: Set<String>?,
    ): Boolean {
        if (keywords.isNullOrEmpty() || forwardNumber.isNullOrBlank()) return false
        if (
            messageBody.isEmpty() ||
            messageBody.startsWith(FORWARD_PREFIX) ||
            messageBody.startsWith(LEGACY_FORWARD_PREFIX)
        ) {
            return false
        }
        if (sender != null && sameNumber(sender, forwardNumber)) return false

        val normalizedMessage = messageBody.lowercase()
        return keywords.any { keyword -> normalizedMessage.contains(keyword.lowercase()) }
    }

    fun formatForwardedMessage(
        messageBody: String,
        sender: String?,
    ): String {
        val safeSender =
            sender
                ?.replace('\r', ' ')
                ?.replace('\n', ' ')
                ?.takeIf(String::isNotBlank)
                ?: "Unknown"

        return "$FORWARD_PREFIX\nFrom: $safeSender\n$messageBody"
    }

    private fun sameNumber(
        first: String,
        second: String,
    ): Boolean {
        val normalizedFirst = first.filter(Char::isDigit)
        val normalizedSecond = second.filter(Char::isDigit)
        return normalizedFirst.isNotEmpty() &&
            normalizedSecond.isNotEmpty() &&
            normalizedFirst.takeLast(NUMBER_COMPARISON_DIGITS) ==
            normalizedSecond.takeLast(NUMBER_COMPARISON_DIGITS)
    }

    private const val NUMBER_COMPARISON_DIGITS = 8
}
