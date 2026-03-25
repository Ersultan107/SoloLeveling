package com.sololeveling.app.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sololeveling.app.R
import com.sololeveling.app.SoloLevelingApp
import com.sololeveling.app.data.model.AIPersonality
import com.sololeveling.app.data.repository.UserRepository
import com.sololeveling.app.ui.main.MainActivity
import kotlinx.coroutines.flow.firstOrNull
import java.util.Calendar

object NotificationHelper {

    private const val TAG = "NotificationHelper"
    private const val MAX_NOTIFICATIONS_PER_DAY = 5
    private const val MIN_INTERVAL_MS = 2 * 60 * 60 * 1000L // 2 hours

    private var lastNotificationTime = 0L

    /**
     * Send a smart notification — checks all conditions before firing.
     * Returns true if notification was sent.
     */
    suspend fun sendSmartNotification(
        context: Context,
        repository: UserRepository,
        title: String,
        message: String,
        channelId: String = SoloLevelingApp.CHANNEL_AI_MESSAGES,
        notifId: Int = SoloLevelingApp.NOTIF_ID_AI_MESSAGE,
        openChat: Boolean = true,
        forceShow: Boolean = false
    ): Boolean {

        if (!forceShow) {
            // Check safe mode
            if (repository.isSafeMode().firstOrNull() == true) {
                Log.d(TAG, "Safe mode active — skipping notification")
                return false
            }

            // Check pause
            if (repository.isPaused().firstOrNull() == true) {
                Log.d(TAG, "System paused — skipping notification")
                return false
            }

            // Check battery
            if (getBatteryLevel(context) < 15 && !isCharging(context)) {
                Log.d(TAG, "Low battery — skipping notification")
                return false
            }

            // Check quiet hours
            val quietHours = repository.getQuietHours().firstOrNull() ?: Pair(22, 8)
            if (isInQuietHours(quietHours.first, quietHours.second)) {
                Log.d(TAG, "Quiet hours — skipping notification")
                return false
            }

            // Check daily limit
            val todayCount = repository.getNotificationCountToday()
            if (todayCount >= MAX_NOTIFICATIONS_PER_DAY) {
                Log.d(TAG, "Daily notification limit reached ($todayCount)")
                return false
            }

            // Check minimum interval
            val elapsed = System.currentTimeMillis() - lastNotificationTime
            if (elapsed < MIN_INTERVAL_MS) {
                Log.d(TAG, "Too soon since last notification")
                return false
            }

            // Check if user is in focus session
            if (UsageStatsHelper.isLikelyInFocusSession(context)) {
                Log.d(TAG, "User in focus session — skipping notification")
                return false
            }
        }

        // Send notification
        sendNotificationInternal(context, title, message, channelId, notifId, openChat)
        repository.incrementNotificationCount()
        lastNotificationTime = System.currentTimeMillis()

        return true
    }

    /**
     * Send achievement notification (always shown — important events).
     */
    fun sendAchievementNotification(
        context: Context,
        title: String,
        message: String
    ) {
        sendNotificationInternal(
            context, title, message,
            SoloLevelingApp.CHANNEL_ACHIEVEMENTS,
            SoloLevelingApp.NOTIF_ID_ACHIEVEMENT,
            openChat = false
        )
    }

    /**
     * Send level-up notification.
     */
    fun sendLevelUpNotification(
        context: Context,
        level: Int,
        rankName: String
    ) {
        sendNotificationInternal(
            context,
            "⬆️ ПОВЫШЕНИЕ УРОВНЯ!",
            "Поздравляем! Уровень $level достигнут. Ранг: $rankName 🏆",
            SoloLevelingApp.CHANNEL_ACHIEVEMENTS,
            SoloLevelingApp.NOTIF_ID_ACHIEVEMENT + level,
            openChat = false
        )
    }

    /**
     * Build a contextual AI message based on time of day and personality.
     */
    fun buildContextualMessage(
        personality: AIPersonality,
        userName: String,
        aiName: String,
        context: NotificationContext
    ): Pair<String, String> { // title, message
        val title = "$aiName:"

        val message = when (context) {
            NotificationContext.MORNING -> when (personality) {
                AIPersonality.FRIENDLY ->
                    "Доброе утро, $userName! ☀️ Новый день, новые возможности. Квесты ждут!"
                AIPersonality.STRICT ->
                    "Подъём, $userName! Квесты сами себя не выполнят. Вперёд!"
                AIPersonality.BALANCED ->
                    "Доброе утро, $userName! ⚡ Готов покорять новые высоты?"
            }
            NotificationContext.MIDDAY_CHECKIN -> when (personality) {
                AIPersonality.FRIENDLY ->
                    "Привет, $userName! 😊 Как дела? Есть прогресс по заданиям?"
                AIPersonality.STRICT ->
                    "$userName, статус квестов? Жду отчёта."
                AIPersonality.BALANCED ->
                    "Как дела, $userName? Квесты продвигаются?"
            }
            NotificationContext.EVENING_REPORT -> when (personality) {
                AIPersonality.FRIENDLY ->
                    "Вечер, $userName! 🌅 Пора подвести итоги дня. Как прошло?"
                AIPersonality.STRICT ->
                    "$userName, вечерний отчёт. Что выполнено сегодня?"
                AIPersonality.BALANCED ->
                    "Добрый вечер, $userName! Давай проверим прогресс за день."
            }
            NotificationContext.SLEEP_REMINDER -> when (personality) {
                AIPersonality.FRIENDLY ->
                    "Уже поздно, $userName 🌙 Хороший сон — залог продуктивного завтра!"
                AIPersonality.STRICT ->
                    "$userName, время сна. Сон — часть системы. Ложись."
                AIPersonality.BALANCED ->
                    "Пора отдыхать, $userName. 🌙 Ляг вовремя для лучших результатов."
            }
            NotificationContext.QUEST_REMINDER -> when (personality) {
                AIPersonality.FRIENDLY ->
                    "$userName, не забудь о квестах! Осталось немного времени 😊"
                AIPersonality.STRICT ->
                    "$userName! Квесты истекают сегодня. Выполняй."
                AIPersonality.BALANCED ->
                    "Напоминание, $userName: квесты ждут завершения!"
            }
            NotificationContext.ENCOURAGEMENT -> when (personality) {
                AIPersonality.FRIENDLY ->
                    "Ты отлично справляешься, $userName! ⚡ Продолжай в том же духе!"
                AIPersonality.STRICT ->
                    "Хорошая работа, $userName. Не останавливайся."
                AIPersonality.BALANCED ->
                    "Отличный прогресс, $userName! 🔥 Ты движешься вперёд!"
            }
        }

        return Pair(title, message)
    }

    // ─── Internal Helpers ─────────────────────────────────────────────────────

    private fun sendNotificationInternal(
        context: Context,
        title: String,
        message: String,
        channelId: String,
        notifId: Int,
        openChat: Boolean
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (openChat) putExtra("open_chat", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId, notification)

        Log.d(TAG, "Notification sent: $title")
    }

    private fun isInQuietHours(start: Int, end: Int): Boolean {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return if (start > end) {
            currentHour >= start || currentHour < end
        } else {
            currentHour in start until end
        }
    }

    private fun getBatteryLevel(context: Context): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun isCharging(context: Context): Boolean {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.isCharging
    }
}

enum class NotificationContext {
    MORNING,
    MIDDAY_CHECKIN,
    EVENING_REPORT,
    SLEEP_REMINDER,
    QUEST_REMINDER,
    ENCOURAGEMENT
}
