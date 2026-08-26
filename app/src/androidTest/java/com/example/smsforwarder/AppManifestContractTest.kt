package com.example.smsforwarder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppManifestContractTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val packageManager = context.packageManager

    @Test
    fun manifestDeclaresSmsPermissionsAndSecuredReceiver() {
        val packageInfo = packageInfo()
        val requestedPermissions = packageInfo.requestedPermissions.orEmpty().toSet()

        assertTrue(requestedPermissions.contains(Manifest.permission.RECEIVE_SMS))
        assertTrue(requestedPermissions.contains(Manifest.permission.SEND_SMS))
        assertTrue(requestedPermissions.contains(Manifest.permission.POST_NOTIFICATIONS))

        val receiver = packageInfo.receivers.orEmpty().single { it.name == SmsReceiver::class.java.name }
        assertTrue(receiver.enabled)
        assertTrue(receiver.exported)
        assertEquals(Manifest.permission.BROADCAST_SMS, receiver.permission)

        val statusReceiver =
            packageInfo.receivers.orEmpty().single {
                it.name == SmsSendStatusReceiver::class.java.name
            }
        assertTrue(statusReceiver.enabled)
        assertTrue(!statusReceiver.exported)
    }

    @Test
    fun smsReceivedActionResolvesToSmsReceiver() {
        val intent =
            Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
                .setPackage(context.packageName)

        @Suppress("DEPRECATION")
        val receivers = packageManager.queryBroadcastReceivers(intent, 0)

        assertTrue(receivers.any { it.activityInfo.name == SmsReceiver::class.java.name })
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(): PackageInfo =
        packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_RECEIVERS,
        )
}
