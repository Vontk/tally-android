@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.vontk.tally

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.core.graphics.toColorInt
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.ByteArrayOutputStream
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private fun color(hex: String) = Color(hex.toColorInt())

private fun shortDate(day: LocalDate, locale: Locale) =
    day.format(DateTimeFormatter.ofPattern("MMM d", locale))

private fun stamp(timestamp: Long, zone: ZoneId, locale: Locale) =
    Instant.ofEpochMilli(timestamp)
        .atZone(zone)
        .format(DateTimeFormatter.ofPattern("HH:mm", locale))

private val Shape = RoundedCornerShape(12.dp)

@Composable
fun TallyApp(model: TallyViewModel, systemBars: (Boolean) -> Unit = {}) {
    val state by model.state.collectAsStateWithLifecycle()
    val backupBusy by model.backupBusy.collectAsState()
    val uiScope = rememberCoroutineScope()
    LifecycleResumeEffect(model) {
        model.refreshClock()
        val clockJob = uiScope.launch {
            var lastDay = LocalDate.now()
            var lastZone = ZoneId.systemDefault()
            while (isActive) {
                delay(1000)
                val zone = ZoneId.systemDefault()
                val day = LocalDate.now(zone)
                if (day != lastDay || zone != lastZone) {
                    lastDay = day
                    lastZone = zone
                    model.refreshClock()
                }
            }
        }
        onPauseOrDispose { clockJob.cancel() }
    }
    val selectedAccent = state.selected?.color?.let(::color) ?: color(Palette[0])
    var swipeAccent by remember { mutableStateOf<Color?>(null) }
    val animatedAccent by animateColorAsState(selectedAccent, label = "Tally color")
    val accent = swipeAccent ?: animatedAccent
    val background = if (state.light) Color(0xFFF8F7FA) else Color(0xFF171719)
    val side = if (state.light) Color(0xFFF0EFF3) else Color(0xFF131315)
    val panel = if (state.light) Color.White else Color(0xFF202023)
    val text = if (state.light) Color(0xFF27252D) else Color(0xFFECECEF)
    val muted = if (state.light) Color(0xFF706B79) else Color(0xFFA2A2AA)
    val line = if (state.light) Color(0xFFDDDCE3) else Color(0xFF343438)
    val scheme = if (state.light) lightColorScheme(primary = Color(0xFF303034), onPrimary = Color.White) else darkColorScheme(primary = Color(0xFFF5F5F0), onPrimary = Color(0xFF171719))
    MaterialTheme(
        typography =
            Typography(
                bodyLarge = TextStyle(fontSize = 15.sp),
                bodyMedium = TextStyle(fontSize = 14.sp),
                bodySmall = TextStyle(fontSize = 12.sp),
            ),
        colorScheme =
            scheme.copy(
                background = background,
                onBackground = text,
                surface = panel,
                onSurface = text,
                onSurfaceVariant = muted,
                outline = line,
                surfaceVariant = side,
                secondaryContainer = scheme.primary.copy(alpha = .12f),
                onSecondaryContainer = text,
                surfaceContainer = panel,
                surfaceContainerHigh = panel,
                surfaceContainerHighest = panel,
                error = if (state.light) Color(0xFFAA333B) else Color(0xFFEC969A),
                onError = if (state.light) Color.White else Color(0xFF291519),
            ),
    ) {
        LaunchedEffect(state.light) { systemBars(state.light) }
        val editor by model.editorVisible.collectAsState()
        val saving by model.editBusy.collectAsState()
        var deletingSubjectId by rememberSaveable { mutableStateOf<String?>(null) }
        val deletingSubject = state.subjects.find { it.subject.id == deletingSubjectId }?.subject
        var subjectPicker by rememberSaveable { mutableStateOf(false) }
        val counterFocus = remember { FocusRequester() }
        LaunchedEffect(state.screen, state.selected?.id, editor, subjectPicker) {
            if (state.screen == "counter" && !editor && !subjectPicker && state.selected != null)
                counterFocus.requestFocus()
        }
        val editingId by model.editorSubjectId.collectAsState()
        val editingSubject = state.subjects.find { it.subject.id == editingId }?.subject
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(state.message) {
            state.message?.let { message ->
                val result =
                    snackbar.showSnackbar(
                        message,
                        actionLabel = if (message.startsWith("Tally added")) "Undo" else null,
                        duration = SnackbarDuration.Short,
                    )
                if (result == SnackbarResult.ActionPerformed)
                    model.undoFeedback(model.feedbackTally)
                if (model.state.value.message == message) model.clearMessage()
            }
        }
        BackHandler(enabled = state.screen != "counter") { if (!backupBusy) model.back() }
        val create = { model.openEditor(null) }
        val edit = { model.openEditor(state.selected?.id) }
        Surface(color = background, modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val wide = maxWidth >= 700.dp
                Scaffold(
                    containerColor = background,
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        if (!wide)
                            NavigationBar(
                                containerColor = panel,
                                tonalElevation = 0.dp,
                                windowInsets = WindowInsets(0),
                            ) {
                                listOf("counter" to "Counter", "history" to "History").forEach {
                                    (destination, label) ->
                                    NavigationBarItem(
                                        selected = model.primaryScreen() == destination,
                                        enabled = !backupBusy,
                                        onClick = { model.screen(destination) },
                                        icon = {
                                            Glyph(
                                                if (destination == "counter") "count" else "history"
                                            )
                                        },
                                        label = {
                                            Text(
                                                label,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Normal,
                                            )
                                        },
                                        colors =
                                            NavigationBarItemDefaults.colors(
                                                selectedIconColor =
                                                    MaterialTheme.colorScheme.primary,
                                                selectedTextColor =
                                                    MaterialTheme.colorScheme.onSurface,
                                                unselectedIconColor = muted,
                                                unselectedTextColor = muted,
                                            ),
                                    )
                                }
                            }
                    },
                ) { padding ->
                    Row(Modifier.fillMaxSize().padding(padding)) {
                        if (wide) Sidebar(state, model, create, side)
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            if (!wide) {
                                Row(
                                    Modifier.fillMaxWidth()
                                        .padding(start = 22.dp, end = 10.dp, top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Brand(Modifier.weight(1f))
                                    TextButton(
                                        onClick = { model.screen("settings") },
                                        modifier =
                                            Modifier.heightIn(min = 48.dp).background(panel, Shape),
                                    ) {
                                        Glyph("settings")
                                        Spacer(Modifier.width(6.dp))
                                        Text("Settings", color = muted, fontSize = 13.sp)
                                    }
                                }
                            }
                            if (!wide && state.screen != "settings" && state.selected != null) {
                                Row(
                                    Modifier.fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Spacer(Modifier.weight(1f))
                                    TextButton(onClick = create) {
                                        Glyph("plus", description = "Add tally")
                                    }
                                    TextButton(onClick = { subjectPicker = true }) {
                                        Text("Edit")
                                    }
                                }
                            }
                            if (wide) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Dot(accent)
                                    Spacer(Modifier.width(9.dp))
                                    Text(
                                        state.selected?.name ?: "Loading…",
                                        Modifier.weight(1f),
                                        fontSize = 15.sp,
                                    )
                                    TextButton(onClick = { subjectPicker = true }, enabled = state.selected != null) {
                                        Text("Edit tallies")
                                    }
                                }
                            }
                            if (state.preview) PreviewBanner(model)
                            if (state.screen == "settings") SettingsScreen(model)
                            else if (state.selected == null)
                                EmptySubjects(create) { model.screen("settings") }
                            else
                                Box(
                                    Modifier.weight(1f).fillMaxWidth(),
                                    contentAlignment = Alignment.TopCenter,
                                ) {
                                    Column(
                                        Modifier.widthIn(max = 940.dp)
                                            .fillMaxSize()
                                            .padding(horizontal = if (wide) 44.dp else 22.dp)
                                            .focusRequester(counterFocus)
                                            .focusable()
                                            .onKeyEvent { event ->
                                                if (
                                                    event.key == Key.Spacebar &&
                                                        event.type == KeyEventType.KeyDown &&
                                                        event.nativeKeyEvent.repeatCount == 0 &&
                                                        state.screen == "counter" &&
                                                        !editor &&
                                                        !subjectPicker
                                                ) {
                                                    model.tally()
                                                    true
                                                } else false
                                            }
                                    ) {
                                        if (state.screen == "counter") CounterPager(state, model) { swipeAccent = it }
                                        else HistoryScreen(state, model)
                                    }
                                }
                        }
                    }
                }
            }
        }
        if (subjectPicker) {
            ModalBottomSheet(onDismissRequest = { subjectPicker = false }, containerColor = panel) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Edit tallies",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { subjectPicker = false }) { Text("Done") }
                }
                TextButton(onClick = {
                    subjectPicker = false
                    model.openEditor(state.selected?.id)
                }, enabled = state.selected != null) { Text("Edit current tally") }
                Text("Reorder with the arrows", Modifier.padding(horizontal = 24.dp), fontSize = 13.sp)
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(state.subjects, key = { it.subject.id }) { subject ->
                        ListItem(
                            headlineContent = { Text(subject.subject.name) },
                            supportingContent = { Text("${subject.count} tallies") },
                            leadingContent = { Dot(color(subject.subject.color), 12.dp) },
                            trailingContent = {
                                Row {
                                    val index = state.subjects.indexOf(subject)
                                    TextButton(onClick = { model.moveSubject(subject.subject.id, -1) }, enabled = index > 0) { Text("↑") }
                                    TextButton(onClick = { model.moveSubject(subject.subject.id, 1) }, enabled = index < state.subjects.lastIndex) { Text("↓") }
                                    TextButton(onClick = {
                                        subjectPicker = false
                                        model.openEditor(subject.subject.id)
                                    }) { Text("Edit") }
                                }
                            },
                            colors =
                                ListItemDefaults.colors(
                                    containerColor =
                                        if (subject.subject.id == state.selected?.id)
                                            MaterialTheme.colorScheme.secondaryContainer
                                        else panel
                                ),
                            modifier =
                                Modifier.background(
                                        if (subject.subject.id == state.selected?.id)
                                            MaterialTheme.colorScheme.secondaryContainer
                                        else Color.Transparent,
                                        Shape,
                                    )
                                    .clickable {
                                        model.select(subject.subject.id)
                                        subjectPicker = false
                                    }
                                    .heightIn(min = 64.dp),
                        )
                    }
                }
                Button(
                    shape = Shape,
                    onClick = {
                        subjectPicker = false
                        create()
                    },
                    modifier =
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Glyph("plus")
                    Spacer(Modifier.width(8.dp))
                    Text("New subject")
                }
            }
        }
        if (editor)
            ProfileEditor(
                editingSubject,
                Palette[state.subjects.size % Palette.size],
                busy = saving,
                dismiss = model::closeEditor,
                delete =
                    if (editingSubject == null || state.preview) null
                    else ({ deletingSubjectId = editingSubject?.id }),
                save = { name, accentHex, negative ->
                    model.saveSubject(editingSubject?.id, name, accentHex, negative)
                },
            )
        state.entries
            .find { it.id == state.momentId }
            ?.let { tally ->
                MomentDetails(state, tally, model)
            }
        deletingSubject?.let { subject ->
            DeleteSubjectConfirmation(
                subject.name,
                state.subjects.find { it.subject.id == subject.id }?.count?.toInt() ?: 0,
                busy = saving,
                onCancel = { deletingSubjectId = null },
                onConfirm = {
                    model.deleteSubject(subject.id) { success ->
                        if (success) deletingSubjectId = null
                    }
                },
            )
        }
    }
}

