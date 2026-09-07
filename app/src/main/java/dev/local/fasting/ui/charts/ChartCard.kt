package dev.local.fasting.ui.charts

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.TableRows
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Legend entry: a swatch carries identity, the text stays in ink tokens. */
data class LegendItem(
    val label: String,
    val color: Color,
    val valueLabel: String? = null,
    val shape: MarkShape = MarkShape.Square,
)

enum class MarkShape { Square, Circle, Line, Triangle }

/** One row of the table-view twin every chart carries. */
data class ChartTableRow(val label: String, val values: List<String>)

/**
 * Shell for every chart on the Insights screen.
 *
 * Each chart ships with a table view reachable from the header, so no value is ever gated behind a
 * tap-only readout — the requirement that a colour-encoded chart always has a WCAG-clean twin.
 */
@Composable
fun ChartCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    legend: List<LegendItem> = emptyList(),
    tableHeaders: List<String> = emptyList(),
    tableRows: List<ChartTableRow> = emptyList(),
    footnote: String? = null,
    readout: String? = null,
    content: @Composable () -> Unit,
) {
    var showTable by remember { mutableStateOf(false) }
    val hasTable = tableRows.isNotEmpty()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (hasTable) {
                    IconButton(onClick = { showTable = !showTable }) {
                        Icon(
                            imageVector = if (showTable) Icons.Outlined.BarChart else Icons.Outlined.TableRows,
                            contentDescription = if (showTable) "Show chart" else "Show values as a table",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            AnimatedContent(targetState = showTable, label = "chart-table") { table ->
                if (table) {
                    ChartTable(tableHeaders, tableRows)
                } else {
                    Column {
                        content()
                        if (readout != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = readout,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (legend.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            ChartLegend(legend)
                        }
                    }
                }
            }

            if (footnote != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = footnote,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ChartLegend(items: List<LegendItem>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                LegendSwatch(item)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.valueLabel != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = item.valueLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendSwatch(item: LegendItem) {
    when (item.shape) {
        MarkShape.Circle, MarkShape.Triangle ->
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(item.color),
            )
        MarkShape.Line ->
            Box(
                Modifier
                    .width(14.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(item.color),
            )
        MarkShape.Square ->
            Box(
                Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(item.color),
            )
    }
}

@Composable
private fun ChartTable(headers: List<String>, rows: List<ChartTableRow>) {
    Column(Modifier.fillMaxWidth()) {
        if (headers.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Text(
                    text = headers.first(),
                    modifier = Modifier.weight(1.4f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                headers.drop(1).forEach { header ->
                    Text(
                        text = header,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.label,
                    modifier = Modifier.weight(1.4f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                row.values.forEach { value ->
                    Text(
                        text = value,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFeatureSettings = "tnum",
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}
