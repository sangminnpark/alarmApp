package com.example.activity_mainxml.ui.main

import com.example.activity_mainxml.model.AlarmItem
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AlarmRow(
    alarm: AlarmItem,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val dayLabels = listOf("월", "화", "수", "목", "금", "토", "일")

    val amPm = remember(alarm.hour) { if (alarm.hour < 12) "오전" else "오후" }
    val displayHour = remember(alarm.hour) { if (alarm.hour % 12 == 0) 12 else alarm.hour % 12 }
    val displayMinute = remember(alarm.minute) { String.format("%02d", alarm.minute) }

    // 파스텔 톤 색상
    val activeColor = Color(0xFFE3F2FD) // 연한 파란색
    val inactiveColor = Color(0xFFF5F5F5) // 연한 회색

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp), // 상하 여백 약간 축소
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (alarm.isEnabled) activeColor else inactiveColor
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp) // 내부 여백 축소
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 💡 왼쪽 영역 (55%로 약간 확장): 정보 및 제어
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp) // 일정한 간격 유지
            ) {
                Column {
                    Text(
                        text = "$amPm $displayHour:$displayMinute",
                        style = MaterialTheme.typography.titleLarge, // 💡 폰트 크기 약간 축소
                        fontWeight = FontWeight.Bold,
                        color = if (alarm.isEnabled) Color.Unspecified else Color.Gray
                    )

                    val daysText = remember(alarm.repeatDays) {
                        if (alarm.repeatDays.isNotEmpty()) {
                            alarm.repeatDays.sortedBy { if (it == 1) 8 else it }
                                .joinToString(", ") { dayInt ->
                                    val index = if (dayInt == 1) 6 else dayInt - 2
                                    dayLabels[index]
                                }
                        } else "반복 없음"
                    }
                    Text(
                        text = daysText,
                        style = MaterialTheme.typography.labelSmall, // 💡 크기 축소
                        color = Color.Gray
                    )
                }

                // 💡 조작 버튼 영역 (더 컴팩트하게)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(36.dp)
                ) {
                    Switch(
                        checked = alarm.isEnabled,
                        onCheckedChange = { 
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggle(it) 
                        },
                        modifier = Modifier.scale(0.7f) // 💡 조금 더 축소
                    )

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            Log.d("ALARM_DEBUG", "삭제 버튼 클릭됨: ${alarm.id}")
                            onDelete()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "삭제",
                            tint = Color.Gray.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // 💡 알림 모드 아이콘 (명확히 보이도록 위치 고정)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    if (!alarm.isSoundEnabled && !alarm.isVibrationEnabled) {
                        ModeIndicator(
                            icon = Icons.AutoMirrored.Filled.VolumeOff,
                            text = "무음",
                            isEnabled = alarm.isEnabled,
                            color = Color.Gray
                        )
                    } else {
                        if (alarm.isSoundEnabled) {
                            ModeIndicator(
                                icon = Icons.AutoMirrored.Filled.VolumeUp,
                                text = "사운드",
                                isEnabled = alarm.isEnabled,
                                color = Color(0xFF4CAF50)
                            )
                        }
                        if (alarm.isVibrationEnabled) {
                            ModeIndicator(
                                icon = Icons.Default.Vibration,
                                text = "진동",
                                isEnabled = alarm.isEnabled,
                                color = Color(0xFF2196F3)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 💡 오른쪽 영역 (45%): 메시지 텍스트
            Box(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.3f))
                    .padding(8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = alarm.message.ifBlank { "보이스 시간 알림" },
                    color = if (alarm.isEnabled) MaterialTheme.colorScheme.primary else Color.Gray,
                    style = MaterialTheme.typography.bodySmall,
                    minLines = 3
                )
            }
        }
    }
}

@Composable
fun ModeIndicator(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    isEnabled: Boolean,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (isEnabled) color else Color.LightGray
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (isEnabled) color else Color.LightGray
        )
    }
}
