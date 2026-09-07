package dev.local.fasting.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.local.fasting.domain.Symptom
import dev.local.fasting.domain.SymptomLog

/**
 * Captures the subjective side of a fast: energy, clarity, hunger and any symptoms. These are the
 * y-values of the correlation charts, so the scales are fixed 1–5 rather than free text.
 */
@Composable
fun LogFeelingDialog(
    onDismiss: () -> Unit,
    onSave: (energy: Int, clarity: Int, hunger: Int, symptoms: Set<Symptom>, note: String) -> Unit,
) {
    var energy by remember { mutableStateOf(3) }
    var clarity by remember { mutableStateOf(3) }
    var hunger by remember { mutableStateOf(3) }
    var symptoms by remember { mutableStateOf(emptySet<Symptom>()) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How does this fast feel?") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                LevelPicker("Energy", energy, "Drained", "Excellent") { energy = it }
                LevelPicker("Mental clarity", clarity, "Foggy", "Sharp") { clarity = it }
                LevelPicker("Hunger", hunger, "None", "Ravenous") { hunger = it }

                Column {
                    Text(
                        text = "Symptoms",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Symptom.entries.forEach { symptom ->
                            FilterChip(
                                selected = symptom in symptoms,
                                onClick = {
                                    symptoms = if (symptom in symptoms) {
                                        symptoms - symptom
                                    } else {
                                        symptoms + symptom
                                    }
                                },
                                label = { Text(symptom.displayName) },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(energy, clarity, hunger, symptoms, note.trim()) }) {
                Text("Save log")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Fixed 1–5 scale, labelled at both ends so the numbers keep their meaning between sessions. */
@Composable
fun LevelPicker(
    label: String,
    value: Int,
    lowLabel: String,
    highLabel: String,
    onChange: (Int) -> Unit,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SymptomLog.LEVEL_RANGE.forEachIndexed { index, level ->
                SegmentedButton(
                    selected = value == level,
                    onClick = { onChange(level) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = SymptomLog.LEVEL_RANGE.count(),
                    ),
                    label = { Text("$level") },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = lowLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = highLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
