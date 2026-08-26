package com.example.smsforwarder

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.util.UUID
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

internal sealed interface ForwardingGuardResult {
    data class Allowed(
        val attemptId: String,
    ) : ForwardingGuardResult

    data class Blocked(
        val reason: ForwardingBlockReason,
    ) : ForwardingGuardResult
}

internal enum class ForwardingBlockReason {
    DUPLICATE,
    BURST_LIMIT,
    DAILY_LIMIT,
    STORAGE_FAILURE,
}

internal fun interface SmsForwardingGuard {
    fun tryAcquire(
        context: Context,
        sender: String?,
        destination: String,
        messageBody: String,
    ): ForwardingGuardResult
}

internal data class DedupeAttempt(
    val attemptId: String,
    val timestampMillis: Long,
    val fingerprint: String,
)

internal data class RateAttempt(
    val attemptId: String,
    val timestampMillis: Long,
)

internal data class ForwardingGuardState(
    val dedupeAttempts: List<DedupeAttempt> = emptyList(),
    val rateAttempts: List<RateAttempt> = emptyList(),
)

internal data class ForwardingGuardEvaluation(
    val result: ForwardingGuardResult,
    val state: ForwardingGuardState,
)

internal object ForwardingGuardPolicy {
    const val DUPLICATE_WINDOW_MILLIS = 10 * 60 * 1000L
    const val BURST_WINDOW_MILLIS = 10 * 60 * 1000L
    const val DAILY_WINDOW_MILLIS = 24 * 60 * 60 * 1000L
    const val MAX_BURST_ATTEMPTS = 5
    const val MAX_DAILY_ATTEMPTS = 20

    fun evaluate(
        nowMillis: Long,
        attemptId: String,
        fingerprint: String,
        previousState: ForwardingGuardState,
    ): ForwardingGuardEvaluation {
        val dedupeAttempts =
            previousState.dedupeAttempts
                .map { attempt -> attempt.withTimestampNoLaterThan(nowMillis) }
                .filter { attempt ->
                    ageMillis(nowMillis, attempt.timestampMillis) < DUPLICATE_WINDOW_MILLIS
                }
        val rateAttempts =
            previousState.rateAttempts
                .map { attempt -> attempt.withTimestampNoLaterThan(nowMillis) }
                .filter { attempt ->
                    ageMillis(nowMillis, attempt.timestampMillis) < DAILY_WINDOW_MILLIS
                }
        val prunedState = ForwardingGuardState(dedupeAttempts, rateAttempts)

        if (dedupeAttempts.any { attempt -> attempt.fingerprint == fingerprint }) {
            return ForwardingGuardEvaluation(
                result = ForwardingGuardResult.Blocked(ForwardingBlockReason.DUPLICATE),
                state = prunedState,
            )
        }

        val burstAttempts =
            rateAttempts.count { attempt ->
                ageMillis(nowMillis, attempt.timestampMillis) < BURST_WINDOW_MILLIS
            }
        if (burstAttempts >= MAX_BURST_ATTEMPTS) {
            return ForwardingGuardEvaluation(
                result = ForwardingGuardResult.Blocked(ForwardingBlockReason.BURST_LIMIT),
                state = prunedState,
            )
        }
        if (rateAttempts.size >= MAX_DAILY_ATTEMPTS) {
            return ForwardingGuardEvaluation(
                result = ForwardingGuardResult.Blocked(ForwardingBlockReason.DAILY_LIMIT),
                state = prunedState,
            )
        }

        return ForwardingGuardEvaluation(
            result = ForwardingGuardResult.Allowed(attemptId),
            state =
                ForwardingGuardState(
                    dedupeAttempts =
                        dedupeAttempts +
                            DedupeAttempt(
                                attemptId = attemptId,
                                timestampMillis = nowMillis,
                                fingerprint = fingerprint,
                            ),
                    rateAttempts =
                        rateAttempts +
                            RateAttempt(
                                attemptId = attemptId,
                                timestampMillis = nowMillis,
                            ),
                ),
        )
    }

    private fun DedupeAttempt.withTimestampNoLaterThan(nowMillis: Long): DedupeAttempt =
        if (timestampMillis > nowMillis) copy(timestampMillis = nowMillis) else this

    private fun RateAttempt.withTimestampNoLaterThan(nowMillis: Long): RateAttempt =
        if (timestampMillis > nowMillis) copy(timestampMillis = nowMillis) else this

    private fun ageMillis(
        nowMillis: Long,
        timestampMillis: Long,
    ): Long {
        val ageMillis = nowMillis - timestampMillis
        return if (ageMillis < 0) Long.MAX_VALUE else ageMillis
    }
}

internal object AndroidSmsForwardingGuard : SmsForwardingGuard {
    private const val PREFERENCES_NAME = "sms_forwarder_runtime"
    private const val KEY_DEDUPE_ATTEMPTS = "dedupe_attempts"
    private const val KEY_RATE_ATTEMPTS = "rate_attempts"
    private const val FIELD_SEPARATOR = "|"
    private const val TAG = "SmsForwardingGuard"

