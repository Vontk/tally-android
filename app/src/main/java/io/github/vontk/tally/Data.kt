package io.github.vontk.tally

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import java.util.UUID
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

val Context.settingsStore by preferencesDataStore("settings")
val Palette = listOf("#F5F5F0", "#B8BDC7", "#FFFFFF", "#EF6A6A", "#F39B59", "#F0CC65", "#A8CA73", "#65C6A1", "#63C8CC", "#67A2EF", "#8588ED", "#BD87DE", "#EE91C0", "#C49C82", "#A69AEE", "#7FB5A4", "#E3A77E", "#D58AAB", "#82AEDD", "#D2BD78")

@Entity(tableName = "subjects")
data class Subject(
    @PrimaryKey val id: String,
    val name: String,
    val color: String,
    val position: Int,
    @ColumnInfo(defaultValue = "0") val negative: Boolean = false,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0,
)

@Entity(
    tableName = "tallies",
    foreignKeys =
        [
            ForeignKey(
                entity = Subject::class,
                parentColumns = ["id"],
                childColumns = ["subjectId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index(value = ["subjectId", "timestamp"])],
)
data class Tally(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: String,
    val timestamp: Long,
)

data class SubjectCount(@Embedded val subject: Subject, val count: Long)

data class Preferences(val selected: String = "", val light: Boolean = false)

data class Backup(
    val subjects: List<Subject>,
    val tallies: List<Tally>,
    val preferences: Preferences,
)

@Dao
interface TallyDao {
    @Query(
        "SELECT subjects.*, COUNT(tallies.id) AS count FROM subjects LEFT JOIN tallies ON subjects.id = tallies.subjectId GROUP BY subjects.id ORDER BY subjects.position"
    )
    fun observeSubjects(): Flow<List<SubjectCount>>

    @Query("SELECT * FROM tallies WHERE subjectId = :subjectId ORDER BY id")
    fun observeTallies(subjectId: String): Flow<List<Tally>>

    @Query("SELECT * FROM subjects ORDER BY position") suspend fun subjects(): List<Subject>

    @Query("SELECT * FROM tallies ORDER BY id") suspend fun tallies(): List<Tally>

    @Insert suspend fun insertSubject(subject: Subject)

    @Insert suspend fun insertTallies(tallies: List<Tally>): List<Long>

    @Query("UPDATE subjects SET name = :name, color = :color, negative = :negative WHERE id = :id")
    suspend fun editSubject(id: String, name: String, color: String, negative: Boolean)

    @Query(
        "DELETE FROM tallies WHERE id = (SELECT MAX(id) FROM tallies WHERE subjectId = :subjectId)"
    )
    suspend fun undo(subjectId: String)

    @Query("DELETE FROM subjects WHERE id = :id") suspend fun deleteSubject(id: String)

    @Query("DELETE FROM tallies WHERE id = :id AND subjectId = :subjectId")
    suspend fun deleteTally(id: Long, subjectId: String)

    @Query("UPDATE subjects SET position = :position WHERE id = :id")
    suspend fun setPosition(id: String, position: Int)

    @Query("DELETE FROM subjects") suspend fun clearSubjects()
}

@Database(entities = [Subject::class, Tally::class], version = 2, exportSchema = true)
abstract class TallyDatabase : RoomDatabase() {
    abstract fun dao(): TallyDao
}

internal val TALLY_MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE subjects ADD COLUMN negative INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE subjects ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE subjects SET createdAt = COALESCE((SELECT MIN(timestamp) FROM tallies WHERE subjectId = subjects.id), ?)", arrayOf<Any>(System.currentTimeMillis()))
        db.execSQL("UPDATE subjects SET color = '#F5F5F0' WHERE position = 0 AND name = 'Little moments' AND color = '#A69AEE'")
    }
}

class TallyRepository(context: Context) {
    private val store = context.settingsStore
    private val selectedKey = stringPreferencesKey("selected")
    private val lightKey = booleanPreferencesKey("light")
    private val initializedKey = booleanPreferencesKey("initialized")
    private val database =
        Room.databaseBuilder(context, TallyDatabase::class.java, "tally.db")
            .addMigrations(TALLY_MIGRATION_1_2).build()
    private val dao = database.dao()
    private val lock = Mutex()
    val subjects = dao.observeSubjects()
    val preferences =
        store.data.map { Preferences(it[selectedKey].orEmpty(), it[lightKey] ?: false) }

    fun tallies(subjectId: String) = dao.observeTallies(subjectId)

    suspend fun initialize() = lock.withLock {
        if (store.data.first()[initializedKey] != true) {
            if (dao.subjects().isEmpty()) {
                val subject = Subject(UUID.randomUUID().toString(), "Little moments", Palette[0], 0, createdAt = System.currentTimeMillis())
                dao.insertSubject(subject)
                select(subject.id)
            }
            store.edit { it[initializedKey] = true }
        }
    }

    suspend fun setTheme(light: Boolean) {
        store.edit { it[lightKey] = light }
    }

    suspend fun deleteSubject(id: String) = lock.withLock {
        // Mark initialized before deletion so an intentionally empty database stays empty.
        store.edit { it[initializedKey] = true }
        val old = dao.subjects()
        val deletedPosition = old.find { it.id == id }?.position ?: -1
        val remaining = database.withTransaction {
            dao.deleteSubject(id)
            dao.subjects()
        }
        if (preferences.first().selected == id)
            select(
                (remaining.firstOrNull { it.position > deletedPosition } ?: remaining.firstOrNull())
                    ?.id
                    .orEmpty()
            )
    }

    suspend fun deleteTally(id: Long, subjectId: String) = lock.withLock {
        dao.deleteTally(id, subjectId)
    }

    suspend fun select(id: String) {
        store.edit { it[selectedKey] = id }
    }

    suspend fun toggleTheme() {
        store.edit { it[lightKey] = !(it[lightKey] ?: false) }
    }

    suspend fun create(name: String, color: String, negative: Boolean = false): String = lock.withLock {
        validateSubject(name, color)
        val subject =
            Subject(
                UUID.randomUUID().toString(),
                name.trim(),
                color.uppercase(),
                (dao.subjects().maxOfOrNull { it.position } ?: -1) + 1,
                negative,
                System.currentTimeMillis(),
            )
        dao.insertSubject(subject)
        select(subject.id)
        subject.id
    }

    suspend fun edit(id: String, name: String, color: String, negative: Boolean = false) = lock.withLock {
        validateSubject(name, color)
        dao.editSubject(id, name.trim(), color.uppercase(), negative)
    }

    suspend fun move(id: String, direction: Int) = lock.withLock {
        val ordered = dao.subjects().toMutableList()
        val from = ordered.indexOfFirst { it.id == id }
        val to = from + direction
        if (from >= 0 && to in ordered.indices) {
            java.util.Collections.swap(ordered, from, to)
            database.withTransaction { ordered.forEachIndexed { index, subject -> dao.setPosition(subject.id, index) } }
        }
    }

    suspend fun add(id: String, timestamp: Long) = lock.withLock {
        dao.insertTallies(listOf(Tally(subjectId = id, timestamp = timestamp))).first()
    }

    suspend fun undo(id: String) = lock.withLock { dao.undo(id) }

    suspend fun backup(): Backup = lock.withLock {
        database.withTransaction { Backup(dao.subjects(), dao.tallies(), preferences.first()) }
    }

    suspend fun restore(backup: Backup) = lock.withLock {
        val oldPreferences = preferences.first()
        try {
            database.withTransaction {
                dao.clearSubjects()
                backup.subjects.forEach { dao.insertSubject(it) }
                dao.insertTallies(backup.tallies)
                store.edit {
                    it[selectedKey] = backup.preferences.selected
                    it[lightKey] = backup.preferences.light
                    it[initializedKey] = true
                }
            }
        } catch (error: Exception) {
            store.edit {
                it[selectedKey] = oldPreferences.selected
                it[lightKey] = oldPreferences.light
            }
            throw error
        }
    }
}

fun validateSubject(name: String, color: String) {
    require(name.trim().isNotEmpty() && name.trim().length <= 36) {
        "Use a subject name of 1–36 characters."
    }
    require(Regex("#[0-9a-fA-F]{6}").matches(color)) {
        "Use a six-digit hex color, such as #A69AEE."
    }
}

object BackupCodec {
    const val MAX_BYTES = 20 * 1024 * 1024

    fun encode(backup: Backup): String =
        JSONObject()
            .apply {
                put("format", "tally-android")
                put("version", 1)
                put(
                    "subjects",
                    JSONArray().apply {
                        backup.subjects.forEach { s ->
                            put(
                                JSONObject()
                                    .put("id", s.id)
                                    .put("name", s.name)
                                    .put("color", s.color)
                                    .put("position", s.position)
                                    .put("negative", s.negative)
                                    .put("createdAt", s.createdAt)
                            )
                        }
                    },
                )
                put(
                    "tallies",
                    JSONArray().apply {
                        backup.tallies.forEach { t ->
                            put(
                                JSONObject()
                                    .put("id", t.id)
                                    .put("subjectId", t.subjectId)
                                    .put("timestamp", t.timestamp)
                            )
                        }
                    },
                )
                put(
                    "preferences",
                    JSONObject()
                        .put("selected", backup.preferences.selected)
                        .put("light", backup.preferences.light),
                )
            }
            .toString(2)

    fun decode(text: String): Backup {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
            "Backup is too large (maximum 20 MB)."
        }
        val json = JSONObject(text)
        require(json.getString("format") == "tally-android" && json.getInt("version") == 1) {
            "This is not a supported Tally backup."
        }
        val subjects =
            json.getJSONArray("subjects").let { array ->
                (0 until array.length()).map { i ->
                    val s = array.getJSONObject(i)
                    Subject(
                        s.getString("id"),
                        s.getString("name"),
                        s.getString("color"),
                        s.getInt("position"),
                        s.optBoolean("negative", false),
                        s.optLong("createdAt", 0),
                    )
                }
            }
        require(subjects.size <= 10000) { "Backup contains too many subjects." }
        subjects.forEach {
            validateSubject(it.name, it.color)
            require(it.id.isNotBlank() && it.id.length <= 128 && it.position >= 0 && it.createdAt >= 0)
        }
        val ids = subjects.map { it.id }.toSet()
        require(
            ids.size == subjects.size &&
                subjects.map { it.position }.distinct().size == subjects.size
        ) {
            "Duplicate subjects in backup."
        }
        val tallies =
            json.getJSONArray("tallies").let { array ->
                (0 until array.length()).map { i ->
                    val t = array.getJSONObject(i)
                    Tally(t.getLong("id"), t.getString("subjectId"), t.getLong("timestamp"))
                }
            }
        require(tallies.map { it.id }.distinct().size == tallies.size) {
            "Duplicate tally IDs in backup."
        }
        tallies.forEach {
            require(
                it.id > 0 &&
                    it.id < Long.MAX_VALUE &&
                    it.subjectId in ids &&
                    it.timestamp in 0..253402300799999L
            ) {
                "Invalid tally in backup."
            }
        }
        val pref = json.getJSONObject("preferences")
        val preferences = Preferences(pref.getString("selected"), pref.getBoolean("light"))
        require(preferences.selected in ids || (ids.isEmpty() && preferences.selected.isEmpty())) {
            "Selected subject is missing."
        }
        return Backup(subjects.sortedBy { it.position }, tallies.sortedBy { it.id }, preferences)
    }
}
