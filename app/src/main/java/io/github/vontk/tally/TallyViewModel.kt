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
) {
    val counts: Map<LocalDate, Int> by lazy { Moments.counts(entries, zone) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TallyViewModel(private val repository: TallyRepository, private val saved: SavedStateHandle) :
    ViewModel() {
    private val refresh = MutableStateFlow(0L)
    private val preview = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val screen = saved.getStateFlow("screen", "counter")
    private val view = saved.getStateFlow("view", "days")
    private val date = saved.getStateFlow("date", LocalDate.now().toString())
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
        combine(screen, view, date, month) { s, v, d, m -> arrayOf(s, v, d, m) }
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
        message.value = text
    }

    fun clearMessage() {
        message.value = null
    }

    fun screen(value: String) {
        saved["screen"] = value
    }

    fun view(value: String) {
        saved["view"] = value
    }

    fun select(id: String) {
        preview.value = false
        enqueue { repository.select(id) }
    }

    fun toggleTheme() {
        enqueue { repository.toggleTheme() }
    }

    fun togglePreview() {
        preview.value = !preview.value
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
        enqueue { repository.add(id, timestamp) }
    }

    fun undo() {
        if (preview.value) return
        val id = state.value.selected?.id ?: return
        enqueue {
            repository.undo(id)
            notify("Last tally removed")
        }
    }

    fun saveSubject(id: String?, name: String, color: String) {
        enqueue {
            if (id == null) {
                repository.create(name, color)
                preview.value = false
                screen("counter")
            } else repository.edit(id, name, color)
            notify(if (id == null) "Your new subject is ready" else "Subject updated")
        }
    }

    fun openDay(day: LocalDate) {
        saved["date"] = day.toString()
        view("hours")
        screen("history")
    }

    fun shift(amount: Long) {
        if (state.value.view == "month")
            saved["month"] = state.value.month.plusMonths(amount).toString()
        else saved["date"] = state.value.date.plusDays(amount).toString()
    }

    fun today() {
        saved["date"] = LocalDate.now().toString()
        saved["month"] = YearMonth.now().toString()
    }

    suspend fun exportBackup(): String =
        withContext(Dispatchers.IO) { BackupCodec.encode(repository.backup()) }

    suspend fun parseBackup(text: String): Backup =
        withContext(Dispatchers.IO) { BackupCodec.decode(text) }

    suspend fun restore(backup: Backup) {
        repository.restore(backup)
        preview.value = false
        screen("counter")
        refreshClock()
        notify("Backup restored")
    }
}
