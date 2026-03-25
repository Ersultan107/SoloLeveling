package com.sololeveling.app.data.local

import androidx.lifecycle.LiveData
import androidx.room.*
import com.sololeveling.app.data.model.*
import kotlinx.coroutines.flow.Flow

// ─── User Profile DAO ─────────────────────────────────────────────────────────

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getUserProfile(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getUserProfileOnce(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(profile: UserProfile)

    @Query("UPDATE user_profile SET xp = xp + :amount WHERE id = 1")
    suspend fun addXP(amount: Long)

    @Query("UPDATE user_profile SET level = :level, rank = :rank WHERE id = 1")
    suspend fun updateLevelAndRank(level: Int, rank: String)

    @Query("UPDATE user_profile SET coins = coins + :amount WHERE id = 1")
    suspend fun addCoins(amount: Int)

    @Query("UPDATE user_profile SET currentStreak = :streak WHERE id = 1")
    suspend fun updateStreak(streak: Int)
}

// ─── Quest DAO ────────────────────────────────────────────────────────────────

@Dao
interface QuestDao {
    @Query("SELECT * FROM quests WHERE status = 'ACTIVE' ORDER BY createdAt DESC")
    fun getActiveQuests(): Flow<List<Quest>>

    @Query("SELECT * FROM quests WHERE status = 'ACTIVE' ORDER BY createdAt DESC")
    suspend fun getActiveQuestsOnce(): List<Quest>

    @Query("SELECT * FROM quests WHERE status = 'COMPLETED' ORDER BY completedAt DESC LIMIT 50")
    fun getCompletedQuests(): Flow<List<Quest>>

    @Query("SELECT * FROM quests ORDER BY createdAt DESC LIMIT 100")
    fun getAllQuests(): Flow<List<Quest>>

    @Query("SELECT * FROM quests WHERE type = 'BOSS' AND status = 'ACTIVE'")
    suspend fun getActiveBossQuest(): Quest?

    @Query("SELECT COUNT(*) FROM quests WHERE status = 'COMPLETED' AND completedAt > :since")
    suspend fun getCompletedCount(since: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(quest: Quest): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(quests: List<Quest>)

    @Update
    suspend fun update(quest: Quest)

    @Query("UPDATE quests SET status = 'COMPLETED', completedAt = :time, currentValue = targetValue WHERE id = :id")
    suspend fun completeQuest(id: Long, time: Long = System.currentTimeMillis())

    @Query("UPDATE quests SET status = 'FAILED' WHERE expiresAt < :now AND status = 'ACTIVE'")
    suspend fun expireOldQuests(now: Long = System.currentTimeMillis())

    @Query("UPDATE quests SET currentValue = :value WHERE id = :id")
    suspend fun updateProgress(id: Long, value: Int)

    @Query("DELETE FROM quests WHERE status IN ('FAILED', 'EXPIRED') AND createdAt < :before")
    suspend fun cleanupOldQuests(before: Long)
}

// ─── Chat Message DAO ─────────────────────────────────────────────────────────

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int = 20): List<ChatMessage>

    @Insert
    suspend fun insert(message: ChatMessage): Long

    @Query("DELETE FROM chat_messages WHERE timestamp < :before")
    suspend fun cleanupOldMessages(before: Long)

    @Query("SELECT COUNT(*) FROM chat_messages WHERE role = 'USER'")
    suspend fun getUserMessageCount(): Int
}

// ─── Sleep Record DAO ─────────────────────────────────────────────────────────

@Dao
interface SleepRecordDao {
    @Query("SELECT * FROM sleep_records ORDER BY date DESC LIMIT 7")
    fun getRecentSleep(): Flow<List<SleepRecord>>

    @Query("SELECT * FROM sleep_records ORDER BY date DESC LIMIT 1")
    suspend fun getLastSleepRecord(): SleepRecord?

    @Query("SELECT AVG(quality) FROM sleep_records WHERE date > :since")
    suspend fun getAverageSleepQuality(since: Long): Float?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: SleepRecord): Long

    @Update
    suspend fun update(record: SleepRecord)
}

// ─── Activity Record DAO ──────────────────────────────────────────────────────

@Dao
interface ActivityRecordDao {
    @Query("SELECT * FROM activity_records ORDER BY date DESC LIMIT 7")
    fun getRecentActivity(): Flow<List<ActivityRecord>>

    @Query("SELECT * FROM activity_records WHERE date > :since ORDER BY date DESC")
    suspend fun getActivitySince(since: Long): List<ActivityRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ActivityRecord): Long

    @Query("SELECT * FROM activity_records ORDER BY date DESC LIMIT 1")
    suspend fun getTodayActivity(): ActivityRecord?
}
