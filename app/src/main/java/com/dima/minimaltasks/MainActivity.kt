package com.dima.minimaltasks

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.dima.minimaltasks.data.local.AttachmentEntity
import com.dima.minimaltasks.data.local.RecurrenceUnit
import com.dima.minimaltasks.data.local.TaskEntity
import com.dima.minimaltasks.data.backup.BackupManager
import com.dima.minimaltasks.ui.EditorError
import com.dima.minimaltasks.ui.TaskEditorState
import com.dima.minimaltasks.ui.TaskFormatters
import com.dima.minimaltasks.ui.CalendarDaySummary
import com.dima.minimaltasks.ui.CalendarDotKind
import com.dima.minimaltasks.ui.CalendarMonthModel
import com.dima.minimaltasks.ui.CalendarTaskGrouping
import com.dima.minimaltasks.ui.CompletionFeedback
import com.dima.minimaltasks.ui.CompletionFeedbackPolicy
import com.dima.minimaltasks.ui.TasksViewModel
import com.dima.minimaltasks.ui.TasksViewModelFactory
import com.dima.minimaltasks.data.settings.SettingsRepository
import com.dima.minimaltasks.data.settings.SettingsState
import com.dima.minimaltasks.data.settings.ThemeMode
import com.dima.minimaltasks.notifications.AlarmAccuracy
import com.dima.minimaltasks.notifications.ReminderScheduling
import com.dima.minimaltasks.ui.theme.MinimalTasksTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val Blue = Color(0xFF2F80ED)
private val Red = Color(0xFFEF4444)

class MainActivity : AppCompatActivity() {
    private lateinit var app: MinimalTasksApplication
    private lateinit var reminderPermissionController: ReminderPermissionController

