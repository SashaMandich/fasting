package dev.local.fasting.ui

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import dev.local.fasting.appContainer
import dev.local.fasting.tracking.FastNotifications
import dev.local.fasting.ui.theme.FastingTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var notificationsGranted by mutableStateOf(false)
    private var startDestination by mutableStateOf(Destination.TIMER)
    private var openFeelingDialog by mutableStateOf(false)

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            notificationsGranted = granted
            // Re-derive the notification now that the answer is known.
            lifecycleScope.launch { appContainer.tracker.sync() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        FastNotifications.ensureChannel(this)
        notificationsGranted = FastNotifications.canPost(this)
        applyIntent(intent)

        // The widget and notification can change state while the app is closed; re-sync on open.
        lifecycleScope.launch { appContainer.tracker.sync() }

        if (!notificationsGranted && appContainer.settings.current().liveNotificationEnabled) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            FastingTheme {
                FastingApp(
                    startDestination = startDestination,
                    openFeelingDialog = openFeelingDialog,
                    notificationsGranted = notificationsGranted,
                    onRequestNotificationPermission = {
                        requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        notificationsGranted = FastNotifications.canPost(this)
    }

    /** Routes the widget's "Edit" button and the notification's "Log how I feel" action. */
    private fun applyIntent(intent: Intent?) {
        when (intent?.getStringExtra(EXTRA_DESTINATION)) {
            DESTINATION_EDIT_FAST -> {
                startDestination = Destination.LOG
                openFeelingDialog = false
            }
            DESTINATION_LOG_SYMPTOM -> {
                startDestination = Destination.TIMER
                openFeelingDialog = true
            }
        }
    }

    companion object {
        const val EXTRA_DESTINATION = "dev.local.fasting.extra.DESTINATION"
        const val DESTINATION_LOG_SYMPTOM = "log_symptom"
        const val DESTINATION_EDIT_FAST = "edit_fast"
    }
}
