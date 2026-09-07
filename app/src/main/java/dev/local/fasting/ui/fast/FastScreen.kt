package dev.local.fasting.ui.fast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.local.fasting.domain.FastRecord
import dev.local.fasting.domain.FastStage
import dev.local.fasting.domain.FastingGoal
import dev.local.fasting.domain.GoalEngine
import dev.local.fasting.domain.MealComposition
import dev.local.fasting.domain.StageEngine
import dev.local.fasting.ui.components.DetailRow
import dev.local.fasting.ui.components.InstantPickerRow
import dev.local.fasting.ui.components.LogFeelingDialog
import dev.local.fasting.ui.components.RatioMeter
import dev.local.fasting.ui.components.SectionHeader
import dev.local.fasting.ui.components.SegmentedFilterRow
import dev.local.fasting.ui.components.StageProgressRing
import dev.local.fasting.ui.format.formatClock
import dev.local.fasting.ui.format.formatHoursDecimal
import dev.local.fasting.ui.format.formatHoursMinutes
import dev.local.fasting.ui.format.formatTime
import dev.local.fasting.ui.theme.HeroFigureStyle
import dev.local.fasting.ui.theme.Viz
import java.time.Duration
import java.time.Instant

/**
 * The timer screen: one hero figure, the stage ring around it, and the controls that change state.
 * Everything else on this screen explains the current stage rather than competing with the number.
 *
 * The hero figure follows the goal. While a target is pending it counts down to that target; once
 * the target is met — or if the fast is open-ended — it counts total elapsed time as a general
 * timer, and the ring rescales to match.
 */
@Composable
fun FastScreen(
    state: FastUiState,
    onSelectMeal: (MealComposition) -> Unit,
    onSelectGoal: (FastingGoal) -> Unit,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onChangeActiveMeal: (MealComposition) -> Unit,
    onChangeActiveGoal: (FastingGoal) -> Unit,
    onAdjustStart: (Instant) -> Unit,
    onLogFeeling: (Int, Int, Int, Set<dev.local.fasting.domain.Symptom>, String) -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
    showFeelingDialogInitially: Boolean = false,
) {
    var showFeelingDialog by remember { mutableStateOf(showFeelingDialogInitially) }
    var showStartAdjuster by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TimerHero(state)

        if (state.isFasting) {
            ActiveFastControls(
                state = state,
                onEnd = onEnd,
                onLogFeeling = { showFeelingDialog = true },
                onAdjustStart = { showStartAdjuster = !showStartAdjuster },
            )
            if (showStartAdjuster) {
                StartTimeAdjuster(
                    fast = state.activeFast!!,
                    onAdjust = onAdjustStart,
                    onChangeMeal = onChangeActiveMeal,
                    onChangeGoal = onChangeActiveGoal,
                )
            }
            GoalProgressCard(state)
            AutophagyProgressCard(state)
        } else {
            GoalPicker(
                selected = state.plannedGoal,
                onSelect = onSelectGoal,
            )
            MealCompositionPicker(
                selected = state.plannedMeal,
                onSelect = onSelectMeal,
            )
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Outlined.Bolt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Start fast")
            }
            TextButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Edit, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Log a fast that already happened")
            }
        }

        StageTimeline(
            meal = state.activeFast?.meal ?: state.plannedMeal,
            start = state.activeFast?.start,
            elapsedHours = state.progress?.elapsedHours,
        )

        if (state.recentFasts.isNotEmpty()) {
            RecentFasts(state.recentFasts, state.now, onOpenHistory)
        }

        Spacer(Modifier.height(8.dp))
    }

    if (showFeelingDialog) {
        LogFeelingDialog(
            onDismiss = { showFeelingDialog = false },
            onSave = { energy, clarity, hunger, symptoms, note ->
                onLogFeeling(energy, clarity, hunger, symptoms, note)
                showFeelingDialog = false
            },
        )
    }
}

