package com.example.activity_mainxml.ui.main

import com.example.activity_mainxml.model.AlarmItem
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AlarmRow(
    alarm: AlarmItem,
    isSimpleMode: Boolean = false,
    isSelected: Boolean = false, // 💡 선택 상태
    isSelectionMode: Boolean = false, // 💡 선택 모드 활성화 여부
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit, // 💡 롱클릭 핸들러 추가
    onDelete: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val dayLabels = listOf("월", "화", "수", "목", "금", "토", "일")

    val amPm = remember(alarm.hour) { if (alarm.hour < 12) "오전" else "오후" }
    val displayHour = remember(alarm.hour) { if (alarm.hour % 12 == 0) 12 else alarm.hour % 12 }
    val displayMinute = remember(alarm.minute) { String.format("%02d", alarm.minute) }

    // 파스텔 톤 색상
    val activeColor = Color(0xFFE3F2FD) 
    val inactiveColor = Color(0xFFF5F5F5)
    val selectedColor = MaterialTheme.colorScheme.primaryContainer // 💡 선택 시 색상

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .heightIn(min = if (isSimpleMode) 90.dp else 160.dp) // 💡 지표 추가를 위해 높이 약간 조정
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .pointerInput(isSelectionMode) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick() 
                    }
                )
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> selectedColor
                alarm.isEnabled -> activeColor
                else -> inactiveColor
            }
        )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = if (isSimpleMode) 10.dp else 12.dp)
                .fillMaxWidth()
                .wrapContentHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 💡 선택 모드일 때 체크박스 표시
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            // 💡 왼쪽 영역: 정보 및 제어
            Column(
                modifier = Modifier.weight(1.1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "$amPm $displayHour:$displayMinute",
                            style = if (isSimpleMode) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
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
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }

                    if (isSimpleMode && !isSelectionMode) {
                        Switch(
                            checked = alarm.isEnabled,
                            onCheckedChange = { 
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggle(it) 
                            },
                            modifier = Modifier.scale(0.6f)
                        )
                    }
                }

                // 💡 간단 모드에서도 지표 표시
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    if (!alarm.isSoundEnabled && !alarm.isVibrationEnabled) {
                        ModeIndicator(icon = Icons.AutoMirrored.Filled.VolumeOff, text = "무음", isEnabled = alarm.isEnabled, color = Color.Gray)
                    } else {
                        if (alarm.isSoundEnabled) ModeIndicator(icon = Icons.AutoMirrored.Filled.VolumeUp, text = "사운드", isEnabled = alarm.isEnabled, color = Color(0xFF4CAF50))
                        if (alarm.isVibrationEnabled) ModeIndicator(icon = Icons.Default.Vibration, text = "진동", isEnabled = alarm.isEnabled, color = Color(0xFF2196F3))
                    }
                }

                if (!isSimpleMode) {
                    // 💡 상세 모드에서만 보이는 버튼 영역
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(36.dp)
                    ) {
                        if (!isSelectionMode) {
                            Switch(
                                checked = alarm.isEnabled,
                                onCheckedChange = { 
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggle(it) 
                                },
                                modifier = Modifier.scale(0.7f)
                            )

                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                    }
                }
            }

            if (!isSimpleMode) {
                Spacer(modifier = Modifier.width(8.dp))

                // 💡 오른쪽 영역 (상세 모드 전용): 메시지 텍스트
                Box(
                    modifier = Modifier
                        .weight(0.9f)
                        .height(110.dp)
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
