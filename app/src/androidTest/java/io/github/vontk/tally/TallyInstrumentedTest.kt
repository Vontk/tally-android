package io.github.vontk.tally

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TallyInstrumentedTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val repository
        get() = (rule.activity.application as TallyApplication).repository

    @Before
    fun reset(): Unit = runBlocking {
        repository.restore(
            Backup(
                listOf(Subject("test-subject", "Little moments", Palette[0], 0)),
                emptyList(),
                Preferences("test-subject", false),
            )
        )
    }

    @Test
    fun rapidTapsUndoAndPersistence(): Unit = runBlocking {
        rule.waitUntil(10000) {
            rule
                .onAllNodesWithContentDescription("Add a tally for Little moments")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        repeat(20) {
            rule.onNodeWithContentDescription("Add a tally for Little moments").performClick()
        }
        awaitTallyCount(20)
        rule.waitUntil(10000) {
            rule
                .onAllNodes(hasText("Undo last tally") and isEnabled())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        rule.onNodeWithText("Undo last tally").performScrollTo().performClick()
        awaitTallyCount(19)
        rule.activityRule.scenario.recreate()
        rule.waitUntil(10000) {
            rule
                .onAllNodesWithContentDescription("Add a tally for Little moments")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        assertEquals(19, repository.backup().tallies.size)
    }

    private suspend fun awaitTallyCount(expected: Int) {
        try {
            withTimeout(10000) { repository.tallies("test-subject").first { it.size == expected } }
        } catch (timeout: TimeoutCancellationException) {
            val actual = repository.tallies("test-subject").first().size
            throw AssertionError("Expected $expected saved tallies, found $actual", timeout)
        }
    }

    @Test
    fun backupRoundTripAndInvalidInput(): Unit = runBlocking {
        repository.add("test-subject", 1791628800000L)
        val source = repository.backup()
        val decoded = BackupCodec.decode(BackupCodec.encode(source))
        assertEquals(source, decoded)
        assertThrows(Exception::class.java) { BackupCodec.decode("{broken") }
        assertThrows(Exception::class.java) {
            BackupCodec.decode(BackupCodec.encode(source).replace("tally-android", "unknown"))
        }
        assertThrows(Exception::class.java) {
            BackupCodec.decode(BackupCodec.encode(source).replace("1791628800000", "-1"))
        }
        assertThrows(Exception::class.java) {
            BackupCodec.decode(BackupCodec.encode(source).replace("#A69AEE", "invalid"))
        }
        assertEquals(source, repository.backup())
        repository.undo("test-subject")
        repository.restore(decoded)
        assertEquals(source, repository.backup())
    }

    @Test
    fun subjectIsolationAndUndoUsesInsertionOrder(): Unit = runBlocking {
        repository.add("test-subject", 2000)
        repository.add("test-subject", 1000)
        val other = repository.create("Water", Palette[1])
        repository.add(other, 3000)
        repository.undo("test-subject")
        assertEquals(listOf(2000L), repository.tallies("test-subject").first().map { it.timestamp })
        assertEquals(1, repository.tallies(other).first().size)
        repository.edit(other, "Tea", Palette[2])
        assertEquals(
            "Tea",
            repository.subjects.first().first { it.subject.id == other }.subject.name,
        )
    }

    @Test
    fun historyTabsAndPreviewIsolation(): Unit = runBlocking {
        rule.waitUntil(10000) {
            rule.onAllNodesWithText("History").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Settings").performClick()
        rule.onNodeWithText("Try example data").performClick()
        rule.onNodeWithText("History").performClick()
        rule.onNodeWithText("Hours").performClick()
        rule.onNodeWithText("Month").performClick()
        rule.onNodeWithText("Days").performClick()
        rule.onNodeWithText("Exit preview").assertExists()
        assertTrue(repository.backup().tallies.isEmpty())
    }
}
