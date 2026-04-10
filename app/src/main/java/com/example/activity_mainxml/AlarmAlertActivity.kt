package com.example.activity_mainxml

import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Base64
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
    private var isAlarmActive = true
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    val API_KEY = BuildConfig.GOOGLE_TTS_API_KEY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 데이터 추출
        message = intent.getStringExtra("msg")?.trim() ?: ""
        voiceId = intent.getStringExtra("voiceName")

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

        val calendar = Calendar.getInstance()
        val amPm = if (calendar.get(Calendar.AM_PM) == Calendar.AM) "오전" else "오후"
        val hour = calendar.get(Calendar.HOUR).let { if (it == 0) 12 else it }
        val minute = calendar.get(Calendar.MINUTE)
        val timeText = "현재 시간은 ${amPm} ${hour}시 ${minute}분입니다."
        val fullText = if (message.isBlank()) timeText else "$timeText $message"

        if (!voiceId.isNullOrBlank()) {
            callGoogleTts(fullText, voiceId!!)
        } else {
            fallbackToDefaultTts(fullText)
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
                val response = RetrofitClient.ttsService.synthesizeText(API_KEY, request)
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