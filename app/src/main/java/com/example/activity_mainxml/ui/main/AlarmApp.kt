package com.example.activity_mainxml.ui.main

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.activity_mainxml.data.VoiceRepository
import com.example.activity_mainxml.model.AlarmItem
import com.example.activity_mainxml.ui.edit.AlarmEditScreen
import com.example.activity_mainxml.ui.edit.VoiceRegistrationDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

fun List<AlarmItem>.sortByTime(): List<AlarmItem> {
    return this.sortedWith(compareBy<AlarmItem> { it.hour }.thenBy { it.minute })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmApp(
    initialAlarms: List<AlarmItem>,
    onSetAlarm: (AlarmItem) -> Unit,
    customVoices: List<Triple<String, String, String>>,
    onGenerateVoice: (File, String, String, (String) -> Unit) -> Unit,
    onCancelAlarm: (AlarmItem) -> Unit,
    onDeleteAlarm: (AlarmItem) -> Unit,
    onDeleteVoice: (Triple<String, String, String>) -> Unit,
    onSaveToDisk: (List<AlarmItem>) -> Unit
) {
    var showVoiceRegistration by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var alarmList by remember { mutableStateOf(initialAlarms.sortByTime()) }
    var editingAlarm by remember { mutableStateOf<AlarmItem?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }
    var pendingDeleteAlarm by remember { mutableStateOf<AlarmItem?>(null) }

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
                            onDeleteAlarm(alarm)
                            alarmList = alarmList.filter { it.id != alarm.id }
                            Toast.makeText(context, "삭제되었습니다.", Toast.LENGTH_SHORT).show()
                            pendingDeleteAlarm = null
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteAlarm = null }) { Text("취소") } }
        )
    }

    LaunchedEffect(alarmList) { onSaveToDisk(alarmList) }

    val pureVoiceIdExtractor = remember {
        { rawId: String ->
            if (rawId.contains("eleven_")) {
                val idWithTimestamp = rawId.substringAfter("eleven_").substringBefore(".mp3")
                idWithTimestamp.replace(Regex("\\d{10,}$"), "")
            } else rawId
        }
    }

    if (isAddingNew || editingAlarm != null) {
        AlarmEditScreen(
            alarm = editingAlarm,
            customVoices = customVoices,
            onDeleteVoice = { voiceTriple ->
                scope.launch {
                    VoiceRepository.deleteElevenLabsVoice(voiceTriple.second)
                    onDeleteVoice(voiceTriple)
                }
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
                                finalVoiceId = pureVoiceIdExtractor(audioFile.name)
                            }
                        }
                        val currentAlarm = if (isAddingNew) {
                            AlarmItem(hour = h, minute = m, message = msg, repeatDays = days, voiceName = finalVoiceId, localFilePath = localPath, isEnabled = true)
                        } else {
                            val original = editingAlarm!!
                            if (original.isEnabled) onCancelAlarm(original)
                            original.copy(hour = h, minute = m, message = msg, repeatDays = days, voiceName = finalVoiceId, localFilePath = localPath, isEnabled = original.isEnabled)
                        }
                        alarmList = (if (isAddingNew) alarmList + currentAlarm else alarmList.map { if (it.id == currentAlarm.id) currentAlarm else it }).sortByTime()
                        if (currentAlarm.isEnabled) onSetAlarm(currentAlarm)
                        isAddingNew = false
                        editingAlarm = null
                    } catch (e: Exception) { Log.e("ALARM_SAVE", "${e.message}") }
                }
            },
            onCancel = { isAddingNew = false; editingAlarm = null }
        )
    } else {
        Scaffold(
            topBar = { CenterAlignedTopAppBar(title = { Text("내 보이스 알람") }) },
            floatingActionButton = {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FloatingActionButton(onClick = { showVoiceRegistration = true }, containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                        Icon(Icons.Default.Mic, contentDescription = "목소리 등록")
                    }
                    FloatingActionButton(onClick = { isAddingNew = true }, containerColor = MaterialTheme.colorScheme.primary) {
                        Icon(Icons.Default.Add, contentDescription = "알람 추가")
                    }
                }
            }
        ) { padding ->
            if (alarmList.isEmpty()) {
                Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "설정된 알람이 없습니다.", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                    items(items = alarmList, key = { it.id }) { alarm ->
                        AlarmRow(
                            alarm = alarm,
                            onToggle = { isChecked ->
                                alarmList = alarmList.map { if (it.id == alarm.id) it.copy(isEnabled = isChecked) else it }.sortByTime()
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
