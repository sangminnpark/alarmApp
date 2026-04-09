package com.example.activity_mainxml

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import java.util.Calendar
import java.util.Locale
import java.util.Random

// 1. 데이터 모델
data class AlarmItem(
    val id: Int = Random().nextInt(10000),
    var hour: Int,
    var minute: Int,
    var message: String,
    var isEnabled: Boolean = true,
    var repeatDays: Set<Int> = emptySet()
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

        // [핵심 로직] 요일이 비어있거나 오늘 요일이 포함되지 않으면 실행 안 함 (유령 알람)
        if (!days.contains(today)) return

        val msg = intent.getStringExtra("msg") ?: ""
        val alertIntent = Intent(context, AlarmAlertActivity::class.java).apply {
            putExtra("msg", msg)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(alertIntent)
    }
}

class AlarmAlertActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var message: String = ""
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var isAlarmActive = true

    private fun playAlarmVoice() {
        if (!isAlarmActive) return
        val calendar = Calendar.getInstance()
        val amPm = if (calendar.get(Calendar.AM_PM) == Calendar.AM) "오전" else "오후"
        val hour = calendar.get(Calendar.HOUR).let { if (it == 0) 12 else it }
        val minute = calendar.get(Calendar.MINUTE)

        val timeText = "현재 시간은 ${amPm} ${hour}시 ${minute}분입니다."
        val fullText = if (message.isBlank()) timeText else "$timeText $message"

        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
        }
        tts?.speak(fullText, TextToSpeech.QUEUE_FLUSH, params, "AlarmID")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        message = intent.getStringExtra("msg")?.trim() ?: ""
        tts = TextToSpeech(this, this)

        tts?.setOnUtteranceProgressListener(object :
            android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (isAlarmActive) handler.postDelayed({ playAlarmVoice() }, 2000)
            }

            override fun onError(utteranceId: String?) {
                if (isAlarmActive) handler.postDelayed({ playAlarmVoice() }, 2000)
            }
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

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
                    if (message.isNotBlank()) Text(
                        text = message,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(48.dp))
                    Button(
                        onClick = {
                            isAlarmActive =
                                false; handler.removeCallbacksAndMessages(null); finish()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("알람 종료", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.KOREAN
            playAlarmVoice()
        }
    }

    override fun onDestroy() {
        isAlarmActive = false
        handler.removeCallbacksAndMessages(null)
        tts?.stop()
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
            onSave = { h, m, msg, days ->
                val newAlarm = if (isAddingNew) AlarmItem(
                    hour = h,
                    minute = m,
                    message = msg,
                    repeatDays = days
                )
                else editingAlarm!!.copy(
                    hour = h,
                    minute = m,
                    message = msg,
                    repeatDays = days,
                    isEnabled = true
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
            LazyColumn(modifier = Modifier
                .padding(padding)
                .fillMaxSize()) {
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
    onSave: (Int, Int, String, Set<Int>) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var amPmOffset by remember { mutableStateOf<Int?>(alarm?.let { if (it.hour < 12) 0 else 12 }) }
    var hour by remember {
        mutableIntStateOf(alarm?.let { if (it.hour % 12 == 0) 12 else it.hour % 12 } ?: 12)
    }
    var minute by remember { mutableIntStateOf(alarm?.minute ?: 0) }
    var message by remember { mutableStateOf(alarm?.message ?: "") }
    var selectedDays by remember { mutableStateOf(alarm?.repeatDays ?: (1..7).toSet()) }
    val dayLabels = listOf("월", "화", "수", "목", "금", "토", "일")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }) {
        Text("알람 설정", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { amPmOffset = 0; focusManager.clearFocus() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (amPmOffset == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (amPmOffset == 0) Color.White else Color.Gray
                )
            ) { Text("오전") }
            Button(
                onClick = { amPmOffset = 12; focusManager.clearFocus() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (amPmOffset == 12) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (amPmOffset == 12) Color.White else Color.Gray
                )
            ) { Text("오후") }
        }
        Spacer(modifier = Modifier.height(30.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            TimeInputUnit(value = hour, onValueChange = { hour = it }, range = 1..12, label = "시")
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
        Spacer(modifier = Modifier.height(30.dp))
        Text("반복 요일", style = MaterialTheme.typography.bodyMedium)
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (i in 1..7) {
                FilterChip(
                    selected = selectedDays.contains(i),
                    onClick = {
                        selectedDays =
                            if (selectedDays.contains(i)) selectedDays - i else selectedDays + i; focusManager.clearFocus()
                    },
                    label = { Text(dayLabels[i - 1]) })
            }
        }
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("보이스 메시지 (비워두면 시간만 읽음)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("취소") }
            Button(onClick = {
                if (amPmOffset == null) Toast.makeText(
                    context,
                    "오전/오후를 선택해주세요!",
                    Toast.LENGTH_SHORT
                ).show()
                else onSave(
                    if (hour == 12) amPmOffset!! else amPmOffset!! + hour,
                    minute,
                    message,
                    selectedDays
                )
            }, modifier = Modifier.weight(1f)) { Text("저장") }
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