package com.example.smsforwarder

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmsReceiverWiringTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences =
        context.getSharedPreferences("sms_forwarder_prefs", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
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
                    SmsSender { _, phoneNumber, message ->
                        sentSms = SentSms(phoneNumber, message)
                    },
            )

        receiver.onReceive(
            context,
            Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION),
        )

        assertNotNull(sentSms)
        assertEquals("010-1234-5678", sentSms?.phoneNumber)
        assertEquals(
            "${SmsForwardingPolicy.FORWARD_PREFIX}Your verification code is 123456",
            sentSms?.message,
        )
    }

    private data class SentSms(
        val phoneNumber: String,
        val message: String,
    )
}
