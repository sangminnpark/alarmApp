package com.example.activity_mainxml.ui.alert

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.activity_mainxml.BuildConfig
import com.example.activity_mainxml.alarm.AlarmScheduler
import com.example.activity_mainxml.data.VoiceRepository
import com.example.activity_mainxml.data.remote.RetrofitClient
import com.example.activity_mainxml.model.AlarmItem
import com.example.activity_mainxml.model.TtsAudioConfig
import com.example.activity_mainxml.model.TtsInput
import com.example.activity_mainxml.model.TtsModel
import com.example.activity_mainxml.model.TtsVoice
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
    private var vibrator: Vibrator? = null
    private var message: String = ""
    private var voiceId: String? = null

    private var alarmId: Int = -1
    private var isAlarmActive = true
    private var isSoundEnabled = true
    private var isVibrationEnabled = true
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val API_KEY = BuildConfig.GOOGLE_API_KEY
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private var currentTime by mutableStateOf(
        String.format("%02d:%02d", Calendar.getInstance().get(Calendar.HOUR_OF_DAY), Calendar.getInstance().get(Calendar.MINUTE))
    )
    private var setAlarmTimeText by mutableStateOf("")

    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private var audioFocusRequest: android.media.AudioFocusRequest? = null

    private var currentVolume = 0.2f
    private val FADE_STEP = 0.1f
    private val MAX_VOLUME = 1.0f

    private var isFirstPlay = true
    private var lastMinute = -1
    private var lastAudio: ByteArray? = null
    private var lastElevenFile: File? = null

    private val timeCheckRunnable = object : Runnable {
        override fun run() {
            val calendar = Calendar.getInstance()
            val currentMin = calendar.get(Calendar.MINUTE)
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            
            currentTime = String.format("%02d:%02d", currentHour, currentMin)

            // 분이 바뀌었을 때 (사운드가 켜져 있을 때만 음성 갱신)
            if (isAlarmActive && currentMin != lastMinute && lastMinute != -1) {
                Log.d("ALARM_DEBUG", "분 변경 감지 ($lastMinute -> $currentMin)")
                if (isSoundEnabled) {
                    stopAlarmVoiceOnly()
                    playAlarmVoice()
                } else {
                    lastMinute = currentMin
                }
            }
            handler.postDelayed(this, 1000)
        }
    }

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
        
        // 알람 정보 파싱
        val alarmJson = intent.getStringExtra("alarm_json")
        if (alarmJson != null) {
            val alarm = com.google.gson.Gson().fromJson(alarmJson, AlarmItem::class.java)
            isSoundEnabled = alarm.isSoundEnabled
            isVibrationEnabled = alarm.isVibrationEnabled

            val amPm = if (alarm.hour < 12) "오전" else "오후"
            val hour = if (alarm.hour % 12 == 0) 12 else alarm.hour % 12
            setAlarmTimeText = String.format("%s %02d:%02d", amPm, hour, alarm.minute)
        }

        message = intent.getStringExtra("msg")?.trim() ?: ""
        voiceId = intent.getStringExtra("voiceId")
        alarmId = intent.getIntExtra("alarmId", -1)

        setupLockScreenVisible()
        acquireWakeLock()

        if (isVibrationEnabled) startVibration()

        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.errorContainer) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 상단 콘텐츠 영역 (중앙 정렬 및 남은 공간 차지)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Notifications, null, modifier = Modifier.size(100.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (setAlarmTimeText.isNotBlank()) {
                            Text(
                                text = "설정된 알람: $setAlarmTimeText",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = currentTime, 
                            style = MaterialTheme.typography.displayLarge, 
                            fontWeight = FontWeight.Black
                        )
                        
                        if (message.isNotBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            // 메시지가 길 경우를 대비해 스크롤 가능하게 처리
                            Box(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = message, 
                                    style = MaterialTheme.typography.headlineSmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 종료 버튼 (하단 고정)
                    Button(
                        onClick = { stopAlarm(); finish() },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = MaterialTheme.shapes.large
                    ) { 
                        Text("알람 종료", fontSize = 22.sp, fontWeight = FontWeight.Bold) 
                    }
                }
            }
        }
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.KOREAN
            if (isSoundEnabled) playAlarmVoice()
        } else {
            if (isSoundEnabled) playAlarmVoice()
        }
        // 시간 변경 감지 시작
        handler.post(timeCheckRunnable)
    }

    private fun startVibration() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 1000, 1000) // 1초 진동, 1초 대기
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0)) // 0: 무한 반복
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
            Log.d("ALARM_DEBUG", "진동 시작됨")
        } catch (e: Exception) {
            Log.e("ALARM_DEBUG", "진동 시작 실패: ${e.message}")
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

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener { }
                .build()
            audioManager.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun playAlarmVoice() {
        if (!isAlarmActive || !isSoundEnabled) return
        requestAudioFocus()

        val calendar = Calendar.getInstance()
        val currentMin = calendar.get(Calendar.MINUTE)
        val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
        val amPm = if (hourOfDay < 12) "오전" else "오후"
        val displayHour = calendar.get(Calendar.HOUR).let { if (it == 0) 12 else it }

        currentTime = String.format("%02d:%02d", hourOfDay, currentMin)
        val timeText = "현재 시간은 ${amPm} ${displayHour}시 ${currentMin}분입니다."
        val fullText = if (message.isNotBlank()) "$timeText $message" else timeText

        val localFilePath = intent.getStringExtra("localFilePath")
        val file = localFilePath?.let { File(it) }

        if (isFirstPlay && file != null && file.exists()) {
            isFirstPlay = false
            playCustomVoiceFile(file)
            return
        }

        if (voiceId != null) {
            if (voiceId!!.startsWith("ko-KR-")) callGoogleTts(fullText, voiceId!!, currentMin)
            else callElevenLabsTts(fullText, voiceId!!, currentMin)
        } else fallbackToDefaultTts(fullText)
    }

    private fun callElevenLabsTts(text: String, vId: String, currentMin: Int) {
        if (currentMin == lastMinute && lastElevenFile != null && lastElevenFile!!.exists()) {
            playCustomVoiceFile(lastElevenFile!!)
            return
        }
        scope.launch {
            try {
                val audioFile = VoiceRepository.makeElevenLabsVoiceFile(vId, text, this@AlarmAlertActivity)
                if (audioFile != null && audioFile.exists()) {
                    lastElevenFile = audioFile
                    lastMinute = currentMin
                    playCustomVoiceFile(audioFile)
                } else {
                    // 💡 [방어 로직] 생성 실패 시 즉시 기본 TTS로 폴백
                    Log.e("ALARM_DEBUG", "ElevenLabs generation failed, falling back to default TTS")
                    fallbackToDefaultTts(text)
                }
            } catch (e: Exception) {
                // 💡 [방어 로직] 예외 발생 시 즉시 기본 TTS로 폴백
                Log.e("ALARM_DEBUG", "ElevenLabs Exception: ${e.message}, falling back to default TTS")
                fallbackToDefaultTts(text)
            }
        }
    }

    private fun callGoogleTts(text: String, vId: String, currentMin: Int) {
        if (currentMin == lastMinute && lastAudio != null) {
            playAudio(lastAudio!!)
            return
        }
        scope.launch {
            try {
                val request = TtsModel(TtsInput(text), TtsVoice("ko-KR", vId), TtsAudioConfig())
                val response = RetrofitClient.googleTtsService.synthesizeText(API_KEY, request)
                if (response.isSuccessful && response.body() != null) {
                    val audioBytes = Base64.decode(response.body()!!.audioContent, Base64.DEFAULT)
                    lastAudio = audioBytes
                    lastMinute = currentMin
                    playAudio(audioBytes)
                } else {
                    // 💡 [방어 로직] 구글 API 실패 시 즉시 기본 TTS로 폴백
                    Log.e("ALARM_DEBUG", "Google TTS failure (code: ${response.code()}), falling back to default TTS")
                    fallbackToDefaultTts(text)
                }
            } catch (e: Exception) {
                // 💡 [방어 로직] 예외 발생 시 즉시 기본 TTS로 폴백
                Log.e("ALARM_DEBUG", "Google TTS Exception: ${e.message}, falling back to default TTS")
                fallbackToDefaultTts(text)
            }
        }
    }

    private fun playCustomVoiceFile(file: File) {
        try {
            if (mediaPlayer == null) mediaPlayer = MediaPlayer() else mediaPlayer?.reset()
            mediaPlayer?.apply {
                setDataSource(file.absolutePath)
                // 💡 미디어 스트림을 사용하여 이어폰/블루투스 연결 시 해당 기기로 출력되도록 설정
                val attr = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                setAudioAttributes(attr)
                setVolume(currentVolume, currentVolume)
                isLooping = false
                prepare()
                start()
                if (currentVolume < MAX_VOLUME) currentVolume += FADE_STEP
                setOnCompletionListener { if (isAlarmActive) handler.postDelayed({ playAlarmVoice() }, 1000) }
            }
        } catch (e: Exception) { fallbackToDefaultTts(message) }
    }

    private fun playAudio(audioBytes: ByteArray) {
        try {
            val tempFile = File.createTempFile("tts_temp", "mp3", cacheDir)
            FileOutputStream(tempFile).use { it.write(audioBytes) }
            if (mediaPlayer == null) mediaPlayer = MediaPlayer() else mediaPlayer?.reset()
            mediaPlayer?.apply {
                setDataSource(tempFile.absolutePath)
                // 💡 미디어 스트림을 사용하여 이어폰/블루투스 연결 시 해당 기기로 출력되도록 설정
                val attr = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                setAudioAttributes(attr)
                setVolume(currentVolume, currentVolume)
                isLooping = false
                prepare()
                start()
                if (currentVolume < MAX_VOLUME) currentVolume += FADE_STEP
                setOnCompletionListener { if (isAlarmActive) handler.postDelayed({ playAlarmVoice() }, 1000) }
            }
        } catch (e: Exception) { fallbackToDefaultTts(message) }
    }

    private fun fallbackToDefaultTts(text: String) {
        val params = Bundle().apply { 
            // 💡 미디어 스트림 사용
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC) 
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { if (isAlarmActive) handler.postDelayed({ playAlarmVoice() }, 2000) }
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
        vibrator?.cancel()
        vibrator = null
        scope.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
        }
        if (wakeLock?.isHeld == true) { wakeLock?.release(); wakeLock = null }
        cleanupTempFiles()
        rescheduleIfNecessary()
    }

    private fun cleanupTempFiles() {
        try {
            cacheDir.listFiles { file -> file.name.startsWith("tts_temp") || file.name.startsWith("eleven_") }?.forEach { it.delete() }
        } catch (e: Exception) { }
    }

    private fun rescheduleIfNecessary() {
        val alarmJson = intent.getStringExtra("alarm_json")
        if (alarmJson != null) {
            val alarm = com.google.gson.Gson().fromJson(alarmJson, AlarmItem::class.java)
            if (alarm.repeatDays.isNotEmpty()) AlarmScheduler.schedule(this, alarm)
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
        isFirstPlay = true
        lastMinute = -1
        lastAudio = null
        lastElevenFile = null

        val alarmJson = intent.getStringExtra("alarm_json")
        if (alarmJson != null) {
            val alarm = com.google.gson.Gson().fromJson(alarmJson, AlarmItem::class.java)
            isSoundEnabled = alarm.isSoundEnabled
            isVibrationEnabled = alarm.isVibrationEnabled
        }

        stopAlarmVoiceOnly()
        vibrator?.cancel()
        if (isVibrationEnabled) startVibration()

        message = intent.getStringExtra("msg")?.trim() ?: ""
        voiceId = intent.getStringExtra("voiceId")
        alarmId = intent.getIntExtra("alarmId", -1)
        if (isSoundEnabled) playAlarmVoice()
    }

    private fun stopAlarmVoiceOnly() {
        handler.removeCallbacksAndMessages(null)
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        tts?.stop()
    }
}
