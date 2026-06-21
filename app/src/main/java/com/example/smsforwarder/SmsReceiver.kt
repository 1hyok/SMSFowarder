package com.example.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return

        val sharedPref = context.getSharedPreferences("sms_forwarder_prefs", Context.MODE_PRIVATE)
        val keywords = sharedPref.getStringSet("keywords", null)
        val forwardNumber = sharedPref.getString("forward_number", null)

        if (keywords.isNullOrEmpty() || forwardNumber.isNullOrBlank()) return

        val messageBody = messages.joinToString("") { it.messageBody ?: "" }
        if (messageBody.isEmpty()) return

        val lowerMessage = messageBody.lowercase()
        val hasKeyword = keywords.any { lowerMessage.contains(it.lowercase()) }

        if (hasKeyword) {
            sendSms(context, forwardNumber, "📱$messageBody") // context 전달
        }
    }

    private fun sendSms(
        context: Context,
        phoneNumber: String,
        message: String,
    ) { // context 매개변수 추가
        try {
            val smsManager =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    context.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }

            if (message.length <= 160) {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            } else {
                smsManager.sendMultipartTextMessage(
                    phoneNumber,
                    null,
                    smsManager.divideMessage(message),
                    null,
                    null,
                )
            }
        } catch (e: Exception) {
            Log.e("SmsReceiver", "전송 실패: ${e.message}")
        }
    }
}
