package com.example.activity_mainxml.ui.theme

import AlarmItem
import VoiceRegistrationDialog
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.activity_mainxml.network.VoiceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// 1. ✨ 확장 함수는 컴포저블 '밖'에 딱 하나만 정의합니다. (Type Mismatch 해결 핵심)
fun List<AlarmItem>.sortByTime(): List<AlarmItem> {
    return this.sortedWith(
        compareBy<AlarmItem> { it.hour }
            .thenBy { it.minute }
    )
}

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
    var showVoiceRegistration by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var alarmList by remember { mutableStateOf(initialAlarms.sortByTime()) }
    var editingAlarm by remember { mutableStateOf<AlarmItem?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }

    // 💡 삭제 대기 상태
    var pendingDeleteAlarm by remember { mutableStateOf<AlarmItem?>(null) }

    // 💡 1. 삭제 확인 다이얼로그
    if (pendingDeleteAlarm != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteAlarm = null },
            title = { Text("알람 삭제") },
            text = { Text("이 알람을 정말 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val alarm = pendingDeleteAlarm!!
                        scope.launch {
                            onCancelAlarm(alarm)
                            alarmList = alarmList.filter { it.id != alarm.id }
                            Toast.makeText(context, "삭제되었습니다.", Toast.LENGTH_SHORT).show()
                            pendingDeleteAlarm = null
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteAlarm = null }) { Text("취소") }
            }
        )
    }

    LaunchedEffect(alarmList) { onSaveToDisk(alarmList) }

    // ID 정제용 함수
    fun extractPureVoiceId(rawId: String): String {
        return if (rawId.contains("eleven_")) {
            val idWithTimestamp = rawId.substringAfter("eleven_").substringBefore(".mp3")
            idWithTimestamp.replace(Regex("\\d{10,}\$"), "")
        } else {
            rawId
        }
    }

    // 💡 2. 화면 전환 로직 (수정/추가 화면 vs 리스트 화면)
    if (isAddingNew || editingAlarm != null) {
        AlarmEditScreen(
            alarm = editingAlarm,
            customVoices = customVoices,
            onDeleteVoice = { voiceTriple ->
                val pureId = extractPureVoiceId(voiceTriple.third)
                scope.launch {
                    VoiceRepository.deleteElevenLabsVoice(pureId)
                    onDeleteVoice(voiceTriple)
                }
            },
            onGenerateNewVoice = { file, promptText, customName ->
                onGenerateVoice(file, promptText, customName) { _ -> }
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
                        alarmList =
                            (if (isAddingNew) alarmList + currentAlarm else alarmList.map { if (it.id == currentAlarm.id) currentAlarm else it }).sortByTime()
                        onSetAlarm(currentAlarm)
                        isAddingNew = false
                        editingAlarm = null
                    } catch (e: Exception) {
                        Log.e("ALARM_SAVE", "${e.message}")
                    }
                }
            },
            onCancel = { isAddingNew = false; editingAlarm = null }
        )
    } else {
        Scaffold(
            topBar = { CenterAlignedTopAppBar(title = { Text("내 보이스 알람") }) },
            floatingActionButton = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FloatingActionButton(
                        onClick = { showVoiceRegistration = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "목소리 등록")
                    }
                    FloatingActionButton(
                        onClick = { isAddingNew = true },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "알람 추가")
                    }
                }
            }
        ) { padding ->
            // 💡 3. 리스트 또는 빈 화면 처리
            if (alarmList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "설정된 알람이 없습니다.", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()) {
                    // AlarmApp 내부의 LazyColumn 부분
                    items(alarmList) { alarm ->
                        AlarmRow(
                            alarm = alarm,
                            onToggle = { isChecked ->
                                // 1. UI 상태 변경
                                alarmList = alarmList.map {
                                    if (it.id == alarm.id) it.copy(isEnabled = isChecked) else it
                                }.sortByTime()

                                // 2. ✨ 실제 시스템 제어 로직 (여기 딱 한 군데만 있으면 됩니다!)
                                if (isChecked) {
                                    onSetAlarm(alarm.copy(isEnabled = true))
                                    Toast.makeText(context, "알람이 켜졌습니다.", Toast.LENGTH_SHORT).show()
                                } else {
                                    onCancelAlarm(alarm)
                                    Toast.makeText(context, "알람이 꺼졌습니다.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onClick = { editingAlarm = alarm },
                            onDelete = { pendingDeleteAlarm = alarm }
                        )
                    }
                }
            }

            if (showVoiceRegistration) {
                VoiceRegistrationDialog(
                    onDismiss = { showVoiceRegistration = false },
                    onGenerateVoice = { file, name ->
                        onGenerateVoice(file, "", name) { _ -> }
                        showVoiceRegistration = false
                    }
                )
            }
        }
    }
}