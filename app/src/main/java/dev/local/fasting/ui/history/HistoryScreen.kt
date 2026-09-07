package dev.local.fasting.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.HistoryToggleOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.local.fasting.domain.FastRecord
import dev.local.fasting.domain.FastingGoal
import dev.local.fasting.domain.MealComposition
import dev.local.fasting.domain.StageEngine
import dev.local.fasting.domain.SymptomLog
import dev.local.fasting.ui.components.DetailRow
import dev.local.fasting.ui.components.EmptyState
import dev.local.fasting.ui.components.InstantPickerRow
import dev.local.fasting.ui.components.SectionHeader
import dev.local.fasting.ui.components.SegmentedFilterRow
import dev.local.fasting.ui.format.formatDayTime
import dev.local.fasting.ui.format.formatHoursMinutes
import dev.local.fasting.ui.format.formatTime
import dev.local.fasting.ui.theme.Viz
import java.time.Duration
import java.time.Instant

/**
 * The log itself, with full CRUD. Any fast can be re-timed after the event, including the one that
 * is currently running — the engine simply re-derives its stages from the new timestamps.
 */
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onSave: (
        id: Long,
        start: Instant,
        end: Instant?,
        meal: MealComposition,
        goal: FastingGoal,
        note: String,
    ) -> Unit,
    onDelete: (Long) -> Unit,
    onDeleteSymptomLog: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<FastRecord?>(null) }
    var editorOpen by remember { mutableStateOf(false) }

    // Lazy: the log grows without bound, so rows are composed as they scroll into view.
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(
                    title = "Fasting log",
                    subtitle = "${state.items.size} logged",
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = {
                    editing = null
                    editorOpen = true
                }) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Add")
                }
            }
        }

        if (!state.hasEntries) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Outlined.HistoryToggleOff,
                    title = "No fasts logged yet",
                    message = "Start one from the timer, or add a fast you have already finished.",
                )
            }
        }

        items(state.items, key = { it.record.id }) { item ->
            FastCard(
                item = item,
                onEdit = {
                    editing = item.record
                    editorOpen = true
                },
                onDelete = { onDelete(item.record.id) },
                onDeleteSymptomLog = onDeleteSymptomLog,
            )
        }

        item(key = "footer") { Spacer(Modifier.height(16.dp)) }
    }

    if (editorOpen) {
        FastEditorSheet(
            existing = editing,
            onDismiss = { editorOpen = false },
            onSave = { start, end, meal, goal, note ->
                onSave(editing?.id ?: 0L, start, end, meal, goal, note)
                editorOpen = false
            },
            onDelete = editing?.let { record ->
                {
                    onDelete(record.id)
                    editorOpen = false
                }
            },
        )
    }
}

@Composable
private fun FastCard(
    item: FastListItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDeleteSymptomLog: (Long) -> Unit,
) {
    val viz = Viz.colors
    Card(
        onClick = onEdit,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = formatHoursMinutes(item.durationHours),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = buildString {
                            append(formatDayTime(item.record.start))
                            append(" → ")
                            append(item.record.end?.let { formatTime(it) } ?: "running")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete fast",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Identity comes from the swatch beside the text, never from colouring the text.
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(viz.forStage(item.stageReached)),
                )
                Text(
                    text = item.stageReached.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "·",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = item.record.meal.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            DetailRow(
                label = "Autophagic hours",
                value = formatHoursMinutes(item.autophagyHours),
            )

            if (item.record.note.isNotBlank()) {
                Text(
                    text = item.record.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (item.symptomLogs.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                item.symptomLogs.forEach { log ->
                    SymptomLogRow(
                        log = log,
                        fastStart = item.record.start,
                        onDelete = { onDeleteSymptomLog(log.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SymptomLogRow(
    log: SymptomLog,
    fastStart: Instant,
    onDelete: () -> Unit,
) {
    val elapsed = Duration.between(fastStart, log.timestamp)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "At ${formatHoursMinutes(elapsed.toMillis() / 3_600_000.0)}: " +
                    "energy ${log.energyLevel}/5 · clarity ${log.clarityLevel}/5 · hunger ${log.hungerLevel}/5",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (log.symptoms.isNotEmpty()) {
                Text(
                    text = log.symptoms.joinToString { it.displayName },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (log.note.isNotBlank()) {
                Text(
                    text = log.note,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Delete log",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Editor for one fast. Leaving "still running" on saves an open-ended fast, which is how a forgotten
 * start gets fixed after the fact.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FastEditorSheet(
    existing: FastRecord?,
    onDismiss: () -> Unit,
    onSave: (
        start: Instant,
        end: Instant?,
        meal: MealComposition,
        goal: FastingGoal,
        note: String,
    ) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val now = remember { Instant.now() }
    var start by remember { mutableStateOf(existing?.start ?: now.minusSeconds(16 * 3600)) }
    var end by remember { mutableStateOf(existing?.end ?: now) }
    var running by remember { mutableStateOf(existing?.isActive ?: false) }
    var meal by remember { mutableStateOf(existing?.meal ?: MealComposition.DEFAULT) }
    // A fast logged after the event had no live target, so a new one starts open rather than
    // inheriting the app default and claiming a goal that was never set.
    var goal by remember { mutableStateOf(existing?.goal ?: FastingGoal.OPEN) }
    var note by remember { mutableStateOf(existing?.note ?: "") }

    val effectiveEnd = if (running) null else end
    val durationHours = Duration.between(start, effectiveEnd ?: now).toMillis() / 3_600_000.0
    val invalid = effectiveEnd != null && effectiveEnd.isBefore(start)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = if (existing == null) "Add a fast" else "Edit fast",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            InstantPickerRow(label = "Started", instant = start, onChange = { start = it })

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Still running",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Switch(checked = running, onCheckedChange = { running = it })
            }

            if (!running) {
                InstantPickerRow(label = "Ended", instant = end, onChange = { end = it })
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Fasting goal",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SegmentedFilterRow(
                    options = FastingGoal.entries.toList(),
                    selected = goal,
                    labelOf = { it.label },
                    onSelect = { goal = it },
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Pre-fast meal",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MealComposition.entries.forEach { option ->
                        FilterChip(
                            selected = option == meal,
                            onClick = { meal = option },
                            label = { Text(option.displayName) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            if (invalid) {
                Text(
                    text = "The end time is before the start time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                DetailRow(
                    label = "Duration",
                    value = formatHoursMinutes(durationHours),
                )
                DetailRow(
                    label = "Stage reached",
                    value = StageEngine.stageAt(durationHours, meal).shortName,
                )
                DetailRow(
                    label = "Autophagic hours",
                    value = formatHoursMinutes(StageEngine.autophagyHours(durationHours, meal)),
                )
                goal.fastHours?.let { target ->
                    DetailRow(
                        label = "Against ${goal.label}",
                        value = if (durationHours >= target) {
                            "met · +${formatHoursMinutes(durationHours - target)}"
                        } else {
                            "${formatHoursMinutes(target - durationHours)} short"
                        },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                        Text("Delete")
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = { onSave(start, effectiveEnd, meal, goal, note.trim()) },
                    enabled = !invalid,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save")
                }
            }
        }
    }
}