@Composable
private fun Brand(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Glyph("brand", modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            "tally.",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1).sp,
        )
    }
}

@Composable
private fun Sidebar(state: TallyState, model: TallyViewModel, create: () -> Unit, side: Color) {
    Column(Modifier.width(236.dp).fillMaxHeight().background(side).padding(20.dp)) {
        Brand(Modifier.padding(top = 16.dp, bottom = 32.dp))
        listOf("counter" to "Counter", "history" to "History").forEach { (screen, label) ->
            TextButton(
                onClick = { model.screen(screen) },
                modifier =
                    Modifier.fillMaxWidth()
                        .background(
                            if (model.primaryScreen() == screen)
                                MaterialTheme.colorScheme.outline.copy(alpha = .4f)
                            else Color.Transparent,
                            Shape,
                        ),
            ) {
                Glyph(if (screen == "counter") "count" else "history")
                Spacer(Modifier.width(12.dp))
                Text(label, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
            }
        }
        Text(
            "YOUR SUBJECTS",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            letterSpacing = 1.8.sp,
            modifier = Modifier.padding(top = 30.dp, bottom = 12.dp),
        )
        LazyColumn(Modifier.weight(1f)) {
            items(state.subjects, key = { it.subject.id }) { s ->
                Row(
                    Modifier.fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .background(
                            if (s.subject.id == state.selected?.id)
                                MaterialTheme.colorScheme.primary.copy(alpha = .09f)
                            else Color.Transparent,
                            Shape,
                        )
                        .clickable { model.select(s.subject.id) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Dot(color(s.subject.color))
                    Spacer(Modifier.width(10.dp))
                    Text(s.subject.name, modifier = Modifier.weight(1f), fontSize = 13.sp)
                    Text(
                        s.count.toString(),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                TextButton(onClick = create) {
                    Glyph("plus")
                    Spacer(Modifier.width(8.dp))
                    Text("New subject")
                }
            }
        }
        TextButton(onClick = model::toggleTheme) {
            Glyph(if (state.light) "moon" else "sun")
            Spacer(Modifier.width(10.dp))
            Text(if (state.light) "Dark appearance" else "Light appearance")
        }
        TextButton(onClick = { model.screen("settings") }) {
            Glyph("settings")
            Spacer(Modifier.width(10.dp))
            Text("Settings and backup")
        }
        Text(
            "•  Saved on this device",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun SubjectChip(subject: SubjectCount, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.heightIn(min = 48.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .09f)
                else Color.Transparent,
                Shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Dot(color(subject.subject.color))
        Spacer(Modifier.width(9.dp))
        Text(
            subject.subject.name,
            fontSize = 13.sp,
            color =
                if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Heading(state: TallyState) {
    val locale = LocalLocale.current.platformLocale
    val counter = state.screen == "counter"
    Row(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (counter) {
            Dot(state.selected?.color?.let(::color) ?: color(Palette[0]), 12.dp)
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                if (counter) state.selected?.name.orEmpty() else "History",
                fontSize = 24.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-.5).sp,
            )
            Text(
                if (counter) state.today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", locale))
                else if (state.view == "hours")
                    "${state.counts[state.date] ?: 0} tallies on this day"
                else if (state.view == "month")
                    "${state.counts.filterKeys { YearMonth.from(it) == state.month }.values.sum()} tallies this month"
                else "${state.entries.size} recorded moments",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Each subject is a real swipeable page; vertical scrolling and circle taps stay independent. */
@Composable
private fun CounterPager(state: TallyState, model: TallyViewModel, onAccent: (Color?) -> Unit) {
    val selectedIndex =
        state.subjects.indexOfFirst { it.subject.id == state.selected?.id }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = selectedIndex, pageCount = { state.subjects.size })
    val latest by rememberUpdatedState(state)
    var syncingSelection by remember { mutableStateOf(false) }
    var pendingSwipeId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.selected?.id, state.subjects.map { it.subject.id }) {
        // Delayed preference writes from an earlier swipe must not pull a newer swipe back.
        if (pendingSwipeId != null) {
            if (
                state.subjects.none { it.subject.id == pendingSwipeId } ||
                    state.selected?.id == pendingSwipeId
            )
                pendingSwipeId = null
            else return@LaunchedEffect
        }
        if (pager.isScrollInProgress) return@LaunchedEffect
        if (pager.currentPage != selectedIndex) {
            syncingSelection = true
            try {
                pager.scrollToPage(selectedIndex)
            } finally {
                syncingSelection = false
            }
        }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage to pager.isScrollInProgress }
            .filter { !it.second }
            .distinctUntilChanged()
            .collect { (index, _) ->
                val subject = latest.subjects.getOrNull(index)?.subject
                if (
                    !syncingSelection &&
                        subject != null &&
                        (subject.id != latest.selected?.id ||
                            (pendingSwipeId != null && pendingSwipeId != subject.id))
                ) {
                    pendingSwipeId = subject.id
                    model.selectFromSwipe(subject.id) { success ->
                        if (!success) pendingSwipeId = null
                    }
                }
            }
    }
    val accentCallback by rememberUpdatedState(onAccent)
    DisposableEffect(Unit) {
        onDispose { accentCallback(null) }
    }
    LaunchedEffect(pager, state.subjects) {
        snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction }.collect { position ->
            val lower = kotlin.math.floor(position).toInt().coerceIn(0, state.subjects.lastIndex)
            val upper = (lower + 1).coerceAtMost(state.subjects.lastIndex)
            accentCallback(lerp(color(state.subjects[lower].subject.color), color(state.subjects[upper].subject.color), (position - lower).coerceIn(0f, 1f)))
        }
    }
    HorizontalPager(
        state = pager,
        key = { state.subjects.getOrNull(it)?.subject?.id ?: "removed-$it" },
        beyondViewportPageCount = 1,
        userScrollEnabled = state.subjects.size > 1,
        modifier = Modifier.fillMaxSize(),
    ) { index ->
        val subject = state.subjects.getOrNull(index)?.subject ?: return@HorizontalPager
        val recordsFlow = remember(subject.id, model) { model.entriesFor(subject.id) }
        val records by recordsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
        val pageEntries =
            when {
                state.preview -> Moments.preview(state.today, state.zone, subject.id)
                subject.id == state.selected?.id -> state.entries
                else -> records
            }
        CounterScreen(state.copy(selected = subject, entries = pageEntries), model)
    }
}

@Composable
private fun CounterScreen(state: TallyState, model: TallyViewModel) {
    val locale = LocalLocale.current.platformLocale
    val accent = state.selected?.color?.let(::color) ?: color(Palette[0])
    val fontScale = LocalDensity.current.fontScale
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) .96f else 1f, label = "Tally tap")
    LaunchedEffect(pressed) {
        if (pressed) {
            delay(100)
            pressed = false
        }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        overscrollEffect = null,
    ) {
        item { Heading(state) }
        item {
            Column(
                Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(244.dp)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                            CircleShape,
                        )
                        .background(
                            accent.copy(alpha = .04f),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier.size(220.dp)
                            .scale(scale)
                            .clip(CircleShape)
                            .background(accent, CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .clickable(enabled = !state.preview, role = Role.Button) {
                                model.tally(state.selected?.id)
                                if (!state.preview) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    pressed = true
                                }
                            }
                            .semantics {
                                contentDescription = "Add a tally for ${state.selected?.name}"
                                stateDescription = "${state.counts[state.today] ?: 0} tallies today"
                            }
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            (state.counts[state.today] ?: 0).toString(),
                            fontSize =
                                if ((state.counts[state.today] ?: 0) > 9999)
                                    (48f / fontScale.coerceAtLeast(1f)).sp
                                else (66f / (fontScale / 1.3f).coerceAtLeast(1f)).sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = (-3).sp,
                            color = readableOn(accent),
                        )

                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    if (!state.preview) {
                        CompositionLocalProvider(
                            LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
                        ) {
                            Glyph("plus", Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        if (state.preview) "Tallying is disabled in preview." else "Tap to add",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier,
                    )
                }
                TextButton(
                    onClick = { model.undo(state.selected?.id) },
                    modifier =
                        Modifier.padding(top = 8.dp)
                            .heightIn(min = 48.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape),
                    enabled = state.entries.isNotEmpty() && !state.preview,
                ) {
                    Glyph("undo")
                    Spacer(Modifier.width(7.dp))
                    Text("Undo last tally", fontSize = 13.sp)
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, Shape)
                    .background(MaterialTheme.colorScheme.surface, Shape)
                    .padding(vertical = 12.dp)
            ) {
                Stat(
                    "Last 7 days",
                    Moments.week(state.counts, state.today),
                    "tallies",
                    Modifier.weight(1f),
                )
                Stat("All time", state.entries.size, "moments", Modifier.weight(1f))
                Stat(
                    "Streak",
                    Moments.streak(state.counts, state.today, state.selected?.negative == true,
                        state.selected?.createdAt?.takeIf { it > 0 }?.let { Moments.day(it, state.zone) }
                            ?: state.counts.keys.minOrNull() ?: state.today),
                    "days",
                    Modifier.weight(1f),
                )
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Recent moments", modifier = Modifier.weight(1f), fontSize = 14.sp)
                TextButton(onClick = { model.screen("history") }) {
                    Text(
                        "View all",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        val recent = state.entries.sortedByDescending { it.timestamp }.take(3)
        if (recent.isEmpty())
            item {
                Text(
                    "Your first moment starts with a tap.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 22.dp),
                )
            }
        items(recent, key = { it.id }) { tally ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(
                Modifier.fillMaxWidth()
                    .clickable { model.openMoment(tally.id) }
                    .heightIn(min = 48.dp)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dot(accent)
                Spacer(Modifier.width(12.dp))
                val day = Moments.day(tally.timestamp, state.zone)
                Text(
                    if (day == state.today) "Today" else shortDate(day, locale),
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stamp(tally.timestamp, state.zone, locale),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Glyph("right")
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, unit: String, modifier: Modifier) {
    Column(modifier.padding(horizontal = 12.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Text(value.toString(), fontSize = 24.sp, lineHeight = 28.sp)
        Text(unit, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ColumnScope.HistoryScreen(state: TallyState, model: TallyViewModel) {
    val locale = LocalLocale.current.platformLocale
    val fontScale = LocalDensity.current.fontScale
    var picking by rememberSaveable { mutableStateOf(false) }
    Heading(state)
    Segments(
        listOf("days" to "Days", "hours" to "Hours", "month" to "Month"),
        state.view,
        model::view,
    )
    if (state.view != "days") {
        FlowRow(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(
                onClick = { model.shift(-1) },
                modifier =
                    Modifier.size(48.dp).background(MaterialTheme.colorScheme.surface, Shape),
            ) {
                Glyph(
                    "left",
                    description = "Previous ${if (state.view == "month") "month" else "day"}",
                )
            }
            Surface(
                onClick = { picking = true },
                color = MaterialTheme.colorScheme.surface,
                shape = Shape,
                modifier = Modifier.weight(1f).widthIn(min = 120.dp).heightIn(min = 48.dp),
            ) {
                Column(
                    Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (state.view == "month")
                            state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
                        else
                            state.date.format(
                                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                                    .withLocale(locale)
                            ),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Choose date",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(
                onClick = { model.shift(1) },
                modifier =
                    Modifier.size(48.dp).background(MaterialTheme.colorScheme.surface, Shape),
            ) {
                Glyph(
                    "right",
                    description = "Next ${if (state.view == "month") "month" else "day"}",
                )
            }
            TextButton(
                onClick = model::today,
                modifier =
                    Modifier.heightIn(min = 48.dp)
                        .background(MaterialTheme.colorScheme.surface, Shape),
            ) {
                Text("Today", fontSize = 12.sp)
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Earlier days are above today",
                Modifier.weight(1f),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = model::today) { Text("Today", fontSize = 12.sp) }
        }
    }
    Box(Modifier.weight(1f).fillMaxWidth()) {
        when (state.view) {
            "days" -> DaysView(state, model)
            "hours" -> HoursView(state, model)
            else -> MonthView(state, model)
        }
    }
    if (state.view != "days" && fontScale < 1.5f)
        Text(
            if (state.view == "hours") "Tap a dot for details. Scroll for more hours."
            else "Select a day to see its tallies.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp),
        )
    if (picking) {
        val day = if (state.view == "month") state.month.atDay(1) else state.date
        val picker =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                yearRange = 1900..2200,
            )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        picker.selectedDateMillis?.let {
                            model.chooseDate(
                                Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                            )
                        }
                        picking = false
                    },
                    enabled = picker.selectedDateMillis != null,
                ) {
                    Text(if (state.view == "month") "Show month" else "Show day")
                }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) {
            DatePicker(
                picker,
                title = { Text("Choose date", Modifier.padding(24.dp), fontSize = 24.sp) },
            )
        }
    }
}

@Composable
private fun Segments(items: List<Pair<String, String>>, selected: String, click: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, Shape)
            .background(MaterialTheme.colorScheme.surface, Shape)
            .padding(4.dp)
    ) {
        items.forEach { (id, label) ->
            TextButton(
                onClick = { click(id) },
                modifier =
                    Modifier.weight(1f)
                        .heightIn(min = 40.dp)
                        .background(
                            if (id == selected) MaterialTheme.colorScheme.secondaryContainer
                            else Color.Transparent,
                            RoundedCornerShape(8.dp),
                        )
                        .semantics { this.selected = id == selected },
            ) {
                Text(
                    label,
                    fontSize = 14.sp,
                    color =
                        if (id == selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DaysView(state: TallyState, model: TallyViewModel) {
    val todayRequests by model.todayRequests.collectAsState()
    val locale = LocalLocale.current.platformLocale
    val earliest = state.counts.keys.filter { it <= state.today }.minOrNull() ?: state.today
    val span =
        maxOf(30, (ChronoUnit.DAYS.between(earliest, state.today) + 1).coerceAtMost(3660).toInt())
    val list = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, span - 7))
    LaunchedEffect(state.date, state.today, state.selected?.id, state.preview, todayRequests) {
        val index =
            (span - 1 - ChronoUnit.DAYS.between(state.date, state.today))
                .toInt()
                .coerceIn(0, span - 1)
        list.scrollToItem(maxOf(0, index - 6))
    }
    LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
        items(span, key = { state.today.minusDays((span - it - 1).toLong()).toString() }) { index ->
            val date = state.today.minusDays((span - index - 1).toLong())
            val count = state.counts[date] ?: 0
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(
                Modifier.fillMaxWidth()
                    .clickable { model.openDay(date) }
                    .semantics { contentDescription = "$date, $count tallies" }
                    .heightIn(min = 56.dp)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    date.dayOfMonth.toString(),
                    fontSize = 27.sp,
                    letterSpacing = (-1).sp,
                    modifier = Modifier.widthIn(min = 48.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(date.format(DateTimeFormatter.ofPattern("EEEE", locale)), fontSize = 14.sp)
                    Text(
                        date.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)) +
                            if (date == state.today) " · Today" else "",
                        fontSize = 12.sp,
                        color =
                            if (date == state.today) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(Modifier.padding(start = 8.dp), horizontalAlignment = Alignment.End) {
                    Text(count.toString(), fontSize = 22.sp)
                    Text(
                        if (count == 1) "tally" else "tallies",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Glyph("right", Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun HoursView(state: TallyState, model: TallyViewModel) {
    val locale = LocalLocale.current.platformLocale
    val entries =
        state.entries
            .filter { Moments.day(it.timestamp, state.zone) == state.date }
            .sortedBy { it.timestamp }
    val initialHour =
        entries.firstOrNull()?.let { Instant.ofEpochMilli(it.timestamp).atZone(state.zone).hour }
            ?: 0
    val timelineState = rememberLazyListState()
    LaunchedEffect(state.date, state.selected?.id, state.preview) {
        timelineState.scrollToItem(maxOf(0, initialHour - 1))
    }
    var listMode by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (listMode) "Exact timestamps" else "Timeline · all 24 hours",
                Modifier.weight(1f),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { listMode = !listMode },
                modifier =
                    Modifier.heightIn(min = 48.dp)
                        .background(MaterialTheme.colorScheme.surface, Shape),
            ) {
                Text(if (listMode) "Timeline" else "View list", fontSize = 12.sp)
            }
        }
        if (entries.isEmpty())
            Text(
                "No tallies on this day.",
                Modifier.padding(vertical = 8.dp),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        if (listMode)
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(entries, key = { it.id }) { tally ->
                    Row(
                        Modifier.fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { model.openMoment(tally.id) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Dot(state.selected?.color?.let(::color) ?: color(Palette[0]))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stamp(tally.timestamp, state.zone, locale),
                            Modifier.weight(1f),
                            fontSize = 15.sp,
                        )
                        Text(
                            "Details",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Glyph("right")
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        else
            LazyColumn(
                state = timelineState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
            ) {
                items(24) { hour ->
                    val moments = entries.filter {
                        Instant.ofEpochMilli(it.timestamp).atZone(state.zone).hour == hour
                    }
                    Row(Modifier.fillMaxWidth().height(72.dp)) {
                        Text(
                            "%02d:00".format(hour),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.widthIn(min = 52.dp).padding(top = 2.dp),
                        )
                        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                            val trackWidth = maxOf(maxWidth, (moments.size * 48).dp)
                            Box(Modifier.horizontalScroll(rememberScrollState())) {
                                Box(Modifier.width(trackWidth).fillMaxHeight()) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                    moments.forEachIndexed { index, tally ->
                                        val time =
                                            Instant.ofEpochMilli(tally.timestamp).atZone(state.zone)
                                        val minute = time.minute + time.second / 60f
                                        Box(
                                            Modifier.offset(
                                                    x = (index * 48).dp,
                                                    y = (minute / 60f * 72 - 24).dp,
                                                )
                                                .size(48.dp)
                                                .clickable(role = Role.Button) {
                                                    model.openMoment(tally.id)
                                                }
                                                .semantics {
                                                    contentDescription =
                                                        "Tally at ${stamp(tally.timestamp, state.zone, locale)}"
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Box(
                                                Modifier.size(12.dp)
                                                    .border(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.onSurface.copy(
                                                            alpha = .6f
                                                        ),
                                                        CircleShape,
                                                    )
                                                    .background(
                                                        state.selected?.color?.let(::color)
                                                            ?: color(Palette[0]),
                                                        CircleShape,
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
    }
}

@Composable
private fun MonthView(state: TallyState, model: TallyViewModel) {
    val locale = LocalLocale.current.platformLocale
    val offset = state.month.atDay(1).dayOfWeek.value - 1
    val max =
        (1..state.month.lengthOfMonth())
            .maxOf { state.counts[state.month.atDay(it)] ?: 0 }
            .coerceAtLeast(1)
    val accent = state.selected?.color?.let(::color) ?: color(Palette[0])
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val calendarGap = ((maxWidth - 336.dp) / 6).coerceIn(0.dp, 3.dp)
        if (maxWidth < 336.dp || fontScale >= 1.5f) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text(
                        "Calendar day list",
                        Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
                items(state.month.lengthOfMonth()) { index ->
                    val day = state.month.atDay(index + 1)
                    val count = state.counts[day] ?: 0
                    ListItem(
                        headlineContent = {
                            Text(day.format(DateTimeFormatter.ofPattern("EEE, MMM d", locale)))
                        },
                        supportingContent = if (day == state.today) ({ Text("Today") }) else null,
                        trailingContent = {
                            Text("$count", color = MaterialTheme.colorScheme.primary)
                        },
                        modifier =
                            Modifier.heightIn(min = 56.dp)
                                .clickable { model.openDay(day) }
                                .semantics { contentDescription = "$day, $count tallies" },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                }
            }
        } else
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Row {
                    (1..7).forEach { day ->
                        Text(
                            DayOfWeek.of(day)
                                .getDisplayName(java.time.format.TextStyle.NARROW, locale),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                        )
                    }
                }
                repeat((offset + state.month.lengthOfMonth() + 6) / 7) { week ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(calendarGap),
                    ) {
                        repeat(7) { column ->
                            val number = week * 7 + column - offset + 1
                            Box(Modifier.weight(1f).padding(bottom = 3.dp)) {
                                if (number in 1..state.month.lengthOfMonth()) {
                                    val day = state.month.atDay(number)
                                    val count = state.counts[day] ?: 0
                                    val fill =
                                        if (count == 0) MaterialTheme.colorScheme.surface
                                        else
                                            androidx.compose.ui.graphics.lerp(
                                                MaterialTheme.colorScheme.surface,
                                                accent,
                                                .12f + count.toFloat() / max * .60f,
                                            )
                                    Column(
                                        Modifier.fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .border(
                                                1.dp,
                                                if (day == state.today)
                                                    MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outline,
                                                RoundedCornerShape(8.dp),
                                            )
                                            .background(fill, RoundedCornerShape(8.dp))
                                            .clickable { model.openDay(day) }
                                            .semantics {
                                                contentDescription =
                                                    "$day, $count tallies${if(day == state.today) ", Today" else ""}"
                                            }
                                            .padding(5.dp)
                                    ) {
                                        Text(
                                            number.toString(),
                                            fontSize = 12.sp,
                                            color = readableOn(fill),
                                        )
                                        Text(
                                            if (count > 0) count.toString() else "",
                                            Modifier.fillMaxWidth(),
                                            fontSize = 14.sp,
                                            textAlign = TextAlign.End,
                                            color = readableOn(fill),
                                        )
                                        if (day == state.today)
                                            Box(
                                                Modifier.width(12.dp)
                                                    .height(2.dp)
                                                    .background(readableOn(fill))
                                            )
                                    }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Fewer",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    listOf(.12f, .25f, .45f, .72f).forEach { alpha ->
                        Box(
                            Modifier.padding(start = 5.dp)
                                .size(12.dp)
                                .background(
                                    androidx.compose.ui.graphics.lerp(
                                        MaterialTheme.colorScheme.surface,
                                        accent,
                                        alpha,
                                    ),
                                    RoundedCornerShape(3.dp),
                                )
                        )
                    }
                    Text(
                        "More",
                        Modifier.padding(start = 12.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
    }
}

@Composable
private fun ProfileEditor(
    subject: Subject?,
    default: String,
    busy: Boolean,
    dismiss: () -> Unit,
    delete: (() -> Unit)?,
    save: (String, String, Boolean) -> Unit,
) {
    var name by rememberSaveable(subject?.id) { mutableStateOf(subject?.name.orEmpty()) }
    var hex by rememberSaveable(subject?.id) { mutableStateOf(subject?.color ?: default) }
    var negative by rememberSaveable(subject?.id) { mutableStateOf(subject?.negative ?: false) }
    var paletteOpen by rememberSaveable { mutableStateOf(false) }
    var custom by rememberSaveable { mutableStateOf(false) }
    val validColor = Regex("#[0-9a-fA-F]{6}").matches(hex)
    ModalBottomSheet(
        onDismissRequest = { if (!busy) dismiss() },
        containerColor = MaterialTheme.colorScheme.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (subject == null) "New subject" else "Edit subject",
                    Modifier.weight(1f),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = dismiss, enabled = !busy) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                "Subject name",
                Modifier.padding(top = 16.dp, bottom = 8.dp),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                name,
                { name = it.take(36) },
                placeholder = { Text("e.g. Water or Reading") },
                singleLine = true,
                shape = Shape,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                supportingText = { if (name.isNotEmpty()) Text("${name.length}/36") },
            )
            Text(
                "Accent color",
                Modifier.padding(top = 16.dp, bottom = 8.dp),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(color(hex), 20.dp)
                TextButton(onClick = { paletteOpen = true }, enabled = !busy) { Text("Choose color") }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Negative tally")
                    Text(if (negative) "Days without tallies build your streak." else "Days with tallies build your streak.", fontSize = 12.sp)
                }
                Switch(checked = negative, onCheckedChange = { negative = it }, enabled = !busy)
            }
            Text(
                "Custom color",
                Modifier.padding(top = 16.dp, bottom = 8.dp),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                onClick = { custom = true },
                enabled = !busy,
                color = MaterialTheme.colorScheme.background,
                shape = Shape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(
                    Modifier.fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Dot(color(hex), 20.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(hex, Modifier.weight(1f), fontSize = 15.sp)
                    Text("RGB", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Glyph("right")
                }
            }
            Text(
                "Used for your counter, stamps and heatmap.",
                Modifier.padding(top = 18.dp, bottom = 24.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { save(name.trim(), hex, negative) },
                enabled = !busy && name.trim().isNotEmpty() && validColor,
                shape = Shape,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(
                    if (subject == null) "Create subject" else "Save changes",
                    fontWeight = FontWeight.Bold,
                )
            }
            if (name.trim().isEmpty())
                Text(
                    "Enter a name to continue.",
                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            if (delete != null)
                TextButton(
                    onClick = delete,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp),
                ) {
                    Text("Delete subject", color = MaterialTheme.colorScheme.error)
                }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
        }
    }
    if (paletteOpen) AlertDialog(
        onDismissRequest = { paletteOpen = false },
        title = { Text("Choose color") },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Palette.forEach { c -> Swatch(c, hex) { hex = c; paletteOpen = false } }
            }
        },
        confirmButton = { TextButton(onClick = { paletteOpen = false; custom = true }) { Text("Custom color") } },
        dismissButton = { TextButton(onClick = { paletteOpen = false }) { Text("Cancel") } },
    )
    if (custom)
        CustomColorEditor(hex, { custom = false }) {
            hex = it
            custom = false
        }
}

@Composable
private fun CustomColorEditor(initial: String, dismiss: () -> Unit, use: (String) -> Unit) {
    var hex by rememberSaveable { mutableStateOf(initial) }
    val valid = Regex("#[0-9a-fA-F]{6}").matches(hex)
    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Custom color",
                    Modifier.weight(1f),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(onClick = dismiss) { Text("Cancel") }
            }
            Text(
                "Hex color",
                Modifier.padding(top = 12.dp, bottom = 8.dp),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                hex,
                { hex = it.take(7).uppercase() },
                singleLine = true,
                shape = Shape,
                isError = !valid,
                supportingText =
                    if (!valid) ({ Text("Use six hex digits, such as #A69AEE.") }) else null,
                modifier = Modifier.fillMaxWidth(),
            )
            if (valid) {
                val rgb =
                    listOf(
                        (hex.toColorInt() shr 16) and 255,
                        (hex.toColorInt() shr 8) and 255,
                        hex.toColorInt() and 255,
                    )
                listOf("Red", "Green", "Blue").forEachIndexed { index, label ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label,
                            Modifier.weight(1f),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            rgb[index].toString(),
                            Modifier.border(1.dp, MaterialTheme.colorScheme.outline, Shape)
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            fontSize = 14.sp,
                        )
                    }
                    Slider(
                        value = rgb[index].toFloat(),
                        onValueChange = { next ->
                            val values = rgb.toMutableList()
                            values[index] = next.toInt()
                            hex = "#%02X%02X%02X".format(values[0], values[1], values[2])
                        },
                        valueRange = 0f..255f,
                        modifier =
                            Modifier.semantics { contentDescription = "$label color channel" },
                    )
                }
                Box(
                    Modifier.fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .background(color(hex), Shape)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Color preview", color = readableOn(color(hex)))
                }
            }
            Button(
                onClick = { use(hex) },
                enabled = valid,
                shape = Shape,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 20.dp),
            ) {
                Text("Use this color", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PreviewBanner(model: TallyViewModel) {
    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, Shape)
            .background(MaterialTheme.colorScheme.surface, Shape)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Example data", fontSize = 14.sp)
            Text(
                "Your real records are separate",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = model::togglePreview) { Text("Exit preview", fontSize = 12.sp) }
    }
}

@Composable
private fun EmptySubjects(create: () -> Unit, restore: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text(
            "A fresh start.",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            "Create a subject to begin counting.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            Modifier.fillMaxWidth().padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Glyph("count", Modifier.size(40.dp))
            Spacer(Modifier.height(24.dp))
            Text("No subjects yet", fontSize = 22.sp)
            Text(
                "Your next moment starts here.",
                Modifier.padding(top = 12.dp, bottom = 32.dp),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = create,
                shape = Shape,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text("Create subject", fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = restore, modifier = Modifier.padding(top = 12.dp)) {
                Text("Or restore a backup in Settings.", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MomentDetails(state: TallyState, tally: Tally, model: TallyViewModel) {
    val locale = LocalLocale.current.platformLocale
    val day = Moments.day(tally.timestamp, state.zone)
    val fullDate =
        day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
    val time = stamp(tally.timestamp, state.zone, locale)
    var deleting by rememberSaveable(tally.id) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = { if (!busy) model.closeMoment() },
        containerColor = MaterialTheme.colorScheme.surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp)) {
            Text("Recorded moment", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(state.selected?.name.orEmpty(), Modifier.padding(top = 16.dp), fontSize = 16.sp)
            Text(
                fullDate,
                Modifier.padding(top = 12.dp),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(time, fontSize = 28.sp)
            FilledTonalButton(
                onClick = model::closeMoment,
                enabled = !busy,
                shape = Shape,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 16.dp),
            ) {
                Text("Done")
            }
            if (!state.preview)
                TextButton(
                    onClick = { deleting = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 12.dp),
                ) {
                    MinimalTrashIcon()
                    Spacer(Modifier.width(10.dp))
                    Text("Delete tally", color = MaterialTheme.colorScheme.error)
                }
        }
    }
    if (deleting)
        DeleteTallyConfirmation(
            state.selected?.name.orEmpty(),
            "$fullDate, $time",
            busy,
            onCancel = { deleting = false },
            onConfirm = {
                busy = true
                model.deleteTally(tally) { success ->
                    busy = false
                    if (success) {
                        deleting = false
                        model.closeMoment()
                    }
                }
            },
        )
}

@Composable
private fun Swatch(hex: String, selected: String, click: () -> Unit) {
    Box(
        Modifier.size(48.dp).clickable(role = Role.Button, onClick = click).semantics {
            contentDescription = "Accent $hex"
            this.selected = hex.equals(selected, true)
        },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(30.dp)
                .border(
                    if (hex.equals(selected, true)) 2.dp else 0.dp,
                    MaterialTheme.colorScheme.onSurface,
                    CircleShape,
                )
                .padding(3.dp)
                .background(color(hex), CircleShape)
        )
    }
}

@Composable
private fun SettingsScreen(model: TallyViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current.applicationContext
    val pending by model.pendingBackup.collectAsState()
    val confirming by model.confirmRestore.collectAsState()
    val busy by model.backupBusy.collectAsState()
    val error by model.backupError.collectAsState()
    val errorIsExport by model.backupErrorIsExport.collectAsStateWithLifecycle()
    var exportBeforeRestore by rememberSaveable { mutableStateOf(false) }
    val exporter =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri == null) {
                model.notify("Export canceled")
                if (exportBeforeRestore) model.confirmRestore.value = true
                exportBeforeRestore = false
            } else
                model.runBackup {
                    model.backupBusy.value = true
                    model.backupError.value = null
                    try {
                        val json = model.exportBackup()
                        withContext(Dispatchers.IO) {
                            requireNotNull(context.contentResolver.openOutputStream(uri, "wt"))
                                .bufferedWriter(Charsets.UTF_8)
                                .use { it.write(json) }
                        }
                        model.notify("Backup saved")
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        model.backupErrorIsExport.value = true
                        model.backupError.value =
                            "Backup could not be saved. Please try another location."
                    } finally {
                        model.backupBusy.value = false
                        if (exportBeforeRestore) model.confirmRestore.value = true
                        exportBeforeRestore = false
                    }
                }
        }
    val importer =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) model.notify("Restore canceled")
            else
                model.runBackup {
                    model.backupBusy.value = true
                    model.backupError.value = null
                    try {
                        val text =
                            withContext(Dispatchers.IO) {
                                requireNotNull(context.contentResolver.openInputStream(uri)).use {
                                    input ->
                                    val out = ByteArrayOutputStream()
                                    val bytes = ByteArray(8192)
                                    while (true) {
                                        val read = input.read(bytes)
                                        if (read < 0) break
                                        require(out.size() + read <= BackupCodec.MAX_BYTES) {
                                            "Backup is too large (maximum 20 MB)."
                                        }
                                        out.write(bytes, 0, read)
                                    }
                                    out.toString("UTF-8")
                                }
                            }
                        model.setPendingBackup(model.parseBackup(text))
                        model.confirmRestore.value = true
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        model.backupErrorIsExport.value = false
                        model.backupError.value =
                            "Backup could not be opened. Your current data has not changed."
                    } finally {
                        model.backupBusy.value = false
                    }
                }
        }
    val export = { exporter.launch("tally-backup-${LocalDate.now()}.json") }
    val import = {
        importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = model::back, enabled = !busy) {
                Glyph("left", description = "Back")
            }
            Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Text("Appearance", Modifier.padding(bottom = 12.dp), fontSize = 14.sp)
        Segments(
            listOf("dark" to "Dark", "light" to "Light"),
            if (state.light) "light" else "dark",
        ) {
            model.setTheme(it == "light")
        }
        Text("Backup", Modifier.padding(top = 28.dp, bottom = 12.dp), fontSize = 14.sp)
        Column(
            Modifier.fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, Shape)
                .background(MaterialTheme.colorScheme.surface, Shape)
        ) {
            SettingsAction(
                "Export backup",
                "Save all subjects and tallies",
                "export",
                !busy,
                export,
            )
            HorizontalDivider(
                Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outline,
            )
            SettingsAction("Restore backup", "Choose a backup file", "import", !busy, import)
        }
        Text(
            "Restore replaces your data after confirmation.",
            Modifier.padding(top = 12.dp),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))

        error?.let { message ->
            Column(
                Modifier.fillMaxWidth()
                    .padding(top = 24.dp)
                    .border(1.dp, MaterialTheme.colorScheme.error, Shape)
                    .padding(16.dp)
            ) {
                Text(message, fontSize = 14.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        onClick = {
                            model.backupError.value = null
                            if (model.backupErrorIsExport.value) export() else import()
                        },
                        enabled = !busy,
                    ) {
                        Text(
                            if (errorIsExport) "Try another location" else "Choose another file",
                            fontSize = 12.sp,
                        )
                    }
                    TextButton(onClick = { model.backupError.value = null }) {
                        Text("Dismiss", fontSize = 12.sp)
                    }
                }
            }
        }
        Text(
            "Saved on this device · Works offline",
            Modifier.padding(top = 28.dp),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Export a backup before uninstalling.",
            Modifier.padding(top = 12.dp),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Tally ${BuildConfig.VERSION_NAME}",
            Modifier.padding(top = 16.dp),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (confirming)
        pending?.let { backup ->
            AlertDialog(
                onDismissRequest = {
                    if (!busy) {
                        model.confirmRestore.value = false
                        model.setPendingBackup(null)
                    }
                },
                title = { Text("Replace current data?") },
                text = {
                    Column {
                        Text(
                            "Backup: ${backup.subjects.size} subjects · ${backup.tallies.size} tallies",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Your current subjects and tallies will be replaced. This cannot be undone.",
                            Modifier.padding(top = 16.dp),
                            fontSize = 14.sp,
                        )
                        OutlinedButton(
                            onClick = {
                                model.confirmRestore.value = false
                                exportBeforeRestore = true
                                export()
                            },
                            enabled = !busy,
                            shape = Shape,
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        ) {
                            Text("Export current data first")
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            model.backupBusy.value = true
                            model.runBackup {
                                try {
                                    model.restore(backup)
                                    model.setPendingBackup(null)
                                    model.confirmRestore.value = false
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    model.backupError.value =
                                        "Restore failed. Your current data has not changed."
                                    model.confirmRestore.value = false
                                } finally {
                                    model.backupBusy.value = false
                                }
                            }
                        },
                        enabled = !busy,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        shape = Shape,
                    ) {
                        Text("Replace with backup")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            model.confirmRestore.value = false
                            model.setPendingBackup(null)
                        },
                        enabled = !busy,
                    ) {
                        Text("Cancel")
                    }
                },
            )
        }
}

@Composable
private fun SettingsAction(
    title: String,
    subtitle: String,
    glyph: String,
    enabled: Boolean,
    click: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = enabled, onClick = click)
            .heightIn(min = 72.dp)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.sp,
                color =
                    if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
            Glyph(glyph)
        }
    }
}

@Composable
private fun Dot(color: Color, size: Dp = 9.dp) {
    Box(
        Modifier.size(size)
            .background(color, CircleShape)
            .border(.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
    )
}

@Composable
private fun Glyph(type: String, modifier: Modifier = Modifier, description: String? = null) {
    val tint = if (type == "brand") MaterialTheme.colorScheme.primary else LocalContentColor.current
    Canvas(
        modifier
            .size(20.dp)
            .then(
                if (description != null) Modifier.semantics { contentDescription = description }
                else Modifier
            )
    ) {
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(
                tint,
                androidx.compose.ui.geometry.Offset(size.width * x1 / 24, size.height * y1 / 24),
                androidx.compose.ui.geometry.Offset(size.width * x2 / 24, size.height * y2 / 24),
                strokeWidth = 1.7.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        fun circle(radius: Float) {
            drawCircle(
                tint,
                size.minDimension * radius / 24,
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.7.dp.toPx()),
            )
        }
        when (type) {
            "settings" -> {
                line(4f, 6f, 20f, 6f)
                line(4f, 12f, 20f, 12f)
                line(4f, 18f, 20f, 18f)
                line(8f, 4f, 8f, 8f)
                line(16f, 10f, 16f, 14f)
                line(10f, 16f, 10f, 20f)
            }
            "check" -> {
                line(4f, 12f, 9f, 17f)
                line(9f, 17f, 20f, 6f)
            }
            "export" -> {
                line(12f, 16f, 12f, 3f)
                line(8f, 7f, 12f, 3f)
                line(16f, 7f, 12f, 3f)
                line(5f, 13f, 5f, 21f)
                line(5f, 21f, 19f, 21f)
                line(19f, 21f, 19f, 13f)
            }
            "import" -> {
                line(12f, 3f, 12f, 16f)
                line(8f, 12f, 12f, 16f)
                line(16f, 12f, 12f, 16f)
                line(5f, 13f, 5f, 21f)
                line(5f, 21f, 19f, 21f)
                line(19f, 21f, 19f, 13f)
            }

            "brand" -> {
                listOf(5f, 10f, 15f, 20f).forEach { line(it, 4f, it, 20f) }
                line(2f, 17f, 22f, 7f)
            }
            "count" -> {
                circle(8f)
                line(12f, 8f, 12f, 16f)
                line(8f, 12f, 16f, 12f)
            }
            "plus" -> {
                line(12f, 5f, 12f, 19f)
                line(5f, 12f, 19f, 12f)
            }
            "history" -> listOf(5f, 12f, 19f).forEach { line(4f, it, 20f, it) }
            "down" -> {
                line(6f, 9f, 12f, 15f)
                line(12f, 15f, 18f, 9f)
            }
            "left" -> {
                line(15f, 6f, 9f, 12f)
                line(9f, 12f, 15f, 18f)
            }
            "right" -> {
                line(9f, 6f, 15f, 12f)
                line(15f, 12f, 9f, 18f)
            }
            "undo" -> {
                line(8f, 5f, 4f, 9f)
                line(4f, 9f, 8f, 13f)
                line(4f, 9f, 16f, 9f)
                line(16f, 9f, 19f, 13f)
                line(19f, 13f, 17f, 18f)
                line(17f, 18f, 10f, 18f)
            }
            "edit" -> {
                line(5f, 16f, 16f, 5f)
                line(16f, 5f, 20f, 9f)
                line(20f, 9f, 9f, 20f)
                line(9f, 20f, 4f, 20f)
                line(4f, 20f, 5f, 16f)
            }
            "sun" -> {
                circle(4f)
                listOf(
                        0.0,
                        Math.PI / 2,
                        Math.PI,
                        Math.PI * 1.5,
                        Math.PI / 4,
                        Math.PI * .75,
                        Math.PI * 1.25,
                        Math.PI * 1.75,
                    )
                    .forEach { a ->
                        line(
                            12 + kotlin.math.cos(a).toFloat() * 8,
                            12 + kotlin.math.sin(a).toFloat() * 8,
                            12 + kotlin.math.cos(a).toFloat() * 10,
                            12 + kotlin.math.sin(a).toFloat() * 10,
                        )
                    }
            }
            "moon" -> {
                drawArc(
                    tint,
                    45f,
                    270f,
                    false,
                    androidx.compose.ui.geometry.Offset(size.width * .2f, size.height * .2f),
                    androidx.compose.ui.geometry.Size(size.width * .6f, size.height * .6f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.7.dp.toPx()),
                )
                line(17f, 6f, 11f, 10f)
                line(11f, 10f, 11f, 14f)
                line(11f, 14f, 17f, 18f)
            }
            else -> {
                circle(4f)
                listOf(0f, 6f, 12f, 18f).forEach { n ->
                    val a = n / 24 * Math.PI * 2
                    line(
                        12 + kotlin.math.cos(a).toFloat() * 7,
                        12 + kotlin.math.sin(a).toFloat() * 7,
                        12 + kotlin.math.cos(a).toFloat() * 10,
                        12 + kotlin.math.sin(a).toFloat() * 10,
                    )
                }
            }
        }
    }
}
