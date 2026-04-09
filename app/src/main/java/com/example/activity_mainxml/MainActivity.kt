package com.example.activity_mainxml

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Random

// 1. 데이터 모델
data class AlarmItem(
    val id: Int = Random().nextInt(10000),
    var hour: Int,
    var minute: Int,
    var message: String,
    var isEnabled: Boolean = true,
    var repeatDays: Set<Int> = emptySet(),
    var voiceName: String? = null,
    var isGoogleVoice: Boolean = false
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
            putExtra("voiceName", alarm.voiceName)
            putExtra("isGoogleVoice", alarm.isGoogleVoice)
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
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(
                calendar.timeInMillis,
                pendingIntent
            ), pendingIntent
        )
    }

    fun cancel(context: Context, alarm: AlarmItem) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}

// 3. 수신기 및 알람 활동
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val days = intent.getIntegerArrayListExtra("repeatDays") ?: arrayListOf()
        val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        if (days.isNotEmpty() && !days.contains(today)) return
        val alertIntent = Intent(context, AlarmAlertActivity::class.java).apply {
            putExtra("msg", intent.getStringExtra("msg"))
            putExtra("voiceName", intent.getStringExtra("voiceName"))
            putExtra("isGoogleVoice", intent.getBooleanExtra("isGoogleVoice", false))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(alertIntent)
    }
}

class AlarmAlertActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var isAlarmActive = true
    private val scope = kotlinx.coroutines.MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val message = intent.getStringExtra("msg") ?: ""
        val voiceName = intent.getStringExtra("voiceName") ?: "ko-KR-Standard-A"
        val isGoogleVoice = intent.getBooleanExtra("isGoogleVoice", false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        // --- 반복 재생 로직 시작 ---
        if (isGoogleVoice) {
            scope.launch {
                while (isAlarmActive) {
                    val now = Calendar.getInstance()
                    val hour = now.get(Calendar.HOUR_OF_DAY)
                    val minute = now.get(Calendar.MINUTE)
                    val amPm = if (hour < 12) "오전" else "오후"
                    val displayHour = if (hour % 12 == 0) 12 else hour % 12

                    val timeText = "현재 시각은 $amPm ${displayHour}시 ${minute}분입니다."
                    val speechText = if (message.isNotBlank()) "$timeText $message" else timeText

                    // 1. 재생이 완료될 때까지 여기서 기다립니다 (함수가 suspend일 경우)
                    TtsManager.fetchAndPlayVoice(
                        context = this@AlarmAlertActivity,
                        text = speechText,
                        apiKey = "AIzaSyBM_69Za936hpHn115ZCDKRKg92xnlC-OU",
                        voiceName = voiceName
                    )

                    // 2. 위 함수가 끝나면(음성 출력이 완료되면) 정확히 2초를 쉽니다.
                    kotlinx.coroutines.delay(2000)
                }
            }
        }

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Notifications, null, modifier = Modifier.size(100.dp))
                    Text("알람 울림", style = MaterialTheme.typography.displayMedium)
                    Spacer(modifier = Modifier.height(20.dp))

                    Text("알람이 종료될 때까지 반복됩니다", style = MaterialTheme.typography.bodyMedium)

                    if (message.isNotBlank()) {
                        Text(message, style = MaterialTheme.typography.headlineMedium)
                    }

                    Spacer(modifier = Modifier.height(50.dp))
                    Button(
                        onClick = {
                            isAlarmActive = false // 루프를 멈춤
                            finish()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("알람 종료", color = MaterialTheme.colorScheme.onError)
                    }
                }
            }
        }
    }

    override fun onInit(status: Int) {}
    override fun onDestroy() {
        super.onDestroy()
        isAlarmActive = false // 액티비티가 닫히면 코루틴 루프도 종료됨
    }
}

