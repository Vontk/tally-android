package io.github.vontk.tally

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class MomentsTest {
    private val zone = ZoneId.of("America/New_York")

    private fun entry(date: String) =
        Tally(1, "s", ZonedDateTime.parse(date).toInstant().toEpochMilli())

    @Test
    fun weekUsesSevenCalendarDaysAndExcludesFuture() {
        val today = LocalDate.of(2026, 10, 10)
        val counts =
            mapOf(
                today to 2,
                today.minusDays(6) to 3,
                today.minusDays(7) to 10,
                today.plusDays(1) to 20,
            )
        assertEquals(5, Moments.week(counts, today))
    }

    @Test
    fun streakCanContinueFromYesterdayButStopsAtGap() {
        val today = LocalDate.of(2026, 10, 10)
        val counts =
            mapOf(today.minusDays(1) to 1, today.minusDays(2) to 4, today.minusDays(4) to 1)
        assertEquals(2, Moments.streak(counts, today))
        assertEquals(3, Moments.streak(counts + (today to 1), today))
        assertEquals(0, Moments.streak(emptyMap(), today))
    }

    @Test
    fun dstRepeatedHourRemainsSameCalendarDay() {
        val a = entry("2026-11-01T01:30:00-04:00[America/New_York]")
        val b = entry("2026-11-01T01:30:00-05:00[America/New_York]")
        assertEquals(mapOf(LocalDate.of(2026, 11, 1) to 2), Moments.counts(listOf(a, b), zone))
    }

    @Test
    fun timezoneChangeRecalculatesDay() {
        val timestamp = Instant.parse("2026-10-10T01:00:00Z").toEpochMilli()
        assertEquals(
            LocalDate.of(2026, 10, 9),
            Moments.day(timestamp, ZoneId.of("America/Argentina/Buenos_Aires")),
        )
        assertEquals(LocalDate.of(2026, 10, 10), Moments.day(timestamp, ZoneId.of("Asia/Tokyo")))
    }

    @Test
    fun previewHasSevenTodayAndUniqueIds() {
        val today = LocalDate.of(2026, 10, 10)
        val preview = Moments.preview(today, zone, "s")
        assertEquals(7, Moments.counts(preview, zone)[today])
        assertEquals(preview.size, preview.map { it.id }.distinct().size)
        assertTrue(preview.all { it.subjectId == "s" })
    }

    @Test
    fun namesAndColorsAreValidated() {
        validateSubject("  Water  ", "#abcdef")
        listOf("" to "#abcdef", "a".repeat(37) to "#abcdef", "Water" to "oops").forEach {
            (name, hex) ->
            assertThrows(IllegalArgumentException::class.java) { validateSubject(name, hex) }
        }
    }
}
