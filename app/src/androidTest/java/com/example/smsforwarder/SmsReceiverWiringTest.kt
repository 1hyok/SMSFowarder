package com.example.smsforwarder

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmsReceiverWiringTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences =
        context.getSharedPreferences("sms_forwarder_prefs", Context.MODE_PRIVATE)
    private val runtimePreferences =
        context.getSharedPreferences("sms_forwarder_runtime", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        runtimePreferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
        runtimePreferences.edit().clear().commit()
    }

    @Test
    fun smsReceivedReadsSettingsCombinesPartsAndSendsPrefixedMessage() {
        preferences
            .edit()
            .putString("forward_number", "010-1234-5678")
            .putStringSet("keywords", setOf("verification code"))
            .commit()

        var sentSms: SentSms? = null
        val receiver =
            SmsReceiver(
                messageDecoder =
                    SmsMessageDecoder {
                        listOf(
                            DecodedSmsMessage("Your verification ", "1588-0000"),
                            DecodedSmsMessage("code is 123456", "1588-0000"),
                        )
                    },
                smsSender =
                    SmsSender { _, phoneNumber, message, attemptId ->
                        sentSms = SentSms(phoneNumber, message, attemptId)
                    },
                forwardingGuard =
                    SmsForwardingGuard { _, _, _, _ ->
                        ForwardingGuardResult.Allowed("test-attempt")
                    },
            )

        receiver.onReceive(
            context,
            Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION),
        )

        assertNotNull(sentSms)
        assertEquals("010-1234-5678", sentSms?.phoneNumber)
        assertEquals(
            "[FWD]\nFrom: 1588-0000\nYour verification code is 123456",
            sentSms?.message,
        )
        assertEquals("test-attempt", sentSms?.attemptId)
    }

    @Test
    fun duplicateBroadcastIsForwardedOnlyOnce() {
        preferences
            .edit()
            .putString("forward_number", "010-1234-5678")
            .putStringSet("keywords", setOf("인증번호"))
            .commit()

        var sendCount = 0
        val receiver =
            SmsReceiver(
                messageDecoder =
                    SmsMessageDecoder {
                        listOf(DecodedSmsMessage("인증번호는 123456입니다", "1588-0000"))
                    },
                smsSender = SmsSender { _, _, _, _ -> sendCount += 1 },
                forwardingGuard = AndroidSmsForwardingGuard,
            )
        val intent = Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)

        receiver.onReceive(context, intent)
        receiver.onReceive(context, intent)

        assertEquals(1, sendCount)
        val storedRuntimeState = runtimePreferences.all.toString()
        assertFalse(storedRuntimeState.contains("인증번호는 123456입니다"))
        assertFalse(storedRuntimeState.contains("1588-0000"))
        assertFalse(storedRuntimeState.contains("010-1234-5678"))
    }

    private data class SentSms(
        val phoneNumber: String,
        val message: String,
        val attemptId: String,
    )
}
