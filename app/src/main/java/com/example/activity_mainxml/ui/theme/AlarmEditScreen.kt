package com.example.activity_mainxml.ui.theme

import AlarmItem
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(
    alarm: AlarmItem?,
    customVoices: List<Triple<String, String, String>>,
    onDeleteVoice: (Triple<String, String, String>) -> Unit,
    onGenerateNewVoice: (File, String, String) -> Unit,
    onSave: (Int, Int, String, Set<Int>, String, File?, String?) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    var customVoiceName by remember { mutableStateOf("") }

    // --- [상태 관리] ---
    var recordedFile by remember { mutableStateOf<File?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    // 💡 저장 중 상태를 관리하는 변수 추가
    var isSaving by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val tempFile = copyUriToTempFile(context, it)
            recordedFile = tempFile
            Toast.makeText(context, "파일이 선택되었습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    var selectedVoiceId by remember { mutableStateOf<String?>(alarm?.voiceName) }
    var expanded by remember { mutableStateOf(false) }
    var amPmOffset by remember { mutableStateOf<Int?>(alarm?.let { if (it.hour < 12) 0 else 12 }) }
    var hour by remember {
        mutableIntStateOf(alarm?.let { if (it.hour % 12 == 0) 12 else it.hour % 12 } ?: 12)
    }
    var minute by remember { mutableIntStateOf(alarm?.minute ?: 0) }
    var message by remember { mutableStateOf(alarm?.message ?: "") }
    var selectedDays by remember { mutableStateOf(alarm?.repeatDays ?: (1..7).toSet()) }

    val API_KEY = BuildConfig.GOOGLE_API_KEY
    val dayLabels = listOf("월", "화", "수", "목", "금", "토", "일")

    data class VoiceInfo(
        val id: String,
        val displayName: String,
        val gender: String = "",
        val isCustom: Boolean = false,
        val previewPath: String? = null
    )

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
    val allVoices = googleVoices + customVoices.map {
        VoiceInfo(id = it.second, displayName = it.first, isCustom = true, previewPath = it.third)
    }
    val currentVoice = allVoices.find { it.id == selectedVoiceId }

    // --- [미리듣기 로직] ---
    // AlarmEditScreen.kt 내부의 playPreview 함수 수정
    fun playPreview(voice: VoiceInfo) {
        // 💡 Dispatchers.IO를 유지하되, 커스텀 보이스는 파일 로딩만 하므로 매우 빨라집니다.
        scope.launch(Dispatchers.IO) {
            try {
                if (voice.isCustom) {
                    // 💡 [핵심 수정] 서버에 가지 않고, 미리 저장된 previewPath 파일을 재생합니다.
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
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "미리보기 파일을 찾을 수 없습니다.", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                } else {
                    // 🌐 구글 보이스일 경우 (기존 로직 유지)
                    val previewText = "이 목소리를 선택합니다."
                    val request = TtsModel(
                        input = TtsInput(previewText),
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
                Log.e("PLAY_PREVIEW", "에러 발생: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "미리보기 재생 실패", Toast.LENGTH_SHORT).show()
                }
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

            // --- [1. 목소리 선택 드롭다운] ---
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
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    allVoices.forEach { voice ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 목소리 이름 표시
                                    Text(if (voice.isCustom) "🎙 ${voice.displayName}" else "🌐 ${voice.displayName} (${voice.gender})")

                                    // 💡 커스텀 보이스일 때만 삭제 아이콘 표시
                                    if (voice.isCustom) {
                                        IconButton(
                                            onClick = {
                                                // voice.id(경로)와 displayName을 이용해 삭제 실행
                                                onDeleteVoice(
                                                    Triple(
                                                        voice.displayName,
                                                        voice.id,
                                                        voice.previewPath ?: ""
                                                    )
                                                )
                                                expanded = false // 메뉴 닫기
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "삭제",
                                                tint = Color.Red,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            onClick = {
                                selectedVoiceId = voice.id
                                expanded = false
                                playPreview(voice)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- [2. 파일 업로드 섹션 (녹음 대신)] ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "내 목소리 등록 (ElevenLabs)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // 1. 생성할 보이스 이름 입력
                    OutlinedTextField(
                        value = customVoiceName,
                        onValueChange = { customVoiceName = it },
                        label = { Text("목소리 이름 (예: 엄마 목소리)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. 파일 선택 및 생성 버튼 레이아웃
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 파일 선택 버튼
                        OutlinedButton(
                            onClick = { filePickerLauncher.launch("audio/*") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Upload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (recordedFile == null) "음성 파일 선택" else "파일 변경")
                        }

                        // 생성 버튼 (파일이 있을 때만 활성화)
                        Button(
                            onClick = {
                                if (customVoiceName.isBlank()) {
                                    Toast.makeText(context, "보이스 이름을 입력해주세요.", Toast.LENGTH_SHORT)
                                        .show()
                                } else {
                                    // 💡 promptText를 빈 문자열("")로 넘깁니다.
                                    onGenerateNewVoice(recordedFile!!, "", customVoiceName)
                                }
                            },
                            enabled = recordedFile != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("보이스 생성")
                        }
                    }

                    // 3. 선택된 파일 정보 표시
                    if (recordedFile != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "📎 ${recordedFile!!.name}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { recordedFile = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    "삭제",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- 시간 및 요일 설정 부분 (기존과 동일) ---
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
            Spacer(modifier = Modifier.height(16.dp))
            Text("반복 요일", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                dayLabels.forEachIndexed { index, label ->
                    val systemDayInt = when (index) {
                        6 -> 1; else -> index + 2
                    }
                    val isSelected = selectedDays.contains(systemDayInt)
                    Surface(
                        onClick = {
                            selectedDays =
                                if (isSelected) selectedDays - systemDayInt else selectedDays + systemDayInt
                        },
                        modifier = Modifier.size(40.dp), shape = CircleShape,
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
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    enabled = !isSaving // 저장 중에는 취소도 일단 막음 (데이터 무결성)
                ) { Text("취소") }

                Button(
                    onClick = {
                        if (isSaving) return@Button // 이미 저장 중이면 무시

                        if (amPmOffset == null) {
                            Toast.makeText(context, "오전/오후 선택!", Toast.LENGTH_SHORT).show()
                        } else if (selectedVoiceId == null) {
                            Toast.makeText(context, "목소리 선택!", Toast.LENGTH_SHORT).show()
                        } else {
                            // 💡 1. 즉시 저장 상태로 변경 (연타 방지)
                            isSaving = true
                            Log.d("ALARM_DEBUG", "저장 프로세스 시작 (isSaving=true)")

                            val finalHour = if (hour == 12) amPmOffset!! else amPmOffset!! + hour

                            // 💡 2. 상위 AlarmApp으로 데이터 전달
                            // (성공 시 AlarmApp에서 화면을 닫으므로 다시 false로 바꿀 필요 없음)
                            // (만약 실패 시를 대비하려면 onSave 내부 try-catch에서 isSaving = false를 해줘야 함)
                            onSave(
                                finalHour,
                                minute,
                                message,
                                selectedDays,
                                selectedVoiceId!!,
                                null,
                                null
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isSaving // 저장 중 버튼 비활성화
                ) {
                    if (isSaving) {
                        // 💡 3. 저장 중일 때 버튼 안에 로딩 표시
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("저장 중...")
                        }
                    } else {
                        Text("저장")
                    }
                }
            }
        }
    }
}

// 💡 Uri를 File로 변환해주는 유틸리티 함수 (파일 하단에 추가)
fun copyUriToTempFile(context: Context, uri: Uri): File {
    val inputStream = context.contentResolver.openInputStream(uri)
    val tempFile = File(context.cacheDir, "temp_audio_${System.currentTimeMillis()}.wav")
    inputStream?.use { input ->
        tempFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }
    return tempFile
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