package com.example.activity_mainxml.ui.theme

import AlarmItem
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.activity_mainxml.network.VoiceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmApp(
    initialAlarms: List<AlarmItem>,
    onSetAlarm: (AlarmItem) -> Unit,
    customVoices: List<Triple<String, String, String>>,
    onGenerateVoice: (File, String, String, (String) -> Unit) -> Unit,
    onCancelAlarm: (AlarmItem) -> Unit,
    onDeleteVoice: (Triple<String, String, String>) -> Unit,
    onSaveToDisk: (List<AlarmItem>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var alarmList by remember { mutableStateOf(initialAlarms) }
    var editingAlarm by remember { mutableStateOf<AlarmItem?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }

    LaunchedEffect(alarmList) { onSaveToDisk(alarmList) }

    // ID 정제용 공통 함수 (경로 제거 + 타임스탬프 숫자 제거)
    fun extractPureVoiceId(rawId: String): String {
        return if (rawId.contains("eleven_")) {
            // 1. 경로와 확장자 제거
            val idWithTimestamp = rawId.substringAfter("eleven_").substringBefore(".mp3")
            // 2. ⭐️ 마지막에 붙은 10자리 이상의 숫자(타임스탬프) 제거
            idWithTimestamp.replace(Regex("\\d{10,}\$"), "")
        } else {
            rawId
        }
    }

    if (isAddingNew || editingAlarm != null) {
        AlarmEditScreen(
            alarm = editingAlarm,
            customVoices = customVoices,
            onDeleteVoice = { voiceTriple ->
                val pureId = extractPureVoiceId(voiceTriple.third)
                Log.d("ALARM_API", "추출된 ID로 삭제 시도 (EditScreen): $pureId")

                scope.launch {
                    val isSuccess = VoiceRepository.deleteElevenLabsVoice(pureId)
                    if (isSuccess) {
                        onDeleteVoice(voiceTriple)
                        Toast.makeText(context, "서버에서 음성이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.e("ALARM_API", "서버 삭제 실패")
                        onDeleteVoice(voiceTriple) // 실패해도 리스트에선 지움 (선택 사항)
                    }
                }
            },
            onGenerateNewVoice = { file, promptText, customName ->
                onGenerateVoice(file, promptText, customName) { newPath -> }
            },
            onSave = { h, m, msg, days, vId, _, _ ->
                scope.launch {
                    try {
                        var localPath: String? = null
                        var finalVoiceId = vId

                        if (!vId.startsWith("ko-KR-")) {
                            val amPm = if (h < 12) "오전" else "오후"
                            val displayHour = if (h % 12 == 0) 12 else h % 12
                            val fullText = "현재 시간은 ${amPm} ${displayHour}시 ${m}분입니다. $msg"

                            val audioFile = withContext(Dispatchers.IO) {
                                VoiceRepository.makeElevenLabsVoiceFile(vId, fullText, context)
                            }
                            if (audioFile != null) {
                                localPath = audioFile.absolutePath
                                // 파일 이름에서 순수 ID만 추출하여 저장 (추후 삭제를 위해)
                                finalVoiceId = extractPureVoiceId(audioFile.name)
                            }
                        }

                        val currentAlarm = if (isAddingNew) {
                            AlarmItem(
                                hour = h,
                                minute = m,
                                message = msg,
                                repeatDays = days,
                                voiceName = finalVoiceId,
                                localFilePath = localPath
                            )
                        } else {
                            onCancelAlarm(editingAlarm!!)
                            editingAlarm!!.copy(
                                hour = h,
                                minute = m,
                                message = msg,
                                repeatDays = days,
                                voiceName = finalVoiceId,
                                localFilePath = localPath,
                                isEnabled = true
                            )
                        }

                        alarmList = if (isAddingNew) alarmList + currentAlarm
                        else alarmList.map { if (it.id == currentAlarm.id) currentAlarm else it }

                        onSetAlarm(currentAlarm)
                        isAddingNew = false
                        editingAlarm = null
                        Toast.makeText(context, "알람이 설정되었습니다.", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e("ALARM_SAVE", "파일 생성 실패: ${e.message}")
                    }
                }
            },
            onCancel = { isAddingNew = false; editingAlarm = null }
        )
    } else {
        Scaffold(
            topBar = { CenterAlignedTopAppBar(title = { Text("내 보이스 알람") }) },
            floatingActionButton = {
                FloatingActionButton(onClick = { isAddingNew = true }) {
                    Icon(Icons.Default.Add, "추가")
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                items(alarmList) { alarm ->
                    AlarmRow(
                        alarm = alarm,
                        onToggle = { isChecked ->
                            alarmList =
                                alarmList.map { if (it.id == alarm.id) it.copy(isEnabled = isChecked) else it }
                        },
                        onClick = { editingAlarm = alarm },
                        onDelete = {
                            scope.launch {
                                val pureId = extractPureVoiceId(alarm.voiceName)
                                Log.d("ALARM_API", "서버 삭제 요청 ID: [$pureId]")

                                if (pureId.isEmpty()) {
                                    // ID가 비어있다면 로컬에서만 삭제하고 종료
                                    alarmList = alarmList.filter { it.id != alarm.id }
                                    return@launch
                                }

                                // 1. 서버 삭제가 필요한 경우 (ElevenLabs 커스텀 보이스 등)
                                if (!pureId.startsWith("ko-KR-")) {
                                    val success = VoiceRepository.deleteElevenLabsVoice(pureId)

                                    if (success) {
                                        Log.d("ALARM_API", "서버 삭제 성공")
                                        alarmList = alarmList.filter { it.id != alarm.id }
                                    } else {
                                        Log.e("ALARM_API", "서버 삭제 실패 (이미 없거나 권한 부족)")
                                        // 서버에 없더라도 사용자 경험을 위해 로컬에서는 제거
                                        alarmList = alarmList.filter { it.id != alarm.id }
                                        Toast.makeText(context, "삭제되었습니다.", Toast.LENGTH_SHORT)
                                            .show()
                                    }
                                }
                                // 2. 서버 삭제가 필요 없는 경우 (기본 ko-KR 보이스 등)
                                else {
                                    Log.d("ALARM_API", "삭제되었습니다.")
                                    alarmList = alarmList.filter { it.id != alarm.id }
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

