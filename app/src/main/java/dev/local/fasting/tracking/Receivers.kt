package dev.local.fasting.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.local.fasting.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Runs [block] on a background scope while holding the broadcast alive, then releases it.
 * Receivers here only touch Room, the notification manager and the widget, all of which are quick.
 */
private fun BroadcastReceiver.goAsyncWith(block: suspend () -> Unit) {
    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try {
            block()
        } finally {
            pendingResult.finish()
        }
    }
}

/** Periodic/stage-boundary refresh of the notification text and widget. */
class StageAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val container = context.appContainer
        goAsyncWith { container.tracker.sync() }
    }
}

/** Restores the live notification and refresh alarm after a reboot or app update. */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val container = context.appContainer
        goAsyncWith { container.tracker.sync() }
    }
}

/** Handles the notification's action buttons. */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val container = context.appContainer
        when (intent?.action) {
            ACTION_END_FAST -> goAsyncWith { container.tracker.endFast() }
            ACTION_START_FAST -> goAsyncWith { container.tracker.startFast() }
        }
    }

    companion object {
        const val ACTION_END_FAST = "dev.local.fasting.action.END_FAST"
        const val ACTION_START_FAST = "dev.local.fasting.action.START_FAST"
    }
}
