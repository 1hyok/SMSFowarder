package com.example.smsforwarder

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
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
        attemptId: String,
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
        attemptId: String,
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
                reportImmediateFailure(context, attemptId)
                return
            }

            val messageParts = smsManager.divideMessage(message)
            val sentIntents =
                ArrayList(
                    messageParts.indices.map { partIndex ->
                        createSentPendingIntent(
                            context = context,
                            attemptId = attemptId,
                            partIndex = partIndex,
                        )
                    },
                )
            if (messageParts.size == 1) {
                smsManager.sendTextMessage(
                    phoneNumber,
                    null,
                    messageParts.single(),
                    sentIntents.single(),
                    null,
                )
            } else {
                smsManager.sendMultipartTextMessage(
                    phoneNumber,
                    null,
                    messageParts,
                    sentIntents,
                    null,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "전송 실패", e)
            reportImmediateFailure(context, attemptId)
        }
    }

    private fun createSentPendingIntent(
        context: Context,
        attemptId: String,
        partIndex: Int,
    ): PendingIntent {
        val statusIntent =
            Intent(context, SmsSendStatusReceiver::class.java).apply {
                action = SmsSendStatusReceiver.ACTION_SMS_PART_SENT
                data =
                    Uri
                        .Builder()
                        .scheme("smsforwarder")
                        .authority("send-status")
                        .appendPath(attemptId)
                        .appendPath(partIndex.toString())
                        .build()
                putExtra(SmsSendStatusReceiver.EXTRA_ATTEMPT_ID, attemptId)
            }
        return PendingIntent.getBroadcast(
            context,
            0,
            statusIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT,
        )
    }

    private fun reportImmediateFailure(
        context: Context,
        attemptId: String,
    ) {
        AndroidSmsForwardingGuard.releaseDuplicate(context, attemptId)
        if (SmsFailureTracker.markFailureOnce(context, attemptId)) {
            ForwardingNotifier.notifySendFailure(
                context,
                attemptId,
                SmsManager.RESULT_ERROR_GENERIC_FAILURE,
            )
        }
    }

    private const val TAG = "SmsReceiver"
}

class SmsReceiver internal constructor(
    private val messageDecoder: SmsMessageDecoder,
    private val smsSender: SmsSender,
    private val forwardingGuard: SmsForwardingGuard,
) : BroadcastReceiver() {
    constructor() : this(
        AndroidSmsMessageDecoder,
        AndroidSmsSender,
        AndroidSmsForwardingGuard,
    )

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
            val destination = requireNotNull(forwardNumber)
            when (
                val guardResult =
                    forwardingGuard.tryAcquire(
                        context = context,
                        sender = sender,
                        destination = destination,
                        messageBody = messageBody,
                    )
            ) {
                is ForwardingGuardResult.Allowed -> {
                    smsSender.send(
                        context = context,
                        phoneNumber = destination,
                        message = SmsForwardingPolicy.formatForwardedMessage(messageBody, sender),
                        attemptId = guardResult.attemptId,
                    )
                }

                is ForwardingGuardResult.Blocked -> {
                    ForwardingNotifier.notifyBlocked(context, guardResult.reason)
                }
            }
        }
    }
}
