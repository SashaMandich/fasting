package dev.local.fasting.ui.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.local.fasting.data.AppSettings
import dev.local.fasting.domain.FastingGoal
import dev.local.fasting.domain.MealComposition
import dev.local.fasting.domain.WeightUnit
import dev.local.fasting.ui.components.SectionHeader
import dev.local.fasting.ui.components.SegmentedFilterRow
import dev.local.fasting.widget.FastingWidgetReceiver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: AppSettings,
    notificationsGranted: Boolean,
    onDismiss: () -> Unit,
    onSetUnit: (WeightUnit) -> Unit,
    onSetDefaultMeal: (MealComposition) -> Unit,
    onSetDefaultGoal: (FastingGoal) -> Unit,
    onSetLiveNotification: (Boolean) -> Unit,
    onSetEmaPeriod: (Int) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onClearHistoryRequest: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(title = "Weight unit", subtitle = "Stored in kilograms either way")
                SegmentedFilterRow(
                    options = WeightUnit.entries.toList(),
                    selected = settings.weightUnit,
                    labelOf = { it.label },
                    onSelect = onSetUnit,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(
                    title = "Default fasting goal",
                    subtitle = "Pre-selected on the timer, and used when a fast is started from the " +
                        "widget or notification",
                )
                SegmentedFilterRow(
                    options = FastingGoal.entries.toList(),
                    selected = settings.defaultGoal,
                    labelOf = { it.label },
                    onSelect = onSetDefaultGoal,
                )
                Text(
                    text = settings.defaultGoal.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(
                    title = "Default pre-fast meal",
                    subtitle = "Used when a fast is started from the widget or notification",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MealComposition.entries.forEach { meal ->
                        FilterChip(
                            selected = meal == settings.defaultMeal,
                            onClick = { onSetDefaultMeal(meal) },
                            label = { Text(meal.displayName) },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(
                    title = "Trend smoothing",
                    subtitle = "Window for the weight EMA",
                )
                SegmentedFilterRow(
                    options = listOf(5, 7, 10, 14),
                    selected = settings.emaPeriodDays,
                    labelOf = { "${it}d" },
                    onSelect = onSetEmaPeriod,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Live status notification",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Ongoing notification with a ticking count-up and the active phase.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.liveNotificationEnabled && notificationsGranted,
                    onCheckedChange = { enabled ->
                        if (enabled && !notificationsGranted) {
                            onRequestNotificationPermission()
                        }
                        onSetLiveNotification(enabled)
                    },
                )
            }
            if (settings.liveNotificationEnabled && !notificationsGranted) {
                Text(
                    text = "Notifications are blocked for this app in system settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            val context = LocalContext.current
            val widgetManager = remember(context) { AppWidgetManager.getInstance(context) }
            if (widgetManager.isRequestPinAppWidgetSupported) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionHeader(
                        title = "Home screen widget",
                        subtitle = "Elapsed fast, active phase and start/end controls",
                    )
                    OutlinedButton(
                        onClick = {
                            widgetManager.requestPinAppWidget(
                                ComponentName(context, FastingWidgetReceiver::class.java),
                                null,
                                null,
                            )
                        },
                    ) {
                        Text("Add widget to home screen")
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionHeader(
                    title = "Danger zone",
                    subtitle = "Deletes every fast, weight entry and symptom log on this device",
                )
                OutlinedButton(
                    // The confirmation lives in the caller: a dialog opened from inside a
                    // ModalBottomSheet takes focus, which fires the sheet's onDismissRequest and
                    // unmounts the dialog with it.
                    onClick = onClearHistoryRequest,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Outlined.DeleteForever, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Clear all history")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Text(
                text = "This app runs entirely on this device. There is no account, no server and no " +
                    "network permission — your log lives in a local database and never leaves the phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Step 1 states what is lost; step 2 makes the user commit. Two dialogs rather than one because
 * there is no undo and no copy of the data anywhere else. Hosted by the caller, not the sheet.
 */
@Composable
fun ClearHistoryConfirmation(
    step: Int,
    onStepChange: (Int) -> Unit,
    onConfirmed: () -> Unit,
) {
    if (step == 1) {
        AlertDialog(
            onDismissRequest = { onStepChange(0) },
            title = { Text("Clear all history?") },
            text = {
                Text(
                    "Every fast, weight entry and symptom log on this device will be deleted. " +
                        "Your settings are kept.",
                )
            },
            confirmButton = {
                TextButton(onClick = { onStepChange(2) }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { onStepChange(0) }) { Text("Cancel") }
            },
        )
    }

    if (step == 2) {
        AlertDialog(
            onDismissRequest = { onStepChange(0) },
            title = { Text("This cannot be undone") },
            text = { Text("There is no backup and no server copy. Deleting is permanent.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onStepChange(0)
                        onConfirmed()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Delete everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { onStepChange(0) }) { Text("Keep my data") }
            },
        )
    }
}
