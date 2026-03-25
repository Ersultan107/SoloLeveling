package com.sololeveling.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.sololeveling.app.data.local.*
import com.sololeveling.app.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "solo_leveling_prefs")

class UserRepository(private val context: Context) {

    private val db = SoloLevelingDatabase.getDatabase(context)
    private val userDao = db.userProfileDao()
    private val questDao = db.questDao()
    private val chatDao = db.chatMessageDao()
    private val sleepDao = db.sleepRecordDao()
    private val activityDao = db.activityRecordDao()

    companion object {
        val KEY_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val KEY_SAFE_MODE = booleanPreferencesKey("safe_mode")
        val KEY_TEST_MODE = booleanPreferencesKey("test_mode")
        val KEY_IS_PAUSED = booleanPreferencesKey("is_paused")
        val KEY_NOTIFICATION_COUNT_TODAY = intPreferencesKey("notif_count_today")
        val KEY_LAST_GREETING_DATE = longPreferencesKey("last_greeting_date")
        val KEY_SLEEP_TIME = longPreferencesKey("sleep_time")
        val KEY_LAST_QUEST_DATE = longPreferencesKey("last_quest_date")
        val KEY_BATTERY_THRESHOLD = intPreferencesKey("battery_threshold")
        val KEY_QUIET_START = intPreferencesKey("quiet_hours_start")
        val KEY_QUIET_END = intPreferencesKey("quiet_hours_end")
        val KEY_API_KEY = stringPreferencesKey("api_key")
    }

    // ─── User Profile ─────────────────────────────────────────────────────────

    fun getUserProfile(): Flow<UserProfile?> = userDao.getUserProfile()
    suspend fun getUserProfileOnce() = userDao.getUserProfileOnce()
    suspend fun saveUserProfile(profile: UserProfile) = userDao.insertOrUpdate(profile)

    suspend fun addXP(amount: Long) {
        userDao.addXP(amount)
        checkLevelUp()
    }

    suspend fun addCoins(amount: Int) = userDao.addCoins(amount)

    private suspend fun checkLevelUp() {
        val profile = userDao.getUserProfileOnce() ?: return
        if (profile.xp >= profile.xpToNextLevel) {
            val newLevel = profile.level + 1
            val newXpNeeded = calculateXPNeeded(newLevel)
            val newRank = calculateRank(newLevel)
            userDao.insertOrUpdate(
                profile.copy(
                    level = newLevel,
                    xp = profile.xp - profile.xpToNextLevel,
                    xpToNextLevel = newXpNeeded,
                    rank = newRank
                )
            )
        }
    }

    private fun calculateXPNeeded(level: Int): Long = (100 * Math.pow(1.15, level.toDouble())).toLong()

    private fun calculateRank(level: Int) = when {
        level >= 200 -> Rank.SSS
        level >= 150 -> Rank.SS
        level >= 100 -> Rank.S
        level >= 75 -> Rank.A
        level >= 50 -> Rank.B
        level >= 25 -> Rank.C
        level >= 10 -> Rank.D
        else -> Rank.E
    }

    // ─── Quests ───────────────────────────────────────────────────────────────

    fun getActiveQuests(): Flow<List<Quest>> = questDao.getActiveQuests()
    fun getCompletedQuests(): Flow<List<Quest>> = questDao.getCompletedQuests()
    suspend fun getActiveQuestsOnce() = questDao.getActiveQuestsOnce()
    suspend fun insertQuest(quest: Quest) = questDao.insert(quest)
    suspend fun insertQuests(quests: List<Quest>) = questDao.insertAll(quests)
    suspend fun updateQuest(quest: Quest) = questDao.update(quest)

    suspend fun completeQuest(quest: Quest) {
        questDao.completeQuest(quest.id)
        addXP(quest.xpReward.toLong())
        addCoins(quest.coinReward)
        val profile = getUserProfileOnce()
        if (profile != null) {
            userDao.insertOrUpdate(
                profile.copy(
                    totalQuestsCompleted = profile.totalQuestsCompleted + 1
                )
            )
        }
    }

    suspend fun expireOldQuests() = questDao.expireOldQuests()
    suspend fun getActiveBossQuest() = questDao.getActiveBossQuest()
    suspend fun getCompletedTodayCount(): Int {
        val midnight = getMidnightTimestamp()
        return questDao.getCompletedCount(midnight)
    }

    // ─── Chat ─────────────────────────────────────────────────────────────────

