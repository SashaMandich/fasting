package dev.local.fasting.tracking

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.local.fasting.R
import dev.local.fasting.domain.FastRecord
import dev.local.fasting.ui.MainActivity
import dev.local.fasting.ui.format.formatHoursMinutes

/**
 * The sticky "fast in progress" notification.
 *
 * The live figure is rendered by the platform chronometer — seeded with the goal instant while a
 * goal is pending, and with the fast's start once it is met — so it ticks every second with no
 * service, alarm or wake-up of our own. We only rewrite the notification when the *text* changes,
 * i.e. on a stage transition, the goal being reached, or a periodic refresh.
 */
object FastNotifications {

    private const val CHANNEL_ID = "active_fast"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setShowBadge(false)
            enableVibration(false)
        }
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun show(context: Context, fast: FastRecord, now: java.time.Instant) {
        if (!canPost(context)) return
        ensureChannel(context)

        val progress = fast.progress(now)
        val goal = fast.goalProgress(now)
        val nextLine = progress.nextStage?.let { next ->
            val remaining = progress.hoursToNextStage ?: 0.0
            "${next.displayName} in ${formatHoursMinutes(remaining)}"
        } ?: "Peak autophagy — extended repair"

        // While a goal is pending the title carries it and the stage moves into the body; once the
        // goal is met (or there never was one) the stage is the headline again.
        val title = when {
            goal.isPending -> "${goal.goal.label} · ${formatHoursMinutes(goal.hoursRemaining!!)} left"
            goal.isReached -> "${goal.goal.label} reached · +${formatHoursMinutes(goal.overtimeHours!!)}"
            else -> progress.stage.displayName
        }
        val body = if (goal.isOpen) nextLine else "${progress.stage.displayName} · $nextLine"

        // Pending goal: count *down* to the target. Reached or open: count total elapsed time up,
        // which is the general timer. Either way the platform ticks it with no work of ours.
        val countDownTo = fast.goalReachedAt?.takeIf { goal.isPending }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$body\n${progress.stage.summary}")
            )
            .setWhen((countDownTo ?: fast.start).toEpochMilli())
            .setUsesChronometer(true)
            .setChronometerCountDown(countDownTo != null)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent(context))
            .addAction(
                R.drawable.ic_notification,
                "End fast",
                broadcast(context, NotificationActionReceiver.ACTION_END_FAST, requestCode = 1),
            )
            .addAction(
                R.drawable.ic_notification,
                "Log how I feel",
                openAppIntent(context, MainActivity.DESTINATION_LOG_SYMPTOM, requestCode = 2),
            )
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun openAppIntent(
        context: Context,
        destination: String? = null,
        requestCode: Int = 0,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            destination?.let { putExtra(MainActivity.EXTRA_DESTINATION, it) }
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun broadcast(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
