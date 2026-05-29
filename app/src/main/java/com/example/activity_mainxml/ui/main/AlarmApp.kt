package com.example.activity_mainxml.ui.main

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.unit.sp
import com.example.activity_mainxml.R
import com.example.activity_mainxml.data.VoiceRepository
import com.example.activity_mainxml.model.AlarmItem
import com.example.activity_mainxml.ui.edit.AlarmEditScreen
import com.example.activity_mainxml.ui.edit.VoiceRegistrationDialog
import com.example.activity_mainxml.ui.theme.VoiceWakeTheme
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
    onSaveToDisk: (List<AlarmItem>) -> Unit,
    currentThemeMode: String,
    onThemeChange: (String) -> Unit,
    currentListMode: String,
    onListModeChange: (String) -> Unit,
    currentUiScale: String,
    onUiScaleChange: (String) -> Unit
) {
    var showVoiceRegistration by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val isSimpleMode = currentListMode == "simple"
    var selectedAlarmIds by remember { mutableStateOf(setOf<Int>()) } 
    var isMultiSelectActive by remember { mutableStateOf(false) }
    val isSelectionMode = isMultiSelectActive

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
    var showMultiDeleteConfirmation by remember { mutableStateOf(false) }

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
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
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
                            isMultiSelectActive = false
                            Toast.makeText(context, "${toDelete.size}개의 알람이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                            showMultiDeleteConfirmation = false
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { showMultiDeleteConfirmation = false }) { Text("취소") } }
        )
    }

    LaunchedEffect(alarmList) { onSaveToDisk(alarmList) }

    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),
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
                            Icon(Icons.Default.Delete, contentDescription = "다중 삭제", tint = MaterialTheme.colorScheme.error)
                        }
                        
                        IconButton(onClick = { 
                            selectedAlarmIds = emptySet() 
                            isMultiSelectActive = false 
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "취소")
                        }
                    } else {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "설정",
                                tint = MaterialTheme.colorScheme.primary
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
                    Text(text = "설정된 알람이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items = sortedAlarms, key = { it.id }) { alarm ->
                        AlarmRow(
                            alarm = alarm,
                            isSimpleMode = isSimpleMode,
                            uiScale = currentUiScale,
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
                                    isMultiSelectActive = true
                                    selectedAlarmIds = setOf(alarm.id)
                                }
                            },
                            onDelete = { pendingDeleteAlarm = alarm }
                        )
                    }
                }
            }
        }
        
        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                title = { Text("앱 설정", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // 1. 표시 모드 설정
                        Column {
                            Text("알람 리스트 표시", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            listOf("detailed" to "자세히 보기", "simple" to "간단히 보기").forEach { (mode, label) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().clickable { onListModeChange(mode) }
                                ) {
                                    RadioButton(selected = currentListMode == mode, onClick = { onListModeChange(mode) })
                                    Text(label)
                                }
                            }
                        }
                        
                        HorizontalDivider()

                        // 2. 크기 설정
                        Column {
                            Text("글자 및 아이콘 크기", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            listOf("small" to "작게", "normal" to "보통", "large" to "크게").forEach { (scale, label) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().clickable { onUiScaleChange(scale) }
                                ) {
                                    RadioButton(selected = currentUiScale == scale, onClick = { onUiScaleChange(scale) })
                                    Text(label)
                                }
                            }
                        }

                        HorizontalDivider()

                        // 3. 테마 설정
                        Column {
                            Text("테마 설정", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(8.dp))
                            listOf("system" to "시스템 설정", "light" to "라이트 모드", "dark" to "다크 모드").forEach { (mode, label) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().clickable { onThemeChange(mode) }
                                ) {
                                    RadioButton(selected = currentThemeMode == mode, onClick = { onThemeChange(mode) })
                                    Text(label)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettings = false }) { Text("확인") }
                }
            )
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

        if (isAddingNew || editingAlarm != null) {
            key(editingAlarm?.id ?: "new") {
                AlarmEditScreen(
                    alarm = editingAlarm,
                    customVoices = customVoices,
                    onDeleteVoice = { voiceTriple ->
                        scope.launch {
                            VoiceRepository.deleteElevenLabsVoice(voiceTriple.second)
                            onDeleteVoice(voiceTriple)
                        }
                    },
                    onSave = { h, m, msg, days, vId, _, _, sound, vib, fadeIn, excludeHolidays ->
                        scope.launch {
                            try {
                                val original = editingAlarm
                                var localPath: String? = original?.localFilePath
                                var finalVoiceId = vId

                                val needsRegeneration = isAddingNew || 
                                    original == null ||
                                    original.hour != h || 
                                    original.minute != m || 
                                    original.message != msg || 
                                    original.voiceId != vId

                                if (needsRegeneration && !vId.startsWith("ko-KR-")) {
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
                                    } else if (isAddingNew) {
                                        finalVoiceId = "ko-KR-Standard-A"
                                        localPath = null
                                    } else {
                                        finalVoiceId = original?.voiceId ?: "ko-KR-Standard-A"
                                        localPath = original?.localFilePath
                                    }
                                }
                                
                                val currentAlarm = if (isAddingNew) {
                                    AlarmItem(
                                        hour = h,
                                        minute = m,
                                        message = msg,
                                        repeatDays = days,
                                        voiceId = finalVoiceId,
                                        voiceName = finalVoiceId,
                                        localFilePath = localPath,
                                        isEnabled = true,
                                        isSoundEnabled = sound,
                                        isVibrationEnabled = vib,
                                        fadeInDurationSeconds = fadeIn,
                                        isExcludeHolidays = excludeHolidays
                                    )
                                } else {
                                    original!!.copy(
                                        hour = h,
                                        minute = m,
                                        message = msg,
                                        repeatDays = days,
                                        voiceId = finalVoiceId,
                                        voiceName = finalVoiceId,
                                        localFilePath = localPath,
                                        isSoundEnabled = sound,
                                        isVibrationEnabled = vib,
                                        fadeInDurationSeconds = fadeIn,
                                        isExcludeHolidays = excludeHolidays
                                    )
                                }

                                val newList = alarmList.toMutableList()
                                if (isAddingNew) {
                                    newList.add(currentAlarm)
                                } else {
                                    val index = newList.indexOfFirst { it.id == currentAlarm.id }
                                    if (index != -1) {
                                        newList[index] = currentAlarm
                                    }
                                }
                                
                                alarmList = newList.sortByTime()

                                if (currentAlarm.isEnabled) onSetAlarm(currentAlarm)
                                
                                Toast.makeText(context, "알람 설정이 저장되었습니다.", Toast.LENGTH_SHORT).show()
                                isAddingNew = false
                                editingAlarm = null
                            } catch (e: Exception) { }
                        }
                    },
                    onCancel = { isAddingNew = false; editingAlarm = null }
                )
            }
        }
    }
}
