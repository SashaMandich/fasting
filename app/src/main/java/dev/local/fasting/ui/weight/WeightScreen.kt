package dev.local.fasting.ui.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import dev.local.fasting.domain.WeightRecord
import dev.local.fasting.domain.WeightTag
import dev.local.fasting.domain.WeightUnit
import dev.local.fasting.ui.charts.ChartCard
import dev.local.fasting.ui.charts.ChartTableRow
import dev.local.fasting.ui.charts.LegendItem
import dev.local.fasting.ui.charts.LinePoint
import dev.local.fasting.ui.charts.MarkShape
import dev.local.fasting.ui.charts.TrendLineChart
import dev.local.fasting.ui.components.EmptyState
import dev.local.fasting.ui.components.SectionHeader
import dev.local.fasting.ui.components.SegmentedFilterRow
import dev.local.fasting.ui.components.StatTile
import dev.local.fasting.ui.format.formatDate
import dev.local.fasting.ui.format.formatDayTime
import dev.local.fasting.ui.format.formatPercent
import dev.local.fasting.ui.format.formatWeight
import dev.local.fasting.ui.format.formatWeightDelta
import dev.local.fasting.ui.theme.Viz
import java.time.Instant

/**
 * Weight module: raw measurements are the input, the smoothed trend is the answer. The chart is an
 * emphasis form — the EMA in the accent, the daily readings behind it in the de-emphasis grey.
 */
@Composable
fun WeightScreen(
    state: WeightUiState,
    onSave: (id: Long, value: Double, unit: WeightUnit, bodyFat: Double?, tag: WeightTag, timestamp: Instant, note: String) -> Unit,
    onDelete: (Long) -> Unit,
    onSetUnit: (WeightUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viz = Viz.colors
    val unit = state.settings.weightUnit
    var editing by remember { mutableStateOf<WeightRecord?>(null) }
    var editorOpen by remember { mutableStateOf(false) }

    val trendPoints = state.trend.map { point ->
        LinePoint(
            label = formatDate(point.date),
            value = unit.fromKg(point.emaKg),
            rawValue = unit.fromKg(point.rawKg),
        )
    }

    // Lazy: measurements accumulate daily, so the entry rows are composed on demand.
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "units") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(
                    title = "${state.settings.emaPeriodDays}-day trend",
                    subtitle = "Exponential moving average of daily measurements",
                    modifier = Modifier.weight(1f),
                )
                SegmentedFilterRow(
                    options = WeightUnit.entries.toList(),
                    selected = unit,
                    labelOf = { it.label },
                    onSelect = onSetUnit,
                    modifier = Modifier.width(140.dp),
                )
            }
        }

        item(key = "tiles") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    label = "Trend",
                    value = state.currentEma?.let { formatWeight(it, unit) } ?: "—",
                    deltaLabel = state.emaDeltaKg?.let { "${formatWeightDelta(it, unit)} / 7d" },
                    deltaIsGood = state.emaDeltaKg?.let { it <= 0.0 },
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Last measurement",
                    value = state.latest?.let { formatWeight(it.weightKg, unit) } ?: "—",
                    caption = state.latest?.tag?.displayName,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item(key = "chart") {
            ChartCard(
                title = "Weight trend",
                subtitle = "Daily measurements with the smoothed trendline",
                legend = listOf(
                    LegendItem("Daily measurement", viz.deEmphasis, shape = MarkShape.Circle),
                    LegendItem("${state.settings.emaPeriodDays}-day EMA", viz.emphasis, shape = MarkShape.Line),
                ),
                tableHeaders = listOf("Date", "Measured", "Trend"),
                tableRows = state.trend.takeLast(30).reversed().map { point ->
                    ChartTableRow(
                        label = formatDate(point.date),
                        values = listOf(
                            formatWeight(point.rawKg, unit),
                            formatWeight(point.emaKg, unit),
                        ),
                    )
                },
                footnote = "A missed weigh-in leaves the trend untouched rather than decaying it.",
            ) {
                TrendLineChart(
                    points = trendPoints,
                    valueFormatter = { "%.1f".format(it) },
                )
            }
        }

        item(key = "add") {
            Button(
                onClick = {
                    editing = null
                    editorOpen = true
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Log weight")
            }
        }

        if (state.entries.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Outlined.MonitorWeight,
                    title = "No measurements yet",
                    message = "Weigh in at the same point each day — morning fasted is the most comparable.",
                )
            }
        } else {
            item(key = "entries-header") {
                SectionHeader(title = "Entries", subtitle = "${state.entries.size} logged")
            }
            items(
                items = state.entries.sortedByDescending { it.timestamp },
                key = { it.id },
            ) { entry ->
                WeightEntryRow(
                    entry = entry,
                    unit = unit,
                    onEdit = {
                        editing = entry
                        editorOpen = true
                    },
                    onDelete = { onDelete(entry.id) },
                )
            }
        }

        item(key = "footer") { Spacer(Modifier.height(16.dp)) }
    }

    if (editorOpen) {
        WeightEditorSheet(
            existing = editing,
            unit = unit,
            onDismiss = { editorOpen = false },
            onSave = { value, bodyFat, tag, timestamp, note ->
                onSave(editing?.id ?: 0L, value, unit, bodyFat, tag, timestamp, note)
                editorOpen = false
            },
        )
    }
}

