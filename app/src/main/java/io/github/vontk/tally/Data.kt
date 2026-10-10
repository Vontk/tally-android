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
val Palette = listOf("#A69AEE", "#7FB5A4", "#E3A77E", "#D58AAB", "#82AEDD", "#D2BD78")

@Entity(tableName = "subjects")
data class Subject(
    @PrimaryKey val id: String,
    val name: String,
    val color: String,
    val position: Int,
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

    @Insert suspend fun insertTallies(tallies: List<Tally>)

    @Query("UPDATE subjects SET name = :name, color = :color WHERE id = :id")
    suspend fun editSubject(id: String, name: String, color: String)

    @Query(
        "DELETE FROM tallies WHERE id = (SELECT MAX(id) FROM tallies WHERE subjectId = :subjectId)"
    )
    suspend fun undo(subjectId: String)

    @Query("DELETE FROM subjects") suspend fun clearSubjects()
}

@Database(entities = [Subject::class, Tally::class], version = 1, exportSchema = true)
abstract class TallyDatabase : RoomDatabase() {
    abstract fun dao(): TallyDao
}

class TallyRepository(context: Context) {
    private val store = context.settingsStore
    private val selectedKey = stringPreferencesKey("selected")
    private val lightKey = booleanPreferencesKey("light")
    private val database =
        Room.databaseBuilder(context, TallyDatabase::class.java, "tally.db").build()
    private val dao = database.dao()
    private val lock = Mutex()
    val subjects = dao.observeSubjects()
    val preferences =
        store.data.map { Preferences(it[selectedKey].orEmpty(), it[lightKey] ?: false) }

    fun tallies(subjectId: String) = dao.observeTallies(subjectId)

    suspend fun initialize() = lock.withLock {
        if (dao.subjects().isEmpty()) {
            val subject = Subject(UUID.randomUUID().toString(), "Little moments", Palette[0], 0)
            dao.insertSubject(subject)
            select(subject.id)
        }
    }

    suspend fun select(id: String) {
        store.edit { it[selectedKey] = id }
    }

    suspend fun toggleTheme() {
        store.edit { it[lightKey] = !(it[lightKey] ?: false) }
    }

    suspend fun create(name: String, color: String): String = lock.withLock {
        validateSubject(name, color)
        val subject =
            Subject(
                UUID.randomUUID().toString(),
                name.trim(),
                color.uppercase(),
                dao.subjects().size,
            )
        dao.insertSubject(subject)
        select(subject.id)
        subject.id
    }

    suspend fun edit(id: String, name: String, color: String) = lock.withLock {
        validateSubject(name, color)
        dao.editSubject(id, name.trim(), color.uppercase())
    }

    suspend fun add(id: String, timestamp: Long) = lock.withLock {
        dao.insertTallies(listOf(Tally(subjectId = id, timestamp = timestamp)))
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
                    )
                }
            }
        require(subjects.isNotEmpty() && subjects.size <= 10000) { "Backup must contain subjects." }
        subjects.forEach {
            validateSubject(it.name, it.color)
            require(it.id.isNotBlank() && it.id.length <= 128 && it.position >= 0)
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
        require(preferences.selected in ids) { "Selected subject is missing." }
        return Backup(subjects.sortedBy { it.position }, tallies.sortedBy { it.id }, preferences)
    }
}
