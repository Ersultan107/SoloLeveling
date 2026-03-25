package com.sololeveling.app.worker

import android.content.Context
import androidx.work.*
import com.sololeveling.app.SoloLevelingApp
import com.sololeveling.app.service.AIBackgroundService
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that handles scheduled daily tasks.
 * This supplements AlarmManager for robustness across device restarts.
 */
class DailyQuestWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = context.applicationContext as SoloLevelingApp
            val repo = app.repository

            val safeMode = repo.isSafeMode().firstOrNull() ?: false
            val isPaused = repo.isPaused().firstOrNull() ?: false

            if (!safeMode && !isPaused) {
                // Ensure background service is running
                AIBackgroundService.start(context)

                // Reset daily notification count
                repo.resetNotificationCount()

                // Expire old quests
                repo.expireOldQuests()
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "daily_quest_work"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val request = PeriodicWorkRequestBuilder<DailyQuestWorker>(
                24, TimeUnit.HOURS,
                15, TimeUnit.MINUTES // flex interval
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun scheduleImmediate(context: Context) {
            val request = OneTimeWorkRequestBuilder<DailyQuestWorker>()
                .setInitialDelay(0, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}

/**
 * Worker that checks in with the user periodically (smart check-ins).
 */
class SmartCheckInWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = context.applicationContext as SoloLevelingApp
            val repo = app.repository

            val safeMode = repo.isSafeMode().firstOrNull() ?: false
            val isPaused = repo.isPaused().firstOrNull() ?: false

            if (!safeMode && !isPaused) {
                AIBackgroundService.start(context)
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "smart_check_in_work"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            // Check in every 2 hours (respects quiet hours internally)
            val request = PeriodicWorkRequestBuilder<SmartCheckInWorker>(
                2, TimeUnit.HOURS,
                30, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

/**
 * Worker that runs analysis and adapts quest difficulty overnight.
 */
class NightlyAnalysisWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val app = context.applicationContext as SoloLevelingApp
            val repo = app.repository
            val aiEngine = app.aiEngine

            val profile = repo.getUserProfileOnce() ?: return Result.success()
            val sleepRecords = repo.getRecentSleep().firstOrNull() ?: emptyList()
            val activityRecords = repo.getActivitySince(7)
            val completedCount = repo.getCompletedTodayCount()

            // Run analysis (updates difficulty for tomorrow's quests)
            aiEngine.analyzeUserState(profile, sleepRecords, activityRecords, completedCount)

            // Update streak if quests were completed today
            val todayCompleted = repo.getCompletedTodayCount()
            if (todayCompleted >= 1) {
                repo.saveUserProfile(
                    profile.copy(
                        currentStreak = profile.currentStreak + 1,
                        longestStreak = maxOf(profile.currentStreak + 1, profile.longestStreak)
                    )
                )
            } else {
                // Reset streak if no quests completed
                if (profile.currentStreak > 0) {
                    repo.saveUserProfile(profile.copy(currentStreak = 0))
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "nightly_analysis_work"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<NightlyAnalysisWorker>(
                24, TimeUnit.HOURS,
                1, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInitialDelay(calculateDelayUntilMidnight(), TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        private fun calculateDelayUntilMidnight(): Long {
            val cal = java.util.Calendar.getInstance()
            val now = cal.timeInMillis
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 5)
            cal.set(java.util.Calendar.SECOND, 0)
            return cal.timeInMillis - now
        }
    }
}
