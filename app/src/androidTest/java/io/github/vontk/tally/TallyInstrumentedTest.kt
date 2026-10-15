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
        rule.waitUntil(10000) {
            rule
                .onAllNodes(hasText("Tally added", substring = true))
                .fetchSemanticsNodes()
                .isEmpty()
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
            BackupCodec.decode(BackupCodec.encode(source).replace(Palette[0], "invalid"))
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
    fun versionOneDatabaseMigratesWithoutLosingTallies(): Unit = runBlocking {
        val context = rule.activity.applicationContext
        val name = "migration-${java.util.UUID.randomUUID()}.db"
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE subjects (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, color TEXT NOT NULL, position INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE tallies (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, subjectId TEXT NOT NULL, timestamp INTEGER NOT NULL, FOREIGN KEY(subjectId) REFERENCES subjects(id) ON DELETE CASCADE)")
                        db.execSQL("CREATE INDEX index_tallies_subjectId_timestamp ON tallies(subjectId, timestamp)")
                        db.execSQL("INSERT INTO subjects VALUES ('original', 'Little moments', '#A69AEE', 0)")
                        db.execSQL("INSERT INTO subjects VALUES ('custom', 'Custom', '#A69AEE', 1)")
                        db.execSQL("INSERT INTO tallies VALUES (1, 'original', 1791628800000)")
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        helper.writableDatabase
        helper.close()
        val database = androidx.room.Room.databaseBuilder(context, TallyDatabase::class.java, name)
            .addMigrations(TALLY_MIGRATION_1_2).build()
        try {
            val subjects = database.dao().subjects()
            assertEquals(Palette[0], subjects.first().color)
            assertEquals(1791628800000L, subjects.first().createdAt)
            assertFalse(subjects.first().negative)
            assertEquals("#A69AEE", subjects.last().color)
            assertEquals(1, database.dao().tallies().size)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun negativeTypeAndOrderSurviveBackup(): Unit = runBlocking {
        val negative = repository.create("Avoid distractions", Palette[2], negative = true)
        repository.edit(negative, "Distractions", Palette[3], negative = true)
        repository.move(negative, -1)
        val backup = repository.backup()
        assertEquals(negative, backup.subjects.first().id)
        assertTrue(backup.subjects.first().negative)
        assertTrue(backup.subjects.first().createdAt > 0)
        val decoded = BackupCodec.decode(BackupCodec.encode(backup))
        repository.restore(decoded)
        assertEquals(backup, repository.backup())
        repository.move(negative, 1)
        assertEquals(negative, repository.backup().subjects.last().id)
    }

    @Test
    fun historyTabsWithoutMockData(): Unit = runBlocking {
        rule.waitUntil(10000) {
            rule.onAllNodesWithText("History").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Settings").performClick()
        rule.onNodeWithText("Try example data").assertDoesNotExist()
        rule.onNodeWithText("Counter").performClick()
        rule.onNodeWithText("History").performClick()
        rule.onNodeWithText("Hours").performClick()
        rule.onNodeWithText("Month").performClick()
        rule.onNodeWithText("Days").performClick()
        rule.onNodeWithText("Exit preview").assertDoesNotExist()
        assertTrue(repository.backup().tallies.isEmpty())
    }
}
