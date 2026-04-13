package com.example.activity_mainxml

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.activity_mainxml.data.AlarmRepository.loadAlarms
import com.example.activity_mainxml.data.AlarmRepository.saveAlarms
import com.example.activity_mainxml.ui.theme.AlarmApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 권한 체크
        checkSystemPermissions()
        checkAudioPermission()

        val initialAlarms = loadAlarms(this)

        setContent {
            // 💡 [추가] 앱 시작 시 저장된 커스텀 보이스 리스트를 불러옵니다.
            // MainActivity.kt 내 setContent 블록 시작 부분
            var customVoices by remember {
                mutableStateOf<List<Triple<String, String, String>>>(loadCustomVoicesFromPrefs())
            }

            AlarmApp(
                initialAlarms = initialAlarms,
                customVoices = customVoices, // 💡 AlarmApp으로 리스트 전달
                onSetAlarm = { alarm -> AlarmScheduler.schedule(this, alarm) },

                onDeleteVoice = { voiceToDelete ->
                    // voiceToDelete: Triple(이름, voiceId, 미리보기경로)
                    val previewFile = File(voiceToDelete.third)
                    if (previewFile.exists()) previewFile.delete()

                    // 리스트에서 삭제 (voiceId가 일치하지 않는 것만 남김)
                    val updatedList = customVoices.filter { it.second != voiceToDelete.second }
                    customVoices = updatedList
                    saveCustomVoicesToPrefs(updatedList)

                    Toast.makeText(
                        this@MainActivity,
                        "'${voiceToDelete.first}' 삭제 완료",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                // MainActivity.kt 내부 onGenerateVoice 수정
                onGenerateVoice = { file, _, customName, onComplete -> // promptText는 이제 필요 없으므로 _ 처리
                    lifecycleScope.launch {
                        try {
                            // 💡 [변경] 일레븐랩스 서버에 등록하고 ID와 미리보기 파일을 가져옵니다.
                            val result = withContext(Dispatchers.IO) {
                                AlarmScheduler.requestElevenLabsVoice(
                                    context = this@MainActivity,
                                    recordFile = file,
                                    voiceName = customName.ifBlank { "내 목소리 ${customVoices.size + 1}" }
                                )
                            }

                            if (result != null) {
                                val (voiceId, previewFile) = result // Pair(String, File) 분해
                                val previewPath = previewFile.absolutePath
                                val newName =
                                    if (customName.isNotBlank()) customName else "내 목소리 ${customVoices.size + 1}"

                                // 💡 [핵심] 두 번째 인자에 파일 경로 대신 voiceId를 넣습니다.
                                val updatedList =
                                    customVoices + Triple(newName, voiceId, previewPath)

                                saveCustomVoicesToPrefs(updatedList)
                                customVoices = updatedList

                                // UI에 완료 알림 (onComplete에는 식별자로 voiceId를 넘겨줍니다)
                                onComplete(voiceId)
                                Toast.makeText(
                                    this@MainActivity,
                                    "'$newName' 생성 완료!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    this@MainActivity,
                                    "보이스 생성 실패 (ElevenLabs 에러)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: Exception) {
                            Log.e("ALARM_DEBUG", "예외 발생: ${e.message}")
                            Toast.makeText(
                                this@MainActivity,
                                "연결 실패: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onCancelAlarm = { alarm -> AlarmScheduler.cancel(this, alarm) },
                onSaveToDisk = { list -> saveAlarms(this, list) }
            )
        }
    }

    // --- [데이터 저장/불러오기 유틸리티] ---

    /**
     * SharedPreferences에서 "이름|경로" 형태로 저장된 보이스 목록을 불러옵니다.
     */
    // Triple 타입으로 변경 (이름, 메인경로, 미리보기경로)
    private fun loadCustomVoicesFromPrefs(): List<Triple<String, String, String>> {
        val prefs = getSharedPreferences("custom_voices_prefs", MODE_PRIVATE)
        val savedData = prefs.getString("voice_list", "") ?: ""
        if (savedData.isEmpty()) return emptyList()

        return savedData.split(",").mapNotNull {
            val parts = it.split("|")
            // 💡 저장 형식이 이름|메인|미리보기 3개인지 확인
            if (parts.size == 3) Triple(parts[0], parts[1], parts[2]) else null
        }
    }

    private fun saveCustomVoicesToPrefs(voices: List<Triple<String, String, String>>) {
        val prefs = getSharedPreferences("custom_voices_prefs", MODE_PRIVATE)
        // 💡 세 가지 정보를 | 구분자로 합쳐서 저장
        val dataString = voices.joinToString(",") { "${it.first}|${it.second}|${it.third}" }
        prefs.edit().putString("voice_list", dataString).apply()
    }

    // --- [권한 로직 유지] ---

    private fun checkAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 102)
        }
    }

    private fun checkSystemPermissions() {
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