    private val stateLock = Any()

    override fun tryAcquire(
        context: Context,
        sender: String?,
        destination: String,
        messageBody: String,
    ): ForwardingGuardResult =
        synchronized(stateLock) {
            val preferences =
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            val previousState = loadState(preferences)
            val fingerprint =
                runCatching {
                    AndroidHmacFingerprinter.fingerprint(sender, destination, messageBody)
                }.getOrElse { exception ->
                    Log.e(TAG, "중복 방지 지문 생성 실패", exception)
                    return@synchronized ForwardingGuardResult.Blocked(
                        ForwardingBlockReason.STORAGE_FAILURE,
                    )
                }
            val evaluation =
                ForwardingGuardPolicy.evaluate(
                    nowMillis = System.currentTimeMillis(),
                    attemptId = UUID.randomUUID().toString(),
                    fingerprint = fingerprint,
                    previousState = previousState,
                )

            if (!saveState(preferences, evaluation.state)) {
                Log.e(TAG, "전달 제한 상태 저장 실패")
                ForwardingGuardResult.Blocked(ForwardingBlockReason.STORAGE_FAILURE)
            } else {
                evaluation.result
            }
        }

    fun releaseDuplicate(
        context: Context,
        attemptId: String,
    ) {
        synchronized(stateLock) {
            val preferences =
                context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            val previousState = loadState(preferences)
            val updatedState =
                previousState.copy(
                    dedupeAttempts =
                        previousState.dedupeAttempts.filterNot { attempt ->
                            attempt.attemptId == attemptId
                        },
                )
            if (!saveState(preferences, updatedState)) {
                Log.e(TAG, "실패한 전달의 중복 방지 예약 해제 실패")
            }
        }
    }

    private fun loadState(preferences: android.content.SharedPreferences): ForwardingGuardState =
        ForwardingGuardState(
            dedupeAttempts =
                preferences
                    .getStringSet(KEY_DEDUPE_ATTEMPTS, emptySet())
                    .orEmpty()
                    .mapNotNull(::decodeDedupeAttempt),
            rateAttempts =
                preferences
                    .getStringSet(KEY_RATE_ATTEMPTS, emptySet())
                    .orEmpty()
                    .mapNotNull(::decodeRateAttempt),
        )

    private fun saveState(
        preferences: android.content.SharedPreferences,
        state: ForwardingGuardState,
    ): Boolean =
        preferences
            .edit()
            .putStringSet(
                KEY_DEDUPE_ATTEMPTS,
                state.dedupeAttempts.mapTo(mutableSetOf(), ::encodeDedupeAttempt),
            ).putStringSet(
                KEY_RATE_ATTEMPTS,
                state.rateAttempts.mapTo(mutableSetOf(), ::encodeRateAttempt),
            ).commit()

    private fun encodeDedupeAttempt(attempt: DedupeAttempt): String =
        listOf(attempt.attemptId, attempt.timestampMillis, attempt.fingerprint)
            .joinToString(FIELD_SEPARATOR)

    private fun decodeDedupeAttempt(value: String): DedupeAttempt? {
        val fields = value.split(FIELD_SEPARATOR, limit = 3)
        if (fields.size != 3) return null
        return DedupeAttempt(
            attemptId = fields[0],
            timestampMillis = fields[1].toLongOrNull() ?: return null,
            fingerprint = fields[2],
        )
    }

    private fun encodeRateAttempt(attempt: RateAttempt): String =
        listOf(attempt.attemptId, attempt.timestampMillis).joinToString(FIELD_SEPARATOR)

    private fun decodeRateAttempt(value: String): RateAttempt? {
        val fields = value.split(FIELD_SEPARATOR, limit = 2)
        if (fields.size != 2) return null
        return RateAttempt(
            attemptId = fields[0],
            timestampMillis = fields[1].toLongOrNull() ?: return null,
        )
    }
}

private object AndroidHmacFingerprinter {
    private const val KEY_ALIAS = "sms_forwarder_dedupe_hmac_v1"
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"

    fun fingerprint(
        sender: String?,
        destination: String,
        messageBody: String,
    ): String {
        val key = loadOrCreateKey()
        val mac = Mac.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256)
        mac.init(key)
        val digest = mac.doFinal(fingerprintPayload(sender, destination, messageBody))
        return Base64.encodeToString(
            digest,
            Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE,
        )
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) return existingKey

        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEY_STORE)
            .apply {
                init(
                    KeyGenParameterSpec
                        .Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .build(),
                )
            }.generateKey()
    }

    private fun fingerprintPayload(
        sender: String?,
        destination: String,
        messageBody: String,
    ): ByteArray {
        val values = listOf(sender.orEmpty(), destination, messageBody)
        return buildString {
            values.forEach { value ->
                append(value.length)
                append(':')
                append(value)
            }
        }.toByteArray(Charsets.UTF_8)
    }
}