    private val tasksViewModel: TasksViewModel by lazy {
        ViewModelProvider(this, TasksViewModelFactory(app.repository, app.attachmentStore, app.reminderCoordinator))[TasksViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = application as MinimalTasksApplication
        reminderPermissionController = ReminderPermissionController(this, app)
        setContent {
            val settings by app.settingsRepository.state.collectAsStateWithLifecycle(initialValue = SettingsState())
            MinimalTasksTheme(themeMode = settings.themeMode) {
                MinimalTasksApp(
                    viewModel = tasksViewModel,
                    context = applicationContext,
                    contentResolver = contentResolver,
                    settings = settings,
                    settingsRepository = app.settingsRepository,
                    backupManager = app.backupManager,
                    reminderPermissionController = reminderPermissionController,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::reminderPermissionController.isInitialized) reminderPermissionController.onResume()
    }
}

@Composable
private fun MinimalTasksApp(
    viewModel: TasksViewModel,
    context: Context,
    contentResolver: android.content.ContentResolver,
    settings: SettingsState,
    settingsRepository: SettingsRepository,
    backupManager: BackupManager,
    reminderPermissionController: ReminderPermissionController,
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val alarmAccuracy by reminderPermissionController.alarmAccuracy.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(0) }
    var backupOperationInProgress by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(tasks, settings.notificationsEnabled) {
        reminderPermissionController.requestAutomaticIfNeeded(
            hasEligibleTimedTask = tasks.any(ReminderScheduling::isEligible),
        )
    }

    val handleToggleComplete: suspend (TaskEntity) -> Boolean = { task ->
        if (task.completed) {
            viewModel.toggleCompleted(task)
        } else {
            val completed = viewModel.completeTask(task.id) != null
            if (completed) {
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.task_completed),
                        actionLabel = context.getString(R.string.undo),
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoCompletion(task.id)
                }
            }
            completed
        }
    }
    val handleDelete: (String) -> Unit = { taskId ->
        scope.launch {
            if (viewModel.deleteTask(taskId)) {
                val result = snackbarHostState.showSnackbar(
                    message = context.getString(R.string.task_deleted),
                    actionLabel = context.getString(R.string.undo),
                )
                if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(taskId)
                else viewModel.finalizeDelete(taskId)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            BottomNavigation(
                selectedTab = selectedTab,
                enabled = !backupOperationInProgress,
                onSelected = { selectedTab = it },
            )
        },
    ) { padding ->
        Surface(Modifier.fillMaxSize().padding(padding), color = MaterialTheme.colorScheme.background) {
            when (selectedTab) {
                0 -> TodayScreen(
                    tasks = tasks,
                    onAdd = viewModel::openNewTask,
                    onEdit = viewModel::openExistingTask,
                    onToggleComplete = handleToggleComplete,
                    completionFeedback = CompletionFeedbackPolicy.from(settings),
                    onTogglePriority = { task -> scope.launch { viewModel.togglePriority(task) } },
                    onDelete = handleDelete,
                )
                1 -> CalendarScreen(
                    viewModel = viewModel,
                    onEdit = viewModel::openExistingTask,
                    onToggleComplete = handleToggleComplete,
                    completionFeedback = CompletionFeedbackPolicy.from(settings),
                    onTogglePriority = { task -> scope.launch { viewModel.togglePriority(task) } },
                    onDelete = handleDelete,
                    onAdd = { date -> viewModel.openNewTaskForDate(date) },
                )
                else -> SettingsScreen(
                    settings = settings,
                    repository = settingsRepository,
                    backupManager = backupManager,
                    contentResolver = contentResolver,
                    alarmAccuracy = alarmAccuracy,
                    onNotificationsToggle = reminderPermissionController::requestExplicit,
                    backupOperationInProgress = backupOperationInProgress,
                    onBackupOperationInProgressChange = { backupOperationInProgress = it },
                    onBackupResult = { messageRes ->
                        scope.launch { snackbarHostState.showSnackbar(context.getString(messageRes)) }
                    },
                )
            }
        }
    }
    editor?.let { state ->
        TaskEditorSheet(state, viewModel, contentResolver, context, viewModel::cancelEditor)
    }
}

@Composable
private fun CalendarScreen(
    viewModel: TasksViewModel,
    onAdd: (LocalDate) -> Unit,
    onEdit: (String) -> Unit,
    onToggleComplete: suspend (TaskEntity) -> Boolean,
    completionFeedback: com.dima.minimaltasks.ui.CompletionFeedbackDecision,
    onTogglePriority: (TaskEntity) -> Unit,
    onDelete: (String) -> Unit,
) {
    val locale = LocalConfiguration.current.locales.get(0)
    val zoneId = ZoneId.systemDefault()
    var selectedDateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var displayedMonthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val selectedDate = runCatching { LocalDate.parse(selectedDateText) }.getOrDefault(LocalDate.now())
    val displayedMonth = runCatching { YearMonth.parse(displayedMonthText) }.getOrDefault(YearMonth.from(selectedDate))
    val monthModel = remember(displayedMonth, locale) { CalendarMonthModel.forMonth(displayedMonth, locale) }
    val calendarTasksFlow = remember(monthModel.cells.first().date, monthModel.cells.last().date, zoneId) {
        viewModel.observeTasksForCalendarRange(monthModel.cells.first().date, monthModel.cells.last().date, zoneId)
    }
    val calendarTasks by calendarTasksFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val tasksByDate = remember(calendarTasks, zoneId) { CalendarTaskGrouping.byLocalDate(calendarTasks, zoneId) }
    val selectedSummary = CalendarTaskGrouping.summaryFor(selectedDate, tasksByDate)
    var showCompleted by remember(selectedDate) { mutableStateOf(false) }

    fun selectDate(date: LocalDate) {
        selectedDateText = date.toString()
        displayedMonthText = YearMonth.from(date).toString()
    }

    fun moveMonth(monthDelta: Long) {
        val nextMonth = displayedMonth.plusMonths(monthDelta)
        val today = LocalDate.now()
        val nextDate = if (YearMonth.from(today) == nextMonth) today else nextMonth.atDay(1)
        selectedDateText = nextDate.toString()
        displayedMonthText = nextMonth.toString()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 28.dp, end = 20.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = calendarMonthTitle(displayedMonth, locale),
                color = MaterialTheme.colorScheme.onBackground,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                fontSize = 34.sp,
                lineHeight = 38.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { moveMonth(-1) }) {
                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = stringResource(R.string.calendar_previous_month), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { moveMonth(1) }) {
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = stringResource(R.string.calendar_next_month), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(Blue).clickable { onAdd(selectedDate) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.calendar_add_task), tint = Color.White)
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
                monthModel.weekdayLabels.forEach { label ->
                    Text(
                        text = label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            monthModel.cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { cell ->
                        CalendarDayCellView(
                            modifier = Modifier.weight(1f),
                            cell = cell,
                            summary = CalendarTaskGrouping.summaryFor(cell.date, tasksByDate),
                            selected = cell.date == selectedDate,
                            today = cell.date == LocalDate.now(),
                            onClick = { selectDate(cell.date) },
                        )
                    }
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, top = 10.dp, end = 20.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = calendarSelectedDateTitle(selectedDate, locale),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = selectedSummary.tasks.size.toString(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        val active = selectedSummary.tasks.filterNot(TaskEntity::completed)
        val completed = selectedSummary.tasks.filter(TaskEntity::completed)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 4.dp, bottom = 8.dp),
        ) {
            if (active.isEmpty() && completed.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_tasks_for_day),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
                    )
                }
            } else {
                items(active, key = TaskEntity::id) {
                    task -> TaskRow(task, onEdit, onToggleComplete, completionFeedback, onTogglePriority, onDelete)
                }
                if (completed.isNotEmpty()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { showCompleted = !showCompleted }.padding(horizontal = 24.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.completed_count, completed.size), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            Spacer(Modifier.weight(1f))
                            Icon(
                                imageVector = if (showCompleted) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = stringResource(if (showCompleted) R.string.hide_completed else R.string.show_completed),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (showCompleted) items(completed, key = TaskEntity::id) {
                        task -> TaskRow(task, onEdit, onToggleComplete, completionFeedback, onTogglePriority, onDelete)
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCellView(
    modifier: Modifier,
    cell: com.dima.minimaltasks.ui.CalendarDayCell,
    summary: CalendarDaySummary,
    selected: Boolean,
    today: Boolean,
    onClick: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales.get(0)
    val accessibilityTitle = calendarSelectedDateTitle(cell.date, locale)
    val textColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        !cell.isInCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
        else -> MaterialTheme.colorScheme.onBackground
    }
    val contentAlpha = if (summary.completedOnly && !selected) 0.56f else 1f
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                this.selected = selected
                contentDescription = accessibilityTitle
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(if (selected) Blue else Color.Transparent).alpha(contentAlpha),
            contentAlignment = Alignment.Center,
        ) {
            if (today && !selected) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Blue.copy(alpha = 0.55f),
                        style = Stroke(width = 1.4.dp.toPx()),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(cell.date.dayOfMonth.toString(), color = textColor, fontSize = 14.sp)
                Row(Modifier.height(5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    summary.dots.take(3).forEach { dot ->
                        val color = when (dot) {
                            CalendarDotKind.ACTIVE_PRIORITY -> Red
                            CalendarDotKind.ACTIVE -> Blue
                            CalendarDotKind.COMPLETED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        }
                        Box(Modifier.size(4.dp).clip(CircleShape).background(color))
                    }
                }
            }
        }
    }
}

private fun calendarMonthTitle(month: YearMonth, locale: Locale): String =
    month.atDay(1).format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))

