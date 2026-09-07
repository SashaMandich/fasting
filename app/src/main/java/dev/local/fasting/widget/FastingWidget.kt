package dev.local.fasting.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.color.ColorProvider
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.local.fasting.appContainer
import dev.local.fasting.domain.FastRecord
import dev.local.fasting.ui.format.formatHoursMinutes
import dev.local.fasting.ui.format.formatTime
import java.time.Instant

/**
 * Home-screen widget: elapsed fast, goal countdown, active biological stage, and Start / End / Edit
 * actions.
 *
 * Glance widgets are pushed, not polled — the displayed elapsed time is computed when the widget is
 * composed, and [FastTracker][dev.local.fasting.tracking.FastTracker] pushes a refresh on every
 * state change plus on the periodic alarm. Second-accurate ticking is the live notification's job.
 */
class FastingWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = context.appContainer.repository
        val initial = repository.activeFastNow()

        provideContent {
            val active by repository.activeFast.collectAsState(initial)
            WidgetContent(active, Instant.now())
        }
    }

    companion object {
        /** Redraws every placed instance of the widget. */
        suspend fun refresh(context: Context) {
            FastingWidget().updateAll(context)
        }
    }
}

class FastingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FastingWidget()
}

private val Surface = ColorProvider(day = Color(0xFFF3F6F6), night = Color(0xFF12181B))
private val OnSurface = ColorProvider(day = Color(0xFF101416), night = Color(0xFFECF3F3))
private val Muted = ColorProvider(day = Color(0xFF5A6B70), night = Color(0xFF9FB2B7))
private val Accent = ColorProvider(day = Color(0xFF0F6F5C), night = Color(0xFF7BE3C3))
private val AccentContainer = ColorProvider(day = Color(0xFFD3EEE6), night = Color(0xFF17332C))
private val SubtleContainer = ColorProvider(day = Color(0xFFE3E9E9), night = Color(0xFF1E2629))

/**
 * Same green → red stage scale the in-app charts use, mirrored here because Glance composes outside
 * the app's theme. It is carried by a swatch rather than by colouring the label: amber-on-white text
 * would not clear contrast.
 */
private val StageSwatches = listOf(
    ColorProvider(day = Color(0xFF1CA862), night = Color(0xFF1DD980)),
    ColorProvider(day = Color(0xFF5F8A24), night = Color(0xFF93B414)),
    ColorProvider(day = Color(0xFFEFB63C), night = Color(0xFFFEC930)),
    ColorProvider(day = Color(0xFFCF6B22), night = Color(0xFFF97D14)),
    ColorProvider(day = Color(0xFF9E2130), night = Color(0xFFD02B31)),
)

@Composable
private fun WidgetContent(active: FastRecord?, now: Instant) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Surface)
            .cornerRadius(24.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Vertical.Top,
    ) {
        if (active == null) {
            IdleContent()
        } else {
            ActiveContent(active, now)
        }
    }
}

@Composable
private fun IdleContent() {
    Text(
        text = "Not fasting",
        style = TextStyle(color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium),
    )
    Spacer(GlanceModifier.height(4.dp))
    Text(
        text = "Ready",
        style = TextStyle(color = OnSurface, fontSize = 26.sp, fontWeight = FontWeight.Bold),
    )
    Spacer(GlanceModifier.height(10.dp))
    Row {
        WidgetPill(label = "Start", primary = true, onClickAction = actionRunCallback<StartFastAction>())
        Spacer(GlanceModifier.width(8.dp))
        WidgetPill(label = "Edit", primary = false, onClickAction = actionRunCallback<OpenHistoryAction>())
    }
}

@Composable
private fun ActiveContent(fast: FastRecord, now: Instant) {
    val progress = fast.progress(now)
    val goal = fast.goalProgress(now)
    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
        Box(
            modifier = GlanceModifier
                .size(8.dp)
                .cornerRadius(4.dp)
                .background(StageSwatches[progress.stage.ordinal.coerceIn(StageSwatches.indices)]),
            content = {},
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = if (goal.isOpen) {
                progress.stage.shortName.uppercase()
            } else {
                "${goal.goal.label} · ${progress.stage.shortName.uppercase()}"
            },
            style = TextStyle(color = OnSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold),
        )
    }
    Spacer(GlanceModifier.height(2.dp))
    Text(
        text = formatHoursMinutes(progress.elapsedHours),
        style = TextStyle(color = OnSurface, fontSize = 30.sp, fontWeight = FontWeight.Bold),
    )
    // The goal is what the user is actually waiting for, so it takes the line under the figure until
    // it is met; after that the widget is back to reporting stages.
    Text(
        text = when {
            goal.isPending -> "${formatHoursMinutes(goal.hoursRemaining!!)} to ${goal.goal.label}"
            goal.isReached -> "${goal.goal.label} met · +${formatHoursMinutes(goal.overtimeHours!!)}"
            else -> progress.nextStage?.let { next ->
                "${next.shortName} in ${formatHoursMinutes(progress.hoursToNextStage ?: 0.0)}"
            } ?: "Peak autophagy"
        },
        style = TextStyle(color = if (goal.isReached) Accent else Muted, fontSize = 11.sp),
    )
    Spacer(GlanceModifier.height(2.dp))
    Text(
        text = "since ${formatTime(fast.start)} · ${fast.meal.displayName}",
        style = TextStyle(color = Muted, fontSize = 10.sp),
    )
    Spacer(GlanceModifier.height(10.dp))
    Row {
        WidgetPill(label = "End", primary = true, onClickAction = actionRunCallback<EndFastAction>())
        Spacer(GlanceModifier.width(8.dp))
        WidgetPill(label = "Edit", primary = false, onClickAction = actionRunCallback<OpenHistoryAction>())
    }
}

@Composable
private fun WidgetPill(
    label: String,
    primary: Boolean,
    onClickAction: androidx.glance.action.Action,
) {
    Text(
        text = label,
        style = TextStyle(
            color = if (primary) Accent else Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        ),
        modifier = GlanceModifier
            .background(if (primary) AccentContainer else SubtleContainer)
            .cornerRadius(18.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClickAction),
    )
}
