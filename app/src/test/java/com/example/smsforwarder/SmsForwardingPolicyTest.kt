package com.example.smsforwarder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsForwardingPolicyTest {
    @Test
    fun keywordMatchingIsCaseInsensitiveAndAllowsPartialMatches() {
        assertTrue(
            SmsForwardingPolicy.shouldForward(
                messageBody = "Your GitHub verification code is 123456",
                sender = "1588-0000",
                forwardNumber = "010-1234-5678",
                keywords = setOf("github"),
            ),
        )
        assertTrue(
            SmsForwardingPolicy.shouldForward(
                messageBody = "택배가 도착했습니다",
                sender = null,
                forwardNumber = "010-1234-5678",
                keywords = setOf("도착"),
            ),
        )
    }

    @Test
    fun messageWithoutAnyConfiguredKeywordDoesNotForward() {
        assertFalse(
            shouldForward(
                messageBody = "택배가 출발했습니다",
                keywords = setOf("도착", "verification code"),
            ),
        )
    }

    @Test
    fun emptyRequiredValuesDoNotForward() {
        assertFalse(shouldForward(messageBody = "", keywords = setOf("code")))
        assertFalse(shouldForward(messageBody = "code", keywords = emptySet()))
        assertFalse(shouldForward(messageBody = "code", keywords = null))
        assertFalse(shouldForward(messageBody = "code", forwardNumber = " "))
    }

    @Test
    fun forwardedPrefixPreventsPrefixLoop() {
        assertFalse(
            shouldForward(
                messageBody = "${SmsForwardingPolicy.FORWARD_PREFIX}verification code",
                keywords = setOf("code"),
            ),
        )
        assertFalse(
            shouldForward(
                messageBody = "📱verification code",
                keywords = setOf("code"),
            ),
        )
    }

    @Test
    fun forwardedMessageIncludesSanitizedSenderAndOriginalBody() {
        assertEquals(
            "[FWD]\nFrom: 1588-0000 support\n인증번호는 123456입니다",
            SmsForwardingPolicy.formatForwardedMessage(
                messageBody = "인증번호는 123456입니다",
                sender = "1588-0000\nsupport",
            ),
        )
        assertEquals(
            "[FWD]\nFrom: Unknown\ncode 123456",
            SmsForwardingPolicy.formatForwardedMessage(
                messageBody = "code 123456",
                sender = null,
            ),
        )
    }

    @Test
    fun matchingSenderPreventsLoopAfterNumberNormalization() {
        assertFalse(
            shouldForward(
                messageBody = "verification code",
                sender = "+82 10-1234-5678",
                forwardNumber = "01012345678",
                keywords = setOf("code"),
            ),
        )
        assertTrue(
            shouldForward(
                messageBody = "verification code",
                sender = "010-9999-5678",
                forwardNumber = "01012345678",
                keywords = setOf("code"),
            ),
        )
    }

    private fun shouldForward(
        messageBody: String,
        sender: String? = "1588-0000",
        forwardNumber: String? = "010-1234-5678",
        keywords: Set<String>? = setOf("code"),
    ): Boolean =
        SmsForwardingPolicy.shouldForward(
            messageBody = messageBody,
            sender = sender,
            forwardNumber = forwardNumber,
            keywords = keywords,
        )
}
