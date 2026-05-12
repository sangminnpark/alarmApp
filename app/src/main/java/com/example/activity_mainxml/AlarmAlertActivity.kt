package com.example.activity_mainxml

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
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.activity_mainxml.model.AlarmItem
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
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var currentTime by mutableStateOf("00:00")

    // 💡 [추가] 볼륨 페이드 인 관리를 위한 변수
    private var currentVolume = 0.1f
    private val FADE_STEP = 0.05f
    private val MAX_VOLUME = 1.0f

    // 캐싱을 위한 변수들
    private var isFirstPlay = true
    private var lastMinute = -1
    private var lastAudio: ByteArray? = null
    private var lastElevenFile: File? = null

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
        wakeLock = powerManager.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK,
            "AlarmApp:WakeLockTag"
        )
        wakeLock?.acquire(10 * 60 * 1000L)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        message = intent.getStringExtra("msg")?.trim() ?: ""
        voiceId = intent.getStringExtra("voiceId")
        alarmId = intent.getIntExtra("alarmId", -1)

        setupLockScreenVisible()
        acquireWakeLock()

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

                    Text(
                        text = currentTime,
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Black
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
            tts?.language = Locale.KOREAN
            playAlarmVoice()
        } else {
            playAlarmVoice()
        }
    }

    private fun setupLockScreenVisible() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager
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

        // ⭐️ 여기서 기준 시간(Minute)을 한 번만 구합니다.
        val calendar = Calendar.getInstance()
        val currentMin = calendar.get(Calendar.MINUTE)
        val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
        val amPm = if (hourOfDay < 12) "오전" else "오후"
        val displayHour = calendar.get(Calendar.HOUR).let { if (it == 0) 12 else it }

        // UI 업데이트
        currentTime = String.format("%02d:%02d", hourOfDay, currentMin)

        // 음성 문구 생성
        val timeText = "현재 시간은 ${amPm} ${displayHour}시 ${currentMin}분입니다."
        val fullText = if (message.isNotBlank()) "$timeText $message" else timeText

        val localFilePath = intent.getStringExtra("localFilePath")
        val file = localFilePath?.let { File(it) }

        // [케이스 1] 첫 재생 시 로컬 파일이 있다면 우선 실행
        if (isFirstPlay && file != null && file.exists()) {
            Log.d("ALARM_DEBUG", "첫 재생: 로컬 파일 실행")
            isFirstPlay = false
            playCustomVoiceFile(file)
            return
        }

        // [케이스 2 & 3] 실시간 API 호출 (currentMin 전달)
        if (voiceId != null) {
            if (voiceId!!.startsWith("ko-KR-")) {
                callGoogleTts(fullText, voiceId!!, currentMin)
            } else {
                callElevenLabsTts(fullText, voiceId!!, currentMin)
            }
        } else {
            fallbackToDefaultTts(fullText)
        }
    }

    private fun callElevenLabsTts(text: String, vId: String, currentMin: Int) {
        // ⭐️ 전달받은 currentMin으로 캐시 확인
        if (currentMin == lastMinute && lastElevenFile != null && lastElevenFile!!.exists()) {
            Log.d("ALARM_DEBUG", "일레븐랩스 캐시 사용: $currentMin 분")
            playCustomVoiceFile(lastElevenFile!!)
            return
        }

        scope.launch {
            try {
                val audioFile = VoiceRepository.makeElevenLabsVoiceFile(
                    voiceId = vId,
                    targetText = text,
                    context = this@AlarmAlertActivity
                )
                if (audioFile != null && audioFile.exists()) {
                    lastElevenFile = audioFile
                    lastMinute = currentMin
                    playCustomVoiceFile(audioFile)
                } else {
                    fallbackToDefaultTts(text)
                }
            } catch (e: Exception) {
                fallbackToDefaultTts(text)
            }
        }
    }

    private fun callGoogleTts(text: String, vId: String, currentMin: Int) {
        // ⭐️ 전달받은 currentMin으로 캐시 확인
        if (currentMin == lastMinute && lastAudio != null) {
            Log.d("ALARM_DEBUG", "구글 캐시 사용: $currentMin 분")
            playAudio(lastAudio!!)
            return
        }

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
                    lastAudio = audioBytes
                    lastMinute = currentMin
                    playAudio(audioBytes)
                } else {
                    fallbackToDefaultTts(text)
                }
            } catch (e: Exception) {
                fallbackToDefaultTts(text)
            }
        }
    }

    private fun playCustomVoiceFile(file: File) {
        try {
            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer()
            } else {
                mediaPlayer?.reset()
            }

            mediaPlayer?.apply {
                setDataSource(file.absolutePath)
                setAudioStreamType(AudioManager.STREAM_ALARM)
                // 💡 현재 페이드 인 볼륨 적용
                setVolume(currentVolume, currentVolume)
                isLooping = false
                prepare()
                start()
                
                // 💡 볼륨 서서히 키우기
                if (currentVolume < MAX_VOLUME) {
                    currentVolume += FADE_STEP
                }

                setOnCompletionListener {
                    if (isAlarmActive) handler.postDelayed({ playAlarmVoice() }, 1000)
                }
            }
        } catch (e: Exception) {
            Log.e("ALARM_DEBUG", "Custom voice play error: ${e.message}")
            fallbackToDefaultTts(message)
        }
    }

    private fun playAudio(audioBytes: ByteArray) {
        try {
            val tempFile = File.createTempFile("tts_temp", "mp3", cacheDir)
            FileOutputStream(tempFile).use { it.write(audioBytes) }

            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer()
            } else {
                mediaPlayer?.reset()
            }

            mediaPlayer?.apply {
                setDataSource(tempFile.absolutePath)
                setAudioStreamType(AudioManager.STREAM_ALARM)
                // 💡 현재 페이드 인 볼륨 적용
                setVolume(currentVolume, currentVolume)
                isLooping = false
                prepare()
                start()

                // 💡 볼륨 서서히 키우기
                if (currentVolume < MAX_VOLUME) {
                    currentVolume += FADE_STEP
                }

                setOnCompletionListener {
                    if (isAlarmActive) handler.postDelayed({ playAlarmVoice() }, 1000)
                }
            }
        } catch (e: Exception) {
            Log.e("ALARM_DEBUG", "Audio bytes play error: ${e.message}")
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
                if (isAlarmActive) handler.postDelayed({ playAlarmVoice() }, 2000)
            }

            override fun onError(utteranceId: String?) {}
        })
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "AlarmID")
    }

    private fun stopAlarm() {
        isAlarmActive = false
        handler.removeCallbacksAndMessages(null)
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        tts?.stop()
        scope.cancel()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            wakeLock = null
        }

        // 💡 [추가] 반복 알람인 경우 다음 알람 예약
        rescheduleIfNecessary()
    }

    private fun rescheduleIfNecessary() {
        val alarmJson = intent.getStringExtra("alarm_json")
        if (alarmJson != null) {
            val alarm = com.google.gson.Gson().fromJson(alarmJson, AlarmItem::class.java)
            if (alarm.repeatDays.isNotEmpty()) {
                Log.d("ALARM_DEBUG", "반복 알람 재예약 시도: ${alarm.id}")
                AlarmScheduler.schedule(this, alarm)
            }
        }
    }

    override fun onDestroy() {
        stopAlarm()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        isAlarmActive = true

        // 캐시 및 플래그 초기화
        isFirstPlay = true
        lastMinute = -1
        lastAudio = null
        lastElevenFile = null

        stopAlarmVoiceOnly()

        message = intent.getStringExtra("msg")?.trim() ?: ""
        voiceId = intent.getStringExtra("voiceId")
        alarmId = intent.getIntExtra("alarmId", -1)

        playAlarmVoice()
    }

    private fun stopAlarmVoiceOnly() {
        handler.removeCallbacksAndMessages(null)
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        tts?.stop()
    }
}