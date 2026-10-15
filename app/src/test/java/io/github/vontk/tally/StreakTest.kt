package io.github.vontk.tally

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakTest {
    private val today = LocalDate.of(2026, 10, 15)

    @Test fun negativeCountsCompletedCleanDays() {
        assertEquals(5, Moments.streak(emptyMap(), today, true, today.minusDays(5)))
        assertEquals(0, Moments.streak(emptyMap(), today, true, today))
        assertEquals(2, Moments.streak(mapOf(today.minusDays(3) to 1), today, true, today.minusDays(10)))
    }

    @Test fun addingNegativeTallyBreaksStreakAndUndoRestoresIt() {
        val counts = mapOf(today.minusDays(4) to 1)
        assertEquals(3, Moments.streak(counts, today, true, today.minusDays(10)))
        assertEquals(0, Moments.streak(counts + (today to 1), today, true, today.minusDays(10)))
        assertEquals(3, Moments.streak(counts, today, true, today.minusDays(10)))
    }

    @Test fun positiveAllowsTodayToBeInProgress() {
        val counts = mapOf(today.minusDays(1) to 2, today.minusDays(2) to 1)
        assertEquals(2, Moments.streak(counts, today))
        assertEquals(3, Moments.streak(counts + (today to 1), today))
        assertEquals(0, Moments.streak(mapOf(today.minusDays(2) to 1), today))
    }
}
