package com.example.activity_mainxml.util

import android.content.Context
import com.example.activity_mainxml.data.HolidayRepository
import java.util.Calendar

object HolidayUtil {
    /**
     * 오늘이 한국의 공휴일인지 확인합니다 (저장된 API 데이터 기준).
     */
    fun isHoliday(context: Context, calendar: Calendar): Boolean {
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        
        // 날짜 형식 맞추기 (YYYYMMDD)
        val todayInt = year * 10000 + month * 100 + day
        
        val savedHolidays = HolidayRepository.getSavedHolidays(context)
        return savedHolidays.contains(todayInt)
    }
}