private fun calendarSelectedDateTitle(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))

@Composable
private fun TodayScreen(
    tasks: List<TaskEntity>,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onToggleComplete: suspend (TaskEntity) -> Boolean,
    completionFeedback: com.dima.minimaltasks.ui.CompletionFeedbackDecision,
    onTogglePriority: (TaskEntity) -> Unit,
    onDelete: (String) -> Unit,
) {
    val active = tasks.filterNot(TaskEntity::completed)
    val completed = tasks.filter(TaskEntity::completed)
    var showCompleted by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 28.dp, end = 20.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = stringResource(R.string.today),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                    fontSize = 43.sp,
                    lineHeight = 46.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(formatTodayDate(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Blue.copy(alpha = 0.13f))
                    .clickable(onClick = onAdd)
                    .padding(horizontal = 15.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Blue, modifier = Modifier.size(23.dp))
                Spacer(Modifier.width(5.dp))
                Text(stringResource(R.string.add_task), color = Blue, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 8.dp),
        ) {
            if (active.isEmpty()) {
                item {
                    Text(
                        stringResource(if (completed.isEmpty()) R.string.no_tasks_today else R.string.no_active_tasks),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
                    )
                }
            } else {
                items(active, key = TaskEntity::id) {
                    task -> TaskRow(task, onEdit, onToggleComplete, completionFeedback, onTogglePriority, onDelete)
                }
            }
            if (completed.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { showCompleted = !showCompleted }.padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.completed_count, completed.size), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        Spacer(Modifier.weight(1f))
                        Icon(
                            imageVector = if (showCompleted) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = stringResource(if (showCompleted) R.string.hide_completed else R.string.show_completed),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (showCompleted) items(completed, key = TaskEntity::id) {
                    task -> TaskRow(task, onEdit, onToggleComplete, completionFeedback, onTogglePriority, onDelete)
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: TaskEntity,
    onEdit: (String) -> Unit,
    onToggleComplete: suspend (TaskEntity) -> Boolean,
    completionFeedback: com.dima.minimaltasks.ui.CompletionFeedbackDecision,
    onTogglePriority: (TaskEntity) -> Unit,
    onDelete: (String) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var completionInProgress by remember(task.id) { mutableStateOf(false) }
    val completionProgress = remember(task.id) { Animatable(0f) }
    val rowScope = rememberCoroutineScope()
    val feedbackView = LocalView.current
    val locale = LocalLocale.current.platformLocale
    val due = TaskFormatters.duePresentation(task.dueAt, task.dueHasTime, System.currentTimeMillis(), locale)
    val descriptionLabel = stringResource(R.string.has_description)
    val checkboxOutline = MaterialTheme.colorScheme.outline
    val detailSeparator = stringResource(R.string.detail_separator)
    val completionActionDescription = stringResource(
        if (task.completed) R.string.mark_task_incomplete else R.string.mark_task_complete,
    )
    val completionStateDescription = stringResource(
        if (task.completed) R.string.task_completed_state else R.string.task_not_completed_state,
    )
    val removePriorityDescription = stringResource(R.string.remove_priority)
    val detail = buildString {
        due?.let { append(it.text) }
        task.recurrenceUnit?.let {
            if (isNotEmpty()) append(detailSeparator)
            append(recurrenceLabel(it, task.recurrenceInterval))
        }
        if (task.description != null) {
            if (isNotEmpty()) append(detailSeparator)
            append(descriptionLabel)
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(76.dp).clickable(enabled = !completionInProgress) { onEdit(task.id) }.padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .semantics {
                    role = Role.Checkbox
                    contentDescription = completionActionDescription
                    stateDescription = completionStateDescription
                }
                .clickable(enabled = !completionInProgress) {
                    rowScope.launch {
                        if (completionInProgress) return@launch
                        if (task.completed) {
                            onToggleComplete(task)
                            return@launch
                        }
                        completionInProgress = true
                        try {
                            completionProgress.snapTo(0f)
                            completionProgress.animateTo(
                                targetValue = 1f,
                                animationSpec = androidx.compose.animation.core.tween(
                                    durationMillis = 220,
                                    easing = androidx.compose.animation.core.FastOutSlowInEasing,
                                ),
                            )
                            if (onToggleComplete(task)) {
                                CompletionFeedback.play(feedbackView, completionFeedback)
                            }
                        } finally {
                            completionProgress.snapTo(0f)
                            completionInProgress = false
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(37.dp)
                    .scale(if (task.completed) 1f else if (completionInProgress) 0.94f + completionProgress.value * 0.06f else 0.94f)
                    .clip(RoundedCornerShape(7.dp)),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                val progress = completionProgress.value
                val fill = if (task.completed) 1f else progress
                if (fill > 0f) {
                    drawRoundRect(
                        color = Blue.copy(alpha = fill),
                        cornerRadius = CornerRadius(7.dp.toPx()),
                    )
                }
                if (fill < 1f) {
                    drawRoundRect(
                        color = checkboxOutline.copy(alpha = 1f - progress * 0.75f),
                        style = Stroke(width = 1.7.dp.toPx()),
                        cornerRadius = CornerRadius(7.dp.toPx()),
                    )
                }
                if (fill > 0.45f) {
                    val checkProgress = if (task.completed) 1f else ((progress - 0.45f) / 0.55f).coerceIn(0f, 1f)
                    val checkPath = Path().apply {
                        moveTo(size.width * 0.27f, size.height * 0.51f)
                        lineTo(size.width * 0.44f, size.height * 0.68f)
                        lineTo(size.width * 0.75f, size.height * 0.34f)
                    }
                    drawPath(
                        path = checkPath,
                        color = Color.White.copy(alpha = checkProgress),
                        style = Stroke(width = 2.4.dp.toPx()),
                    )
                }
                if (completionInProgress) {
                    val ringProgress = progress.coerceIn(0f, 1f)
                    val ringAlpha = (1f - ringProgress) * 0.28f
                    drawCircle(
                        color = Blue.copy(alpha = ringAlpha),
                        radius = size.minDimension * (0.22f + ringProgress * 0.22f),
                        style = Stroke(width = 1.2.dp.toPx()),
                    )
                    val rayRadius = size.minDimension * (0.34f + ringProgress * 0.09f)
                    val rayLength = size.minDimension * 0.07f
                    repeat(4) { index ->
                        val angle = Math.toRadians(index * 90.0)
                        val center = Offset(
                            x = size.width / 2f + kotlin.math.cos(angle).toFloat() * rayRadius,
                            y = size.height / 2f + kotlin.math.sin(angle).toFloat() * rayRadius,
                        )
                        val direction = Offset(kotlin.math.cos(angle).toFloat(), kotlin.math.sin(angle).toFloat())
                        drawLine(
                            color = Blue.copy(alpha = ringAlpha * 0.9f),
                            start = center - direction * rayLength,
                            end = center + direction * rayLength,
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                }
            }
            }
        }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(
                text = task.title,
                color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
            )
            if (detail.isNotEmpty()) Text(detail, color = if (due?.overdue == true && !task.completed) Red else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
        if (task.isPriority) {
            IconButton(
                enabled = !completionInProgress,
                onClick = { onTogglePriority(task) },
            ) {
                Box(
                    Modifier.semantics {
                        contentDescription = removePriorityDescription
                    },
                ) { FlagIcon() }
            }
        }
        Box {
            IconButton(enabled = !completionInProgress, onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreHoriz, contentDescription = stringResource(R.string.more_actions), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { menuExpanded = false; onEdit(task.id) }, leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) })
                DropdownMenuItem(text = { Text(if (task.isPriority) stringResource(R.string.remove_priority) else stringResource(R.string.add_priority)) }, onClick = { menuExpanded = false; onTogglePriority(task) })
                DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menuExpanded = false; onDelete(task.id) }, leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) })
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 77.dp, end = 24.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditorSheet(
    state: TaskEditorState,
    viewModel: TasksViewModel,
    contentResolver: android.content.ContentResolver,
    context: Context,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { viewModel.stageAttachment(contentResolver, it) } }
    val date = state.dueAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() } ?: LocalDate.now()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp).navigationBarsPadding().imePadding(),
        ) {
            Text(stringResource(if (state.sourceTask == null) R.string.new_task else R.string.edit_task), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(value = state.title, onValueChange = { viewModel.updateEditor { value -> value.copy(title = it) } }, label = { Text(stringResource(R.string.task_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(value = state.description, onValueChange = { viewModel.updateEditor { value -> value.copy(description = it) } }, label = { Text(stringResource(R.string.description)) }, minLines = 3, maxLines = 5, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Blue)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.due_date), modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { DatePickerDialog(context, { _, year, month, day -> viewModel.setDueDate(LocalDate.of(year, month + 1, day)) }, date.year, date.monthValue - 1, date.dayOfMonth).show() }) { Text(if (state.dueAt == null) stringResource(R.string.choose) else formatShortDate(date)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = Blue)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.due_time), modifier = Modifier.weight(1f))
                OutlinedButton(enabled = state.dueAt != null, onClick = {
                    val time = state.dueAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime() }
                    TimePickerDialog(context, { _, hour, minute -> viewModel.setDueTime(hour, minute) }, time?.hour ?: 9, time?.minute ?: 0, true).show()
                }) { Text(if (state.hasTime) formatShortTime(state.dueAt) else stringResource(R.string.optional)) }
            }
            if (state.dueAt != null) TextButton(onClick = viewModel::clearDueDate) { Text(stringResource(R.string.clear_due)) }
            SettingSwitchRow(stringResource(R.string.priority), state.isPriority, { viewModel.updateEditor { value -> value.copy(isPriority = it) } }) { FlagIcon() }
            SettingSwitchRow(stringResource(R.string.repeat_task), state.recurrenceEnabled, { viewModel.updateEditor { value -> value.copy(recurrenceEnabled = it) } }) { Icon(Icons.Default.Repeat, contentDescription = null, tint = Blue) }
            AnimatedVisibility(state.recurrenceEnabled) {
                Column {
                    OutlinedTextField(value = state.recurrenceInterval, onValueChange = { value -> if (value.length <= 3 && value.all(Char::isDigit)) viewModel.updateEditor { it.copy(recurrenceInterval = value) } }, label = { Text(stringResource(R.string.interval)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.width(100.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RecurrenceUnitChip(RecurrenceUnit.DAY, state, viewModel)
                        RecurrenceUnitChip(RecurrenceUnit.WEEK, state, viewModel)
                        RecurrenceUnitChip(RecurrenceUnit.MONTH, state, viewModel)
                        RecurrenceUnitChip(RecurrenceUnit.YEAR, state, viewModel)
                    }
                    if (state.recurrenceUnit == RecurrenceUnit.WEEK) {
                        Text(stringResource(R.string.weekdays), modifier = Modifier.padding(top = 12.dp, bottom = 6.dp), style = MaterialTheme.typography.labelLarge)
                        WeekdayChips(state, viewModel)
                    }
                }
            }
            AttachmentsSection(state, viewModel, launcher, context)
            state.error?.let { Text(stringResource(errorMessage(it)), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { scope.launch { viewModel.saveEditor() } }) { Text(stringResource(R.string.save)) }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, leading: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(12.dp))
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun RecurrenceUnitChip(unit: RecurrenceUnit, state: TaskEditorState, viewModel: TasksViewModel) {
    FilterChip(selected = state.recurrenceUnit == unit, onClick = { viewModel.updateEditor { it.copy(recurrenceUnit = unit) } }, label = { Text(recurrenceUnitLabel(unit)) }, modifier = Modifier.padding(end = 3.dp))
}

@Composable
private fun WeekdayChips(state: TaskEditorState, viewModel: TasksViewModel) {
    val labels = listOf(R.string.mon, R.string.tue, R.string.wed, R.string.thu, R.string.fri, R.string.sat, R.string.sun)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { index, label ->
            FilterChip(selected = state.recurrenceWeekdayMask and (1 shl index) != 0, onClick = { viewModel.updateEditor { it.copy(recurrenceWeekdayMask = it.recurrenceWeekdayMask xor (1 shl index)) } }, label = { Text(stringResource(label), fontSize = 11.sp) })
        }
    }
}

@Composable
private fun AttachmentsSection(state: TaskEditorState, viewModel: TasksViewModel, launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>, context: Context) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.AttachFile, contentDescription = null, tint = Blue)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.attachments), modifier = Modifier.weight(1f))
        TextButton(onClick = { launcher.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.attach)) }
    }
    state.attachments.forEach { attachment ->
        val removed = attachment.id in state.removedAttachmentIds
        AttachmentRow(attachment, removed, { if (!removed) openAttachment(context, attachment) }, { if (removed) viewModel.restoreAttachment(attachment.id) else viewModel.removeAttachment(attachment.id) })
    }
    state.stagedAttachments.forEach { staged ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(staged.value.displayName, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { viewModel.removeStagedAttachment(staged) }) { Text(stringResource(R.string.remove)) }
        }
    }
}

