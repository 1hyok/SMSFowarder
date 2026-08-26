package com.example.smsforwarder

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat

class SmsSendStatusReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        handleSendResult(context, intent, resultCode)
    }

    internal fun handleSendResult(
        context: Context,
        intent: Intent,
        sendResultCode: Int,
    ) {
        if (intent.action != ACTION_SMS_PART_SENT || sendResultCode == Activity.RESULT_OK) return

        val attemptId = intent.getStringExtra(EXTRA_ATTEMPT_ID) ?: return
        AndroidSmsForwardingGuard.releaseDuplicate(context, attemptId)
        if (SmsFailureTracker.markFailureOnce(context, attemptId)) {
            ForwardingNotifier.notifySendFailure(context, attemptId, sendResultCode)
        }
    }

    internal companion object {
        const val ACTION_SMS_PART_SENT =
            "com.example.smsforwarder.action.SMS_PART_SENT"
        const val EXTRA_ATTEMPT_ID = "attempt_id"
    }
}

internal object SmsFailureTracker {
    private const val PREFERENCES_NAME = "sms_forwarder_runtime"
    private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
    private const val ENTRY_SEPARATOR = "|"
    private const val RETENTION_MILLIS = 24 * 60 * 60 * 1000L
    private const val TAG = "SmsFailureTracker"

    private val stateLock = Any()
    private val processFailures = mutableMapOf<String, Long>()

    fun markFailureOnce(
        context: Context,
        attemptId: String,
    ): Boolean =
        synchronized(stateLock) {
            val nowMillis = System.currentTimeMillis()
            val preferences =
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            processFailures.replaceAll { _, timestampMillis ->
                timestampMillis.coerceAtMost(nowMillis)
            }
            processFailures.entries.removeAll { entry ->
                nowMillis - entry.value >= RETENTION_MILLIS
            }
            val retainedEntries =
                preferences
                    .getStringSet(KEY_FAILED_ATTEMPTS, emptySet())
                    .orEmpty()
                    .mapNotNull(::decodeEntry)
                    .map { entry ->
                        if (entry.timestampMillis > nowMillis) {
                            entry.copy(timestampMillis = nowMillis)
                        } else {
                            entry
                        }
                    }.filter { entry ->
                        nowMillis - entry.timestampMillis < RETENTION_MILLIS
                    }
            if (
                processFailures.containsKey(attemptId) ||
                retainedEntries.any { entry -> entry.attemptId == attemptId }
            ) {
                return@synchronized false
            }

            processFailures[attemptId] = nowMillis
            val saved =
                preferences
                    .edit()
                    .putStringSet(
                        KEY_FAILED_ATTEMPTS,
                        (retainedEntries + FailedAttempt(attemptId, nowMillis))
                            .mapTo(mutableSetOf(), ::encodeEntry),
                    ).commit()
            if (!saved) {
                Log.e(TAG, "전송 실패 중복 방지 상태 저장 실패")
            }
            true
        }

    private fun encodeEntry(entry: FailedAttempt): String = "${entry.timestampMillis}$ENTRY_SEPARATOR${entry.attemptId}"

    private fun decodeEntry(value: String): FailedAttempt? {
        val fields = value.split(ENTRY_SEPARATOR, limit = 2)
        if (fields.size != 2) return null
        return FailedAttempt(
            attemptId = fields[1],
            timestampMillis = fields[0].toLongOrNull() ?: return null,
        )
    }

    private data class FailedAttempt(
        val attemptId: String,
        val timestampMillis: Long,
    )
}

internal object ForwardingNotifier {
    private const val CHANNEL_ID = "sms_forwarding_failures"
    private const val CHANNEL_NAME = "SMS 전달 실패"
    private const val RATE_LIMIT_NOTIFICATION_ID = 10_002
    private const val STORAGE_FAILURE_NOTIFICATION_ID = 10_003
    private const val TAG = "ForwardingNotifier"

    fun ensureChannel(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "인증문자 자동 전달 실패와 과다 발송 차단 알림"
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            },
        )
    }

    fun notificationsEnabled(context: Context): Boolean {
        val permissionGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
        if (!permissionGranted) return false

        ensureChannel(context)
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val channelEnabled =
            notificationManager.getNotificationChannel(CHANNEL_ID)?.importance !=
                NotificationManager.IMPORTANCE_NONE
        return notificationManager.areNotificationsEnabled() && channelEnabled
    }

    fun notifySendFailure(
        context: Context,
        attemptId: String,
        resultCode: Int,
    ) {
        val detail =
            when (resultCode) {
                SmsManager.RESULT_ERROR_RADIO_OFF -> "비행기 모드 또는 통신 기능이 꺼져 있습니다."
                SmsManager.RESULT_ERROR_NO_SERVICE -> "통신 서비스에 연결되지 않았습니다."
                SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "기기의 SMS 발송 한도를 초과했습니다."
                else -> "SIM과 통신 상태를 확인하세요."
            }
        notify(
            context = context,
            notificationId = attemptId.hashCode(),
            title = "인증문자 전달 요청 실패",
            message = detail,
        )
    }

    fun notifyBlocked(
        context: Context,
        reason: ForwardingBlockReason,
    ) {
        when (reason) {
            ForwardingBlockReason.DUPLICATE -> {
                Unit
            }

            ForwardingBlockReason.BURST_LIMIT,
            ForwardingBlockReason.DAILY_LIMIT,
            -> {
                notify(
                    context = context,
                    notificationId = RATE_LIMIT_NOTIFICATION_ID,
                    title = "인증문자 과다 전달 차단",
                    message =
                        if (reason == ForwardingBlockReason.BURST_LIMIT) {
                            "10분 동안 5건을 초과해 추가 전달을 차단했습니다."
                        } else {
                            "24시간 동안 20건을 초과해 추가 전달을 차단했습니다."
                        },
                )
            }

            ForwardingBlockReason.STORAGE_FAILURE -> {
                notify(
                    context = context,
                    notificationId = STORAGE_FAILURE_NOTIFICATION_ID,
                    title = "자동 전달 보호 기능 오류",
                    message = "안전을 위해 인증문자 전달을 차단했습니다. 앱을 확인하세요.",
                )
            }
        }
    }

    private fun notify(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
    ) {
        if (!notificationsEnabled(context)) {
            Log.e(TAG, "$title: 알림 권한 또는 채널이 꺼져 있습니다")
            return
        }

        val notification =
            Notification
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_sms)
                .setContentTitle(title)
                .setContentText(message)
                .setCategory(Notification.CATEGORY_ERROR)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .setOnlyAlertOnce(true)
                .build()

        context
            .getSystemService(NotificationManager::class.java)
            .notify(notificationId, notification)
    }
}
