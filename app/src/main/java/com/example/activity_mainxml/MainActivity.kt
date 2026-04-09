package com.example.activity_mainxml

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Locale
import java.util.Random

// 요청 모델
data class TtsRequest(
    val input: TtsInput,
    val voice: TtsVoice,
    val audioConfig: TtsAudioConfig
)

data class TtsInput(val text: String)
data class TtsVoice(val languageCode: String, val name: String)
data class TtsAudioConfig(val audioEncoding: String = "MP3")

// 응답 모델 (Base64 인코딩된 오디오 데이터가 옴)
data class TtsResponse(val audioContent: String)

// Retrofit 인터페이스
interface GoogleTtsService {
    @POST("v1/text:synthesize")
    suspend fun synthesizeText(
        @Query("key") apiKey: String,
        @Body request: TtsRequest
    ): Response<TtsResponse>
}

// 1. 데이터 모델
data class AlarmItem(
    val id: Int = Random().nextInt(10000),
    var hour: Int,
    var minute: Int,
    var message: String,
    var isEnabled: Boolean = true,
    var repeatDays: Set<Int> = emptySet(),
    var voiceName: String? = null // 초기값은 null
)

const val PREFS_NAME = "alarm_prefs"
const val ALARM_KEY = "alarm_list"

// 2. 알람 스케줄러
object AlarmScheduler {
    fun schedule(context: Context, alarm: AlarmItem) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                context.startActivity(intent.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                return
            }
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("msg", alarm.message)
            putExtra("voiceName", alarm.voiceName) // 이 줄 추가!
            putIntegerArrayListExtra("repeatDays", ArrayList(alarm.repeatDays.toList()))
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, alarm.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DATE, 1)
        }

        val alarmClockInfo = AlarmManager.AlarmClockInfo(calendar.timeInMillis, pendingIntent)
        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
    }

    fun cancel(context: Context, alarm: AlarmItem) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, alarm.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}

// 3. 수신기 및 알람 화면
class RebootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(ALARM_KEY, null) ?: return
            val type = object : TypeToken<List<AlarmItem>>() {}.type
            val alarms: List<AlarmItem> = Gson().fromJson(json, type)
            alarms.filter { it.isEnabled }.forEach { AlarmScheduler.schedule(context, it) }
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val days = intent.getIntegerArrayListExtra("repeatDays") ?: arrayListOf()
        val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)

        if (!days.contains(today)) return

        val msg = intent.getStringExtra("msg") ?: ""
        val voiceName = intent.getStringExtra("voiceName") // voiceName 추가 추출

        val alertIntent = Intent(context, AlarmAlertActivity::class.java).apply {
            putExtra("msg", msg)
            putExtra("voiceName", voiceName) // 데이터 전달
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(alertIntent)
    }
}

class AlarmAlertActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var mediaPlayer: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var message: String = ""
    private var voiceId: String? = null
    private var isAlarmActive = true
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val API_KEY = "AIzaSyBM_69Za936hpHn115ZCDKRKg92xnlC-OU"

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
                val request = TtsRequest(
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

// 4. 메인 액티비티
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent =
                    Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = android.net.Uri.parse("package:$packageName")
                    }
                startActivity(intent)
            }
        }
        if (!android.provider.Settings.canDrawOverlays(this)) {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
        checkNotificationPermission()
        val initialAlarms = loadAlarms()
        setContent {
            AlarmApp(
                initialAlarms = initialAlarms,
                onSetAlarm = { AlarmScheduler.schedule(this, it) },
                onCancelAlarm = { AlarmScheduler.cancel(this, it) },
                onSaveToDisk = { saveAlarms(it) }
            )
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }

    private fun saveAlarms(alarms: List<AlarmItem>) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putString(ALARM_KEY, Gson().toJson(alarms)).apply()
    }

    private fun loadAlarms(): List<AlarmItem> {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val json = prefs.getString(ALARM_KEY, null) ?: return emptyList()
        return Gson().fromJson(json, object : TypeToken<List<AlarmItem>>() {}.type)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmApp(
    initialAlarms: List<AlarmItem>,
    onSetAlarm: (AlarmItem) -> Unit,
    onCancelAlarm: (AlarmItem) -> Unit,
    onSaveToDisk: (List<AlarmItem>) -> Unit
) {
    var alarmList by remember { mutableStateOf(initialAlarms) }
    var editingAlarm by remember { mutableStateOf<AlarmItem?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }

    LaunchedEffect(alarmList) { onSaveToDisk(alarmList) }

    if (isAddingNew || editingAlarm != null) {
        AlarmEditScreen(
            alarm = editingAlarm,
            onSave = { h, m, msg, days, vName -> // vName 추가
                val newAlarm = if (isAddingNew) {
                    AlarmItem(
                        hour = h,
                        minute = m,
                        message = msg,
                        repeatDays = days,
                        voiceName = vName
                    )
                } else {
                    editingAlarm!!.copy(
                        hour = h,
                        minute = m,
                        message = msg,
                        repeatDays = days,
                        voiceName = vName,
                        isEnabled = true
                    )
                }
                alarmList =
                    if (isAddingNew) alarmList + newAlarm else alarmList.map { if (it.id == newAlarm.id) newAlarm else it }
                onSetAlarm(newAlarm)
                isAddingNew = false; editingAlarm = null
            },
            onCancel = { isAddingNew = false; editingAlarm = null }
        )
    } else {
        Scaffold(
            topBar = { CenterAlignedTopAppBar(title = { Text("내 보이스 알람") }) },
            floatingActionButton = {
                FloatingActionButton(onClick = { isAddingNew = true }) {
                    Icon(
                        Icons.Default.Add,
                        "추가"
                    )
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                items(alarmList) { alarm ->
                    AlarmRow(
                        alarm = alarm,
                        onToggle = { isChecked ->
                            val updated = alarm.copy(isEnabled = isChecked)
                            alarmList = alarmList.map { if (it.id == alarm.id) updated else it }
                            if (isChecked) onSetAlarm(updated) else onCancelAlarm(updated)
                        },
                        onClick = { editingAlarm = alarm },
                        onDelete = {
                            onCancelAlarm(alarm); alarmList = alarmList.filter { it.id != alarm.id }
                        })
                }
            }
        }
    }
}

@Composable
fun AlarmRow(
    alarm: AlarmItem,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dayLabels = listOf("월", "화", "수", "목", "금", "토", "일")
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (alarm.isEnabled) MaterialTheme.colorScheme.surfaceVariant else Color(
                0xFFE0E0E0
            )
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val amPm = if (alarm.hour < 12) "오전" else "오후"
                val displayHour = if (alarm.hour % 12 == 0) 12 else alarm.hour % 12
                Text(
                    "${amPm} ${displayHour}:${String.format("%02d", alarm.minute)}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (alarm.isEnabled) Color.Unspecified else Color.Gray
                )

                // 시각적 피드백: 요일이 없으면 '반복 없음' 표시
                if (alarm.repeatDays.isNotEmpty()) {
                    val daysText =
                        alarm.repeatDays.sorted().joinToString(", ") { dayLabels[it - 1] }
                    Text(
                        text = "반복: $daysText",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                } else {
                    Text(
                        text = "반복 요일 없음 (울리지 않음)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                }
                Text(
                    text = if (alarm.message.isBlank()) "보이스 시간 알림" else alarm.message,
                    color = if (alarm.isEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                )
            }
            Switch(checked = alarm.isEnabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    "삭제",
                    tint = Color.LightGray
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(
    alarm: AlarmItem?,
    onSave: (Int, Int, String, Set<Int>, String) -> Unit,
    onCancel: () -> Unit
) {
    // --- [추가] 미리듣기를 위한 상태 및 객체 ---
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    val API_KEY = "AIzaSyBM_69Za936hpHn115ZCDKRKg92xnlC-OU"
    fun playPreview(vId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val previewText = "이 목소리로 설정합니다."
                val request = TtsRequest(
                    input = TtsInput(previewText),
                    voice = TtsVoice("ko-KR", vId),
                    audioConfig = TtsAudioConfig()
                )
                val response = RetrofitClient.ttsService.synthesizeText(API_KEY, request)

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
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "미리듣기 재생 실패", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    var selectedVoiceId by remember { mutableStateOf<String?>(alarm?.voiceName) }
    var expanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    var amPmOffset by remember { mutableStateOf<Int?>(alarm?.let { if (it.hour < 12) 0 else 12 }) }
    var hour by remember {
        mutableIntStateOf(alarm?.let { if (it.hour % 12 == 0) 12 else it.hour % 12 } ?: 12)
    }
    var minute by remember { mutableIntStateOf(alarm?.minute ?: 0) }
    var message by remember { mutableStateOf(alarm?.message ?: "") }

    // 요일 상태 (MutableSet으로 관리하여 상태 변경이 확실히 반영되도록 함)
    var selectedDays by remember { mutableStateOf(alarm?.repeatDays ?: (1..7).toSet()) }
    val dayLabels = listOf("월", "화", "수", "목", "금", "토", "일")

    data class VoiceInfo(val id: String, val displayName: String, val gender: String)

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
    val currentVoice = googleVoices.find { it.id == selectedVoiceId }

    // 화면이 닫힐 때 오디오 리소스 해제
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
        ) {
            Text(
                "알람 설정",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(20.dp))

            // 드롭다운 부분
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = currentVoice?.let { "${it.displayName} (${it.gender})" }
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
                    googleVoices.forEach { voice ->
                        DropdownMenuItem(
                            text = { Text("${voice.displayName} (${voice.gender})") },
                            onClick = {
                                selectedVoiceId = voice.id
                                expanded = false
                                playPreview(voice.id)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 오전/오후 버튼
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

            Spacer(modifier = Modifier.height(24.dp))

            // 시간 입력 (TimeInputUnit 호출)
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

            Spacer(modifier = Modifier.height(24.dp))

            // 요일 선택 (버그 수정: clickable 사용)
            Text("반복 요일", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                dayLabels.forEachIndexed { index, label ->
                    val dayInt = index + 1
                    val isSelected = selectedDays.contains(dayInt)

                    Surface(
                        onClick = {
                            selectedDays =
                                if (isSelected) selectedDays - dayInt else selectedDays + dayInt
                        },
                        modifier = Modifier.size(40.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isSelected) Color.White else Color.Black
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text(label, fontSize = 12.sp) }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("취소") }
                Button(
                    onClick = {
                        if (amPmOffset == null) {
                            Toast.makeText(context, "오전/오후를 선택해주세요!", Toast.LENGTH_SHORT).show()
                        } else if (selectedVoiceId == null) {
                            Toast.makeText(context, "목소리를 선택해주세요!", Toast.LENGTH_SHORT).show()
                        } else {
                            onSave(
                                if (hour == 12) amPmOffset!! else amPmOffset!! + hour,
                                minute, message, selectedDays, selectedVoiceId!!
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    // enabled = true로 변경하여 클릭을 유도하고 메시지를 보여줌
                    enabled = true
                ) { Text("저장") }
            }
        }
    }
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

object RetrofitClient {
    private const val BASE_URL = "https://texttospeech.googleapis.com/"

    val ttsService: GoogleTtsService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GoogleTtsService::class.java)
    }
}