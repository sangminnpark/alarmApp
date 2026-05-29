package com.example.activity_mainxml

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.example.activity_mainxml.alarm.AlarmScheduler
import com.example.activity_mainxml.data.AlarmRepository.loadAlarms
import com.example.activity_mainxml.data.AlarmRepository.saveAlarms
import com.example.activity_mainxml.data.HolidayRepository
import com.example.activity_mainxml.model.CustomVoice
import com.example.activity_mainxml.ui.main.AlarmApp
import com.example.activity_mainxml.ui.theme.VoiceWakeTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 💡 키보드 상태 감지(WindowInsets)를 위해 설정
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 권한 체크
        checkSystemPermissions()

        // 💡 공휴일 데이터 업데이트
        lifecycleScope.launch {
            HolidayRepository.fetchAndSaveHolidays(this@MainActivity)
        }

        // 💡 사용자 ID 부여 (식별용도로 유지)
        val userId = getOrCreateUserId()

        val initialAlarms = loadAlarms(this)

        setContent {
            val prefs = remember { getSharedPreferences("app_settings", MODE_PRIVATE) }
            var themeMode by remember { 
                mutableStateOf(prefs.getString("theme_mode", "system") ?: "system") 
            }
            var listMode by remember {
                mutableStateOf(prefs.getString("list_mode", "detailed") ?: "detailed")
            }
            var uiScale by remember {
                mutableStateOf(prefs.getString("ui_scale", "normal") ?: "normal")
            }
            
            VoiceWakeTheme(themeMode = themeMode) {
                // 💡 [변경] CustomVoice 객체 리스트를 사용합니다.
                var customVoices by remember {
                    mutableStateOf<List<CustomVoice>>(loadCustomVoicesFromPrefs())
                }

                AlarmApp(
                    initialAlarms = initialAlarms,
                    customVoices = customVoices.map { Triple(it.name, it.voiceId, it.previewPath) }, 
                    onSetAlarm = { alarm -> AlarmScheduler.schedule(this, alarm) },
                    currentThemeMode = themeMode,
                    onThemeChange = { newMode ->
                        themeMode = newMode
                        prefs.edit().putString("theme_mode", newMode).apply()
                    },
                    currentListMode = listMode,
                    onListModeChange = { newMode ->
                        listMode = newMode
                        prefs.edit().putString("list_mode", newMode).apply()
                    },
                    currentUiScale = uiScale,
                    onUiScaleChange = { newScale ->
                        uiScale = newScale
                        prefs.edit().putString("ui_scale", newScale).apply()
                    },

                    onDeleteVoice = { voiceTriple ->
                        val previewFile = File(voiceTriple.third)
                        if (previewFile.exists()) previewFile.delete()

                        val updatedList = customVoices.filter { it.voiceId != voiceTriple.second }
                        customVoices = updatedList
                        saveCustomVoicesToPrefs(updatedList)

                        Toast.makeText(this@MainActivity, "'${voiceTriple.first}' 삭제 완료", Toast.LENGTH_SHORT).show()
                    },
                    onGenerateVoice = { file, _, customName, onComplete ->
                        lifecycleScope.launch {
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    AlarmScheduler.requestElevenLabsVoice(
                                        context = this@MainActivity,
                                        recordFile = file,
                                        voiceName = customName.ifBlank { "내 목소리 ${customVoices.size + 1}" }
                                    )
                                }

                                if (result != null) {
                                    val (voiceId, previewFile) = result
                                    val newVoice = CustomVoice(
                                        name = if (customName.isNotBlank()) customName else "내 목소리 ${customVoices.size + 1}",
                                        voiceId = voiceId,
                                        previewPath = previewFile.absolutePath,
                                        userId = userId
                                    )

                                    val updatedList = customVoices + newVoice
                                    saveCustomVoicesToPrefs(updatedList)
                                    customVoices = updatedList

                                    onComplete(voiceId)
                                    Toast.makeText(this@MainActivity, "'${newVoice.name}' 생성 완료!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this@MainActivity, "보이스 생성 실패", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(this@MainActivity, "연결 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onCancelAlarm = { alarm -> AlarmScheduler.cancel(this, alarm) },
                    onDeleteAlarm = { alarm ->
                        AlarmScheduler.cancel(this, alarm)
                        alarm.localFilePath?.let { path ->
                            val file = File(path)
                            if (file.exists()) file.delete()
                        }
                    },
                    onSaveToDisk = { list ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            saveAlarms(this@MainActivity, list.map { it.copy(userId = userId) })
                        }
                    }
                )
            }
        }
    }

    private fun getOrCreateUserId(): String {
        val prefs = getSharedPreferences("user_prefs", MODE_PRIVATE)
        var id = prefs.getString("user_id", null)
        if (id == null) {
            id = "user_${System.currentTimeMillis()}_${Random.nextInt(1000)}"
            prefs.edit { putString("user_id", id) }
        }
        return id
    }

    private fun loadCustomVoicesFromPrefs(): List<CustomVoice> {
        val prefs = getSharedPreferences("custom_voices_prefs", MODE_PRIVATE)
        val json = prefs.getString("voice_list_v2", null)

        // 💡 1. 신규 형식이 이미 있다면 그대로 반환
        if (json != null) {
            return Gson().fromJson(json, object : TypeToken<List<CustomVoice>>() {}.type)
        }

        // 💡 2. 신규 형식이 없고 구형 데이터가 있다면 마이그레이션 진행
        val oldData = prefs.getString("voice_list", "") ?: ""
        if (oldData.isNotEmpty()) {
            val userId = getOrCreateUserId()
            val migratedList = oldData.split(",").mapNotNull {
                val parts = it.split("|")
                if (parts.size == 3) {
                    CustomVoice(
                        name = parts[0],
                        voiceId = parts[1],
                        previewPath = parts[2],
                        userId = userId, // 현재 사용자에게 귀속
                        lastUsedAt = System.currentTimeMillis() // 지금부터 3일 카운트 시작
                    )
                } else null
            }
            
            // 마이그레이션된 데이터를 새 키에 저장하고 구형 데이터 삭제
            saveCustomVoicesToPrefs(migratedList)
            prefs.edit { remove("voice_list") }
            return migratedList
        }

        return emptyList()
    }

    private fun saveCustomVoicesToPrefs(voices: List<CustomVoice>) {
        val prefs = getSharedPreferences("custom_voices_prefs", MODE_PRIVATE)
        prefs.edit().putString("voice_list_v2", Gson().toJson(voices)).apply()
    }

    // --- [권한 로직 유지] ---
    private fun checkSystemPermissions() {
        // 1. 배터리 최적화 제외 요청 (알람 정확도 핵심)
        val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) { }
        }

        // 2. 정확한 알람 권한
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent =
                    Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    }
                startActivity(intent)
            }
        }
        if (!android.provider.Settings.canDrawOverlays(this)) {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
        checkNotificationPermission()
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }
}
