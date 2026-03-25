package com.sololeveling.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.sololeveling.app.ai.AIEngine
import com.sololeveling.app.data.local.SoloLevelingDatabase
import com.sololeveling.app.data.repository.UserRepository

class SoloLevelingApp : Application() {

    val database by lazy { SoloLevelingDatabase.getDatabase(this) }
    val repository by lazy { UserRepository(this) }
    val aiEngine by lazy { AIEngine(this, repository) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
        com.sololeveling.app.util.AlarmScheduler.scheduleAll(this)
        com.sololeveling.app.worker.DailyQuestWorker.schedule(this)
        com.sololeveling.app.worker.SmartCheckInWorker.schedule(this)
        com.sololeveling.app.worker.NightlyAnalysisWorker.schedule(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val channels = listOf(
                NotificationChannel(
                    CHANNEL_AI_MESSAGES,
                    "Сообщения ИИ",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Сообщения от вашего ИИ-ассистента"
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_QUESTS,
                    "Квесты",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Новые квесты и напоминания"
                    enableVibration(true)
                    enableLights(true)
                },
                NotificationChannel(
                    CHANNEL_ACHIEVEMENTS,
                    "Достижения",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Уровни и достижения"
                    enableVibration(true)
                    enableLights(true)
                },
                NotificationChannel(
                    CHANNEL_SYSTEM,
                    "Системные",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Системные уведомления"
                },
                NotificationChannel(
                    CHANNEL_FOREGROUND,
                    "Фоновая служба",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = "ИИ-ассистент работает в фоне"
                    setShowBadge(false)
                }
            )

            channels.forEach { nm.createNotificationChannel(it) }
        }
    }

    companion object {
        lateinit var instance: SoloLevelingApp
            private set

        const val CHANNEL_AI_MESSAGES = "ai_messages"
        const val CHANNEL_QUESTS = "quests"
        const val CHANNEL_ACHIEVEMENTS = "achievements"
        const val CHANNEL_SYSTEM = "system"
        const val CHANNEL_FOREGROUND = "foreground_service"

        const val NOTIF_ID_FOREGROUND = 1001
        const val NOTIF_ID_AI_MESSAGE = 2001
        const val NOTIF_ID_QUEST = 3001
        const val NOTIF_ID_ACHIEVEMENT = 4001
    }
}
