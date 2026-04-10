package com.example.activity_mainxml.ui.theme

import android.media.MediaPlayer
import android.util.Base64
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.activity_mainxml.BuildConfig
import com.example.activity_mainxml.model.AlarmItem
import com.example.activity_mainxml.model.TtsAudioConfig
import com.example.activity_mainxml.model.TtsInput
import com.example.activity_mainxml.model.TtsModel
import com.example.activity_mainxml.model.TtsVoice
import com.example.activity_mainxml.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar

@Composable
fun AlarmRow(
    alarm: AlarmItem,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dayLabels = listOf("월", "화", "수", "목", "금", "토", "일")
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (alarm.isEnabled) MaterialTheme.colorScheme.surfaceVariant else Color(
                0xFFE0E0E0
            )
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val amPm = if (alarm.hour < 12) "오전" else "오후"
                val displayHour = if (alarm.hour % 12 == 0) 12 else alarm.hour % 12
                Text(
                    "${amPm} ${displayHour}:${String.format("%02d", alarm.minute)}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (alarm.isEnabled) Color.Unspecified else Color.Gray
                )

                // 시각적 피드백: 요일이 없으면 '반복 없음' 표시
                if (alarm.repeatDays.isNotEmpty()) {
                    val daysText =
                        alarm.repeatDays.sorted().joinToString(", ") { dayLabels[it - 1] }
                    Text(
                        text = "반복: $daysText",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                } else {
                    Text(
                        text = "반복 요일 없음 (울리지 않음)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                }
                Text(
                    text = if (alarm.message.isBlank()) "보이스 시간 알림" else alarm.message,
                    color = if (alarm.isEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                )
            }
            Switch(checked = alarm.isEnabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    "삭제",
                    tint = Color.LightGray
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmApp(
    initialAlarms: List<AlarmItem>,
    onSetAlarm: (AlarmItem) -> Unit,
    onCancelAlarm: (AlarmItem) -> Unit,
    onSaveToDisk: (List<AlarmItem>) -> Unit
) {
    var alarmList by remember { mutableStateOf(initialAlarms) }
    var editingAlarm by remember { mutableStateOf<AlarmItem?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }

    LaunchedEffect(alarmList) { onSaveToDisk(alarmList) }

    if (isAddingNew || editingAlarm != null) {
        AlarmEditScreen(
            alarm = editingAlarm,
            onSave = { h, m, msg, days, vName ->
                if (isAddingNew) {
                    val newAlarm = AlarmItem(
                        hour = h,
                        minute = m,
                        message = msg,
                        repeatDays = days,
                        voiceName = vName
                    )
                    alarmList = alarmList + newAlarm
                    onSetAlarm(newAlarm)
                } else {
                    // [중요] 기존에 등록된 시스템 알람을 먼저 취소합니다.
                    onCancelAlarm(editingAlarm!!)

                    val updatedAlarm = editingAlarm!!.copy(
                        hour = h, minute = m, message = msg,
                        repeatDays = days, voiceName = vName, isEnabled = true
                    )
                    alarmList = alarmList.map { if (it.id == updatedAlarm.id) updatedAlarm else it }

                    // 새 설정으로 다시 등록합니다.
                    onSetAlarm(updatedAlarm)
                }
                isAddingNew = false
                editingAlarm = null
            },
            onCancel = { isAddingNew = false; editingAlarm = null }
        )
    } else {
        Scaffold(
            topBar = { CenterAlignedTopAppBar(title = { Text("내 보이스 알람") }) },
            floatingActionButton = {
                FloatingActionButton(onClick = { isAddingNew = true }) {
                    Icon(
                        Icons.Default.Add,
                        "추가"
                    )
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
                            val updated = alarm.copy(isEnabled = isChecked)
                            alarmList = alarmList.map { if (it.id == alarm.id) updated else it }
                            if (isChecked) onSetAlarm(updated) else onCancelAlarm(updated)
                        },
                        onClick = { editingAlarm = alarm },
                        onDelete = {
                            onCancelAlarm(alarm); alarmList = alarmList.filter { it.id != alarm.id }
                        })
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(
    alarm: AlarmItem?,
    onSave: (Int, Int, String, Set<Int>, String) -> Unit,
    onCancel: () -> Unit
) {
    // --- [추가] 미리듣기를 위한 상태 및 객체 ---
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    val API_KEY = BuildConfig.GOOGLE_TTS_API_KEY
    fun playPreview(vId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val previewText = "이 목소리로 설정합니다."
                val request = TtsModel(
                    input = TtsInput(previewText),
                    voice = TtsVoice("ko-KR", vId),
                    audioConfig = TtsAudioConfig()
                )
                val response = RetrofitClient.ttsService.synthesizeText(API_KEY, request)

                if (response.isSuccessful && response.body() != null) {
                    val audioBytes = Base64.decode(response.body()!!.audioContent, Base64.DEFAULT)
                    val tempFile = File.createTempFile("preview_", "mp3", context.cacheDir)
                    FileOutputStream(tempFile).use { it.write(audioBytes) }

                    withContext(Dispatchers.Main) {
                        mediaPlayer?.stop()
                        mediaPlayer?.release()
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(tempFile.absolutePath)
                            prepare()
                            start()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "미리듣기 재생 실패", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    var selectedVoiceId by remember { mutableStateOf<String?>(alarm?.voiceName) }
    var expanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    var amPmOffset by remember { mutableStateOf<Int?>(alarm?.let { if (it.hour < 12) 0 else 12 }) }
    var hour by remember {
        mutableIntStateOf(alarm?.let { if (it.hour % 12 == 0) 12 else it.hour % 12 } ?: 12)
    }
    var minute by remember { mutableIntStateOf(alarm?.minute ?: 0) }
    var message by remember { mutableStateOf(alarm?.message ?: "") }

    // 요일 상태 (MutableSet으로 관리하여 상태 변경이 확실히 반영되도록 함)
    var selectedDays by remember { mutableStateOf(alarm?.repeatDays ?: (1..7).toSet()) }
    val dayLabels = listOf("월", "화", "수", "목", "금", "토", "일")

    data class VoiceInfo(val id: String, val displayName: String, val gender: String)

    val googleVoices = listOf(
        VoiceInfo("ko-KR-Chirp3-HD-Achernar", "Achernar", "여성"),
        VoiceInfo("ko-KR-Chirp3-HD-Achird", "Achird", "남성"),
        VoiceInfo("ko-KR-Chirp3-HD-Algenib", "Algenib", "남성"),
        VoiceInfo("ko-KR-Chirp3-HD-Algieba", "Algieba", "남성"),
        VoiceInfo("ko-KR-Chirp3-HD-Alnilam", "Alnilam", "남성"),
        VoiceInfo("ko-KR-Chirp3-HD-Aoede", "Aoede", "여성"),
        VoiceInfo("ko-KR-Chirp3-HD-Autonoe", "Autonoe", "여성"),
        VoiceInfo("ko-KR-Chirp3-HD-Callirrhoe", "Callirrhoe", "여성"),
        VoiceInfo("ko-KR-Chirp3-HD-Charon", "Charon", "남성"),
        VoiceInfo("ko-KR-Chirp3-HD-Despina", "Despina", "여성"),
        VoiceInfo("ko-KR-Chirp3-HD-Enceladus", "Enceladus", "남성"),
        VoiceInfo("ko-KR-Chirp3-HD-Erinome", "Erinome", "여성"),
        VoiceInfo("ko-KR-Chirp3-HD-Fenrir", "Fenrir", "남성"),
        VoiceInfo("ko-KR-Chirp3-HD-Gacrux", "Gacrux", "여성"),
        VoiceInfo("ko-KR-Chirp3-HD-Iapetus", "Iapetus", "남성"),
        VoiceInfo("ko-KR-Chirp3-HD-Kore", "Kore", "여성"),
        VoiceInfo("ko-KR-Chirp3-HD-Laomedeia", "Laomedeia", "여성"),
        VoiceInfo("ko-KR-Chirp3-HD-Leda", "Leda", "여성"),
        VoiceInfo("ko-KR-Chirp3-HD-Orus", "Orus", "남성"),
        VoiceInfo("ko-KR-Chirp3-HD-Puck", "Puck", "남성"),
        VoiceInfo("ko-KR-Chirp3-HD-Pulcherrima", "Pulcherrima", "여성"),
        VoiceInfo("ko-KR-Chirp3-HD-Rasalgethi", "Rasalgethi", "남성"),
        VoiceInfo("ko-KR-Chirp3-HD-Sadachbia", "Sadachbia", "남성"),
        VoiceInfo("ko-KR-Chirp3-HD-Sadaltager", "Sadaltager", "남성"),
        VoiceInfo("ko-KR-Chirp3-HD-Schedar", "Schedar", "남성"),
        VoiceInfo("ko-KR-Chirp3-HD-Sulafat", "Sulafat", "여성"),
        VoiceInfo("ko-KR-Chirp3-HD-Umbriel", "Umbriel", "남성"),
        VoiceInfo("ko-KR-Chirp3-HD-Vindemiatrix", "Vindemiatrix", "여성"),
        VoiceInfo("ko-KR-Chirp3-HD-Zephyr", "Zephyr", "여성"),
        VoiceInfo("ko-KR-Chirp3-HD-Zubenelgenubi", "Zubenelgenubi", "남성"),
        VoiceInfo("ko-KR-Neural2-A", "Neural2-A", "여성"),
        VoiceInfo("ko-KR-Neural2-B", "Neural2-B", "여성"),
        VoiceInfo("ko-KR-Neural2-C", "Neural2-C", "남성"),
        VoiceInfo("ko-KR-Standard-A", "Standard-A", "여성"),
        VoiceInfo("ko-KR-Standard-B", "Standard-B", "여성"),
        VoiceInfo("ko-KR-Standard-C", "Standard-C", "남성"),
        VoiceInfo("ko-KR-Standard-D", "Standard-D", "남성"),
        VoiceInfo("ko-KR-Wavenet-A", "Wavenet-A", "여성"),
        VoiceInfo("ko-KR-Wavenet-B", "Wavenet-B", "여성"),
        VoiceInfo("ko-KR-Wavenet-C", "Wavenet-C", "남성"),
        VoiceInfo("ko-KR-Wavenet-D", "Wavenet-D", "남성")
    )
    val currentVoice = googleVoices.find { it.id == selectedVoiceId }

    // 화면이 닫힐 때 오디오 리소스 해제
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
        ) {
            Text(
                "알람 설정",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(20.dp))

            // 드롭다운 부분
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = currentVoice?.let { "${it.displayName} (${it.gender})" }
                        ?: "목소리를 선택하세요",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    label = { Text("목소리 선택 (필수)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    googleVoices.forEach { voice ->
                        DropdownMenuItem(
                            text = { Text("${voice.displayName} (${voice.gender})") },
                            onClick = {
                                selectedVoiceId = voice.id
                                expanded = false
                                playPreview(voice.id)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 오전/오후 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(0 to "오전", 12 to "오후").forEach { (offset, label) ->
                    Button(
                        onClick = { amPmOffset = offset; focusManager.clearFocus() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (amPmOffset == offset) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (amPmOffset == offset) Color.White else Color.Gray
                        )
                    ) { Text(label) }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 시간 입력 (TimeInputUnit 호출)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TimeInputUnit(
                    value = hour,
                    onValueChange = { hour = it },
                    range = 1..12,
                    label = "시"
                )
                Text(
                    ":",
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                TimeInputUnit(
                    value = minute,
                    onValueChange = { minute = it },
                    range = 0..59,
                    label = "분"
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 요일 선택 (버그 수정: clickable 사용)
            Text("반복 요일", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                dayLabels.forEachIndexed { index, label ->
                    // 1. 시스템 요일 숫자로 변환 (월요일 index 0 -> 시스템 2 / 일요일 index 6 -> 시스템 1)
                    val systemDayInt = when (index) {
                        6 -> Calendar.SUNDAY      // 일요일 (6 -> 1)
                        0 -> Calendar.MONDAY      // 월요일 (0 -> 2)
                        1 -> Calendar.TUESDAY     // 화요일 (1 -> 3)
                        2 -> Calendar.WEDNESDAY   // 수요일 (2 -> 4)
                        3 -> Calendar.THURSDAY    // 목요일 (3 -> 5)
                        4 -> Calendar.FRIDAY      // 금요일 (4 -> 6)
                        5 -> Calendar.SATURDAY    // 토요일 (5 -> 7)
                        else -> index + 2
                    }

                    val isSelected = selectedDays.contains(systemDayInt)

                    Surface(
                        onClick = {
                            // 2. 변환된 시스템 숫자를 Set에 넣거나 뺌
                            selectedDays = if (isSelected) {
                                selectedDays - systemDayInt
                            } else {
                                selectedDays + systemDayInt
                            }
                        },
                        modifier = Modifier.size(40.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isSelected) Color.White else Color.Black
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text(label, fontSize = 12.sp) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("보이스 메시지") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("취소") }
                Button(
                    onClick = {
                        if (amPmOffset == null) {
                            Toast.makeText(context, "오전/오후를 선택해주세요!", Toast.LENGTH_SHORT).show()
                        } else if (selectedVoiceId == null) {
                            Toast.makeText(context, "목소리를 선택해주세요!", Toast.LENGTH_SHORT).show()
                        } else {
                            onSave(
                                if (hour == 12) amPmOffset!! else amPmOffset!! + hour,
                                minute, message, selectedDays, selectedVoiceId!!
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    // enabled = true로 변경하여 클릭을 유도하고 메시지를 보여줌
                    enabled = true
                ) { Text("저장") }
            }
        }
    }
}

@Composable
fun TimeInputUnit(value: Int, onValueChange: (Int) -> Unit, range: IntRange, label: String) {
    var textValue by remember(value) { mutableStateOf(value.toString()) }
    val focusManager = LocalFocusManager.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = {
            val next =
                if (value < range.last) value + 1 else range.first; onValueChange(next); focusManager.clearFocus()
        }) { Icon(Icons.Default.KeyboardArrowUp, null, modifier = Modifier.size(32.dp)) }
        BasicTextField(
            value = textValue,
            onValueChange = {
                if (it.length <= 2 && it.all { c -> c.isDigit() }) {
                    textValue = it; it.toIntOrNull()?.let { n -> if (n in range) onValueChange(n) }
                }
            },
            modifier = Modifier.width(60.dp),
            textStyle = TextStyle(
                fontSize = 32.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            singleLine = true
        )
        IconButton(onClick = {
            val prev =
                if (value > range.first) value - 1 else range.last; onValueChange(prev); focusManager.clearFocus()
        }) { Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(32.dp)) }
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}