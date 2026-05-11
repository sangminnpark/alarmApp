package com.example.activity_mainxml.ui.theme

import AlarmItem
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Base64
import android.util.Log
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.activity_mainxml.BuildConfig
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(
    alarm: AlarmItem?,
    customVoices: List<Triple<String, String, String>>,
    onDeleteVoice: (Triple<String, String, String>) -> Unit,
    onSave: (Int, Int, String, Set<Int>, String, File?, String?) -> Unit,
    onCancel: () -> Unit
) {
    BackHandler {
        onCancel()
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val calendar = remember { Calendar.getInstance() }
    val currentHour24 = calendar.get(Calendar.HOUR_OF_DAY)

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    var selectedVoiceId by remember { mutableStateOf<String?>(alarm?.voiceName) }
    var expanded by remember { mutableStateOf(false) }
    var amPmOffset by remember {
        mutableStateOf<Int?>(alarm?.let { if (it.hour < 12) 0 else 12 }
            ?: if (currentHour24 < 12) 0 else 12)
    }
    var hour by remember {
        mutableIntStateOf(
            alarm?.let { if (it.hour % 12 == 0) 12 else it.hour % 12 }
                ?: (if (currentHour24 % 12 == 0) 12 else currentHour24 % 12)
        )
    }
    var minute by remember {
        mutableIntStateOf(alarm?.minute ?: calendar.get(Calendar.MINUTE))
    }
    var message by remember { mutableStateOf(alarm?.message ?: "") }
    var selectedDays by remember { mutableStateOf(alarm?.repeatDays ?: (1..7).toSet()) }

    val API_KEY = BuildConfig.GOOGLE_API_KEY
    val dayLabels = listOf("월", "화", "수", "목", "금", "토", "일")

    // VoiceInfo 데이터 클래스 정의
    data class VoiceInfo(
        val id: String,
        val displayName: String,
        val gender: String = "",
        val isCustom: Boolean = false,
        val previewPath: String? = null
    )

    val googleVoices = listOf(
        VoiceInfo("ko-KR-Standard-A", "Standard-A", "여성"),
        VoiceInfo("ko-KR-Standard-B", "Standard-B", "여성"),
        VoiceInfo("ko-KR-Standard-C", "Standard-C", "남성"),
        VoiceInfo("ko-KR-Standard-D", "Standard-D", "남성"),
        VoiceInfo("ko-KR-Wavenet-A", "Wavenet-A", "여성"),
        VoiceInfo("ko-KR-Wavenet-B", "Wavenet-B", "여성"),
        VoiceInfo("ko-KR-Wavenet-C", "Wavenet-C", "남성"),
        VoiceInfo("ko-KR-Wavenet-D", "Wavenet-D", "남성")
    )
    val allVoices = googleVoices + customVoices.map {
        VoiceInfo(id = it.second, displayName = it.first, isCustom = true, previewPath = it.third)
    }
    val currentVoice = allVoices.find { it.id == selectedVoiceId }

    fun playPreview(voice: VoiceInfo) {
        scope.launch(Dispatchers.IO) {
            try {
                if (voice.isCustom) {
                    val previewFile = voice.previewPath?.let { File(it) }
                    if (previewFile != null && previewFile.exists()) {
                        withContext(Dispatchers.Main) {
                            mediaPlayer?.stop()
                            mediaPlayer?.release()
                            mediaPlayer = MediaPlayer().apply {
                                setDataSource(previewFile.absolutePath)
                                prepare()
                                start()
                            }
                        }
                    }
                } else {
                    val request = TtsModel(
                        input = TtsInput("이 목소리를 선택합니다."),
                        voice = TtsVoice("ko-KR", voice.id),
                        audioConfig = TtsAudioConfig()
                    )
                    val response = RetrofitClient.googleTtsService.synthesizeText(API_KEY, request)
                    if (response.isSuccessful && response.body() != null) {
                        val audioBytes =
                            Base64.decode(response.body()!!.audioContent, Base64.DEFAULT)
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
                }
            } catch (e: Exception) {
                Log.e("PLAY_PREVIEW", "${e.message}")
            }
        }
    }

    DisposableEffect(Unit) { onDispose { mediaPlayer?.release(); mediaPlayer = null } }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(scrollState)
                .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }) {

            Text(
                "알람 설정",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(20.dp))

            // 목소리 선택 드롭다운
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = currentVoice?.let { if (it.isCustom) "[내 목소리] ${it.displayName}" else "${it.displayName} (${it.gender})" }
                        ?: "목소리를 선택하세요",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    label = { Text("목소리 선택 (필수)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) })
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    allVoices.forEach { voice ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween // 텍스트와 아이콘 양끝 배치
                                ) {
                                    Text(if (voice.isCustom) "🎙 ${voice.displayName}" else "🌐 ${voice.displayName} (${voice.gender})")

                                    // ✨ 커스텀 목소리인 경우에만 삭제 아이콘 표시
                                    if (voice.isCustom) {
                                        IconButton(
                                            onClick = {
                                                // customVoices 리스트에서 해당 보이스를 찾아 onDeleteVoice 호출
                                                val target = customVoices.find { it.second == voice.id }
                                                target?.let { onDeleteVoice(it) }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete, // 상단에 Icons.Default.Delete 임포트 필요
                                                contentDescription = "삭제",
                                                tint = Color.Red,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            onClick = {
                                selectedVoiceId = voice.id
                                expanded = false
                                playPreview(voice)
                            })
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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

            Spacer(modifier = Modifier.height(16.dp))

            // 시간 입력 부분 (수정됨)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TimeInputUnit(
                    value = hour,
                    onValueChange = { hour = it },
                    range = 1..12,
                    label = "시",
                    isMinute = false
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
                    label = "분",
                    isMinute = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            // ... (요일, 메시지, 저장 버튼 등 나머지 코드는 기존과 동일) ...
            Text("반복 요일", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                dayLabels.forEachIndexed { index, label ->
                    val systemDayInt = if (index == 6) 1 else index + 2
                    val isSelected = selectedDays.contains(systemDayInt)
                    Surface(
                        onClick = {
                            selectedDays =
                                if (isSelected) selectedDays - systemDayInt else selectedDays + systemDayInt
                        },
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isSelected) Color.White else Color.Black
                    ) { Box(contentAlignment = Alignment.Center) { Text(label, fontSize = 12.sp) } }
                }
            }
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
                OutlinedButton(
                    onClick = onCancel, modifier = Modifier.weight(1f), enabled = !isSaving
                ) { Text("취소") }
                Button(
                    onClick = {
                        // 1. 중복 클릭 방지
                        if (isSaving) return@Button

                        // 2. 필수 선택 사항 체크 (목소리)
                        if (selectedVoiceId == null) {
                            Toast.makeText(context, "알람 목소리를 선택해주세요.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        // 3. 필수 선택 사항 체크 (오전/오후)
                        if (amPmOffset == null) {
                            Toast.makeText(context, "오전 또는 오후를 선택해주세요.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        // 4. 모든 조건 만족 시 저장 진행
                        isSaving = true
                        val finalHour = if (hour == 12) amPmOffset!! else amPmOffset!! + hour
                        onSave(
                            finalHour, minute, message, selectedDays, selectedVoiceId!!, null, null
                        )
                    }, modifier = Modifier.weight(1f), enabled = !isSaving
                ) { Text(if (isSaving) "저장 중..." else "저장") }
            }
        }
    }
}

@Composable
fun TimeInputUnit(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    label: String,
    isMinute: Boolean = false
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var isTyping by remember { mutableStateOf(false) }

    // 화면 표시용 텍스트 상태
    var textFieldValue by remember {
        mutableStateOf(if (isMinute) String.format("%02d", value) else value.toString())
    }

    // 외부 값 변경 감지 (화살표 버튼 등)
    LaunchedEffect(value) {
        if (!isTyping) {
            textFieldValue = if (isMinute) String.format("%02d", value) else value.toString()
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = {
            isTyping = false
            val next = if (value < range.last) value + 1 else range.first
            onValueChange(next)
            focusManager.clearFocus()
        }) { Icon(Icons.Default.KeyboardArrowUp, null, modifier = Modifier.size(32.dp)) }

        BasicTextField(
            value = textFieldValue, onValueChange = { input ->
                if (input.all { it.isDigit() } && input.length <= 2) {
                    if (input.isEmpty()) {
                        isTyping = true
                        textFieldValue = ""
                        return@BasicTextField
                    }

                    val intVal = input.toInt()

                    // 1. 유효 범위 내의 입력 (0~59)
                    if (intVal in range) {
                        isTyping = true
                        textFieldValue = input
                        onValueChange(intVal)
                    }
                    // 2. 범위 초과 시 (60~99)
                    else {
                        // 토스트 메시지 출력
                        Toast.makeText(
                            context, "${range.last}${label} 이하로 설정해주세요.", Toast.LENGTH_SHORT
                        ).show()

                        // 💡 잘못된 입력 시 00(또는 범위 시작값)으로 되돌리기
                        isTyping = false
                        val resetValue = range.first
                        onValueChange(resetValue)
                        textFieldValue = if (isMinute) String.format(
                            "%02d", resetValue
                        ) else resetValue.toString()

                        // 입력이 틀렸으므로 포커스 해제 (선택 사항)
                        focusManager.clearFocus()
                    }
                }
            }, modifier = Modifier
                .width(60.dp)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        isTyping = false
                        textFieldValue =
                            if (isMinute) String.format("%02d", value) else value.toString()
                    }
                }, textStyle = TextStyle(
                fontSize = 32.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            ), keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number, imeAction = ImeAction.Done
            ), keyboardActions = KeyboardActions(onDone = {
                isTyping = false
                textFieldValue = if (isMinute) String.format("%02d", value) else value.toString()
                focusManager.clearFocus()
            }), singleLine = true
        )

        IconButton(onClick = {
            isTyping = false
            val prev = if (value > range.first) value - 1 else range.last
            onValueChange(prev)
            focusManager.clearFocus()
        }) { Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(32.dp)) }

        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

fun copyUriToTempFile(context: Context, uri: Uri): File {
    val inputStream = context.contentResolver.openInputStream(uri)
    val tempFile = File(context.cacheDir, "temp_audio_${System.currentTimeMillis()}.wav")
    inputStream?.use { input -> tempFile.outputStream().use { output -> input.copyTo(output) } }
    return tempFile
}