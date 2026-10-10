@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.vontk.tally

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.*
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

private fun color(hex: String) = Color(hex.toColorInt())

private fun shortDate(day: LocalDate, locale: Locale) =
    day.format(DateTimeFormatter.ofPattern("MMM d", locale))

private fun stamp(timestamp: Long, zone: ZoneId, locale: Locale) =
    Instant.ofEpochMilli(timestamp)
        .atZone(zone)
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))

private val Shape = RoundedCornerShape(12.dp)

@Composable
fun TallyApp(model: TallyViewModel, systemBars: (Boolean) -> Unit = {}) {
    val state by model.state.collectAsStateWithLifecycle()
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
    val accent = state.selected?.color?.let(::color) ?: color(Palette[0])
    val background = if (state.light) Color(0xFFF8F7FA) else Color(0xFF171719)
    val side = if (state.light) Color(0xFFF0EFF3) else Color(0xFF131315)
    val panel = if (state.light) Color.White else Color(0xFF1E1E21)
    val text = if (state.light) Color(0xFF27252D) else Color(0xFFECECEF)
    val muted = if (state.light) Color(0xFF77737E) else Color(0xFF939399)
    val line = if (state.light) Color(0xFFDEDCE4) else Color(0xFF303034)
    val scheme = if (state.light) lightColorScheme() else darkColorScheme()
    val actionBackgrounds = listOf(background, panel, scheme.surfaceContainerHigh)
    val actionBackground =
        if (state.light) actionBackgrounds.minBy { it.luminance() }
        else actionBackgrounds.maxBy { it.luminance() }
    val actionColor = actionAccent(accent, actionBackground)
    MaterialTheme(
        colorScheme =
            scheme.copy(
                primary = actionColor,
                onPrimary = readableOn(actionColor),
                background = background,
                onBackground = text,
                surface = panel,
                onSurface = text,
                onSurfaceVariant = muted,
                outline = line,
                surfaceVariant = side,
                secondaryContainer = accent.copy(alpha = .12f),
                onSecondaryContainer = text,
            )
    ) {
        LaunchedEffect(state.light) { systemBars(state.light) }
        var editor by remember { mutableStateOf(false) }
        val counterFocus = remember { FocusRequester() }
        LaunchedEffect(state.screen, state.selected?.id, editor) {
            if (state.screen == "counter" && !editor && state.selected != null)
                counterFocus.requestFocus()
        }
        var editingSubject by remember { mutableStateOf<Subject?>(null) }
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(state.message) {
            state.message?.let { message ->
                snackbar.showSnackbar(message)
                model.clearMessage()
            }
        }
        BackHandler(enabled = state.screen != "counter") { model.screen("counter") }
        val create = {
            editingSubject = null
            editor = true
        }
        val edit = {
            editingSubject = state.selected
            editor = true
        }
        Surface(color = background, modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                val wide = maxWidth >= 700.dp
                Scaffold(
                    containerColor = background,
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        if (!wide)
                            NavigationBar(
                                containerColor = side,
                                tonalElevation = 0.dp,
                                windowInsets = WindowInsets(0),
                            ) {
                                listOf("counter" to "Counter", "history" to "History").forEach {
                                    (destination, label) ->
                                    NavigationBarItem(
                                        selected = state.screen == destination,
                                        onClick = { model.screen(destination) },
                                        icon = {
                                            Glyph(
                                                if (destination == "counter") "count" else "history"
                                            )
                                        },
                                        label = { Text(label) },
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
                                    IconButton(onClick = model::toggleTheme) {
                                        Glyph(
                                            if (state.light) "moon" else "sun",
                                            description =
                                                "Switch to ${if (state.light) "dark" else "light"} theme",
                                        )
                                    }
                                    IconButton(onClick = { model.screen("settings") }) {
                                        Glyph("settings", description = "Settings and backup")
                                    }
                                }
                                Text(
                                    "YOUR SUBJECTS",
                                    color = muted,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.8.sp,
                                    modifier =
                                        Modifier.padding(start = 22.dp, top = 12.dp, bottom = 4.dp),
                                )
                                Row(
                                    Modifier.fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    state.subjects.forEach { subject ->
                                        SubjectChip(
                                            subject,
                                            subject.subject.id == state.selected?.id,
                                        ) {
                                            model.select(subject.subject.id)
                                        }
                                    }
                                    TextButton(onClick = create) {
                                        Glyph("plus")
                                        Spacer(Modifier.width(6.dp))
                                        Text("New subject", color = muted)
                                    }
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth()
                                    .heightIn(min = 54.dp)
                                    .padding(horizontal = 22.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Dot(accent)
                                Spacer(Modifier.width(9.dp))
                                Text(
                                    state.selected?.name ?: "Loading…",
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    fontSize = 13.sp,
                                )
                                TextButton(onClick = edit, enabled = state.selected != null) {
                                    Glyph("edit")
                                    Spacer(Modifier.width(6.dp))
                                    Text("Edit subject", fontSize = 12.sp, color = muted)
                                }
                            }
                            HorizontalDivider(color = line)
                            if (state.screen == "settings") SettingsScreen(model)
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
                                                        !editor
                                                ) {
                                                    model.tally()
                                                    true
                                                } else false
                                            }
                                    ) {
                                        if (state.screen == "counter") CounterScreen(state, model)
                                        else HistoryScreen(state, model)
                                    }
                                }
                        }
                    }
                }
            }
        }
        if (editor)
            ProfileEditor(
                editingSubject,
                Palette[state.subjects.size % Palette.size],
                { editor = false },
            ) { name, accentHex ->
                model.saveSubject(editingSubject?.id, name, accentHex)
                editor = false
            }
    }
}

