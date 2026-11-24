package dev.axkit.core

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** Korean wording for numbers and times, kept here so it is tested once. Same words as iOS Format.swift. */
object Format {
    val seoul: ZoneId = ZoneId.of("Asia/Seoul")
    private val grouped = NumberFormat.getIntegerInstance(Locale.KOREA)
    private fun g(n: Long) = grouped.format(n)

    /** 12500 → "1만 2,500원", 980 → "980원", 1234567 → "123만 4,567원". */
    fun won(amount: Double): String {
        val value = abs(amount).roundToLong()
        val man = value / 10_000; val rest = value % 10_000
        var text = when {
            man == 0L -> "${g(rest)}원"
            rest == 0L -> "${g(man)}만원"
            else -> "${g(man)}만 ${g(rest)}원"
        }
        if (amount < 0) text = "-$text"
        return text
    }

    fun money(amount: Double, currency: String): String = when (currency.uppercase()) {
        "KRW" -> won(amount)
        "USD" -> dollars(amount)
        else -> String.format(Locale.US, "%.2f %s", amount, currency)
    }

    /** Small API costs: "$0.42", "$0.003" → "1센트 미만". */
    fun dollars(amount: Double): String = if (amount > 0 && amount < 0.01) "1센트 미만" else String.format(Locale.US, "$%.2f", amount)

    /** "방금", "5분 전", "3시간 전", "어제", "11월 3일". */
    fun relative(date: Instant, now: Instant = Instant.now(), zone: ZoneId = seoul): String {
        val seconds = (now.toEpochMilli() - date.toEpochMilli()) / 1000.0
        if (seconds < 60) return "방금"
        if (seconds < 3600) return "${(seconds / 60).toInt()}분 전"
        val d = date.atZone(zone).toLocalDate(); val n = now.atZone(zone).toLocalDate()
        if (d == n) return "${(seconds / 3600).toInt()}시간 전"
        if (d == n.minusDays(1)) return "어제"
        return "${d.monthValue}월 ${d.dayOfMonth}일"
    }

    /** "오후 3:20". */
    fun clock(date: Instant, zone: ZoneId = seoul): String {
        val t = date.atZone(zone)
        val ampm = if (t.hour < 12) "오전" else "오후"
        val h = if (t.hour % 12 == 0) 12 else t.hour % 12
        return "$ampm $h:${t.minute.toString().padStart(2, '0')}"
    }

    /** "오후 5:45까지", "내일 오전 9:45까지", "11월 21일까지". */
    fun deadline(date: Instant, now: Instant = Instant.now(), zone: ZoneId = seoul): String {
        val d = date.atZone(zone).toLocalDate(); val n = now.atZone(zone).toLocalDate()
        if (d == n) return "${clock(date, zone)}까지"
        if (d == n.plusDays(1)) return "내일 ${clock(date, zone)}까지"
        return "${d.monthValue}월 ${d.dayOfMonth}일까지"
    }

    /** "11월 18일". */
    fun monthDay(date: Instant, zone: ZoneId = seoul): String { val d = date.atZone(zone); return "${d.monthValue}월 ${d.dayOfMonth}일" }

    private val weekdays = listOf("월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일")

    /** The line between days in a room: "오늘", "어제", "11월 18일 화요일", "2025년 12월 31일 수요일". */
    fun dayLine(date: Instant, now: Instant = Instant.now(), zone: ZoneId = seoul): String {
        val d = date.atZone(zone).toLocalDate(); val n = now.atZone(zone).toLocalDate()
        if (d == n) return "오늘"
        if (d == n.minusDays(1)) return "어제"
        val w = weekdays[d.dayOfWeek.value - 1]
        return if (d.year == n.year) "${d.monthValue}월 ${d.dayOfMonth}일 $w" else "${d.year}년 ${d.monthValue}월 ${d.dayOfMonth}일 $w"
    }

    fun ymd(date: Instant, zone: ZoneId = seoul): String = date.atZone(zone).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
    fun ymd(date: LocalDate): String = date.format(DateTimeFormatter.ISO_LOCAL_DATE)

    /** Subject particle by the last syllable: "개발 봇" → "개발 봇이", "비서" → "비서가". */
    fun subject(name: String): String {
        val last = name.lastOrNull() ?: return name
        if (last.code !in 0xAC00..0xD7A3) return name + "이(가)"
        return name + if ((last.code - 0xAC00) % 28 == 0) "가" else "이"
    }

    fun duration(seconds: Double): String {
        val s = seconds.toInt()
        return when {
            s < 3600 -> "${maxOf(s / 60, 1)}분"
            s < 86_400 -> "${s / 3600}시간"
            else -> "${s / 86_400}일"
        }
    }
}
