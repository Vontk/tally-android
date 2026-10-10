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
        for (offset in 49 downTo 0) {
            val count = if (offset == 0) 7 else (offset * 13 + 5) % 12
            repeat(count) { j ->
                add(
                    Tally(
                        size.toLong() + 1,
                        subjectId,
                        today
                            .minusDays(offset.toLong())
                            .atTime(7 + (j * 3) % 16, (j * 17 + offset * 7) % 60)
                            .atZone(zone)
                            .toInstant()
                            .toEpochMilli(),
                    )
                )
            }
        }
    }
}