private enum class AppLanguage(val tag: String, val labelRes: Int) {
    SYSTEM("", R.string.language_system),
    RUSSIAN("ru", R.string.language_russian),
    ENGLISH("en", R.string.language_english),
    CHINESE("zh-CN", R.string.language_chinese),
}

@Composable
private fun SettingsScreen(
    settings: SettingsState,
    repository: SettingsRepository,
    backupManager: BackupManager,
    contentResolver: android.content.ContentResolver,
    alarmAccuracy: AlarmAccuracy,
    onNotificationsToggle: (Boolean) -> Unit,
    backupOperationInProgress: Boolean,
    onBackupOperationInProgressChange: (Boolean) -> Unit,
    onBackupResult: (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var languageMenuExpanded by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var showImportConfirmation by remember { mutableStateOf(false) }
    val selectedLanguage = currentAppLanguage()
    BackHandler(enabled = backupOperationInProgress) { }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                onBackupOperationInProgressChange(true)
                val success = try {
                    withContext(Dispatchers.IO) {
                        val output = contentResolver.openOutputStream(uri) ?: error("Unable to open destination")
                        output.use { backupManager.export(it) }
                    }
                    true
                } catch (_: Throwable) {
                    false
                }
                onBackupOperationInProgressChange(false)
                onBackupResult(if (success) R.string.backup_export_success else R.string.backup_error)
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            showImportConfirmation = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Text(
            text = stringResource(R.string.settings),
            color = MaterialTheme.colorScheme.onBackground,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
            fontSize = 43.sp,
            lineHeight = 46.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        )
        Text(
            text = stringResource(R.string.appearance),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 28.dp, bottom = 8.dp),
        )
        Text(stringResource(R.string.theme), style = MaterialTheme.typography.bodyLarge)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.themeMode == mode,
                    onClick = { scope.launch { repository.setThemeMode(mode) } },
                    label = { Text(stringResource(themeLabel(mode))) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 14.dp).clickable { languageMenuExpanded = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.MoreHoriz, contentDescription = null, tint = Blue)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.language), modifier = Modifier.weight(1f))
            Box {
                Text(
                    text = stringResource(selectedLanguage.labelRes),
                    color = Blue,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
                DropdownMenu(
                    expanded = languageMenuExpanded,
                    onDismissRequest = { languageMenuExpanded = false },
                ) {
                    AppLanguage.entries.forEach { language ->
                        DropdownMenuItem(
                            text = { Text(stringResource(language.labelRes)) },
                            onClick = {
                                languageMenuExpanded = false
                                AppCompatDelegate.setApplicationLocales(
                                    if (language.tag.isEmpty()) LocaleListCompat.getEmptyLocaleList()
                                    else LocaleListCompat.forLanguageTags(language.tag),
                                )
                            },
                        )
                    }
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        Text(
            text = stringResource(R.string.behavior),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 24.dp, bottom = 6.dp),
        )
        SettingSwitchRow(stringResource(R.string.completion_sound), settings.completionSoundEnabled, { value -> scope.launch { repository.setCompletionSoundEnabled(value) } }) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = Blue)
        }
        SettingSwitchRow(stringResource(R.string.vibration), settings.vibrationEnabled, { value -> scope.launch { repository.setVibrationEnabled(value) } }) {
            Icon(Icons.Default.AttachFile, contentDescription = null, tint = Blue)
        }
        SettingSwitchRow(stringResource(R.string.notifications), settings.notificationsEnabled, onNotificationsToggle) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Blue)
        }
        Text(
            text = stringResource(reminderAccuracyLabel(alarmAccuracy)),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 44.dp, top = 2.dp),
        )
        HorizontalDivider(modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.backup))
                if (backupOperationInProgress) {
                    Text(
                        stringResource(R.string.backup_in_progress),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        enabled = !backupOperationInProgress,
                        onClick = { exportLauncher.launch("minimal-tasks-backup.zip") },
                    ) { Text(stringResource(R.string.backup_export)) }
                    OutlinedButton(
                        enabled = !backupOperationInProgress,
                        onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                    ) { Text(stringResource(R.string.backup_import)) }
                }
            }
            if (backupOperationInProgress) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        }
    }

    if (showImportConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showImportConfirmation = false
                pendingImportUri = null
            },
            title = { Text(stringResource(R.string.backup_import_confirm_title)) },
            text = { Text(stringResource(R.string.backup_import_confirm_message)) },
            dismissButton = {
                TextButton(onClick = {
                    showImportConfirmation = false
                    pendingImportUri = null
                }) { Text(stringResource(R.string.cancel)) }
            },
            confirmButton = {
                Button(enabled = !backupOperationInProgress, onClick = {
                    val uri = pendingImportUri
                    showImportConfirmation = false
                    pendingImportUri = null
                    if (uri != null) {
                        scope.launch {
                            onBackupOperationInProgressChange(true)
                            val success = try {
                                withContext(Dispatchers.IO) {
                                    val input = contentResolver.openInputStream(uri) ?: error("Unable to open backup")
                                    input.use { backupManager.`import`(it) }
                                }
                                true
                            } catch (_: Throwable) {
                                false
                            }
                            onBackupOperationInProgressChange(false)
                            onBackupResult(if (success) R.string.backup_import_success else R.string.backup_error)
                        }
                    }
                }) { Text(stringResource(R.string.restore)) }
            },
        )
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

