package com.example.activity_mainxml.ui.main

import com.example.activity_mainxml.model.AlarmItem
import androidx.compose.animation.*
import androidx.compose.foundation.background
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
    uiScale: String = "normal", // 💡 UI 크기 파라미터
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val dayLabels = listOf("월", "화", "수", "목", "금", "토", "일")

    // 💡 크기 배율 계산
    val scaleFactor = when (uiScale) {
        "small" -> 0.85f
        "large" -> 1.2f
        else -> 1.0f
    }

    val amPm = remember(alarm.hour) { if (alarm.hour < 12) "오전" else "오후" }
    val displayHour = remember(alarm.hour) { if (alarm.hour % 12 == 0) 12 else alarm.hour % 12 }
    val displayMinute = remember(alarm.minute) { String.format("%02d", alarm.minute) }

    val activeColor = MaterialTheme.colorScheme.primaryContainer
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant
    val selectedColor = MaterialTheme.colorScheme.secondaryContainer

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .heightIn(min = (if (isSimpleMode) 90.dp else 160.dp) * scaleFactor)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .pointerInput(isSelectionMode, alarm) {
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
                .padding(horizontal = 16.dp, vertical = (if (isSimpleMode) 10.dp else 12.dp) * scaleFactor)
                .fillMaxWidth()
                .wrapContentHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 8.dp).scale(scaleFactor)
                )
            }

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
                            style = (if (isSimpleMode) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge).copy(
                                fontSize = (if (isSimpleMode) 18.sp else 22.sp) * scaleFactor
                            ),
                            fontWeight = FontWeight.Bold,
                            color = if (alarm.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp * scaleFactor
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isSimpleMode && !isSelectionMode) {
                        Switch(
                            checked = alarm.isEnabled,
                            onCheckedChange = { 
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggle(it) 
                            },
                            modifier = Modifier.scale(0.6f * scaleFactor)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    if (!alarm.isSoundEnabled && !alarm.isVibrationEnabled) {
                        ModeIndicator(icon = Icons.AutoMirrored.Filled.VolumeOff, text = "무음", isEnabled = alarm.isEnabled, color = MaterialTheme.colorScheme.error, scaleFactor = scaleFactor)
                    } else {
                        if (alarm.isSoundEnabled) ModeIndicator(icon = Icons.AutoMirrored.Filled.VolumeUp, text = "사운드", isEnabled = alarm.isEnabled, color = Color(0xFF4CAF50), scaleFactor = scaleFactor)
                        if (alarm.isVibrationEnabled) ModeIndicator(icon = Icons.Default.Vibration, text = "진동", isEnabled = alarm.isEnabled, color = Color(0xFF2196F3), scaleFactor = scaleFactor)
                    }
                }

                if (!isSimpleMode) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(36.dp * scaleFactor)
                    ) {
                        if (!isSelectionMode) {
                            Switch(
                                checked = alarm.isEnabled,
                                onCheckedChange = { 
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggle(it) 
                                },
                                modifier = Modifier.scale(0.7f * scaleFactor)
                            )

                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDelete()
                                },
                                modifier = Modifier.size(28.dp * scaleFactor)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "삭제",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp * scaleFactor)
                                )
                            }
                        }
                    }
                }
            }

            if (!isSimpleMode) {
                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .weight(0.9f)
                        .height(110.dp * scaleFactor)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = alarm.message.ifBlank { "보이스 시간 알림" },
                        color = if (alarm.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp * scaleFactor
                        ),
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
    color: Color,
    scaleFactor: Float = 1.0f
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp * scaleFactor),
            tint = if (isEnabled) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp * scaleFactor
            ),
            color = if (isEnabled) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}
