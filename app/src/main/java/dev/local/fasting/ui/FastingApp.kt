package dev.local.fasting.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.local.fasting.ui.fast.FastScreen
import dev.local.fasting.ui.fast.FastViewModel
import dev.local.fasting.ui.history.HistoryScreen
import dev.local.fasting.ui.history.HistoryViewModel
import dev.local.fasting.ui.info.InfoScreen
import dev.local.fasting.ui.insights.InsightsScreen
import dev.local.fasting.ui.insights.InsightsViewModel
import dev.local.fasting.ui.settings.ClearHistoryConfirmation
import dev.local.fasting.ui.settings.SettingsSheet
import dev.local.fasting.ui.weight.WeightScreen
import dev.local.fasting.ui.weight.WeightViewModel

enum class Destination(val label: String, val icon: ImageVector) {
    TIMER("Timer", Icons.Outlined.Timer),
    WEIGHT("Weight", Icons.Outlined.MonitorWeight),
    INSIGHTS("Insights", Icons.Outlined.Insights),
    LOG("Log", Icons.Outlined.CalendarMonth),
    INFO("Guide", Icons.Outlined.MenuBook),
}

/**
 * Single-activity shell. Five destinations held in local state — a nav graph would add a dependency
 * and a layer of indirection for five leaf screens with no argument passing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FastingApp(
    startDestination: Destination = Destination.TIMER,
    openFeelingDialog: Boolean = false,
    notificationsGranted: Boolean = false,
    onRequestNotificationPermission: () -> Unit = {},
) {
    // Saveable so a rotation or fold does not bounce the user back to the timer.
    var destination by rememberSaveable { mutableStateOf(startDestination) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // 0 = no confirmation showing, 1 = "what will be deleted", 2 = final commit.
    var clearHistoryStep by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    // Each destination keeps its own scroll position and dialog state across tab switches.
    val screenStates = rememberSaveableStateHolder()

    val fastViewModel: FastViewModel = viewModel(factory = FastViewModel.Factory)
    val weightViewModel: WeightViewModel = viewModel(factory = WeightViewModel.Factory)
    val insightsViewModel: InsightsViewModel = viewModel(factory = InsightsViewModel.Factory)
    val historyViewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)

    val fastState by fastViewModel.state.collectAsStateWithLifecycle()
    val weightState by weightViewModel.state.collectAsStateWithLifecycle()
    val insightsState by insightsViewModel.state.collectAsStateWithLifecycle()
    val historyState by historyViewModel.state.collectAsStateWithLifecycle()

    // One snackbar surface for every screen's confirmations and validation failures.
    LaunchedEffect(fastState.message) {
        fastState.message?.let {
            snackbarHostState.showSnackbar(it)
            fastViewModel.consumeMessage()
        }
    }
    LaunchedEffect(weightState.message) {
        weightState.message?.let {
            snackbarHostState.showSnackbar(it)
            weightViewModel.consumeMessage()
        }
    }
    LaunchedEffect(historyState.message) {
        historyState.message?.let {
            snackbarHostState.showSnackbar(it)
            historyViewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(destination.label) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = entry == destination,
                        onClick = { destination = entry },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            screenStates.SaveableStateProvider(destination.name) {
                when (destination) {
                    Destination.TIMER -> FastScreen(
                        state = fastState,
                        onSelectMeal = fastViewModel::selectMeal,
                        onSelectGoal = fastViewModel::selectGoal,
                        onStart = fastViewModel::startFast,
                        onEnd = fastViewModel::endFast,
                        onChangeActiveMeal = fastViewModel::updateActiveMeal,
                        onChangeActiveGoal = fastViewModel::updateActiveGoal,
                        onAdjustStart = fastViewModel::adjustActiveStart,
                        onLogFeeling = fastViewModel::logHowIFeel,
                        onOpenHistory = { destination = Destination.LOG },
                        showFeelingDialogInitially = openFeelingDialog,
                    )

                    Destination.WEIGHT -> WeightScreen(
                        state = weightState,
                        onSave = { id, value, unit, bodyFat, tag, timestamp, note ->
                            weightViewModel.saveEntry(id, value, unit, bodyFat, tag, timestamp, note)
                        },
                        onDelete = weightViewModel::deleteEntry,
                        onSetUnit = weightViewModel::setUnit,
                    )

                    Destination.INSIGHTS -> InsightsScreen(
                        state = insightsState,
                        onSetRange = insightsViewModel::setRange,
                        onShiftCalendarMonth = insightsViewModel::shiftCalendarMonth,
                        onSetDistributionAsBar = insightsViewModel::setDistributionAsBar,
                        onSelectStage = insightsViewModel::selectStage,
                        onSelectDay = insightsViewModel::selectDay,
                        onSelectSymptomPoint = insightsViewModel::selectSymptomPoint,
                    )

                    Destination.LOG -> HistoryScreen(
                        state = historyState,
                        onSave = { id, start, end, meal, goal, note ->
                            historyViewModel.saveFast(id, start, end, meal, goal, note)
                        },
                        onDelete = historyViewModel::deleteFast,
                        onDeleteSymptomLog = historyViewModel::deleteSymptomLog,
                    )

                    Destination.INFO -> InfoScreen()
                }
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            settings = fastState.settings,
            notificationsGranted = notificationsGranted,
            onDismiss = { showSettings = false },
            onSetUnit = weightViewModel::setUnit,
            onSetDefaultMeal = fastViewModel::setDefaultMeal,
            onSetDefaultGoal = fastViewModel::setDefaultGoal,
            onSetLiveNotification = fastViewModel::setLiveNotification,
            onSetEmaPeriod = weightViewModel::setEmaPeriod,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onClearHistoryRequest = {
                showSettings = false
                clearHistoryStep = 1
            },
        )
    }

    ClearHistoryConfirmation(
        step = clearHistoryStep,
        onStepChange = { clearHistoryStep = it },
        onConfirmed = fastViewModel::clearAllHistory,
    )
}
