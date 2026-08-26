package com.example.smsforwarder

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
        )

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchShowsPrimaryConfigurationControls() {
        composeRule.onNodeWithText("SMS 자동전달").assertIsDisplayed()
        composeRule.onNodeWithText("전달번호").assertIsDisplayed()
        composeRule.onNodeWithText("키워드").assertIsDisplayed()
    }
}
