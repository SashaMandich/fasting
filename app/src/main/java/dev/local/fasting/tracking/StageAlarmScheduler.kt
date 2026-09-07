package dev.local.fasting.tracking

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import dev.local.fasting.domain.FastRecord
import java.time.Duration
import java.time.Instant

/**
 * Wakes the app up just often enough to keep the notification text and the home-screen widget
 * honest while a fast is running.
 *
 * Deliberately uses inexact windowed alarms: they need no special permission, are batched by the
 * system, and nothing here needs second accuracy (the notification's own chronometer covers the
 * live seconds). Refreshes are scheduled for whichever comes first — the next stage boundary, the
 * moment the fasting goal is met, or the next periodic tick.
 */
object StageAlarmScheduler {

    private val REFRESH_INTERVAL: Duration = Duration.ofMinutes(10)
    private val WINDOW: Duration = Duration.ofMinutes(2)
    private const val REQUEST_CODE = 2001

    fun schedule(context: Context, fast: FastRecord, now: Instant) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        val nextBoundary = fast.progress(now).nextStage?.let { fast.stageOnset(it) }
        // The goal moment is a boundary of its own: it is when the notification and widget flip from
        // counting down to the target to running as the general timer.
        val goalMoment = fast.goalReachedAt
        val periodic = now.plus(REFRESH_INTERVAL)
        val target = listOfNotNull(nextBoundary, goalMoment, periodic)
            .filter { it.isAfter(now) }
            .minOrNull() ?: periodic

        alarmManager.setWindow(
            AlarmManager.RTC,
            target.toEpochMilli(),
            WINDOW.toMillis(),
            pendingIntent(context),
        )
    }

    fun cancel(context: Context) {
        context.getSystemService<AlarmManager>()?.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, StageAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
