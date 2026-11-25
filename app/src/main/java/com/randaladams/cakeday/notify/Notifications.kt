package com.randaladams.cakeday.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.randaladams.cakeday.MainActivity
import com.randaladams.cakeday.R
import com.randaladams.cakeday.data.Repository
import com.randaladams.cakeday.logic.NotificationFilter
import com.randaladams.cakeday.logic.Occurrence
import com.randaladams.cakeday.model.AppSettings
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object NotificationScheduler {
    private const val WORK_NAME = "cakeday_daily"

    /** Schedules (or cancels) the once-a-day summary at the time chosen in Settings. */
    fun schedule(context: Context, s: AppSettings) {
        val wm = WorkManager.getInstance(context)
        if (!s.notificationsEnabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(s.notifyHour, s.notifyMinute)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delay = Duration.between(now, next).toMillis()
        val req = OneTimeWorkRequestBuilder<DailyWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, req)
    }
}

class DailyWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repo = Repository.get(applicationContext)
        val state = repo.ensureLoaded()
        val s = state.settings
        if (s.notificationsEnabled && state.hasTree) {
            val today = LocalDate.now()
            val todays = NotificationFilter.filter(state.engine.on(today), s, state.relationships)
            val ahead = if (s.notifyDaysAhead > 0) {
                NotificationFilter.filter(state.engine.on(today.plusDays(s.notifyDaysAhead.toLong())), s, state.relationships)
            } else emptyList()
            Notifier.show(applicationContext, todays, ahead, s.notifyDaysAhead, s.notifyWhenEmpty)
        }
        NotificationScheduler.schedule(applicationContext, s) // queue tomorrow's run
        return Result.success()
    }
}

object Notifier {
    const val CHANNEL_ID = "daily_events"
    private const val NOTIFICATION_ID = 1001

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Daily family events", NotificationManager.IMPORTANCE_DEFAULT)
            ch.description = "Birthdays, anniversaries and remembrances from your family tree"
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    fun show(context: Context, today: List<Occurrence>, ahead: List<Occurrence>, daysAhead: Int, whenEmpty: Boolean) {
        if (today.isEmpty() && ahead.isEmpty() && !whenEmpty) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        createChannel(context)

        val lines = today.map { "${it.event.kind.emoji} ${it.names} — ${it.headline}" }
        val aheadDate = LocalDate.now().plusDays(daysAhead.toLong()).format(DateTimeFormatter.ofPattern("EEE MMM d"))
        val aheadLines = ahead.map { "In $daysAhead ${if (daysAhead == 1) "day" else "days"} ($aheadDate): ${it.names} — ${it.headline}" }
        val all = lines + aheadLines

        val title = when {
            today.isNotEmpty() -> "${today.size} family ${if (today.size == 1) "event" else "events"} today"
            ahead.isNotEmpty() -> "Coming up on $aheadDate"
            else -> "No family events today"
        }
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val style = NotificationCompat.InboxStyle().also { st -> all.take(7).forEach { st.addLine(it) } }
        if (all.size > 7) style.setSummaryText("+${all.size - 7} more")

        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(all.firstOrNull() ?: "Tap to open Cakeday")
            .setStyle(style)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
        } catch (_: SecurityException) { }
    }
}
