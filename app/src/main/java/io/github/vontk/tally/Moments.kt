package io.github.vontk.tally

import java.time.*

/** Calendar boundaries are calculated in the current device zone, never with 24-hour offsets. */
object Moments {
    fun day(timestamp: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()

    fun counts(entries: List<Tally>, zone: ZoneId): Map<LocalDate, Int> =
        entries.groupingBy { day(it.timestamp, zone) }.eachCount()

    fun week(counts: Map<LocalDate, Int>, today: LocalDate): Int =
        (0L..6L).sumOf { counts[today.minusDays(it)] ?: 0 }

    fun streak(counts: Map<LocalDate, Int>, today: LocalDate): Int {
        var cursor = if ((counts[today] ?: 0) > 0) today else today.minusDays(1)
        var result = 0
        while ((counts[cursor] ?: 0) > 0) {
            result++
            cursor = cursor.minusDays(1)
        }
        return result
    }

    fun preview(today: LocalDate, zone: ZoneId, subjectId: String): List<Tally> = buildList {
        // Handoff sample: 7 today, 38 in seven days, a five-day streak, 148 total.
        val recent = listOf(7, 9, 6, 8, 5, 0, 3, 5, 8, 1, 4, 7)
        val times = listOf("07:12", "07:44", "08:18", "08:18", "09:04", "09:21", "09:41")
        fun addDay(offset: Int, count: Int) {
            repeat(count) { index ->
                val time =
                    if (offset == 0) LocalTime.parse(times[index])
                    else LocalTime.of(7 + (index * 3) % 16, (index * 17 + offset * 7) % 60)
                add(
                    Tally(
                        size.toLong() + 1,
                        subjectId,
                        today
                            .minusDays(offset.toLong())
                            .atTime(time)
                            .atZone(zone)
                            .toInstant()
                            .toEpochMilli(),
                    )
                )
            }
        }
        val olderStart = maxOf(12, today.dayOfMonth + 1)
        for (index in 10 downTo 0) addDay(olderStart + index, if (index == 10) 5 else 8)
        for (offset in recent.indices.reversed()) addDay(offset, recent[offset])
    }
}
