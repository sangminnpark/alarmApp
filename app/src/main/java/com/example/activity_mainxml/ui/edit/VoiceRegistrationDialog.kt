package com.example.activity_mainxml.ui.edit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.activity_mainxml.util.FileUtil.copyUriToTempFile
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
    val density = LocalDensity.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    
    // 💡 반응형 폰트 비율
    val fontScale = (configuration.screenWidthDp / 360f).coerceIn(0.85f, 1.15f)
    
    // 💡 키보드 상태 감지
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0 || WindowInsets.isImeVisible

    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window
        window?.let { WindowCompat.setDecorFitsSystemWindows(it, false) }
    }

    var voiceName by remember { mutableStateOf("") }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    
    var isRecording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordFile by remember { mutableStateOf<File?>(null) }
    var recordingTimeMillis by remember { mutableLongStateOf(0L) }
    
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var audioDurationMillis by remember { mutableLongStateOf(0L) }
    var currentPositionMillis by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying) {
                mediaPlayer?.let {
                    try {
                        currentPositionMillis = it.currentPosition.toLong()
                    } catch (e: Exception) { isPlaying = false }
                }
                delay(50)
            }
        } else {
            currentPositionMillis = 0L
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            val startTime = System.currentTimeMillis()
            while (isRecording) {
                recordingTimeMillis = System.currentTimeMillis() - startTime
                delay(100)
            }
        }
    }

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

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (!isGranted) Toast.makeText(context, "녹음 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedFile = copyUriToTempFile(context, it)
            recordFile = null
            recordingTimeMillis = 0L
        }
    }

    LaunchedEffect(selectedFile) {
        selectedFile?.let { audioDurationMillis = getAudioDurationFromFile(it) }
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
                @Suppress("DEPRECATION") MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            isRecording = true
        } catch (e: Exception) { Toast.makeText(context, "녹음을 시작할 수 없습니다.", Toast.LENGTH_SHORT).show() }
    }

    fun stopRecording() {
        try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            isRecording = false
            audioDurationMillis = recordingTimeMillis
        } catch (e: Exception) { isRecording = false }
    }

    DisposableEffect(Unit) {
        onDispose { recorder?.release(); mediaPlayer?.release() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        val dialogWindowView = LocalView.current
        fun hideIme() {
            focusManager.clearFocus()
            ViewCompat.getWindowInsetsController(dialogWindowView)?.hide(WindowInsetsCompat.Type.ime())
        }

        var keyboardWasVisibleAtDown by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .pointerInput(isKeyboardVisible) {
                    awaitEachGesture {
                        // 💡 Initial Pass에서 모든 터치를 가로채 키보드를 숨김
                        awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                        keyboardWasVisibleAtDown = isKeyboardVisible
                        if (isKeyboardVisible) hideIme()
                    }
                }
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    if (!keyboardWasVisibleAtDown && !isRecording) onDismiss()
                },
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .padding(vertical = 12.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* 내부 클릭 소비 */ },
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDE7))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "나만의 목소리 복제", 
                        style = MaterialTheme.typography.titleLarge,
                        fontSize = (21 * fontScale).sp,
                        color = Color(0xFFFBC02D), 
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "5~10초 정도 명확하게 녹음하거나\n음성 파일을 업로드해 주세요.", 
                        style = MaterialTheme.typography.bodySmall, 
                        textAlign = TextAlign.Center,
                        fontSize = (11 * fontScale).sp,
                        lineHeight = (15 * fontScale).sp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = voiceName,
                        onValueChange = { voiceName = it },
                        label = { Text("목소리 이름 (예: 내 목소리)", fontSize = (12 * fontScale).sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFBC02D),
                            unfocusedBorderColor = Color(0xFFFFF9C4)
                        ),
                        textStyle = LocalTextStyle.current.copy(fontSize = (14 * fontScale).sp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

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
                                Box(modifier = Modifier.size(60.dp).scale(pulseScale).clip(CircleShape).background(Color.Red.copy(alpha = 0.3f)))
                            }

                            IconButton(
                                onClick = { if (isRecording) stopRecording() else startRecording() },
                                modifier = Modifier.size(60.dp).clip(CircleShape).background(if (isRecording) Color.Red else Color(0xFFFBC02D))
                            ) {
                                Icon(imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null, tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        OutlinedButton(
                            onClick = { filePicker.launch("audio/*") },
                            modifier = Modifier.height(60.dp).weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            enabled = !isRecording,
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text(
                                text = if (selectedFile == null) "파일 업로드" else "파일 선택됨",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = (14 * fontScale).sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Box(modifier = Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                        if (isRecording || recordingTimeMillis > 0) {
                            val seconds = recordingTimeMillis / 1000
                            val fractional = (recordingTimeMillis % 1000) / 100
                            Text(
                                text = String.format(Locale.US, "%02d.%d초", seconds, fractional),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (recordingTimeMillis >= 3000L) Color(0xFF4CAF50) else Color.Red,
                                fontSize = (15 * fontScale).sp
                            )
                        } else {
                            Text(text = "소스를 선택해 주세요", color = Color.Gray, style = MaterialTheme.typography.labelSmall, fontSize = (11 * fontScale).sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    
                    AnimatedVisibility(visible = finalFile != null && !isRecording) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color(0xFFFBC02D),
                                        modifier = Modifier.size(28.dp).clickable { if (isPlaying) stopPreview() else playPreview() }
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = if (recordFile != null) "녹음본 미리듣기" else "업로드 파일 미리듣기", style = MaterialTheme.typography.labelMedium, fontSize = (12 * fontScale).sp)
                                        
                                        Spacer(modifier = Modifier.height(4.dp))
                                        LinearProgressIndicator(
                                            progress = { if (audioDurationMillis > 0) currentPositionMillis.toFloat() / audioDurationMillis else 0f },
                                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                            color = Color(0xFFFBC02D),
                                            trackColor = Color.LightGray.copy(alpha = 0.5f)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(text = String.format(Locale.US, "%.1f초 분량", audioDurationMillis / 1000f), style = MaterialTheme.typography.bodySmall, color = if (isValidDuration) Color.Gray else Color.Red, fontSize = (10 * fontScale).sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = onDismiss, modifier = Modifier.weight(1f), enabled = !isRecording) { 
                            Text(text = "취소", color = Color.Gray, fontSize = (14 * fontScale).sp, maxLines = 1) 
                        }
                        Button(
                            onClick = { if (voiceName.isNotBlank() && finalFile != null) onGenerateVoice(finalFile, voiceName) },
                            enabled = voiceName.isNotBlank() && finalFile != null && isValidDuration && !isRecording,
                            modifier = Modifier.weight(1.8f),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBC02D)),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text(text = "보이스 복제 시작", fontWeight = FontWeight.Bold, fontSize = (14 * fontScale).sp, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}