    fun getAllChatMessages(): Flow<List<ChatMessage>> = chatDao.getAllMessages()
    suspend fun insertChatMessage(message: ChatMessage) = chatDao.insert(message)
    suspend fun getRecentMessages(limit: Int = 20) = chatDao.getRecentMessages(limit)

    // ─── Sleep ────────────────────────────────────────────────────────────────

    fun getRecentSleep(): Flow<List<SleepRecord>> = sleepDao.getRecentSleep()
    suspend fun saveSleepRecord(record: SleepRecord) = sleepDao.insert(record)
    suspend fun updateSleepRecord(record: SleepRecord) = sleepDao.update(record)
    suspend fun getLastSleepRecord() = sleepDao.getLastSleepRecord()
    suspend fun getAverageSleepQuality(days: Int = 7): Float {
        val since = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        return sleepDao.getAverageSleepQuality(since) ?: 60f
    }

    // ─── Activity ─────────────────────────────────────────────────────────────

    fun getRecentActivity(): Flow<List<ActivityRecord>> = activityDao.getRecentActivity()
    suspend fun saveActivityRecord(record: ActivityRecord) = activityDao.insert(record)
    suspend fun getActivitySince(days: Int = 7): List<ActivityRecord> {
        val since = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        return activityDao.getActivitySince(since)
    }
    suspend fun getTodayActivity() = activityDao.getTodayActivity()

    // ─── DataStore Preferences ────────────────────────────────────────────────

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_DONE] = done }
    }

    fun isOnboardingDone(): Flow<Boolean> = context.dataStore.data.map {
        it[KEY_ONBOARDING_DONE] ?: false
    }

    suspend fun setSafeMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SAFE_MODE] = enabled }
    }

    fun isSafeMode(): Flow<Boolean> = context.dataStore.data.map {
        it[KEY_SAFE_MODE] ?: false
    }

    suspend fun setPaused(paused: Boolean) {
        context.dataStore.edit { it[KEY_IS_PAUSED] = paused }
    }

    fun isPaused(): Flow<Boolean> = context.dataStore.data.map {
        it[KEY_IS_PAUSED] ?: false
    }

    suspend fun incrementNotificationCount() {
        context.dataStore.edit {
            it[KEY_NOTIFICATION_COUNT_TODAY] = (it[KEY_NOTIFICATION_COUNT_TODAY] ?: 0) + 1
        }
    }

    suspend fun getNotificationCountToday(): Int {
        var count = 0
        context.dataStore.data.collect { count = it[KEY_NOTIFICATION_COUNT_TODAY] ?: 0 }
        return count
    }

    suspend fun resetNotificationCount() {
        context.dataStore.edit { it[KEY_NOTIFICATION_COUNT_TODAY] = 0 }
    }

    suspend fun setLastGreetingDate(time: Long) {
        context.dataStore.edit { it[KEY_LAST_GREETING_DATE] = time }
    }

    suspend fun getLastGreetingDate(): Long {
        var date = 0L
        context.dataStore.data.collect { date = it[KEY_LAST_GREETING_DATE] ?: 0L }
        return date
    }

    suspend fun setSleepTime(time: Long) {
        context.dataStore.edit { it[KEY_SLEEP_TIME] = time }
    }

    suspend fun getSleepTime(): Long {
        var time = 0L
        context.dataStore.data.collect { time = it[KEY_SLEEP_TIME] ?: 0L }
        return time
    }

    suspend fun setLastQuestDate(date: Long) {
        context.dataStore.edit { it[KEY_LAST_QUEST_DATE] = date }
    }

    suspend fun getLastQuestDate(): Long {
        var date = 0L
        context.dataStore.data.collect { date = it[KEY_LAST_QUEST_DATE] ?: 0L }
        return date
    }

    suspend fun setQuietHours(start: Int, end: Int) {
        context.dataStore.edit {
            it[KEY_QUIET_START] = start
            it[KEY_QUIET_END] = end
        }
    }

    fun getQuietHours(): Flow<Pair<Int, Int>> = context.dataStore.data.map {
        Pair(it[KEY_QUIET_START] ?: 22, it[KEY_QUIET_END] ?: 8)
    }

    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { it[KEY_API_KEY] = key }
    }

    suspend fun getApiKey(): String {
        var key = ""
        context.dataStore.data.collect { key = it[KEY_API_KEY] ?: "" }
        return key
    }

    private fun getMidnightTimestamp(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
