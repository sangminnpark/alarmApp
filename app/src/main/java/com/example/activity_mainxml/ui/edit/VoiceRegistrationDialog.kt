package com.example.activity_mainxml.ui.edit

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import java.io.File

@Composable
fun VoiceRegistrationDialog(
    onDismiss: () -> Unit,
    onGenerateVoice: (File, String) -> Unit
) {
    val context = LocalContext.current
    var voiceName by remember { mutableStateOf("") }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    
    // --- [녹음 관련 상태] ---
    var isRecording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordFile by remember { mutableStateOf<File?>(null) }

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
            recordFile = null // 파일 선택 시 기존 녹음본 초기화
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
            selectedFile = null // 녹음 시작 시 기존 선택 파일 초기화

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
        } catch (e: Exception) {
            Log.e("RECORD_ERROR", "${e.message}")
            isRecording = false // 에러 발생 시에도 상태 초기화
        }
    }

    Dialog(onDismissRequest = { if (!isRecording) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                    style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                
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
                    // 녹음 버튼
                    Box(contentAlignment = Alignment.Center) {
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
                        
                        if (isRecording) {
                            // 녹음 중 애니메이션 (간단히 표시)
                            CircularProgressIndicator(
                                modifier = Modifier.size(74.dp),
                                color = Color.Red,
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(32.dp))

                    // 파일 선택 버튼
                    OutlinedButton(
                        onClick = { filePicker.launch("audio/*") },
                        modifier = Modifier.height(64.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !isRecording
                    ) {
                        Text(if (selectedFile == null) "파일 업로드" else "파일 선택됨")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                
                // 선택된 소스 표시
                Text(
                    text = when {
                        isRecording -> "녹음 중..."
                        recordFile != null -> "녹음 완료: ${recordFile?.name}"
                        selectedFile != null -> "파일 선택됨: ${selectedFile?.name}"
                        else -> "소스를 선택해 주세요"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (recordFile != null || selectedFile != null) Color(0xFFFBC02D) else Color.Gray
                )

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
                            val finalFile = recordFile ?: selectedFile
                            if (voiceName.isNotBlank() && finalFile != null) {
                                onGenerateVoice(finalFile, voiceName)
                            }
                        },
                        enabled = voiceName.isNotBlank() && (recordFile != null || selectedFile != null) && !isRecording,
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
