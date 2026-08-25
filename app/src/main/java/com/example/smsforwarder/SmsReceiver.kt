package com.example.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log

internal data class DecodedSmsMessage(
    val body: String,
    val sender: String?,
)

internal fun interface SmsMessageDecoder {
    fun decode(intent: Intent): List<DecodedSmsMessage>
}

internal fun interface SmsSender {
    fun send(
        context: Context,
        phoneNumber: String,
        message: String,
    )
}

private object AndroidSmsMessageDecoder : SmsMessageDecoder {
    override fun decode(intent: Intent): List<DecodedSmsMessage> =
        Telephony.Sms.Intents
            .getMessagesFromIntent(intent)
            ?.map { message ->
                DecodedSmsMessage(
                    body = message.messageBody.orEmpty(),
                    sender = message.originatingAddress,
                )
            }.orEmpty()
}

private object AndroidSmsSender : SmsSender {
    override fun send(
        context: Context,
        phoneNumber: String,
        message: String,
    ) {
        try {
            val smsManager =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

            if (smsManager == null) {
                Log.e(TAG, "SmsManager 사용 불가 — telephony 미지원 기기")
                return
            }

            val messageParts = smsManager.divideMessage(message)
            if (messageParts.size == 1) {
                smsManager.sendTextMessage(phoneNumber, null, messageParts.single(), null, null)
            } else {
                smsManager.sendMultipartTextMessage(
                    phoneNumber,
                    null,
                    messageParts,
                    null,
                    null,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "전송 실패", e)
        }
    }

    private const val TAG = "SmsReceiver"
}

class SmsReceiver internal constructor(
    private val messageDecoder: SmsMessageDecoder,
    private val smsSender: SmsSender,
) : BroadcastReceiver() {
    constructor() : this(AndroidSmsMessageDecoder, AndroidSmsSender)

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = messageDecoder.decode(intent)
        if (messages.isEmpty()) return

        val sharedPref = context.getSharedPreferences("sms_forwarder_prefs", Context.MODE_PRIVATE)
        val keywords = sharedPref.getStringSet("keywords", null)
        val forwardNumber = sharedPref.getString("forward_number", null)

        val messageBody = messages.joinToString("") { message -> message.body }
        val sender = messages.first().sender
        if (SmsForwardingPolicy.shouldForward(messageBody, sender, forwardNumber, keywords)) {
            smsSender.send(
                context = context,
                phoneNumber = requireNotNull(forwardNumber),
                message = "${SmsForwardingPolicy.FORWARD_PREFIX}$messageBody",
            )
        }
    }
}
