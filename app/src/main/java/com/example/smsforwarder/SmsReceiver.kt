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

        // 루프 차단 1: 이미 전달된(마커가 붙은) 메시지는 재전달하지 않는다.
        if (messageBody.startsWith(FORWARD_PREFIX)) return

        // 루프 차단 2: 발신자가 전달 대상 번호 자신이면 되돌려보내지 않는다.
        val sender = messages.firstOrNull()?.originatingAddress
        if (sender != null && sameNumber(sender, forwardNumber)) return

        val lowerMessage = messageBody.lowercase()
        val hasKeyword = keywords.any { lowerMessage.contains(it.lowercase()) }

        if (hasKeyword) {
            sendSms(context, forwardNumber, "$FORWARD_PREFIX$messageBody")
        }
    }

    private fun sendSms(
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
            Log.e(TAG, "전송 실패", e)
        }
    }

    companion object {
        private const val TAG = "SmsReceiver"
        private const val FORWARD_PREFIX = "📱"

        // 전화번호 동등 비교 (PhoneNumberUtils.compare 는 deprecated). 숫자만 뽑아 끝 8자리로 비교.
        private fun sameNumber(
            a: String,
            b: String,
        ): Boolean {
            val na = a.filter(Char::isDigit)
            val nb = b.filter(Char::isDigit)
            return na.isNotEmpty() && nb.isNotEmpty() && na.takeLast(8) == nb.takeLast(8)
        }
    }
}