private fun reminderAccuracyLabel(accuracy: AlarmAccuracy): Int = when (accuracy) {
    AlarmAccuracy.EXACT -> R.string.reminder_accuracy_exact
    AlarmAccuracy.FALLBACK -> R.string.reminder_accuracy_fallback
    AlarmAccuracy.STANDARD -> R.string.reminder_accuracy_standard
}

private fun currentAppLanguage(): AppLanguage {
    val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    return when {
        tags.startsWith("ru", ignoreCase = true) -> AppLanguage.RUSSIAN
        tags.startsWith("en", ignoreCase = true) -> AppLanguage.ENGLISH
        tags.startsWith("zh", ignoreCase = true) -> AppLanguage.CHINESE
        else -> AppLanguage.SYSTEM
    }
}

@Composable
private fun AttachmentRow(attachment: AttachmentEntity, muted: Boolean, onOpen: () -> Unit, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().alpha(if (muted) 0.5f else 1f).padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            attachment.displayName,
            Modifier.weight(1f).heightIn(min = 48.dp).clickable(enabled = !muted, onClick = onOpen),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = onOpen, enabled = !muted) { Icon(Icons.Default.OpenInNew, contentDescription = stringResource(R.string.open)) }
        TextButton(onClick = onRemove) { Text(stringResource(if (muted) R.string.restore else R.string.remove)) }
    }
}

