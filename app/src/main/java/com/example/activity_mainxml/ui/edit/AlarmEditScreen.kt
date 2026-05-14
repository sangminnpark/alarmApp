package com.example.activity_mainxml.ui.edit

import com.example.activity_mainxml.model.AlarmItem
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.example.activity_mainxml.BuildConfig
import com.example.activity_mainxml.data.remote.RetrofitClient
import com.example.activity_mainxml.model.TtsAudioConfig
import com.example.activity_mainxml.model.TtsInput
import com.example.activity_mainxml.model.TtsModel
import com.example.activity_mainxml.model.TtsVoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlarmEditScreen(
    alarm: AlarmItem?,
    customVoices: List<Triple<String, String, String>>,
    onDeleteVoice: (Triple<String, String, String>) -> Unit,
    onSave: (Int, Int, String, Set<Int>, String, File?, String?, Boolean, Boolean) -> Unit,
    onCancel: () -> Unit
) {
    BackHandler { onCancel() }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val view = LocalView.current
    
    // 💡 키보드 상태 감지
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0 || WindowInsets.isImeVisible

    // 💡 다이얼로그 전용 윈도우 설정
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window
        window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
    }

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

    var isSoundEnabled by remember { mutableStateOf(alarm?.isSoundEnabled ?: true) }
    var isVibrationEnabled by remember { mutableStateOf(alarm?.isVibrationEnabled ?: true) }

    val API_KEY = BuildConfig.GOOGLE_API_KEY

    data class VoiceInfo(
        val id: String,
        val displayName: String,
        val gender: String = "",
        val isCustom: Boolean = false,
        val previewPath: String? = null
    )

    val googleVoices = remember {
        listOf(
            VoiceInfo("ko-KR-Standard-A", "Standard-A", "여성"),
            VoiceInfo("ko-KR-Standard-B", "Standard-B", "여성"),
            VoiceInfo("ko-KR-Standard-C", "Standard-C", "남성"),
            VoiceInfo("ko-KR-Standard-D", "Standard-D", "남성"),
            VoiceInfo("ko-KR-Wavenet-A", "Wavenet-A", "여성"),
            VoiceInfo("ko-KR-Wavenet-B", "Wavenet-B", "여성"),
            VoiceInfo("ko-KR-Wavenet-C", "Wavenet-C", "남성"),
            VoiceInfo("ko-KR-Wavenet-D", "Wavenet-D", "남성")
        )
    }
    val allVoices = remember(customVoices) {
        googleVoices + customVoices.map {
            VoiceInfo(id = it.second, displayName = it.first, isCustom = true, previewPath = it.third)
        }
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
                }
            } catch (e: Exception) {
                Log.e("PLAY_PREVIEW", "${e.message}")
            }
        }
    }

    DisposableEffect(Unit) { onDispose { mediaPlayer?.release(); mediaPlayer = null } }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        val dialogWindowView = LocalView.current
        fun hideIme() {
            focusManager.clearFocus()
            ViewCompat.getWindowInsetsController(dialogWindowView)?.hide(WindowInsetsCompat.Type.ime())
        }

        var keyboardWasVisibleAtDown by remember { mutableStateOf(false) }

        // 💡 다이얼로그 최상위 컨테이너
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .pointerInput(isKeyboardVisible) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        keyboardWasVisibleAtDown = isKeyboardVisible
                        if (isKeyboardVisible) hideIme()
                    }
                }
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    // 터치 시작 시점에 키보드가 없었다면 배경 클릭 시 닫기
                    if (!keyboardWasVisibleAtDown) onCancel()
                },
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(vertical = 16.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        // 카드 내부 클릭은 배경의 '창 닫기'가 작동하지 않도록 소비
                    },
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(20.dp)
                ) {
                    Text(
                        text = if (alarm == null) "새 알람 추가" else "알람 수정",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TimeInputUnit(value = hour, onValueChange = { hour = it }, range = 1..12, label = "시", isHour = true)
                                Text(":", style = MaterialTheme.typography.displayMedium, modifier = Modifier.padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                TimeInputUnit(value = minute, onValueChange = { minute = it }, range = 0..59, label = "분", isMinute = true)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                                    .padding(4.dp)
                            ) {
                                listOf(0 to "오전", 12 to "오후").forEach { (offset, label) ->
                                    val isSelected = amPmOffset == offset
                                    val bgColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, label = "amPmBg")
                                    val textColor by animateColorAsState(if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, label = "amPmText")

                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(bgColor)
                                            .clickable { amPmOffset = offset }
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                    ) {
                                        Text(label, color = textColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text("반복 요일", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        val dayLabels = listOf("월", "화", "수", "목", "금", "토", "일")
                        dayLabels.forEachIndexed { index, label ->
                            val systemDayInt = if (index == 6) 1 else index + 2
                            val isSelected = selectedDays.contains(systemDayInt)
                            val bgColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, label = "dayBg")
                            val textColor by animateColorAsState(if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, label = "dayText")

                            Surface(
                                onClick = { selectedDays = if (isSelected) selectedDays - systemDayInt else selectedDays + systemDayInt },
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color = bgColor
                            ) { Box(contentAlignment = Alignment.Center) { Text(label, fontSize = 12.sp, color = textColor, fontWeight = FontWeight.Bold) } }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            value = currentVoice?.displayName ?: "목소리를 선택하세요",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("알람 목소리 (필수)", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            shape = RoundedCornerShape(12.dp),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            allVoices.forEach { voice ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(if (voice.isCustom) "🎙 ${voice.displayName}" else "🌐 ${voice.displayName} (${voice.gender})")
                                            if (voice.isCustom) {
                                                IconButton(
                                                    onClick = {
                                                        val target = customVoices.find { it.second == voice.id }
                                                        target?.let { onDeleteVoice(it) }
                                                    }
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "삭제", tint = Color.Red, modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        }
                                    },
                                    onClick = { selectedVoiceId = voice.id; expanded = false; playPreview(voice) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it },
                        label = { Text("보이스 메시지", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.fillMaxWidth().heightIn(max = 100.dp),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Checkbox(checked = isSoundEnabled, onCheckedChange = { isSoundEnabled = it }, modifier = Modifier.scale(0.8f))
                            Text("사운드", style = MaterialTheme.typography.labelMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Checkbox(checked = isVibrationEnabled, onCheckedChange = { isVibrationEnabled = it }, modifier = Modifier.scale(0.8f))
                            Text("진동", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                            Text("취소")
                        }
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (isSaving) return@Button

                                if (selectedVoiceId == null) {
                                    Toast.makeText(context, "알람 목소리를 선택해주세요.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (amPmOffset == null) {
                                    Toast.makeText(context, "오전 또는 오후를 선택해주세요.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }

                                isSaving = true
                                val finalHour = if (hour == 12) amPmOffset!! else amPmOffset!! + hour
                                onSave(finalHour, minute, message, selectedDays, selectedVoiceId!!, null, null, isSoundEnabled, isVibrationEnabled)
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isSaving,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (isSaving) "저장 중..." else "저장", fontWeight = FontWeight.Bold)
                        }
                    }
                }
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
    isMinute: Boolean = false,
    isHour: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var isTyping by remember { mutableStateOf(false) }
    var textFieldValue by remember { mutableStateOf(String.format(Locale.US, "%02d", value)) }

    LaunchedEffect(value) {
        if (!isTyping) {
            textFieldValue = String.format(Locale.US, "%02d", value)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            isTyping = false
            val next = if (value < range.last) value + 1 else range.first
            onValueChange(next)
            focusManager.clearFocus()
        }) { Icon(Icons.Default.KeyboardArrowUp, null, modifier = Modifier.size(32.dp)) }

        BasicTextField(
            value = textFieldValue,
            onValueChange = { input ->
                if (input.all { it.isDigit() } && input.length <= 2) {
                    if (input.isEmpty()) {
                        isTyping = true
                        textFieldValue = ""
                        return@BasicTextField
                    }
                    if (input == "0") {
                        isTyping = true
                        textFieldValue = "0"
                        return@BasicTextField
                    }
                    val intVal = input.toInt()
                    if (intVal in range) {
                        isTyping = true
                        textFieldValue = input
                        onValueChange(intVal)
                    } else if (intVal > range.last) {
                        Toast.makeText(context, "${range.last}${label} 이하로 설정해주세요.", Toast.LENGTH_SHORT).show()
                    }
                }
            }, 
            modifier = Modifier
                .width(80.dp)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        isTyping = false
                        textFieldValue = String.format(Locale.US, "%02d", value)
                    }
                }, 
            textStyle = TextStyle(
                fontSize = 48.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            ), 
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done), 
            keyboardActions = KeyboardActions(onDone = {
                isTyping = false
                textFieldValue = String.format(Locale.US, "%02d", value)
                focusManager.clearFocus()
            }), 
            singleLine = true
        )

        IconButton(onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            isTyping = false
            val prev = if (value > range.first) value - 1 else range.last
            onValueChange(prev)
            focusManager.clearFocus()
        }) { Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(32.dp)) }

        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

fun copyUriToTempFile(context: Context, uri: Uri): File {
    val inputStream = context.contentResolver.openInputStream(uri)
    val tempFile = File(context.cacheDir, "temp_audio_${System.currentTimeMillis()}.wav")
    inputStream?.use { input -> tempFile.outputStream().use { output -> input.copyTo(output) } }
    return tempFile
}
