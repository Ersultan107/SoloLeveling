package com.sololeveling.app.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.sololeveling.app.service.AlarmReceiver
import java.util.Calendar

object AlarmScheduler {

    private const val TAG = "AlarmScheduler"

    /**
     * Schedule all daily alarms.
     * Call this on app start and after boot.
     */
    fun scheduleAll(context: Context) {
        scheduleMorningGreeting(context)
        scheduleEveningReport(context)
        scheduleSleepReminder(context)
        scheduleQuestExpiry(context)
        Log.d(TAG, "All alarms scheduled")
    }

    /**
     * Cancel all alarms (used in Safe Mode / Emergency Stop).
     */
    fun cancelAll(context: Context) {
        cancelAlarm(context, AlarmReceiver.ACTION_MORNING_ALARM, 1001)
        cancelAlarm(context, AlarmReceiver.ACTION_EVENING_ALARM, 1002)
        cancelAlarm(context, AlarmReceiver.ACTION_SLEEP_REMINDER, 1003)
        cancelAlarm(context, AlarmReceiver.ACTION_QUEST_EXPIRY, 1004)
        Log.d(TAG, "All alarms cancelled")
    }

    // ─── Individual Alarms ────────────────────────────────────────────────────

    private fun scheduleMorningGreeting(context: Context) {
        val triggerTime = getNextAlarmTime(7, 30) // 7:30 AM
        scheduleAlarm(
            context,
            AlarmReceiver.ACTION_MORNING_ALARM,
            requestCode = 1001,
            triggerAtMillis = triggerTime,
            repeatingIntervalMs = AlarmManager.INTERVAL_DAY
        )
    }

    private fun scheduleEveningReport(context: Context) {
        val triggerTime = getNextAlarmTime(20, 0) // 8:00 PM
        scheduleAlarm(
            context,
            AlarmReceiver.ACTION_EVENING_ALARM,
            requestCode = 1002,
            triggerAtMillis = triggerTime,
            repeatingIntervalMs = AlarmManager.INTERVAL_DAY
        )
    }

    private fun scheduleSleepReminder(context: Context) {
        val triggerTime = getNextAlarmTime(22, 30) // 10:30 PM
        scheduleAlarm(
            context,
            AlarmReceiver.ACTION_SLEEP_REMINDER,
            requestCode = 1003,
            triggerAtMillis = triggerTime,
            repeatingIntervalMs = AlarmManager.INTERVAL_DAY
        )
    }

    private fun scheduleQuestExpiry(context: Context) {
        val triggerTime = getNextAlarmTime(23, 55) // 11:55 PM — expire quests just before midnight
        scheduleAlarm(
            context,
            AlarmReceiver.ACTION_QUEST_EXPIRY,
            requestCode = 1004,
            triggerAtMillis = triggerTime,
            repeatingIntervalMs = AlarmManager.INTERVAL_DAY
        )
    }

    // ─── Core Scheduler ───────────────────────────────────────────────────────

    private fun scheduleAlarm(
        context: Context,
        action: String,
        requestCode: Int,
        triggerAtMillis: Long,
        repeatingIntervalMs: Long
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Use setExactAndAllowWhileIdle for battery-efficient but exact alarms
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }

            Log.d(TAG, "Alarm scheduled: $action at ${java.util.Date(triggerAtMillis)}")
        } catch (e: SecurityException) {
            // On Android 12+, exact alarms require SCHEDULE_EXACT_ALARM permission
            // Fall back to inexact alarm
            Log.w(TAG, "Exact alarm permission denied, using inexact: $action")
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                repeatingIntervalMs,
                pendingIntent
            )
        }
    }

    private fun cancelAlarm(context: Context, action: String, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun getNextAlarmTime(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        // If time has already passed today, schedule for tomorrow
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        return cal.timeInMillis
    }

    /**
     * Check if exact alarm permission is available (Android 12+).
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }
}
