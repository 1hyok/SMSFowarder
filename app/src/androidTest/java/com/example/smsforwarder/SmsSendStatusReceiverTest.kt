package com.example.smsforwarder

import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmsSendStatusReceiverTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val runtimePreferences =
        context.getSharedPreferences("sms_forwarder_runtime", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        runtimePreferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        runtimePreferences.edit().clear().commit()
    }

    @Test
    fun sendFailureReleasesDuplicateReservationAndIsRecordedOnce() {
        val firstPermit = acquirePermit()
        val failureIntent =
            Intent(SmsSendStatusReceiver.ACTION_SMS_PART_SENT)
                .putExtra(SmsSendStatusReceiver.EXTRA_ATTEMPT_ID, firstPermit.attemptId)
        val receiver = SmsSendStatusReceiver()

        receiver.handleSendResult(
            context,
            failureIntent,
            SmsManager.RESULT_ERROR_NO_SERVICE,
        )

        val secondPermit = acquirePermit()
        assertNotEquals(firstPermit.attemptId, secondPermit.attemptId)
        assertTrue(!SmsFailureTracker.markFailureOnce(context, firstPermit.attemptId))
    }

    private fun acquirePermit(): ForwardingGuardResult.Allowed {
        val result =
            AndroidSmsForwardingGuard.tryAcquire(
                context = context,
                sender = "1588-0000",
                destination = "010-1234-5678",
                messageBody = "인증번호는 123456입니다",
            )
        assertTrue(result is ForwardingGuardResult.Allowed)
        return result as ForwardingGuardResult.Allowed
    }
}
