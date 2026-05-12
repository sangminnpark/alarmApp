package com.example.activity_mainxml.ui.main

import com.example.activity_mainxml.model.AlarmItem
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AlarmRow(
    alarm: AlarmItem,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dayLabels = listOf("월", "화", "수", "목", "금", "토", "일")

    val amPm = remember(alarm.hour) { if (alarm.hour < 12) "오전" else "오후" }
    val displayHour = remember(alarm.hour) { if (alarm.hour % 12 == 0) 12 else alarm.hour % 12 }
    val displayMinute = remember(alarm.minute) { String.format("%02d", alarm.minute) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        onClick = onClick,
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
                Text(
                    text = "$amPm $displayHour:$displayMinute",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (alarm.isEnabled) Color.Unspecified else Color.Gray
                )

                if (alarm.repeatDays.isNotEmpty()) {
                    val daysText = remember(alarm.repeatDays) {
                        alarm.repeatDays.sortedBy { if (it == 1) 8 else it }
                            .joinToString(", ") { dayInt ->
                                val index = if (dayInt == 1) 6 else dayInt - 2
                                dayLabels[index]
                            }
                    }
                    Text(
                        text = "반복: $daysText",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                } else {
                    Text(
                        text = "반복 없음",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                }

                Text(
                    text = alarm.message.ifBlank { "보이스 시간 알림" },
                    color = if (alarm.isEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = onToggle
                )

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(onClick = {
                    Log.d("ALARM_DEBUG", "삭제 버튼 클릭됨: ${alarm.id}")
                    onDelete()
                }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "삭제",
                        tint = Color.Gray.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
