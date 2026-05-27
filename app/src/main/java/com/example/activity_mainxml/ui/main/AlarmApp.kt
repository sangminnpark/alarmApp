package com.example.activity_mainxml.ui.main

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ViewHeadline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.activity_mainxml.R
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
    var isSimpleMode by remember { mutableStateOf(false) } 
    var selectedAlarmIds by remember { mutableStateOf(setOf<Int>()) } 
    var isMultiSelectActive by remember { mutableStateOf(false) } // 💡 선택 모드 활성화 상태 독립 관리
    val isSelectionMode = isMultiSelectActive

    // 💡 선택 모드일 때 뒤로가기 버튼 처리: 모드 종료
    BackHandler(enabled = isSelectionMode) {
        isMultiSelectActive = false
        selectedAlarmIds = emptySet()
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var alarmList by remember { mutableStateOf(initialAlarms) }
    val sortedAlarms = remember(alarmList) { alarmList.sortByTime() }
    
    var editingAlarm by remember { mutableStateOf<AlarmItem?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }
    var pendingDeleteAlarm by remember { mutableStateOf<AlarmItem?>(null) }
    var showMultiDeleteConfirmation by remember { mutableStateOf(false) } // 💡 다중 삭제 확인 팝업 상태

    val pureVoiceIdExtractor = remember {
        { rawId: String ->
            if (rawId.contains("eleven_")) {
                val idWithTimestamp = rawId.substringAfter("eleven_").substringBefore(".mp3")
                idWithTimestamp.replace(Regex("\\d{10,}$"), "")
            } else rawId
        }
    }

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

    if (showMultiDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showMultiDeleteConfirmation = false },
            title = { Text("다중 삭제") },
            text = { Text("선택한 ${selectedAlarmIds.size}개의 알람을 정말 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toDelete = alarmList.filter { it.id in selectedAlarmIds }
                        scope.launch {
                            toDelete.forEach { onDeleteAlarm(it) }
                            alarmList = alarmList.filter { it.id !in selectedAlarmIds }
                            selectedAlarmIds = emptySet()
                            isMultiSelectActive = false // 💡 삭제 후 모드 종료
                            Toast.makeText(context, "${toDelete.size}개의 알람이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                            showMultiDeleteConfirmation = false
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { showMultiDeleteConfirmation = false }) { Text("취소") } }
        )
    }

    LaunchedEffect(alarmList) { onSaveToDisk(alarmList) }

    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars), // 💡 상하단 바 겹침 방지
        topBar = { 
            CenterAlignedTopAppBar(
                title = { 
                    if (isSelectionMode) {
                        Text("${selectedAlarmIds.size}개 선택됨", style = MaterialTheme.typography.titleMedium)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_voice_wake_logo),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Voice Wake",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        // 💡 전체 선택 체크박스 + 텍스트
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedAlarmIds = if (selectedAlarmIds.size == alarmList.size) {
                                        emptySet()
                                    } else {
                                        alarmList.map { it.id }.toSet()
                                    }
                                }
                                .padding(horizontal = 4.dp)
                        ) {
                            Checkbox(
                                checked = selectedAlarmIds.size == alarmList.size && alarmList.isNotEmpty(),
                                onCheckedChange = null
                            )
                            Text(
                                text = "전체",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        IconButton(onClick = { showMultiDeleteConfirmation = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "다중 삭제", tint = Color.Red)
                        }
                        
                        IconButton(onClick = { 
                            selectedAlarmIds = emptySet() 
                            isMultiSelectActive = false 
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "취소")
                        }
                    } else {
                        // 💡 직관적인 텍스트 기반 모드 전환 버튼
                        TextButton(onClick = { isSimpleMode = !isSimpleMode }) {
                            Text(
                                text = if (isSimpleMode) "상세 보기" else "간단 보기",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            ) 
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FloatingActionButton(
                    onClick = { showVoiceRegistration = true }, 
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "목소리 등록")
                }
                FloatingActionButton(
                    onClick = { isAddingNew = true }, 
                    containerColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "알람 추가")
                }
            }
        }
    ) { padding ->
        // 💡 선택 모드 중에 리스트 외 화면(배경) 터치 시 모드 해제
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) {
                    if (isSelectionMode) {
                        isMultiSelectActive = false
                        selectedAlarmIds = emptySet()
                    }
                }
        ) {
            if (alarmList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "설정된 알람이 없습니다.", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = sortedAlarms, key = { it.id }) { alarm ->
                        AlarmRow(
                            alarm = alarm,
                            isSimpleMode = isSimpleMode,
                            isSelected = selectedAlarmIds.contains(alarm.id),
                            isSelectionMode = isSelectionMode,
                            onToggle = { isChecked ->
                                alarmList = alarmList.map { if (it.id == alarm.id) it.copy(isEnabled = isChecked) else it }
                                if (isChecked) {
                                    onSetAlarm(alarm.copy(isEnabled = true))
                                    Toast.makeText(context, "알람이 켜졌습니다.", Toast.LENGTH_SHORT).show()
                                } else {
                                    onCancelAlarm(alarm)
                                    Toast.makeText(context, "알람이 꺼졌습니다.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onClick = {
                                if (isSelectionMode) {
                                    selectedAlarmIds = if (selectedAlarmIds.contains(alarm.id)) {
                                        selectedAlarmIds - alarm.id
                                    } else {
                                        selectedAlarmIds + alarm.id
                                    }
                                } else {
                                    editingAlarm = alarm
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    isMultiSelectActive = true // 💡 롱클릭 시 모드 활성화
                                    selectedAlarmIds = setOf(alarm.id)
                                }
                            },
                            onDelete = { pendingDeleteAlarm = alarm }
                        )
                    }
                }
            }
        }
        
        // 💡 다이얼로그 오버레이 섹션
        if (showVoiceRegistration) {
            VoiceRegistrationDialog(
                onDismiss = { showVoiceRegistration = false },
                onGenerateVoice = { file, name ->
                    onGenerateVoice(file, "", name) { _ -> }
                    showVoiceRegistration = false
                }
            )
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
                onSave = { h, m, msg, days, vId, _, _, sound, vib ->
                    scope.launch {
                        try {
                            var localPath: String? = null
                            var finalVoiceId = vId
                            if (!vId.startsWith("ko-KR-")) {
                                val amPm = if (h < 12) "오전" else "오후"
                                val displayHour = if (h % 12 == 0) 12 else h % 12
                                val fullText = "현재 시간은 ${amPm} ${displayHour}시 ${m}분입니다. $msg"
                                
                                val audioFile = try {
                                    withContext(Dispatchers.IO) {
                                        VoiceRepository.makeElevenLabsVoiceFile(vId, fullText, context)
                                    }
                                } catch (e: Exception) { null }

                                if (audioFile != null && audioFile.exists()) {
                                    localPath = audioFile.absolutePath
                                    finalVoiceId = pureVoiceIdExtractor(audioFile.name)
                                } else {
                                    finalVoiceId = "ko-KR-Standard-A"
                                }
                            }
                            val currentAlarm = if (isAddingNew) {
                                AlarmItem(hour = h, minute = m, message = msg, repeatDays = days, voiceId = finalVoiceId, voiceName = finalVoiceId, localFilePath = localPath, isEnabled = true, isSoundEnabled = sound, isVibrationEnabled = vib)
                            } else {
                                val original = editingAlarm!!
                                if (original.isEnabled) onCancelAlarm(original)
                                original.copy(hour = h, minute = m, message = msg, repeatDays = days, voiceId = finalVoiceId, voiceName = finalVoiceId, localFilePath = localPath, isEnabled = original.isEnabled, isSoundEnabled = sound, isVibrationEnabled = vib)
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
        }
    }
}
