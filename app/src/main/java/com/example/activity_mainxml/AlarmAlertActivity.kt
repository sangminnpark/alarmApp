package com.example.activity_mainxml

import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.activity_mainxml.model.TtsAudioConfig
import com.example.activity_mainxml.model.TtsInput
import com.example.activity_mainxml.model.TtsModel
import com.example.activity_mainxml.model.TtsVoice
import com.example.activity_mainxml.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Locale

class AlarmAlertActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var mediaPlayer: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var message: String = ""
    private var voiceId: String? = null

    private var alarmId: Int = -1
    private var isAlarmActive = true
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val API_KEY = BuildConfig.GOOGLE_API_KEY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 데이터 추출
        message = intent.getStringExtra("msg")?.trim() ?: ""
        voiceId = intent.getStringExtra("voiceId")
        alarmId = intent.getIntExtra("alarmId", -1)

        // 2. TTS 초기화
        tts = TextToSpeech(this, this)

        // 3. 잠금 화면 위 노출 설정
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        // 4. UI 설정 (setContent 내부에는 UI 컴포저블만!)
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Notifications, null, modifier = Modifier.size(100.dp))
                    Spacer(modifier = Modifier.height(16.dp))

                    val calendar = Calendar.getInstance()
                    Text(
                        text = String.format(
                            "%02d:%02d",
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE)
                        ),
                        style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black
                    )

                    if (message.isNotBlank()) {
                        Text(text = message, style = MaterialTheme.typography.headlineSmall)
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    Button(
                        onClick = { stopAlarm(); finish() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("알람 종료", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }

        // 5. 알람 실행
        playAlarmVoice()
    }

    // --- 여기서부터는 일반 멤버 함수입니다 (setContent 밖에 위치) ---

    private fun playAlarmVoice() {
        if (!isAlarmActive) return

        // 💡 Intent에서 미리 생성된 파일 경로를 받아옵니다.
        val localFilePath = intent.getStringExtra("localFilePath")
        val file = localFilePath?.let { File(it) }

        if (file != null && file.exists()) {
            // ✅ 서버 호출 없이 즉시 로컬 파일 재생!
            Log.d("ALARM_DEBUG", "로컬 파일 재생 시작: ${file.name}")
            playCustomVoiceFile(file)
        } else {
            // 파일이 없으면 기존처럼 구글 TTS나 기본 TTS로 백업
            Log.e("ALARM_DEBUG", "로컬 파일을 찾을 수 없어 백업 TTS 실행")
            fallbackToDefaultTts(message)
        }
    }

    // ✅ 일레븐랩스 API를 호출하여 "사용자 메시지"가 담긴 음성을 실시간 생성
    private fun callElevenLabsTts(text: String, vId: String) {
        scope.launch {
            try {
                // RetrofitClient를 통해 일레븐랩스 서버에서 음성 파일 생성
                // text 인자에 우리가 만든 fullText가 들어갑니다.
                val audioFile = RetrofitClient.makeElevenLabsVoiceFile(
                    voiceId = vId,
                    targetText = text,
                    context = this@AlarmAlertActivity
                )

                if (audioFile != null && audioFile.exists()) {
                    playCustomVoiceFile(audioFile)
                } else {
                    fallbackToDefaultTts(text) // 실패 시 기본 TTS로 백업
                }
            } catch (e: Exception) {
                Log.e("AlarmAlert", "일레븐랩스 재생 실패: ${e.localizedMessage}")
                fallbackToDefaultTts(text)
            }
        }
    }

    private fun playCustomVoiceFile(file: File) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setAudioStreamType(AudioManager.STREAM_ALARM)
                prepare()
                start()
                setOnCompletionListener {
                    // 알람이 꺼지기 전까지 반복 (필요 시 약간의 딜레이 후 재호출)
                    if (isAlarmActive) handler.postDelayed({ playAlarmVoice() }, 2000)
                }
            }
        } catch (e: Exception) {
            fallbackToDefaultTts(message)
        }
    }

    private fun callGoogleTts(text: String, vId: String) {
        scope.launch {
            try {
                val request = TtsModel(
                    input = TtsInput(text),
                    voice = TtsVoice("ko-KR", vId),
                    audioConfig = TtsAudioConfig()
                )
                val response = RetrofitClient.googleTtsService.synthesizeText(API_KEY, request)
                if (response.isSuccessful && response.body() != null) {
                    val audioBytes = Base64.decode(response.body()!!.audioContent, Base64.DEFAULT)
                    playAudio(audioBytes)
                } else {
                    fallbackToDefaultTts(text)
                }
            } catch (e: Exception) {
                fallbackToDefaultTts(text)
            }
        }
    }

    private fun playAudio(audioBytes: ByteArray) {
        try {
            val tempFile = File.createTempFile("tts_temp", "mp3", cacheDir)
            FileOutputStream(tempFile).use { it.write(audioBytes) }

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                setAudioStreamType(AudioManager.STREAM_ALARM)
                prepare()
                start()
                setOnCompletionListener {
                    if (isAlarmActive) handler.postDelayed({ playAlarmVoice() }, 2000)
                }
            }
        } catch (e: Exception) {
            fallbackToDefaultTts(message)
        }
    }

    private fun fallbackToDefaultTts(text: String) {
        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "AlarmID")
        // 기본 TTS는 반복 로직을 위해 UtteranceProgressListener가 추가로 필요할 수 있습니다.
    }

    private fun stopAlarm() {
        isAlarmActive = false
        handler.removeCallbacksAndMessages(null)
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        tts?.stop()
        scope.cancel()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale.KOREAN
    }

    override fun onDestroy() {
        stopAlarm()
        tts?.shutdown()
        super.onDestroy()
    }
}