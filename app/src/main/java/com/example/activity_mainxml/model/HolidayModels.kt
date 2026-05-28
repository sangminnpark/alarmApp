package com.example.activity_mainxml.model

data class HolidayResponse(
    val response: HolidayResponseData
)

data class HolidayResponseData(
    val body: HolidayResponseBody
)

data class HolidayResponseBody(
    val items: HolidayItems?
)

data class HolidayItems(
    val item: Any? // 단일 항목일 경우 객체, 다수일 경우 리스트로 오기 때문에 Any로 처리
)

data class HolidayItem(
    val locdate: Int,   // 예: 20240101
    val dateName: String,
    val isHoliday: String // "Y" 또는 "N"
)