private fun openAttachment(context: Context, attachment: AttachmentEntity) {
    val app = context.applicationContext as MinimalTasksApplication
    val file = app.attachmentStore.fileFor(attachment.relativePath)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, attachment.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, context.getString(R.string.error_open_attachment), Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, context.getString(R.string.error_open_attachment), Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun FlagIcon() {
    Canvas(modifier = Modifier.size(width = 23.dp, height = 28.dp)) {
        val poleX = 4.dp.toPx()
        drawLine(Red, Offset(poleX, 4.dp.toPx()), Offset(poleX, 25.dp.toPx()), strokeWidth = 2.dp.toPx())
        val flag = Path().apply { moveTo(poleX, 4.dp.toPx()); lineTo(20.dp.toPx(), 4.dp.toPx()); lineTo(20.dp.toPx(), 16.dp.toPx()); lineTo(poleX, 16.dp.toPx()); close() }
        drawPath(flag, color = Red)
    }
}

@Composable
private fun BottomNavigation(selectedTab: Int, enabled: Boolean, onSelected: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().navigationBarsPadding().height(72.dp).background(MaterialTheme.colorScheme.surface), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        NavigationItem(Icons.Default.Home, stringResource(R.string.today), selectedTab == 0, enabled) { onSelected(0) }
        NavigationItem(Icons.Default.CalendarMonth, stringResource(R.string.calendar), selectedTab == 1, enabled) { onSelected(1) }
        NavigationItem(Icons.Default.MoreHoriz, stringResource(R.string.more), selectedTab == 2, enabled) { onSelected(2) }
    }
}

