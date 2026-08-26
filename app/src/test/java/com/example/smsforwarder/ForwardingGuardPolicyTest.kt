package com.example.smsforwarder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForwardingGuardPolicyTest {
    @Test
    fun exactDuplicateIsBlockedUntilDuplicateWindowExpires() {
        val initial = evaluateAllowed(nowMillis = 0, attemptId = "first", fingerprint = "same")

        val blocked =
            ForwardingGuardPolicy.evaluate(
                nowMillis = ForwardingGuardPolicy.DUPLICATE_WINDOW_MILLIS - 1,
                attemptId = "second",
                fingerprint = "same",
                previousState = initial.state,
            )
        assertBlocked(blocked, ForwardingBlockReason.DUPLICATE)

        val allowed =
            ForwardingGuardPolicy.evaluate(
                nowMillis = ForwardingGuardPolicy.DUPLICATE_WINDOW_MILLIS,
                attemptId = "third",
                fingerprint = "same",
                previousState = initial.state,
            )
        assertTrue(allowed.result is ForwardingGuardResult.Allowed)
    }

    @Test
    fun sixthAttemptWithinTenMinutesIsBlocked() {
        var state = ForwardingGuardState()
        repeat(ForwardingGuardPolicy.MAX_BURST_ATTEMPTS) { index ->
            val evaluation =
                evaluateAllowed(
                    nowMillis = index.toLong(),
                    attemptId = "attempt-$index",
                    fingerprint = "fingerprint-$index",
                    previousState = state,
                )
            state = evaluation.state
        }

        val blocked =
            ForwardingGuardPolicy.evaluate(
                nowMillis = ForwardingGuardPolicy.BURST_WINDOW_MILLIS - 1,
                attemptId = "blocked",
                fingerprint = "new",
                previousState = state,
            )

        assertBlocked(blocked, ForwardingBlockReason.BURST_LIMIT)
    }

    @Test
    fun twentyFirstAttemptWithinDayIsBlockedWithoutTriggeringBurstLimit() {
        var state = ForwardingGuardState()
        repeat(ForwardingGuardPolicy.MAX_DAILY_ATTEMPTS) { index ->
            val nowMillis = index * ForwardingGuardPolicy.BURST_WINDOW_MILLIS
            val evaluation =
                evaluateAllowed(
                    nowMillis = nowMillis,
                    attemptId = "attempt-$index",
                    fingerprint = "fingerprint-$index",
                    previousState = state,
                )
            state = evaluation.state
        }

        val blocked =
            ForwardingGuardPolicy.evaluate(
                nowMillis =
                    ForwardingGuardPolicy.MAX_DAILY_ATTEMPTS *
                        ForwardingGuardPolicy.BURST_WINDOW_MILLIS,
                attemptId = "blocked",
                fingerprint = "new",
                previousState = state,
            )

        assertBlocked(blocked, ForwardingBlockReason.DAILY_LIMIT)
    }

    @Test
    fun futureEntriesAreClampedAndStillProtectAfterClockRollback() {
        val futureState =
            ForwardingGuardState(
                dedupeAttempts = listOf(DedupeAttempt("future", 2_000, "same")),
                rateAttempts = listOf(RateAttempt("future", 2_000)),
            )

        val evaluation =
            ForwardingGuardPolicy.evaluate(
                nowMillis = 1_000,
                attemptId = "current",
                fingerprint = "same",
                previousState = futureState,
            )

        assertBlocked(evaluation, ForwardingBlockReason.DUPLICATE)
        assertEquals(
            1_000,
            evaluation.state.dedupeAttempts
                .single()
                .timestampMillis,
        )
        assertEquals(
            1_000,
            evaluation.state.rateAttempts
                .single()
                .timestampMillis,
        )
    }

    private fun evaluateAllowed(
        nowMillis: Long,
        attemptId: String,
        fingerprint: String,
        previousState: ForwardingGuardState = ForwardingGuardState(),
    ): ForwardingGuardEvaluation {
        val evaluation =
            ForwardingGuardPolicy.evaluate(
                nowMillis = nowMillis,
                attemptId = attemptId,
                fingerprint = fingerprint,
                previousState = previousState,
            )
        assertTrue(evaluation.result is ForwardingGuardResult.Allowed)
        return evaluation
    }

    private fun assertBlocked(
        evaluation: ForwardingGuardEvaluation,
        reason: ForwardingBlockReason,
    ) {
        assertEquals(ForwardingGuardResult.Blocked(reason), evaluation.result)
    }
}
