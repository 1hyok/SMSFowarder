package com.example.smsforwarder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telephony.PhoneNumberUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.smsforwarder.ui.theme.SMSForwarderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SMSForwarderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    SmsForwarderApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsForwarderApp() {
    val context = LocalContext.current
    var forwardNumber by remember { mutableStateOf("") }
    var newKeyword by remember { mutableStateOf("") }
    var keywords by remember { mutableStateOf(emptyList<String>()) }
    var hasSmsPermissions by remember { mutableStateOf(false) }
    var hasFailureNotifications by remember { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            hasSmsPermissions = checkSmsPermissions(context)
            hasFailureNotifications = ForwardingNotifier.notificationsEnabled(context)
            if (!hasSmsPermissions) {
                Toast.makeText(context, "SMS 권한 필요", Toast.LENGTH_SHORT).show()
            } else if (!hasFailureNotifications) {
                Toast.makeText(context, "전송 실패 알림이 꺼져 있습니다", Toast.LENGTH_SHORT).show()
            }
        }

    // 초기 설정 로드
    LaunchedEffect(Unit) {
        val sharedPref = context.getSharedPreferences("sms_forwarder_prefs", Context.MODE_PRIVATE)
        forwardNumber = sharedPref.getString("forward_number", "") ?: ""
        keywords = sharedPref.getStringSet("keywords", emptySet())?.toList() ?: emptyList()

        ForwardingNotifier.ensureChannel(context)
        hasSmsPermissions = checkSmsPermissions(context)
        hasFailureNotifications = ForwardingNotifier.notificationsEnabled(context)
        val missingPermissions = missingRuntimePermissions(context)
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions)
        }
    }

    // 설정 등에서 권한을 바꾸고 돌아오면 재확인
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    hasSmsPermissions = checkSmsPermissions(context)
                    hasFailureNotifications = ForwardingNotifier.notificationsEnabled(context)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "SMS 자동전달",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        // 권한 상태
        Card(
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        when {
                            !hasSmsPermissions -> MaterialTheme.colorScheme.errorContainer
                            !hasFailureNotifications -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.primaryContainer
                        },
                ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text =
                        if (hasSmsPermissions) {
                            "✅ SMS 수신·발신 권한 OK"
                        } else {
                            "❌ SMS 수신·발신 권한 필요"
                        },
                )
                Text(
                    text =
                        if (hasFailureNotifications) {
                            "✅ 전송 실패 알림 ON"
                        } else {
                            "⚠️ 전송 실패 알림 OFF"
                        },
                )
                if (!hasSmsPermissions || !hasFailureNotifications) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val missingPermissions = missingRuntimePermissions(context)
                        if (missingPermissions.isNotEmpty()) {
                            Button(
                                onClick = {
                                    permissionLauncher.launch(missingPermissions)
                                },
                            ) {
                                Text("권한 요청")
                            }
                        }
                        TextButton(
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null),
                                    ),
                                )
                            },
                        ) {
                            Text("설정 열기")
                        }
                    }
                }
            }
        }

        // 전화번호 설정
        Card {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("아버지 번호 (단일 전달 대상)", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = forwardNumber,
                    onValueChange = { forwardNumber = it },
                    placeholder = { Text("01012345678") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val trimmed = forwardNumber.trim()
                        if (!PhoneNumberUtils.isWellFormedSmsAddress(trimmed)) {
                            Toast.makeText(context, "올바른 전화번호를 입력하세요", Toast.LENGTH_SHORT).show()
                        } else {
                            forwardNumber = trimmed
                            saveForwardNumber(context, trimmed)
                            Toast.makeText(context, "저장완료", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("저장")
                }
            }
        }

        // 키워드 추가
        Card {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("키워드", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newKeyword,
                        onValueChange = { newKeyword = it },
                        placeholder = { Text("키워드 입력") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Button(
                        onClick = {
                            val trimmed = newKeyword.trim()
                            if (trimmed.isNotEmpty() && trimmed !in keywords) {
                                val updated = keywords + trimmed
                                keywords = updated
                                saveKeywords(context, updated)
                                newKeyword = ""
                                Toast.makeText(context, "추가완료", Toast.LENGTH_SHORT).show()
                            }
                        },
                    ) {
                        Text("추가")
                    }
                }
            }
        }

        // 키워드 목록
        if (keywords.isNotEmpty()) {
            Card {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("등록된 키워드 (${keywords.size}개)", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(keywords) { keyword ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(text = keyword)
                                TextButton(
                                    onClick = {
                                        val updated = keywords - keyword
                                        keywords = updated
                                        saveKeywords(context, updated)
                                        Toast.makeText(context, "삭제완료", Toast.LENGTH_SHORT).show()
                                    },
                                ) {
                                    Text("×", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private val SMS_PERMISSIONS =
    arrayOf(
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.SEND_SMS,
    )

private fun checkSmsPermissions(context: Context): Boolean =
    SMS_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

private fun missingRuntimePermissions(context: Context): Array<String> =
    buildList {
        SMS_PERMISSIONS
            .filterTo(this) { permission ->
                ContextCompat.checkSelfPermission(context, permission) !=
                    PackageManager.PERMISSION_GRANTED
            }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

private fun saveForwardNumber(
    context: Context,
    number: String,
) {
    context
        .getSharedPreferences("sms_forwarder_prefs", Context.MODE_PRIVATE)
        .edit()
        .putString("forward_number", number)
        .apply()
}

private fun saveKeywords(
    context: Context,
    keywords: List<String>,
) {
    context
        .getSharedPreferences("sms_forwarder_prefs", Context.MODE_PRIVATE)
        .edit()
        .putStringSet("keywords", keywords.toSet())
        .apply()
}