@Composable
private fun TimerHero(state: FastUiState) {
    val progress = state.progress
    val fast = state.activeFast
    val goal = state.goalProgress ?: GoalEngine.progress(state.plannedGoal, 0.0)
    val goalMoment = fast?.goalReachedAt

    // Counting down to the target is only honest while there is a target ahead and a fast running.
    val countingDown = fast != null && goal.isPending && goalMoment != null

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        StageProgressRing(
            progress = progress ?: StageEngine.progress(0.0, state.plannedMeal),
            goal = goal,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // The caption says which clock the figure below is — without it a 16:00:00 countdown
                // is indistinguishable from sixteen hours already banked.
                Text(
                    text = when {
                        fast == null && goal.isOpen -> "Open timer"
                        fast == null -> "${goal.goal.label} ready"
                        goal.isReached ->
                            "${goal.goal.label} met · +${formatHoursMinutes(goal.overtimeHours!!)}"
                        countingDown -> "${goal.goal.label} · time to goal"
                        else -> "Open timer · elapsed"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (goal.isReached) {
                        Viz.colors.emphasis
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Text(
                    text = when {
                        countingDown -> formatClock(Duration.between(state.now, goalMoment))
                        fast != null -> formatClock(Duration.between(fast.start, state.now))
                        else -> "00:00:00"
                    },
                    style = HeroFigureStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = progress?.stage?.displayName ?: "Not fasting",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                if (fast != null) {
                    Text(
                        // While counting down, the elapsed total would otherwise be off the screen.
                        text = if (countingDown) {
                            "${formatHoursMinutes(goal.elapsedHours)} in · since ${formatTime(fast.start)}"
                        } else {
                            "since ${formatTime(fast.start)}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveFastControls(
    state: FastUiState,
    onEnd: () -> Unit,
    onLogFeeling: () -> Unit,
    onAdjustStart: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onEnd,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("End fast")
            }
            FilledTonalButton(
                onClick = onLogFeeling,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Outlined.MonitorHeart, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("How I feel")
            }
        }
        OutlinedButton(onClick = onAdjustStart, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Edit, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Adjust start time, goal or meal")
        }
        state.activeFast?.let { fast ->
            Text(
                text = buildString {
                    append("Started ${formatTime(fast.start)} after a ")
                    append("${fast.meal.displayName.lowercase()} meal")
                    if (!fast.goal.isOpen) append(" · aiming for ${fast.goal.label}")
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StartTimeAdjuster(
    fast: FastRecord,
    onAdjust: (Instant) -> Unit,
    onChangeMeal: (MealComposition) -> Unit,
    onChangeGoal: (FastingGoal) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InstantPickerRow(
                label = "Fast started",
                instant = fast.start,
                onChange = onAdjust,
            )
            GoalPicker(selected = fast.goal, onSelect = onChangeGoal, compact = true)
            MealCompositionPicker(selected = fast.meal, onSelect = onChangeMeal, compact = true)
        }
    }
}

/**
 * Progress toward the chosen goal, and — once it is met — the statement that the timer has taken
 * over as a general one. Absent entirely for an open-ended fast: there is no target to report.
 */
@Composable
private fun GoalProgressCard(state: FastUiState) {
    val goal = state.goalProgress ?: return
    val target = goal.targetHours ?: return
    val fast = state.activeFast ?: return
    val viz = Viz.colors

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (goal.isReached) {
                        "${goal.goal.label} goal reached"
                    } else {
                        "${goal.goal.label} goal"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (goal.isReached) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = viz.emphasis,
                    )
                }
            }
            Text(
                text = if (goal.isReached) {
                    "Still fasting past the target, so this is now a general timer — it counts up " +
                        "with nothing left to reach."
                } else {
                    goal.goal.description
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RatioMeter(
                fraction = goal.fraction,
                fillColor = if (goal.isReached) viz.emphasis else viz.stageRamp[1],
            )
            DetailRow(label = "Target", value = formatHoursDecimal(target))
            DetailRow(
                label = if (goal.isReached) "Past target by" else "Still to go",
                value = if (goal.isReached) {
                    formatHoursMinutes(goal.overtimeHours!!)
                } else {
                    formatHoursMinutes(goal.hoursRemaining!!)
                },
            )
            fast.goalReachedAt?.let { moment ->
                DetailRow(
                    label = if (goal.isReached) "Target passed at" else "Target lands at",
                    value = formatTime(moment),
                )
            }
        }
    }
}

/**
 * The fasting goal as a fast-hours/eating-hours split. Eating hours are shown because they are what
 * makes "16-8" legible, but nothing is tracked against them.
 */
@Composable
private fun GoalPicker(
    selected: FastingGoal,
    onSelect: (FastingGoal) -> Unit,
    compact: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = "Fasting goal",
            subtitle = if (compact) {
                null
            } else {
                "A wall-clock target. Stage timings below still move with the pre-fast meal."
            },
        )
        SegmentedFilterRow(
            options = FastingGoal.entries.toList(),
            selected = selected,
            labelOf = { it.label },
            onSelect = onSelect,
        )
        Text(
            text = if (selected.isOpen) {
                selected.description
            } else {
                "${selected.splitSummary} — ${selected.description}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AutophagyProgressCard(state: FastUiState) {
    val progress = state.progress ?: return
    val viz = Viz.colors
    val autophagyOnset = StageEngine.onsetHours(FastStage.AUTOPHAGY_THRESHOLD, progress.meal)
    val fraction = (progress.elapsedHours / autophagyOnset).coerceIn(0.0, 1.0).toFloat()

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = if (progress.isInAutophagy) "In autophagy" else "Autophagy onset",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = progress.stage.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RatioMeter(
                fraction = fraction,
                fillColor = if (progress.isInAutophagy) viz.stageRamp[2] else viz.stageRamp[1],
            )
            DetailRow(
                label = if (progress.isInAutophagy) "Autophagic time banked" else "Autophagy starts at",
                value = if (progress.isInAutophagy) {
                    formatHoursMinutes(progress.autophagyHours)
                } else {
                    formatHoursDecimal(autophagyOnset)
                },
            )
            progress.nextStage?.let { next ->
                DetailRow(
                    // Short name, not the display name: "Glycogen Depletion / Ketosis Onset in 12h
                    // 0m" squeezes the label into two lines on a narrower phone.
                    label = "Next stage",
                    value = "${next.shortName} in ${formatHoursMinutes(progress.hoursToNextStage ?: 0.0)}",
                )
            }
            DetailRow(
                label = "Onset modifier",
                value = "${progress.meal.displayName} · ×${"%.2f".format(progress.meal.onsetMultiplier)}",
            )
        }
    }
}

@Composable
private fun MealCompositionPicker(
    selected: MealComposition,
    onSelect: (MealComposition) -> Unit,
    compact: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = "Pre-fast meal",
            subtitle = if (compact) null else "Scales every stage boundary — glycogen decides when autophagy starts.",
        )
        MealComposition.entries.forEach { meal ->
            val isSelected = meal == selected
            Card(
                onClick = { onSelect(meal) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = meal.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        Text(
                            text = meal.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Text(
                        text = "×${"%.2f".format(meal.onsetMultiplier)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

/** The stage schedule for this fast, with the wall-clock time each boundary is reached. */
@Composable
private fun StageTimeline(
    meal: MealComposition,
    start: Instant?,
    elapsedHours: Double?,
) {
    val viz = Viz.colors
    val schedule = StageEngine.schedule(meal)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = "Stage schedule",
            subtitle = "Adjusted for a ${meal.displayName.lowercase()} pre-fast meal",
        )
        schedule.forEachIndexed { index, window ->
            val reached = elapsedHours != null && elapsedHours >= window.startHours
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            if (reached) viz.stageRamp[index] else viz.trackFill
                        ),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = window.stage.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = window.endHours?.let {
                            "${formatHoursDecimal(window.startHours)} – ${formatHoursDecimal(it)}"
                        } ?: "${formatHoursDecimal(window.startHours)}+",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (start != null) {
                    Text(
                        text = formatTime(
                            start.plusMillis((window.startHours * FastRecord.MILLIS_PER_HOUR).toLong())
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentFasts(
    fasts: List<FastRecord>,
    now: Instant,
    onOpenHistory: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(title = "Recent fasts", modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenHistory) { Text("All") }
        }
        fasts.forEach { fast ->
            DetailRow(
                label = dev.local.fasting.ui.format.formatDay(fast.start),
                value = buildString {
                    append(formatHoursMinutes(fast.durationHours(now)))
                    if (fast.isActive) append(" · running")
                },
            )
        }
    }
}
