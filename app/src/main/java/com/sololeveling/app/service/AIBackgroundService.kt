package com.sololeveling.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sololeveling.app.R
import com.sololeveling.app.SoloLevelingApp
import com.sololeveling.app.ai.AIEngine
import com.sololeveling.app.ai.TimeOfDay
import com.sololeveling.app.data.model.*
import com.sololeveling.app.data.repository.UserRepository
import com.sololeveling.app.ui.main.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import java.util.*

class AIBackgroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: UserRepository
    private lateinit var aiEngine: AIEngine

    private var batteryLevel = 100
    private var isCharging = false
    private var lastNotificationTime = 0L
    private val MIN_NOTIFICATION_INTERVAL_MS = 2 * 60 * 60 * 1000L // 2 hours

    companion object {
        private const val TAG = "AIBackgroundService"

        fun start(context: Context) {
            val intent = Intent(context, AIBackgroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AIBackgroundService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        val app = application as SoloLevelingApp
        repository = app.repository
        aiEngine = app.aiEngine

        registerBatteryReceiver()
        startForeground(SoloLevelingApp.NOTIF_ID_FOREGROUND, buildForegroundNotification())

        // Start periodic tasks
        startPeriodicTasks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        unregisterReceiver(batteryReceiver)
    }

    // ─── Foreground Notification ──────────────────────────────────────────────

    private fun buildForegroundNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, SoloLevelingApp.CHANNEL_FOREGROUND)
            .setContentTitle("Solo Leveling")
            .setContentText("ИИ-ассистент активен")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    // ─── Periodic Tasks ───────────────────────────────────────────────────────

    private fun startPeriodicTasks() {
        // Check system state every 15 minutes
        serviceScope.launch {
            while (isActive) {
                try {
                    checkAndPerformPeriodicActions()
                } catch (e: Exception) {
                    Log.e(TAG, "Periodic task error", e)
                }
                delay(15 * 60 * 1000L) // 15 minutes
            }
        }

        // Daily quest generation at midnight
        serviceScope.launch {
            while (isActive) {
                try {
                    checkAndGenerateDailyQuests()
                } catch (e: Exception) {
                    Log.e(TAG, "Quest generation error", e)
                }
                delay(60 * 60 * 1000L) // Check every hour
            }
        }

        // Morning greeting check
        serviceScope.launch {
            while (isActive) {
                try {
                    checkMorningGreeting()
                } catch (e: Exception) {
                    Log.e(TAG, "Morning greeting error", e)
                }
                delay(30 * 60 * 1000L) // Check every 30 minutes
            }
        }
    }

    private suspend fun checkAndPerformPeriodicActions() {
        val safeMode = repository.isSafeMode().firstOrNull() ?: false
        val isPaused = repository.isPaused().firstOrNull() ?: false

        if (safeMode || isPaused) return
        if (batteryLevel < 15 && !isCharging) {
            Log.d(TAG, "Battery low ($batteryLevel%), entering silent mode")
            return
        }

        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val quietHours = repository.getQuietHours().firstOrNull() ?: Pair(22, 8)

        if (isInQuietHours(currentHour, quietHours.first, quietHours.second)) {
            Log.d(TAG, "In quiet hours, skipping notifications")
            return
        }

        // Check if we should send an AI check-in
        val timeSinceLastNotification = System.currentTimeMillis() - lastNotificationTime
        if (timeSinceLastNotification >= MIN_NOTIFICATION_INTERVAL_MS) {
            sendSmartCheckIn()
        }
    }

    private suspend fun checkAndGenerateDailyQuests() {
        val lastQuestDate = repository.getLastQuestDate()
        val today = getDateString(System.currentTimeMillis())
        val lastDate = getDateString(lastQuestDate)

        if (today != lastDate) {
            generateDailyQuestsForUser()
            repository.setLastQuestDate(System.currentTimeMillis())
            repository.expireOldQuests()
        }
    }

    private suspend fun checkMorningGreeting() {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (currentHour !in 7..9) return

        val lastGreeting = repository.getLastGreetingDate()
        val today = getDateString(System.currentTimeMillis())
        val lastGreetingDay = getDateString(lastGreeting)

        if (today != lastGreetingDay) {
            sendMorningGreeting()
            repository.setLastGreetingDate(System.currentTimeMillis())
        }
    }

    // ─── AI Check-In ──────────────────────────────────────────────────────────

    private suspend fun sendSmartCheckIn() {
        val profile = repository.getUserProfileOnce() ?: return
        val notifCount = repository.getNotificationCountToday()

        if (notifCount >= 5) return // Max 5 per day

        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val timeOfDay = getTimeOfDay(currentHour)

        val checkInMessages = when (profile.aiPersonality) {
            AIPersonality.FRIENDLY -> listOf(
                "Как дела? 😊 Расскажи, всё ли хорошо?",
                "Привет! Как самочувствие сегодня?",
                "Не забывай делать перерывы! Как ты?"
            )
            AIPersonality.STRICT -> listOf(
                "Отчёт! Как продвигаются задания?",
                "Статус дня? Квесты выполняются?",
                "Проверка состояния. Как работается?"
            )
            AIPersonality.BALANCED -> listOf(
                "Как дела? Есть прогресс по заданиям?",
                "Как ты сегодня? Всё идёт по плану?",
                "Привет! Как самочувствие и продуктивность?"
            )
        }

        val message = checkInMessages.random()

        sendNotification(
            channelId = SoloLevelingApp.CHANNEL_AI_MESSAGES,
            notifId = SoloLevelingApp.NOTIF_ID_AI_MESSAGE,
            title = "${profile.aiName} спрашивает:",
            text = message,
            openChat = true
        )

        repository.incrementNotificationCount()
        lastNotificationTime = System.currentTimeMillis()
    }

    private suspend fun sendMorningGreeting() {
        val profile = repository.getUserProfileOnce() ?: return
        val sleepRecords = repository.getRecentSleep().firstOrNull() ?: emptyList()
        val activityRecords = repository.getActivitySince(7)
        val completedCount = repository.getCompletedTodayCount()

        val analysis = aiEngine.analyzeUserState(profile, sleepRecords, activityRecords, completedCount)

        val greetings = when (profile.aiPersonality) {
            AIPersonality.FRIENDLY -> listOf(
                "Доброе утро, ${profile.name}! ☀️ Новый день — новые возможности! Сегодняшние квесты уже ждут тебя!",
                "Привет, ${profile.name}! 🌟 Как спалось? Готов покорять новые высоты?",
                "Доброе утро! 😊 ${profile.name}, сегодня отличный день для прогресса!"
            )
            AIPersonality.STRICT -> listOf(
                "Подъём, ${profile.name}! Квесты сами себя не выполнят!",
                "Доброе утро, ${profile.name}. Сегодня у нас работа. Готов?",
                "${profile.name}! Новый день — новый шанс стать лучше. Не упусти его!"
            )
            AIPersonality.BALANCED -> listOf(
                "Доброе утро, ${profile.name}! ⚡ Новые квесты доступны. Давай сегодня будет продуктивным!",
                "Привет! ${profile.name}, новый день начинается. Как себя чувствуешь?",
                "Утро, ${profile.name}! Вчера был хороший день. Сегодня сделаем ещё лучше!"
            )
        }

        sendNotification(
            channelId = SoloLevelingApp.CHANNEL_AI_MESSAGES,
            notifId = SoloLevelingApp.NOTIF_ID_AI_MESSAGE + 1,
            title = "Утро с ${profile.aiName}",
            text = greetings.random(),
            openChat = true
        )

        repository.incrementNotificationCount()
        lastNotificationTime = System.currentTimeMillis()
    }

    // ─── Quest Generation ─────────────────────────────────────────────────────

    private suspend fun generateDailyQuestsForUser() {
        val profile = repository.getUserProfileOnce() ?: return
        val sleepRecords = repository.getRecentSleep().firstOrNull() ?: emptyList()
        val activityRecords = repository.getActivitySince(7)
        val completedCount = repository.getCompletedTodayCount()

        val analysis = aiEngine.analyzeUserState(profile, sleepRecords, activityRecords, completedCount)
        val dailyQuests = aiEngine.generateDailyQuests(profile, analysis)

        repository.insertQuests(dailyQuests)

        // Check if we should generate a boss quest (every 7 days)
        if (profile.totalQuestsCompleted > 0 && profile.totalQuestsCompleted % 7 == 0) {
            val existingBoss = repository.getActiveBossQuest()
            if (existingBoss == null) {
                val bossQuest = aiEngine.generateBossQuest(profile)
                repository.insertQuest(bossQuest)
            }
        }

        sendNotification(
            channelId = SoloLevelingApp.CHANNEL_QUESTS,
            notifId = SoloLevelingApp.NOTIF_ID_QUEST,
            title = "⚔️ Новые квесты доступны!",
            text = "Сегодня ${dailyQuests.size} новых задания ждут тебя. Удачи, ${profile.name}!",
            openChat = false
        )
    }

    // ─── Notification Helper ──────────────────────────────────────────────────

    private fun sendNotification(
        channelId: String,
        notifId: Int,
        title: String,
        text: String,
        openChat: Boolean
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (openChat) putExtra("open_chat", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId, notification)
    }

    // ─── Battery Receiver ─────────────────────────────────────────────────────

    private val batteryReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    batteryLevel = if (scale > 0) (level * 100 / scale) else 100

                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL

                    Log.d(TAG, "Battery: $batteryLevel%, charging: $isCharging")
                }
            }
        }
    }

    private fun registerBatteryReceiver() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
    }

    // ─── Helper Functions ─────────────────────────────────────────────────────

    private fun isInQuietHours(currentHour: Int, start: Int, end: Int): Boolean {
        return if (start > end) {
            currentHour >= start || currentHour < end
        } else {
            currentHour in start until end
        }
    }

    private fun getTimeOfDay(hour: Int): TimeOfDay = when (hour) {
        in 6..11 -> TimeOfDay.MORNING
        in 12..17 -> TimeOfDay.AFTERNOON
        in 18..21 -> TimeOfDay.EVENING
        else -> TimeOfDay.NIGHT
    }

    private fun getDateString(timestamp: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}-${cal.get(Calendar.DAY_OF_MONTH)}"
    }
}