@Composable
private fun NavigationItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(96.dp).clip(RoundedCornerShape(18.dp)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = if (selected) Blue else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Text(label, color = if (selected) Blue else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun PlaceholderScreen(titleRes: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(titleRes), color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp) }
}

@Composable
private fun formatTodayDate(): String {
    val locale = LocalLocale.current.platformLocale
    return remember(locale) {
        java.time.format.DateTimeFormatter.ofPattern("EEE, d MMMM yyyy", locale).format(LocalDate.now())
    }
}

@Composable
private fun formatShortDate(date: LocalDate): String {
    val locale = LocalLocale.current.platformLocale
    return remember(date, locale) { java.time.format.DateTimeFormatter.ofPattern("d MMM", locale).format(date) }
}

@Composable
private fun formatShortTime(millis: Long?): String {
    val locale = LocalLocale.current.platformLocale
    return remember(millis, locale) {
        millis?.let {
            java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
                .withLocale(locale)
                .format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
        }.orEmpty()
    }
}

@Composable
private fun recurrenceUnitLabel(unit: RecurrenceUnit): String = stringResource(when (unit) {
    RecurrenceUnit.DAY -> R.string.unit_day
    RecurrenceUnit.WEEK -> R.string.unit_week
    RecurrenceUnit.MONTH -> R.string.unit_month
    RecurrenceUnit.YEAR -> R.string.unit_year
})

@Composable
private fun recurrenceLabel(unit: RecurrenceUnit, interval: Int): String = stringResource(when (unit) {
    RecurrenceUnit.DAY -> R.string.repeat_days
    RecurrenceUnit.WEEK -> R.string.repeat_weeks
    RecurrenceUnit.MONTH -> R.string.repeat_months
    RecurrenceUnit.YEAR -> R.string.repeat_years
}, interval)

private fun errorMessage(error: EditorError): Int = when (error) {
    EditorError.REQUIRED_TITLE -> R.string.error_title_required
    EditorError.INVALID_RECURRENCE -> R.string.error_recurrence_interval
    EditorError.RECURRENCE_REQUIRES_DUE_DATE -> R.string.error_recurrence_due_date
    EditorError.ATTACHMENT_LIMIT -> R.string.error_attachment_limit
    EditorError.ATTACHMENT_TOO_LARGE -> R.string.error_attachment_too_large
    EditorError.ATTACHMENT_ERROR -> R.string.error_attachment
    EditorError.SAVE_ERROR -> R.string.error_save
}
