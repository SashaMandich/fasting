package dev.local.fasting.widget

import android.content.Context
import android.content.Intent
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dev.local.fasting.appContainer
import dev.local.fasting.ui.MainActivity

/** Widget "Start" button — begins a fast using the configured default meal composition. */
class StartFastAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.appContainer.tracker.startFast()
    }
}

/** Widget "End" button — closes the running fast at the current time. */
class EndFastAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        context.appContainer.tracker.endFast()
    }
}

/**
 * Widget "Edit" button — opens the app on the history list, where start/end timestamps of any fast
 * (including the running one) can be adjusted retroactively.
 */
class OpenHistoryAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_EDIT_FAST)
        }
        context.startActivity(intent)
    }
}
