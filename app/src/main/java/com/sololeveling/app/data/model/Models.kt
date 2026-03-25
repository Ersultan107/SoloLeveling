package com.sololeveling.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.sololeveling.app.data.local.Converters

// ─── User Profile ────────────────────────────────────────────────────────────

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val aiName: String = "ARIA",
    val aiPersonality: AIPersonality = AIPersonality.FRIENDLY,
    val rank: Rank = Rank.E,
    val level: Int = 1,
    val xp: Long = 0,
    val xpToNextLevel: Long = 100,
    val totalQuestsCompleted: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val coins: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

enum class Rank(val displayName: String, val color: String, val minLevel: Int) {
    E("E-Rank", "#9E9E9E", 1),
    D("D-Rank", "#4CAF50", 10),
    C("C-Rank", "#2196F3", 25),
    B("B-Rank", "#9C27B0", 50),
    A("A-Rank", "#FF9800", 75),
    S("S-Rank", "#F44336", 100),
    SS("SS-Rank", "#FF4081", 150),
    SSS("SSS-Rank", "#00BCD4", 200)
}

enum class AIPersonality(val displayName: String, val emoji: String) {
    FRIENDLY("Дружелюбный", "😊"),
    STRICT("Строгий", "💪"),
    BALANCED("Сбалансированный", "⚡")
}

// ─── Quest System ─────────────────────────────────────────────────────────────

@Entity(tableName = "quests")
@TypeConverters(Converters::class)
data class Quest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val type: QuestType,
    val difficulty: QuestDifficulty,
    val xpReward: Int,
    val coinReward: Int,
    val status: QuestStatus = QuestStatus.ACTIVE,
    val category: QuestCategory,
    val targetValue: Int = 1,
    val currentValue: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
    val completedAt: Long? = null,
    val isSpecial: Boolean = false
)

enum class QuestType { DAILY, RANDOM, BOSS, SLEEP, WEEKLY }
enum class QuestDifficulty(val multiplier: Float) {
    EASY(0.7f), NORMAL(1.0f), HARD(1.5f), EPIC(2.5f), LEGENDARY(4.0f)
}
enum class QuestStatus { ACTIVE, COMPLETED, FAILED, EXPIRED }
enum class QuestCategory {
    HEALTH, PRODUCTIVITY, LEARNING, FITNESS, MINDFULNESS, SOCIAL, CREATIVITY, SLEEP
}

// ─── Chat Messages ────────────────────────────────────────────────────────────

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val role: MessageRole,
    val timestamp: Long = System.currentTimeMillis(),
    val isTyping: Boolean = false,
    val emotion: AIEmotion = AIEmotion.NEUTRAL,
    val messageType: MessageType = MessageType.NORMAL
)

enum class MessageRole { USER, AI }
enum class AIEmotion { HAPPY, NEUTRAL, CONCERNED, EXCITED, PROUD, STERN }
enum class MessageType { NORMAL, QUEST_COMPLETE, LEVEL_UP, ANALYSIS, GREETING, SLEEP_REPORT }

// ─── Sleep Record ─────────────────────────────────────────────────────────────

@Entity(tableName = "sleep_records")
data class SleepRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val sleepTime: Long,
    val wakeTime: Long?,
    val duration: Long = 0,
    val quality: Int = 0, // 0-100
    val affectedDifficulty: Float = 1.0f
)

// ─── Activity Record ──────────────────────────────────────────────────────────

@Entity(tableName = "activity_records")
data class ActivityRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long = System.currentTimeMillis(),
    val steps: Int = 0,
    val activeMinutes: Int = 0,
    val screenTimeMinutes: Int = 0,
    val focusSessionCount: Int = 0
)

// ─── App Usage ────────────────────────────────────────────────────────────────

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val usageTimeMs: Long,
    val category: String
)

// ─── AI Analysis Result ───────────────────────────────────────────────────────

data class AIAnalysis(
    val overallScore: Int,
    val sleepScore: Int,
    val productivityScore: Int,
    val fitnessScore: Int,
    val recommendation: String,
    val suggestedDifficultyMultiplier: Float,
    val mood: String,
    val timestamp: Long = System.currentTimeMillis()
)

// ─── Notification Config ──────────────────────────────────────────────────────

data class NotificationConfig(
    val enabled: Boolean = true,
    val quietHoursStart: Int = 22, // 22:00
    val quietHoursEnd: Int = 8,    // 08:00
    val maxPerDay: Int = 5,
    val intervalMinutes: Int = 120
)

// ─── System State ─────────────────────────────────────────────────────────────

data class SystemState(
    val isSafeMode: Boolean = false,
    val isTestMode: Boolean = false,
    val isPaused: Boolean = false,
    val isFocusModeActive: Boolean = false,
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val isSilentMode: Boolean = false
)