// 4. 메인 액티비티
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    private fun saveAlarms(alarms: List<AlarmItem>) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putString(ALARM_KEY, Gson().toJson(alarms)).apply()
    }

    private fun loadAlarms(): List<AlarmItem> {
        val json = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(ALARM_KEY, null)
            ?: return emptyList()
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
            onSave = { h, m, msg, days, voice, isGoogle ->
                val newAlarm = if (isAddingNew) AlarmItem(
                    hour = h,
                    minute = m,
                    message = msg,
                    repeatDays = days,
                    voiceName = voice,
                    isGoogleVoice = isGoogle
                )
                else editingAlarm!!.copy(
                    hour = h,
                    minute = m,
                    message = msg,
                    repeatDays = days,
                    voiceName = voice,
                    isGoogleVoice = isGoogle
                )
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
            LazyVerticalGrid(
                columns = GridCells.Adaptive(340.dp),
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                items(alarmList) { alarm ->
                    AlarmRow(alarm, { /* toggle */ }, { editingAlarm = alarm }, { /* delete */ })
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
    Card(
        onClick = onClick, modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${alarm.hour}:${String.format("%02d", alarm.minute)}",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditScreen(
    alarm: AlarmItem?,
    onSave: (Int, Int, String, Set<Int>, String?, Boolean) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val googleVoices = listOf(
        // Chirp3 HD (초고음질 프리미엄)
        "ko-KR-Chirp3-HD-Achernar" to "프리미엄 여성 (Achernar)",
        "ko-KR-Chirp3-HD-Achird" to "프리미엄 남성 (Achird)",
        "ko-KR-Chirp3-HD-Algenib" to "프리미엄 남성 (Algenib)",
        "ko-KR-Chirp3-HD-Algieba" to "프리미엄 남성 (Algieba)",
        "ko-KR-Chirp3-HD-Alnilam" to "프리미엄 남성 (Alnilam)",
        "ko-KR-Chirp3-HD-Aoede" to "프리미엄 여성 (Aoede)",
        "ko-KR-Chirp3-HD-Autonoe" to "프리미엄 여성 (Autonoe)",
        "ko-KR-Chirp3-HD-Callirrhoe" to "프리미엄 여성 (Callirrhoe)",
        "ko-KR-Chirp3-HD-Charon" to "프리미엄 남성 (Charon)",
        "ko-KR-Chirp3-HD-Despina" to "프리미엄 여성 (Despina)",
        "ko-KR-Chirp3-HD-Enceladus" to "프리미엄 남성 (Enceladus)",
        "ko-KR-Chirp3-HD-Erinome" to "프리미엄 여성 (Erinome)",
        "ko-KR-Chirp3-HD-Fenrir" to "프리미엄 남성 (Fenrir)",
        "ko-KR-Chirp3-HD-Gacrux" to "프리미엄 여성 (Gacrux)",
        "ko-KR-Chirp3-HD-Iapetus" to "프리미엄 남성 (Iapetus)",
        "ko-KR-Chirp3-HD-Kore" to "프리미엄 여성 (Kore)",
        "ko-KR-Chirp3-HD-Laomedeia" to "프리미엄 여성 (Laomedeia)",
        "ko-KR-Chirp3-HD-Leda" to "프리미엄 여성 (Leda)",
        "ko-KR-Chirp3-HD-Orus" to "프리미엄 남성 (Orus)",
        "ko-KR-Chirp3-HD-Puck" to "프리미엄 남성 (Puck)",
        "ko-KR-Chirp3-HD-Pulcherrima" to "프리미엄 여성 (Pulcherrima)",
        "ko-KR-Chirp3-HD-Rasalgethi" to "프리미엄 남성 (Rasalgethi)",
        "ko-KR-Chirp3-HD-Sadachbia" to "프리미엄 남성 (Sadachbia)",
        "ko-KR-Chirp3-HD-Sadaltager" to "프리미엄 남성 (Sadaltager)",
        "ko-KR-Chirp3-HD-Schedar" to "프리미엄 남성 (Schedar)",
        "ko-KR-Chirp3-HD-Sulafat" to "프리미엄 여성 (Sulafat)",
        "ko-KR-Chirp3-HD-Umbriel" to "프리미엄 남성 (Umbriel)",
        "ko-KR-Chirp3-HD-Vindemiatrix" to "프리미엄 여성 (Vindemiatrix)",
        "ko-KR-Chirp3-HD-Zephyr" to "프리미엄 여성 (Zephyr)",
        "ko-KR-Chirp3-HD-Zubenelgenubi" to "프리미엄 남성 (Zubenelgenubi)",

        // Neural2 (고품질)
        "ko-KR-Neural2-A" to "고품질 여성 A",
        "ko-KR-Neural2-B" to "고품질 여성 B",
        "ko-KR-Neural2-C" to "고품질 남성 C",

        // Wavenet (딥마인드 기술)
        "ko-KR-Wavenet-A" to "웨이브넷 여성 A",
        "ko-KR-Wavenet-B" to "웨이브넷 여성 B",
        "ko-KR-Wavenet-C" to "웨이브넷 남성 C",
        "ko-KR-Wavenet-D" to "웨이브넷 남성 D",

        // Standard (일반)
        "ko-KR-Standard-A" to "표준 여성 A",
        "ko-KR-Standard-B" to "표준 여성 B",
        "ko-KR-Standard-C" to "표준 남성 C",
        "ko-KR-Standard-D" to "표준 남성 D"
    )

    var selectedVoiceName by remember { mutableStateOf(alarm?.voiceName ?: googleVoices[0].first) }
    var amPmOffset by remember { mutableStateOf<Int?>(alarm?.let { if (it.hour < 12) 0 else 12 }) }
    var hour by remember {
        mutableIntStateOf(alarm?.let { if (it.hour % 12 == 0) 12 else it.hour % 12 } ?: 12)
    }
    var minute by remember { mutableIntStateOf(alarm?.minute ?: 0) }
    var message by remember { mutableStateOf(alarm?.message ?: "") }
    var selectedDays by remember { mutableStateOf(alarm?.repeatDays ?: emptySet()) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
                .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("알람 설정", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(20.dp))

            // 오전/오후 버튼
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf(0 to "오전", 12 to "오후").forEach { (offset, label) ->
                    Button(
                        onClick = { amPmOffset = offset },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (amPmOffset == offset) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) { Text(label) }
                }
            }

            // 시간 입력 UI
            Row(verticalAlignment = Alignment.CenterVertically) {
                TimeInputUnit(
                    value = hour,
                    onValueChange = { hour = it },
                    range = 1..12,
                    label = "시"
                )
                Text(":", style = MaterialTheme.typography.displaySmall)
                TimeInputUnit(
                    value = minute,
                    onValueChange = { minute = it },
                    range = 0..59,
                    label = "분"
                )
            }

            Divider(modifier = Modifier.padding(vertical = 20.dp))

            Text("보이스 설정", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            var expanded by remember { mutableStateOf(false) }
            val selectedVoiceDisplay =
                googleVoices.find { it.first == selectedVoiceName }?.second ?: "목소리 선택"

            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(selectedVoiceDisplay)
                        Icon(
                            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null
                        )
                    }
                }

                androidx.compose.material3.DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .heightIn(max = 300.dp)
                ) {
                    googleVoices.forEach { (id, name) ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = {
                                Column {
                                    Text(name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        id,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            },
                            onClick = {
                                selectedVoiceName = id
                                expanded = false
                                scope.launch {
                                    TtsManager.fetchAndPlayVoice(
                                        context = context,
                                        text = "$name 입니다.",
                                        apiKey = "AIzaSyBM_69Za936hpHn115ZCDKRKg92xnlC-OU", // 상민님의 키 확인
                                        voiceName = id
                                    )
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 메시지 입력창
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("메시지") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 취소/저장 버튼
            Row {
                Button(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("취소") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (amPmOffset == null) {
                            android.widget.Toast.makeText(context, "오전/오후를 선택해주세요!", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            onSave(
                                if (hour == 12) amPmOffset!! else amPmOffset!! + hour,
                                minute,
                                message,
                                selectedDays,
                                selectedVoiceName,
                                true
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("저장") }
            }
        }
    }
} // AlarmEditScreen 종료

@Composable
fun TimeInputUnit(value: Int, onValueChange: (Int) -> Unit, range: IntRange, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = { onValueChange(if (value < range.last) value + 1 else range.first) }) {
            Icon(Icons.Default.KeyboardArrowUp, null)
        }
        Text(value.toString(), style = MaterialTheme.typography.displaySmall)
        IconButton(onClick = { onValueChange(if (value > range.first) value - 1 else range.last) }) {
            Icon(Icons.Default.KeyboardArrowDown, null)
        }
        Text(label)
    }
}