@Composable
private fun WeightEntryRow(
    entry: WeightRecord,
    unit: WeightUnit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onEdit,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = formatWeight(entry.weightKg, unit),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = buildString {
                        append(formatDayTime(entry.timestamp))
                        append(" · ")
                        append(entry.tag.displayName)
                        entry.bodyFatPercent?.let {
                            append(" · ")
                            append(formatPercent(it / 100, decimals = 1))
                            append(" fat")
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (entry.note.isNotBlank()) {
                    Text(
                        text = entry.note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete entry",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeightEditorSheet(
    existing: WeightRecord?,
    unit: WeightUnit,
    onDismiss: () -> Unit,
    onSave: (value: Double, bodyFat: Double?, tag: WeightTag, timestamp: Instant, note: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var weightText by remember {
        mutableStateOf(existing?.let { "%.1f".format(unit.fromKg(it.weightKg)) } ?: "")
    }
    var bodyFatText by remember {
        mutableStateOf(existing?.bodyFatPercent?.let { "%.1f".format(it) } ?: "")
    }
    var tag by remember { mutableStateOf(existing?.tag ?: WeightTag.MORNING_FASTED) }
    var timestamp by remember { mutableStateOf(existing?.timestamp ?: Instant.now()) }
    var note by remember { mutableStateOf(existing?.note ?: "") }

    val weight = weightText.replace(',', '.').toDoubleOrNull()
    val bodyFat = bodyFatText.replace(',', '.').toDoubleOrNull()
    val valid = weight != null && weight > 0.0 &&
        (bodyFatText.isBlank() || (bodyFat != null && bodyFat in 1.0..75.0))

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = if (existing == null) "Log weight" else "Edit measurement",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            OutlinedTextField(
                value = weightText,
                onValueChange = { weightText = it },
                label = { Text("Weight (${unit.label})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = bodyFatText,
                onValueChange = { bodyFatText = it },
                label = { Text("Body fat % (optional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Context",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WeightTag.entries.forEach { option ->
                        FilterChip(
                            selected = option == tag,
                            onClick = { tag = option },
                            label = { Text(option.displayName) },
                        )
                    }
                }
            }

            dev.local.fasting.ui.components.InstantPickerRow(
                label = "Measured at",
                instant = timestamp,
                onChange = { timestamp = it },
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = {
                        onSave(
                            weight ?: 0.0,
                            bodyFatText.takeIf { it.isNotBlank() }?.let { bodyFat },
                            tag,
                            timestamp,
                            note.trim(),
                        )
                    },
                    enabled = valid,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save")
                }
            }
        }
    }
}
