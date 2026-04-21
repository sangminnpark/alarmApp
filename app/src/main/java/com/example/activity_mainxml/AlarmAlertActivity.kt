package com.example.activity_mainxml

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
import com.example.activity_mainxml.network.VoiceRepository
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

        setupLockScreenVisible()

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
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.KOREAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "한국어를 지원하지 않습니다.")
            }

            // ⭐️ 중요: TTS 준비가 끝난 "이 시점"에 알람 소리를 시작합니다.
            playAlarmVoice()
        } else {
            Log.e("TTS", "초기화 실패")
            // TTS가 실패하더라도 MediaPlayer 파일 재생은 시도해야 하므로 호출
            playAlarmVoice()
        }
    }

    private fun setupLockScreenVisible() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager =
                getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }

    private fun playAlarmVoice() {
        if (!isAlarmActive) return

        val localFilePath = intent.getStringExtra("localFilePath")
        val file = localFilePath?.let { File(it) }

        // 1. 현재 시간 문구 생성
        val calendar = Calendar.getInstance()
        val amPm = if (calendar.get(Calendar.HOUR_OF_DAY) < 12) "오전" else "오후"
        val hour = calendar.get(Calendar.HOUR).let { if (it == 0) 12 else it }
        val min = calendar.get(Calendar.MINUTE)

        // 💡 메시지가 있으면 붙이고, 없으면 시간만 말하도록 설정
        val timeText = "현재 시간은 ${amPm} ${hour}시 ${min}분입니다."
        val fullText = if (message.isNotBlank()) "$timeText $message" else timeText

        if (file != null && file.exists()) {
            // 1. 일레븐랩스 등 미리 생성된 파일이 있는 경우 (이미 파일에 음성이 고정됨)
            Log.d("ALARM_DEBUG", "로컬 파일 재생: ${file.name}")
            playCustomVoiceFile(file)
        } else if (voiceId != null && voiceId!!.startsWith("ko-KR-")) {
            // 2. 구글 보이스인 경우 (실시간 생성)
            Log.d("ALARM_DEBUG", "구글 TTS 호출: $fullText")
            callGoogleTts(fullText, voiceId!!)
        } else {
            // 3. 마지막 수단 기본 TTS
            Log.e("ALARM_DEBUG", "기본 TTS 실행: $fullText")
            fallbackToDefaultTts(fullText) // message 대신 완성된 fullText를 전달
        }
    }

    // ✅ 일레븐랩스 API를 호출하여 "사용자 메시지"가 담긴 음성을 실시간 생성
    private fun callElevenLabsTts(text: String, vId: String) {
        scope.launch {
            try {
                // RetrofitClient를 통해 일레븐랩스 서버에서 음성 파일 생성
                // text 인자에 우리가 만든 fullText가 들어갑니다.
                val audioFile = VoiceRepository.makeElevenLabsVoiceFile(
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
                isLooping = false // ⭐️ 실시간 시간 반영을 위해 루핑은 끕니다.
                prepare()
                start()

                // ⭐️ 소리가 끝나면 다시 playAlarmVoice를 호출하여 시간을 새로 계산하게 함
                setOnCompletionListener {
                    if (isAlarmActive) {
                        // 1초 정도 쉬었다가 다음 시간을 읽어줌
                        handler.postDelayed({ playAlarmVoice() }, 1000)
                    }
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
                isLooping = false // ⭐️ 루핑 끔
                prepare()
                start()

                setOnCompletionListener {
                    if (isAlarmActive) {
                        handler.postDelayed({ playAlarmVoice() }, 1000)
                    }
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
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                // 소리가 끝나면 다시 재생 (사용자가 종료 버튼을 누르기 전까지)
                if (isAlarmActive) {
                    handler.postDelayed({ fallbackToDefaultTts(text) }, 2000)
                }
            }

            override fun onError(utteranceId: String?) {}
        })
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

    override fun onDestroy() {
        stopAlarm()
        tts?.shutdown()
        super.onDestroy()
    }
    // AlarmAlertActivity.kt 내부에 추가

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // 새로 들어온 인텐트로 교체

        // 1. 기존 알람/소리 즉시 중단
        stopAlarmVoiceOnly()

        // 2. 새로운 데이터 추출
        message = intent.getStringExtra("msg")?.trim() ?: ""
        voiceId = intent.getStringExtra("voiceId")
        alarmId = intent.getIntExtra("alarmId", -1)

        // 3. UI 갱신 (Compose의 경우 상태 변수를 사용하면 좋지만,
        // 가장 확실한 방법은 소리를 다시 재생하는 것)
        Log.d("ALARM_DEBUG", "새로운 알람 수신! ID: $alarmId, 메시지: $message")

        // 4. 새로운 알람으로 다시 시작
        playAlarmVoice()
    }

    // stopAlarm()에서 finish()만 뺀 버전 (기존 소리만 끄기 위함)
    private fun stopAlarmVoiceOnly() {
        handler.removeCallbacksAndMessages(null)
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        tts?.stop()
    }
}