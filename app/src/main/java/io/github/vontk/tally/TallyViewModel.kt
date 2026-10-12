package io.github.vontk.tally

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

private data class Selection(
    val subjects: List<SubjectCount>,
    val preferences: Preferences,
    val selected: Subject?,
)

data class TallyState(
    val subjects: List<SubjectCount> = emptyList(),
    val selected: Subject? = null,
    val entries: List<Tally> = emptyList(),
    val light: Boolean = false,
    val preview: Boolean = false,
    val today: LocalDate = LocalDate.now(),
    val zone: ZoneId = ZoneId.systemDefault(),
    val screen: String = "counter",
    val view: String = "days",
    val date: LocalDate = LocalDate.now(),
    val month: YearMonth = YearMonth.now(),
    val message: String? = null,
    val momentId: Long = 0,
) {
    val counts: Map<LocalDate, Int> by lazy { Moments.counts(entries, zone) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TallyViewModel(private val repository: TallyRepository, private val saved: SavedStateHandle) :
    ViewModel() {
    val editorVisible = saved.getStateFlow("editorVisible", false)
    val editorSubjectId = saved.getStateFlow("editorSubjectId", "")
    val editBusy = MutableStateFlow(false)

    fun openEditor(id: String?) {
        saved["editorSubjectId"] = id.orEmpty()
        saved["editorVisible"] = true
    }

    fun closeEditor() {
        if (!editBusy.value) saved["editorVisible"] = false
    }

    val backupBusy = MutableStateFlow(false)

    fun runBackup(action: suspend () -> Unit) {
        viewModelScope.launch { action() }
    }

    var feedbackTally: Tally? = null
        private set

    fun undoFeedback(tally: Tally?) {
        if (tally == null || preview.value) return
        enqueue {
            repository.deleteTally(tally.id, tally.subjectId)
            notify("Tally removed")
        }
    }

    val confirmRestore = MutableStateFlow(false)
    val backupError = MutableStateFlow<String?>(null)
    val backupErrorIsExport = MutableStateFlow(false)
    val pendingBackup = MutableStateFlow<Backup?>(null)

    fun setPendingBackup(backup: Backup?) {
        pendingBackup.value = backup
    }

    private val refresh = MutableStateFlow(0L)
    private val preview = saved.getStateFlow("preview", false)
    val todayRequests = MutableStateFlow(0)
    private val message = MutableStateFlow<String?>(null)
    private val screen = saved.getStateFlow("screen", "counter")
    private val view = saved.getStateFlow("view", "days")
    private val date = saved.getStateFlow("date", LocalDate.now().toString())
    private val moment = saved.getStateFlow("moment", 0L)
    private val month = saved.getStateFlow("month", YearMonth.now().toString())
    private val actions = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val selection =
        combine(repository.subjects, repository.preferences) { subjects, pref ->
            Selection(
                subjects,
                pref,
                subjects.find { it.subject.id == pref.selected }?.subject
                    ?: subjects.firstOrNull()?.subject,
            )
        }
    private val records = selection.flatMapLatest { selection ->
        val flow = selection.selected?.let { repository.tallies(it.id) } ?: flowOf(emptyList())
        flow.map { selection to it }
    }
    private val navigation =
        combine(screen, view, date, month, moment) { s, v, d, m, t ->
            arrayOf(s, v, d, m, t.toString())
        }
    val state: StateFlow<TallyState> =
        combine(records, preview, refresh, navigation, message) { records, demo, _, nav, msg ->
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)
                val (selection, entries) = records
                TallyState(
                    selection.subjects,
                    selection.selected,
                    if (demo) Moments.preview(today, zone, selection.selected?.id.orEmpty())
                    else entries,
                    selection.preferences.light,
                    demo,
                    today,
                    zone,
                    nav[0],
                    nav[1],
                    LocalDate.parse(nav[2]),
                    YearMonth.parse(nav[3]),
                    msg,
                    nav[4].toLong(),
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TallyState())

    init {
        viewModelScope.launch {
            for (action in actions) try {
                action()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                notify("Changes could not be saved. Please try again.")
            }
        }
        enqueue { repository.initialize() }
    }

    private fun enqueue(action: suspend () -> Unit) {
        actions.trySend(action)
    }

    fun refreshClock() {
        refresh.value++
    }

    fun notify(text: String) {
        feedbackTally = null
        message.value = text
    }

    fun clearMessage() {
        message.value = null
    }

    fun screen(value: String) {
        if (value == "settings" && state.value.screen != "settings")
            saved["settingsOrigin"] = state.value.screen
        if (value == "counter") saved["dayOrigin"] = null
        saved["screen"] = value
    }

    fun primaryScreen(): String =
        if (state.value.screen == "settings") saved.get<String>("settingsOrigin") ?: "counter"
        else state.value.screen

    fun back() {
        if (state.value.screen == "settings")
            screen(saved.get<String>("settingsOrigin") ?: "counter")
        else if (state.value.view == "hours" && saved.get<String>("dayOrigin") != null) {
            saved["view"] = saved.get<String>("dayOrigin")!!
            saved["screen"] = saved.get<String>("dayScreen") ?: "history"
            saved["dayOrigin"] = null
        } else screen("counter")
    }

    fun setTheme(light: Boolean) {
        enqueue { repository.setTheme(light) }
    }

    fun deleteSubject(id: String, done: (Boolean) -> Unit) {
        if (editBusy.value) return
        editBusy.value = true
        enqueue {
            try {
                repository.deleteSubject(id)
                saved["preview"] = false
                notify("Subject and its tallies deleted")
                saved["editorVisible"] = false
                done(true)
            } catch (error: Exception) {
                done(false)
                throw error
            } finally {
                editBusy.value = false
            }
        }
    }

    fun deleteTally(tally: Tally, done: (Boolean) -> Unit) {
        if (preview.value) return
        enqueue {
            try {
                repository.deleteTally(tally.id, tally.subjectId)
                notify("Tally deleted")
                done(true)
            } catch (error: Exception) {
                done(false)
                throw error
            }
        }
    }

    fun chooseDate(day: LocalDate) {
        saved["date"] = day.toString()
        saved["month"] = YearMonth.from(day).toString()
    }

    fun view(value: String) {
        saved["dayOrigin"] = null
        saved["view"] = value
    }

    fun openMoment(id: Long) {
        saved["moment"] = id
    }

    fun closeMoment() {
        saved["moment"] = 0L
    }

    fun select(id: String) {
        closeMoment()
        saved["preview"] = false
        enqueue { repository.select(id) }
    }

    fun toggleTheme() {
        enqueue { repository.toggleTheme() }
    }

    fun togglePreview() {
        closeMoment()
        saved["preview"] = !preview.value
        notify(
            if (preview.value) "Preview data · your records stay separate"
            else "Back to your own tallies"
        )
    }

    fun tally() {
        if (preview.value) {
            notify("Exit preview to record your own tallies.")
            return
        }
        val id = state.value.selected?.id ?: return
        val timestamp = System.currentTimeMillis()
        enqueue {
            val added = repository.add(id, timestamp)
            val time =
                Instant.ofEpochMilli(timestamp)
                    .atZone(ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
            notify("Tally added · $time")
            feedbackTally = Tally(added, id, timestamp)
        }
    }

    fun undo() {
        if (preview.value) return
        val id = state.value.selected?.id ?: return
        enqueue {
            repository.undo(id)
            notify("Last tally removed")
        }
    }

    fun saveSubject(id: String?, name: String, color: String, done: (Boolean) -> Unit = {}) {
        if (editBusy.value) return
        editBusy.value = true
        enqueue {
            try {
                if (id == null) {
                    repository.create(name, color)
                    saved["preview"] = false
                    screen("counter")
                } else repository.edit(id, name, color)
                notify(if (id == null) "Your new subject is ready" else "Subject updated")
                saved["editorVisible"] = false
                done(true)
            } catch (error: Exception) {
                done(false)
                throw error
            } finally {
                editBusy.value = false
            }
        }
    }

    fun openDay(day: LocalDate) {
        if (state.value.view != "hours" || state.value.screen != "history") {
            saved["dayOrigin"] = state.value.view
            saved["dayScreen"] = state.value.screen
        }
        saved["date"] = day.toString()
        saved["view"] = "hours"
        saved["screen"] = "history"
    }

    fun shift(amount: Long) {
        if (state.value.view == "month")
            saved["month"] = state.value.month.plusMonths(amount).toString()
        else saved["date"] = state.value.date.plusDays(amount).toString()
    }

    fun today() {
        todayRequests.value++
        saved["date"] = LocalDate.now().toString()
        saved["month"] = YearMonth.now().toString()
    }

    suspend fun exportBackup(): String =
        withContext(Dispatchers.IO) { BackupCodec.encode(repository.backup()) }

    suspend fun parseBackup(text: String): Backup =
        withContext(Dispatchers.IO) { BackupCodec.decode(text) }

    suspend fun restore(backup: Backup) {
        repository.restore(backup)
        saved["preview"] = false
        closeMoment()
        screen("counter")
        refreshClock()
        notify("Backup restored")
    }
}