@Composable
private fun Brand(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Glyph("brand", modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Text("tally", fontSize = 25.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp)
        Text(".", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 25.sp)
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
                            if (state.screen == screen)
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
    Column(Modifier.padding(top = 28.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (counter) "ONE MOMENT AT A TIME" else "A RECORD OF YOUR MOMENTS",
                modifier = Modifier.weight(1f),
                fontSize = 10.sp,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (state.preview) "Preview data"
                else if (counter) shortDate(state.today, locale)
                else "${state.entries.size} total tallies",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 10.sp,
                modifier =
                    Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = .25f),
                            CircleShape,
                        )
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = .06f),
                            CircleShape,
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        Text(
            if (counter) "Make it count." else "Your history.",
            fontSize = 30.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = (-1).sp,
            modifier = Modifier.padding(top = 10.dp, bottom = 7.dp),
        )
        Text(
            if (counter) "Small moments. A little more intentional."
            else "Every tally has a place in time.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CounterScreen(state: TallyState, model: TallyViewModel) {
    val locale = LocalLocale.current.platformLocale
    val accent = state.selected?.color?.let(::color) ?: color(Palette[0])
    val haptic = LocalHapticFeedback.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) .96f else 1f, label = "Tally tap")
    LaunchedEffect(pressed) {
        if (pressed) {
            delay(100)
            pressed = false
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Heading(state) }
        item {
            Column(
                Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(232.dp)
                        .border(
                            1.dp,
                            accent.copy(alpha = .1f),
                            CircleShape,
                        )
                        .background(
                            accent.copy(alpha = .04f),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier.size(206.dp)
                            .scale(scale)
                            .background(accent, CircleShape)
                            .clickable(role = Role.Button) {
                                model.tally()
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
                                if ((state.counts[state.today] ?: 0) > 9999) 48.sp else 66.sp,
                            fontWeight = FontWeight.Normal,
                            letterSpacing = (-3).sp,
                            color = readableOn(accent),
                        )
                        Text(
                            "TALLIES TODAY  +",
                            fontSize = 10.sp,
                            letterSpacing = 1.5.sp,
                            color = readableOn(accent),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                Text(
                    "Tap to capture a moment",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 23.dp),
                )
                TextButton(
                    onClick = model::undo,
                    enabled = state.entries.isNotEmpty() && !state.preview,
                ) {
                    Glyph("undo")
                    Spacer(Modifier.width(7.dp))
                    Text("Undo last tally", fontSize = 12.sp)
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, Shape)
                    .background(MaterialTheme.colorScheme.surface, Shape)
                    .padding(vertical = 18.dp)
            ) {
                Stat(
                    "Last 7 days",
                    Moments.week(state.counts, state.today),
                    "tallies",
                    Modifier.weight(1f),
                )
                Stat("All time", state.entries.size, "moments", Modifier.weight(1f))
                Stat(
                    "Current streak",
                    Moments.streak(state.counts, state.today),
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
                        "View history ↗",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
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
            }
        }
        item {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                TextButton(onClick = model::togglePreview) {
                    Text(
                        if (state.preview) "Exit example preview" else "Explore with example data",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int, unit: String, modifier: Modifier) {
    Column(modifier.padding(horizontal = 12.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        Text(value.toString(), fontSize = 24.sp, modifier = Modifier.padding(top = 8.dp))
        Text(unit, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ColumnScope.HistoryScreen(state: TallyState, model: TallyViewModel) {
    val locale = LocalLocale.current.platformLocale
    Heading(state)
    Row(
        Modifier.fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, Shape)
            .background(MaterialTheme.colorScheme.surfaceVariant, Shape)
            .padding(4.dp)
    ) {
        listOf("days", "hours", "month").forEach { view ->
            TextButton(
                onClick = { model.view(view) },
                modifier =
                    Modifier.weight(1f)
                        .background(
                            if (state.view == view)
                                MaterialTheme.colorScheme.outline.copy(alpha = .4f)
                            else Color.Transparent,
                            RoundedCornerShape(8.dp),
                        ),
            ) {
                Text(
                    view.replaceFirstChar { it.uppercase() },
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
    if (state.view != "days")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { model.shift(-1) }) {
                Glyph(
                    "left",
                    description = "Previous ${if (state.view == "month") "month" else "day"}",
                )
            }
            Text(
                if (state.view == "month")
                    state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
                else
                    state.date.format(
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
                    ),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
            )
            IconButton(onClick = { model.shift(1) }) {
                Glyph(
                    "right",
                    description = "Next ${if (state.view == "month") "month" else "day"}",
                )
            }
            TextButton(onClick = model::today) { Text("Today", fontSize = 12.sp) }
        }
    else
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = model::togglePreview) {
                Text(if (state.preview) "Exit preview" else "Preview data", fontSize = 12.sp)
            }
        }
    Box(Modifier.weight(1f).fillMaxWidth()) {
        when (state.view) {
            "days" -> DaysView(state, model)
            "hours" -> HoursView(state)
            else -> MonthView(state, model)
        }
    }
    Text(
        when (state.view) {
            "days" -> "Scroll up for earlier days. Select a day to see its timestamps."
            "hours" -> "Each dot is one tally, placed at its recorded minute."
            else -> "A deeper color means more tallies. Select a day to explore."
        },
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}

@Composable
private fun DaysView(state: TallyState, model: TallyViewModel) {
    val locale = LocalLocale.current.platformLocale
    val earliest = state.counts.keys.filter { it <= state.today }.minOrNull() ?: state.today
    val span =
        maxOf(30, (ChronoUnit.DAYS.between(earliest, state.today) + 1).coerceAtMost(3660).toInt())
    val list = rememberLazyListState(initialFirstVisibleItemIndex = span - 1)
    LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
        items(span, key = { state.today.minusDays((span - it - 1).toLong()).toString() }) { index ->
            val date = state.today.minusDays((span - index - 1).toLong())
            val count = state.counts[date] ?: 0
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(
                Modifier.fillMaxWidth()
                    .clickable { model.openDay(date) }
                    .semantics { contentDescription = "$date, $count tallies" }
                    .padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    date.dayOfMonth.toString(),
                    fontSize = 27.sp,
                    letterSpacing = (-1).sp,
                    modifier = Modifier.width(44.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(date.format(DateTimeFormatter.ofPattern("EEEE", locale)), fontSize = 13.sp)
                    Text(
                        if (date == state.today) "TODAY"
                        else date.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
                        fontSize = 10.sp,
                        color =
                            if (date == state.today) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
                Box(
                    Modifier.width(minOf(75, count * 5).dp)
                        .height(4.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = .65f),
                            CircleShape,
                        )
                )
                Column(Modifier.width(54.dp), horizontalAlignment = Alignment.End) {
                    Text(count.toString(), fontSize = 22.sp)
                    Text(
                        if (count == 1) "tally" else "tallies",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HoursView(state: TallyState) {
    val locale = LocalLocale.current.platformLocale
    val entries = state.entries.filter { Moments.day(it.timestamp, state.zone) == state.date }
    var selected by remember { mutableStateOf<Tally?>(null) }
    LazyColumn(Modifier.fillMaxSize()) {
        items(24) { hour ->
            val moments = entries.filter {
                Instant.ofEpochMilli(it.timestamp).atZone(state.zone).hour == hour
            }
            val height = 96
            Row(Modifier.fillMaxWidth().height(height.dp)) {
                Text(
                    "%02d:00".format(hour),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(48.dp).padding(top = 4.dp),
                )
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                    val trackWidth = maxOf(maxWidth, (moments.size * 48).dp)
                    Box(Modifier.horizontalScroll(rememberScrollState())) {
                        Box(Modifier.width(trackWidth).fillMaxHeight()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            moments.forEachIndexed { index, tally ->
                                val minute =
                                    Instant.ofEpochMilli(tally.timestamp).atZone(state.zone).minute
                                val y = (minute / 60f * (height - 48)).dp
                                Box(
                                    Modifier.offset(x = (index * 48).dp, y = y)
                                        .size(48.dp)
                                        .clickable(role = Role.Button) { selected = tally }
                                        .semantics {
                                            contentDescription =
                                                "Tally at ${stamp(tally.timestamp, state.zone, locale)}"
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Dot(MaterialTheme.colorScheme.primary, 14.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    selected?.let { tally ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Recorded moment") },
            text = {
                Text(
                    "${state.selected?.name}\n${state.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))}\n${stamp(tally.timestamp, state.zone, locale)}"
                )
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Done") } },
        )
    }
}

@Composable
private fun MonthView(state: TallyState, model: TallyViewModel) {
    val locale = LocalLocale.current.platformLocale
    val first = state.month.atDay(1)
    val offset = first.dayOfWeek.value - 1
    val max =
        (1..state.month.lengthOfMonth())
            .maxOf { state.counts[state.month.atDay(it)] ?: 0 }
            .coerceAtLeast(1)
    BoxWithConstraints(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        val width = maxOf(maxWidth, 336.dp)
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Column(Modifier.width(width)) {
                Row {
                    (1..7).forEach { day ->
                        Text(
                            DayOfWeek.of(day)
                                .getDisplayName(java.time.format.TextStyle.SHORT, locale)
                                .uppercase(),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                        )
                    }
                }
                repeat((offset + state.month.lengthOfMonth() + 6) / 7) { week ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { column ->
                            val number = week * 7 + column - offset + 1
                            Box(Modifier.weight(1f).padding(2.dp)) {
                                if (number in 1..state.month.lengthOfMonth()) {
                                    val date = state.month.atDay(number)
                                    val count = state.counts[date] ?: 0
                                    val fill =
                                        if (count == 0) MaterialTheme.colorScheme.surface
                                        else
                                            androidx.compose.ui.graphics.lerp(
                                                MaterialTheme.colorScheme.surface,
                                                MaterialTheme.colorScheme.primary,
                                                .12f + count.toFloat() / max * .60f,
                                            )
                                    val foreground = readableOn(fill)
                                    Column(
                                        Modifier.fillMaxWidth()
                                            .heightIn(min = 64.dp)
                                            .border(
                                                1.dp,
                                                if (date == state.today)
                                                    MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outline,
                                                RoundedCornerShape(8.dp),
                                            )
                                            .background(fill, RoundedCornerShape(8.dp))
                                            .clickable { model.openDay(date) }
                                            .semantics {
                                                contentDescription = "$date, $count tallies"
                                            }
                                            .padding(6.dp)
                                    ) {
                                        Text(
                                            number.toString(),
                                            fontSize = 12.sp,
                                            color = foreground,
                                        )
                                        Text(
                                            if (count > 0) count.toString() else "",
                                            color = foreground,
                                            fontSize = 16.sp,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 18.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Less",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    listOf(.12f, .25f, .45f, .72f).forEach {
                        Box(
                            Modifier.padding(start = 5.dp)
                                .size(12.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = it),
                                    RoundedCornerShape(3.dp),
                                )
                        )
                    }
                    Text(
                        "More",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileEditor(
    subject: Subject?,
    default: String,
    dismiss: () -> Unit,
    save: (String, String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(subject?.name.orEmpty()) }
    var hex by rememberSaveable { mutableStateOf(subject?.color ?: default) }
    var custom by rememberSaveable { mutableStateOf(false) }
    val validColor = Regex("#[0-9a-fA-F]{6}").matches(hex)
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (subject == null) "A new subject." else "Edit subject") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Give your moments a name and a color.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(36) },
                    label = { Text("Subject name") },
                    singleLine = true,
                    supportingText = { Text("${name.length}/36") },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                Text(
                    "Accent color",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                Row { Palette.take(3).forEach { c -> Swatch(c, hex) { hex = c } } }
                Row { Palette.drop(3).forEach { c -> Swatch(c, hex) { hex = c } } }
                TextButton(onClick = { custom = !custom }) { Text("Custom color") }
                if (custom) {
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { hex = it.take(7).uppercase() },
                        label = { Text("Hex color") },
                        isError = !validColor,
                        supportingText = { Text("Example: #A69AEE") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (validColor) {
                        val c = color(hex)
                        val values =
                            listOf(
                                "Red" to ((hex.toColorInt() shr 16) and 255),
                                "Green" to ((hex.toColorInt() shr 8) and 255),
                                "Blue" to (hex.toColorInt() and 255),
                            )
                        values.forEachIndexed { index, (label, value) ->
                            Text(label, fontSize = 11.sp)
                            Slider(
                                value = value.toFloat(),
                                onValueChange = { next ->
                                    val rgb = values.map { it.second }.toMutableList()
                                    rgb[index] = next.toInt()
                                    hex = "#%02X%02X%02X".format(rgb[0], rgb[1], rgb[2])
                                },
                                valueRange = 0f..255f,
                                modifier =
                                    Modifier.semantics {
                                        contentDescription = "$label color channel"
                                    },
                            )
                        }
                        Box(Modifier.fillMaxWidth().height(20.dp).background(c, Shape))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { save(name.trim(), hex) },
                enabled = name.trim().isNotEmpty() && validColor,
            ) {
                Text(if (subject == null) "Create subject" else "Save changes")
            }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Backup?>(null) }
    var busy by remember { mutableStateOf(false) }
    val exporter =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null)
                scope.launch {
                    busy = true
                    try {
                        val json = model.exportBackup()
                        withContext(Dispatchers.IO) {
                            requireNotNull(context.contentResolver.openOutputStream(uri, "wt"))
                                .bufferedWriter(Charsets.UTF_8)
                                .use { it.write(json) }
                        }
                        model.notify("Backup saved")
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        model.notify("Backup could not be saved. Please try another location.")
                    } finally {
                        busy = false
                    }
                }
        }
    val importer =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                scope.launch {
                    busy = true
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
                        pending = model.parseBackup(text)
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        model.notify("Invalid or unreadable backup. Your data has not changed.")
                    } finally {
                        busy = false
                    }
                }
        }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Settings & backup", fontSize = 28.sp, modifier = Modifier.padding(bottom = 10.dp))
        Text(
            "Your moments are stored on this device. Save a backup somewhere safe to move them to another device or restore them later.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = { exporter.launch("tally-backup-${LocalDate.now()}.json") },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Export backup")
        }
        OutlinedButton(
            onClick = {
                importer.launch(
                    arrayOf("application/json", "text/plain", "application/octet-stream")
                )
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Restore backup")
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
        HorizontalDivider(Modifier.padding(vertical = 24.dp))
        Text("Tally 1.0.1", fontWeight = FontWeight.Medium)
        Text(
            "Make a moment count.\nSaved on this device · Works offline",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    pending?.let { backup ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Replace current data?") },
            text = {
                Text(
                    "This backup contains ${backup.subjects.size} subjects and ${backup.tallies.size} tallies. Restoring it replaces all current subjects and tallies. Export your current data first if you want to keep it."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pending = null
                        busy = true
                        scope.launch {
                            try {
                                model.restore(backup)
                            } catch (error: Exception) {
                                if (error is CancellationException) throw error
                                model.notify("Restore failed. Please try again.")
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) {
                    Text("Replace and restore")
                }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Dot(color: Color, size: Dp = 9.dp) {
    Box(Modifier.size(size).background(color, CircleShape))
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
