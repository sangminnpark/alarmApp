package com.example.activity_mainxml.ui.edit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceRegistrationDialog(
    onDismiss: () -> Unit,
    onGenerateVoice: (File, String) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val view = LocalView.current
    
    // 💡 키보드 상태 감지
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0 || WindowInsets.isImeVisible

    // 💡 다이얼로그 전용 윈도우 설정
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window
        window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
    }

    var voiceName by remember { mutableStateOf("") }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    
    // --- [녹음 관련 상태] ---
    var isRecording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordFile by remember { mutableStateOf<File?>(null) }
    var recordingTimeMillis by remember { mutableLongStateOf(0L) }
    
    // --- [미리듣기 관련 상태] ---
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var audioDurationMillis by remember { mutableLongStateOf(0L) }

    // 💡 녹음 타이머 로직
    LaunchedEffect(isRecording) {
        if (isRecording) {
            val startTime = System.currentTimeMillis()
            while (isRecording) {
                recordingTimeMillis = System.currentTimeMillis() - startTime
                delay(100)
            }
        }
    }

    // --- [검증 로직] ---
    fun getAudioDurationFromFile(file: File): Long {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val time = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            time?.toLong() ?: 0L
        } catch (e: Exception) { 0L }
    }

    val finalFile = recordFile ?: selectedFile
    val isValidDuration = audioDurationMillis >= 3000L

    fun playPreview() {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(finalFile!!.absolutePath)
                prepare()
                start()
                isPlaying = true
                setOnCompletionListener { isPlaying = false }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "재생할 수 없는 파일입니다.", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopPreview() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
    }

    // 권한 요청 런처
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "녹음 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedFile = copyUriToTempFile(context, it)
            recordFile = null
            recordingTimeMillis = 0L // 파일 선택 시 타이머 리셋
        }
    }

    // 파일 선택 시 길이 측정
    LaunchedEffect(selectedFile) {
        selectedFile?.let {
            audioDurationMillis = getAudioDurationFromFile(it)
        }
    }

    fun startRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        try {
            val file = File(context.cacheDir, "record_${System.currentTimeMillis()}.wav")
            recordFile = file
            selectedFile = null
            recordingTimeMillis = 0L
            audioDurationMillis = 0L

            recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            isRecording = true
        } catch (e: Exception) {
            Log.e("RECORD_ERROR", "${e.message}")
            Toast.makeText(context, "녹음을 시작할 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    fun stopRecording() {
        try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            isRecording = false
            audioDurationMillis = recordingTimeMillis // 녹음 종료 즉시 시간 확정
        } catch (e: Exception) {
            Log.e("RECORD_ERROR", "${e.message}")
            isRecording = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recorder?.release()
            mediaPlayer?.release()
        }
    }

    Dialog(
        onDismissRequest = { if (!isRecording) onDismiss() },
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
                    // 키보드가 없었고 녹음 중이 아닐 때 배경 클릭 시 닫기
                    if (!keyboardWasVisibleAtDown && !isRecording) onDismiss()
                },
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(16.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        // 카드 내부 클릭은 배경의 '창 닫기'가 작동하지 않도록 소비
                    },
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDE7))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("나만의 목소리 복제", style = MaterialTheme.typography.titleLarge, color = Color(0xFFFBC02D), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("5~10초 정도 명확하게 녹음하거나\n음성 파일을 업로드해 주세요.", 
                        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    
                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = voiceName,
                        onValueChange = { voiceName = it },
                        label = { Text("목소리 이름 (예: 내 목소리)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFBC02D),
                            unfocusedBorderColor = Color(0xFFFFF9C4)
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // --- [녹음 / 파일 선택 UI] ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isRecording) {
                                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                val pulseScale by infiniteTransition.animateFloat(
                                    initialValue = 1f,
                                    targetValue = 1.4f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(800, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "scale"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .scale(pulseScale)
                                        .clip(CircleShape)
                                        .background(Color.Red.copy(alpha = 0.3f))
                                    )
                            }

                            IconButton(
                                onClick = { if (isRecording) stopRecording() else startRecording() },
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(if (isRecording) Color.Red else Color(0xFFFBC02D))
                            ) {
                                Icon(
                                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(32.dp))

                        OutlinedButton(
                            onClick = { filePicker.launch("audio/*") },
                            modifier = Modifier.height(64.dp),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !isRecording
                        ) {
                            Text(if (selectedFile == null) "파일 업로드" else "파일 선택됨")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 💡 실시간 타이머 및 상태 표시
                    Box(modifier = Modifier.height(30.dp), contentAlignment = Alignment.Center) {
                        if (isRecording || recordingTimeMillis > 0) {
                            val seconds = recordingTimeMillis / 1000
                            val fractional = (recordingTimeMillis % 1000) / 100
                            Text(
                                text = String.format(Locale.US, "%02d.%d초", seconds, fractional),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (recordingTimeMillis >= 3000L) Color(0xFF4CAF50) else Color.Red
                            )
                        } else {
                            Text(text = "소스를 선택해 주세요", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 선택된 소스 상세 정보 및 미리보기
                    AnimatedVisibility(visible = finalFile != null && !isRecording) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color(0xFFFBC02D),
                                        modifier = Modifier.size(32.dp).clickable { 
                                            if (isPlaying) stopPreview() else playPreview() 
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = if (recordFile != null) "녹음본 미리듣기" else "업로드 파일 미리듣기",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        Text(
                                            text = String.format(Locale.US, "%.1f초 분량", audioDurationMillis / 1000f),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isValidDuration) Color.Gray else Color.Red
                                        )
                                    }
                                }
                            }
                            
                            if (!isValidDuration) {
                                Text(
                                    "💡 최소 3초 이상의 음성이 필요합니다.",
                                    color = Color.Red,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            enabled = !isRecording
                        ) { Text("취소", color = Color.Gray) }

                        Button(
                            onClick = {
                                if (voiceName.isNotBlank() && finalFile != null) {
                                    onGenerateVoice(finalFile, voiceName)
                                }
                            },
                            enabled = voiceName.isNotBlank() && finalFile != null && isValidDuration && !isRecording,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBC02D))
                        ) {
                            Text("보이스 복제 시작")
                        }
                    }
                }
            }
        }
    }
}
