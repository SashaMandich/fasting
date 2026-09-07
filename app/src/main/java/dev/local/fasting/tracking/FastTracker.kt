package dev.local.fasting.tracking

import android.content.Context
import dev.local.fasting.data.FastingRepository
import dev.local.fasting.data.SettingsStore
import dev.local.fasting.domain.FastRecord
import dev.local.fasting.domain.FastingGoal
import dev.local.fasting.domain.MealComposition
import dev.local.fasting.widget.FastingWidget
import java.time.Instant

/**
 * Owns the side effects of a fast changing state: the sticky notification, the refresh alarm and the
 * home-screen widget. Every entry point (UI, widget button, notification action, boot) funnels
 * through here so the three surfaces can never disagree.
 */
class FastTracker(
    private val context: Context,
    private val repository: FastingRepository,
    private val settings: SettingsStore,
) {

    suspend fun startFast(
        meal: MealComposition = settings.current().defaultMeal,
        goal: FastingGoal = settings.current().defaultGoal,
        start: Instant = Instant.now(),
    ): Long {
        val id = repository.startFast(meal, goal, start)
        sync()
        return id
    }

    suspend fun endFast(end: Instant = Instant.now()): FastRecord? {
        val closed = repository.endActiveFast(end)
        sync()
        return closed
    }

    /** Widget/notification convenience: start if idle, end if running. */
    suspend fun toggle(): Boolean {
        val active = repository.activeFastNow()
        return if (active == null) {
            startFast()
            true
        } else {
            endFast()
            false
        }
    }

    /** Re-derives notification, alarm and widget state from the database. Safe to call any time. */
    suspend fun sync(now: Instant = Instant.now()) {
        val active = repository.activeFastNow()
        if (active != null && settings.current().liveNotificationEnabled) {
            FastNotifications.show(context, active, now)
        } else {
            FastNotifications.cancel(context)
        }
        if (active != null) {
            StageAlarmScheduler.schedule(context, active, now)
        } else {
            StageAlarmScheduler.cancel(context)
        }
        FastingWidget.refresh(context)
    }
}
