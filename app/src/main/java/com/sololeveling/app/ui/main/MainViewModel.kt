package com.sololeveling.app.ui.main

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.sololeveling.app.SoloLevelingApp
import com.sololeveling.app.ai.AIEngine
import com.sololeveling.app.ai.AIResponse
import com.sololeveling.app.ai.TimeOfDay
import com.sololeveling.app.data.model.*
import com.sololeveling.app.data.repository.UserRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import java.util.Calendar

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SoloLevelingApp
    val repository = app.repository
    private val aiEngine = app.aiEngine

    // ─── LiveData ─────────────────────────────────────────────────────────────

    val userProfile = repository.getUserProfile().asLiveData()
    val activeQuests = repository.getActiveQuests().asLiveData()
    val completedQuests = repository.getCompletedQuests().asLiveData()
    val chatMessages = repository.getAllChatMessages().asLiveData()
    val recentSleep = repository.getRecentSleep().asLiveData()
    val recentActivity = repository.getRecentActivity().asLiveData()

    private val _currentAnalysis = MutableLiveData<AIAnalysis?>()
    val currentAnalysis: LiveData<AIAnalysis?> = _currentAnalysis

    private val _aiResponse = MutableLiveData<AIResponse?>()
    val aiResponse: LiveData<AIResponse?> = _aiResponse

    private val _isAITyping = MutableLiveData<Boolean>(false)
    val isAITyping: LiveData<Boolean> = _isAITyping

    private val _levelUpEvent = MutableLiveData<Pair<Int, Rank>?>()
    val levelUpEvent: LiveData<Pair<Int, Rank>?> = _levelUpEvent

    private val _toastMessage = MutableLiveData<String?>()
    val toastMessage: LiveData<String?> = _toastMessage

    private val _systemState = MutableLiveData<SystemState>()
    val systemState: LiveData<SystemState> = _systemState

    init {
        loadSystemState()
        refreshAnalysis()
    }

    // ─── System State ─────────────────────────────────────────────────────────

    private fun loadSystemState() {
        viewModelScope.launch {
            val safeMode = repository.isSafeMode().firstOrNull() ?: false
            val isPaused = repository.isPaused().firstOrNull() ?: false
            _systemState.value = SystemState(
                isSafeMode = safeMode,
                isPaused = isPaused
            )
        }
    }

    fun togglePause() {
        viewModelScope.launch {
            val current = _systemState.value?.isPaused ?: false
            repository.setPaused(!current)
            _systemState.value = _systemState.value?.copy(isPaused = !current)
            _toastMessage.value = if (!current) "Система приостановлена" else "Система активна"
        }
    }

    fun toggleSafeMode() {
        viewModelScope.launch {
            val current = _systemState.value?.isSafeMode ?: false
            repository.setSafeMode(!current)
            _systemState.value = _systemState.value?.copy(isSafeMode = !current)
            _toastMessage.value = if (!current) "Безопасный режим включён" else "Безопасный режим выключен"
        }
    }

    // ─── AI Chat ──────────────────────────────────────────────────────────────

    fun sendUserMessage(text: String) {
        viewModelScope.launch {
            val profile = repository.getUserProfileOnce() ?: return@launch

            // Save user message
            val userMessage = ChatMessage(
                content = text,
                role = MessageRole.USER
            )
            repository.insertChatMessage(userMessage)

            // Show typing indicator
            _isAITyping.value = true

            // Get conversation history
            val history = repository.getRecentMessages(15)

            // Get AI response
            val response = aiEngine.sendMessage(
                userMessage = text,
                conversationHistory = history,
                userProfile = profile,
                recentAnalysis = _currentAnalysis.value
            )

            // Save AI message
            val aiMessage = ChatMessage(
                content = response.text,
                role = MessageRole.AI,
                emotion = response.emotion
            )
            repository.insertChatMessage(aiMessage)

            _isAITyping.value = false
            _aiResponse.value = response

            // Handle AI actions
            response.action?.let { handleAIAction(it) }
        }
    }

    fun sendGreeting() {
        viewModelScope.launch {
            val profile = repository.getUserProfileOnce() ?: return@launch
            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val timeOfDay = when (currentHour) {
                in 6..11 -> TimeOfDay.MORNING
                in 12..17 -> TimeOfDay.AFTERNOON
                in 18..21 -> TimeOfDay.EVENING
                else -> TimeOfDay.NIGHT
            }

            _isAITyping.value = true

            val response = aiEngine.generateGreeting(profile, timeOfDay, _currentAnalysis.value)

            val aiMessage = ChatMessage(
                content = response.text,
                role = MessageRole.AI,
                emotion = response.emotion,
                messageType = MessageType.GREETING
            )
            repository.insertChatMessage(aiMessage)

            _isAITyping.value = false
            _aiResponse.value = response
        }
    }

    private suspend fun handleAIAction(action: com.sololeveling.app.ai.AIAction) {
        try {
            when (action.type) {
                "create_quest" -> {
                    // Parse and create quest from AI suggestion
                    action.data?.let { data ->
                        val json = org.json.JSONObject(data)
                        val quest = Quest(
                            title = json.optString("title", "Новый квест"),
                            description = json.optString("description", ""),
                            type = QuestType.valueOf(json.optString("type", "RANDOM")),
                            difficulty = QuestDifficulty.valueOf(json.optString("difficulty", "NORMAL")),
                            xpReward = json.optInt("xpReward", 50),
                            coinReward = json.optInt("xpReward", 50) / 2,
                            category = QuestCategory.valueOf(json.optString("category", "PRODUCTIVITY"))
                        )
                        repository.insertQuest(quest)
                        _toastMessage.value = "✨ Новый квест добавлен!"
                    }
                }
                "analysis_complete" -> {
                    action.data?.let { data ->
                        val json = org.json.JSONObject(data)
                        val analysis = AIAnalysis(
                            overallScore = json.optInt("overallScore", 70),
                            sleepScore = json.optInt("sleepScore", 70),
                            productivityScore = json.optInt("productivityScore", 70),
                            fitnessScore = json.optInt("fitnessScore", 70),
                            recommendation = json.optString("recommendation", ""),
                            suggestedDifficultyMultiplier = json.optDouble("suggestedMultiplier", 1.0).toFloat(),
                            mood = json.optString("mood", "Normal")
                        )
                        _currentAnalysis.value = analysis
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Failed to handle AI action", e)
        }
    }

    // ─── Analysis ─────────────────────────────────────────────────────────────

    fun refreshAnalysis() {
        viewModelScope.launch {
            val profile = repository.getUserProfileOnce() ?: return@launch
            val sleepRecords = repository.getRecentSleep().firstOrNull() ?: emptyList()
            val activityRecords = repository.getActivitySince(7)
            val completedCount = repository.getCompletedTodayCount()

            val analysis = aiEngine.analyzeUserState(
                profile, sleepRecords, activityRecords, completedCount
            )
            _currentAnalysis.value = analysis
        }
    }

    // ─── Quests ───────────────────────────────────────────────────────────────

    fun completeQuest(quest: Quest) {
        viewModelScope.launch {
            val profileBefore = repository.getUserProfileOnce()
            repository.completeQuest(quest)

            val profileAfter = repository.getUserProfileOnce()

            if (profileBefore != null && profileAfter != null &&
                profileAfter.level > profileBefore.level) {
                _levelUpEvent.value = Pair(profileAfter.level, profileAfter.rank)
            }

            _toastMessage.value = "⚡ +${quest.xpReward} XP"

            // Update streak
            val completedToday = repository.getCompletedTodayCount()
            if (completedToday >= 3 && profileAfter != null) {
                repository.saveUserProfile(
                    profileAfter.copy(
                        currentStreak = profileAfter.currentStreak + 1,
                        longestStreak = maxOf(
                            profileAfter.currentStreak + 1,
                            profileAfter.longestStreak
                        )
                    )
                )
            }

            // Send AI congratulation
            val profile = repository.getUserProfileOnce() ?: return@launch
            val congrats = getCongratulationMessage(quest, profile)
            val aiMessage = ChatMessage(
                content = congrats,
                role = MessageRole.AI,
                emotion = AIEmotion.EXCITED,
                messageType = MessageType.QUEST_COMPLETE
            )
            repository.insertChatMessage(aiMessage)
        }
    }

    private fun getCongratulationMessage(quest: Quest, profile: UserProfile): String {
        val messages = when (profile.aiPersonality) {
            AIPersonality.FRIENDLY -> listOf(
                "🎉 Отлично, ${profile.name}! Выполнил \"${quest.title}\"! +${quest.xpReward} XP заслужено! Ты продолжаешь расти!",
                "⚡ Вот это да! \"${quest.title}\" выполнен! Я горжусь тобой, ${profile.name}! Продолжай в том же духе!",
                "🌟 Блестяще! ${profile.name} снова показал класс! Квест завершён, XP получены!"
            )
            AIPersonality.STRICT -> listOf(
                "Хорошая работа. \"${quest.title}\" выполнен. ${quest.xpReward} XP зачислено. Переходи к следующему.",
                "Принято. Квест \"${quest.title}\" закрыт. Без задержек — следующее задание ждёт.",
                "Выполнено. Результат засчитан. +${quest.xpReward} XP. Не расслабляйся."
            )
            AIPersonality.BALANCED -> listOf(
                "⚔️ Квест \"${quest.title}\" завершён! +${quest.xpReward} XP. Хорошая работа, ${profile.name}!",
                "✅ Отличный результат! \"${quest.title}\" выполнен. Ты движешься вперёд!",
                "⚡ Засчитано! \"${quest.title}\" закрыт. ${quest.xpReward} XP твои, ${profile.name}!"
            )
        }
        return messages.random()
    }

    // ─── Sleep Tracking ───────────────────────────────────────────────────────

    fun recordSleepStart() {
        viewModelScope.launch {
            val record = SleepRecord(
                date = System.currentTimeMillis(),
                sleepTime = System.currentTimeMillis()
            )
            repository.saveSleepRecord(record)
            _toastMessage.value = "🌙 Время сна записано. Спокойной ночи!"
        }
    }

    fun recordWakeUp() {
        viewModelScope.launch {
            val lastRecord = repository.getLastSleepRecord() ?: return@launch
            if (lastRecord.wakeTime != null) return@launch

            val wakeTime = System.currentTimeMillis()
            val duration = wakeTime - lastRecord.sleepTime
            val hours = duration / (1000 * 60 * 60)

            val quality = when {
                hours >= 8 -> 100
                hours >= 7 -> 85
                hours >= 6 -> 65
                hours >= 5 -> 45
                else -> 25
            }

            repository.updateSleepRecord(
                lastRecord.copy(
                    wakeTime = wakeTime,
                    duration = duration,
                    quality = quality,
                    affectedDifficulty = quality / 100f + 0.5f
                )
            )

            val profile = repository.getUserProfileOnce()
            val morningMsg = buildSleepReport(hours, quality, profile)
            val aiMsg = ChatMessage(
                content = morningMsg,
                role = MessageRole.AI,
                emotion = if (quality >= 70) AIEmotion.HAPPY else AIEmotion.CONCERNED,
                messageType = MessageType.SLEEP_REPORT
            )
            repository.insertChatMessage(aiMsg)
            refreshAnalysis()
        }
    }

    private fun buildSleepReport(hours: Long, quality: Int, profile: UserProfile?): String {
        val name = profile?.name ?: "Охотник"
        val aiName = profile?.aiName ?: "ARIA"

        return when {
            quality >= 85 -> "☀️ Доброе утро, $name! Анализ сна завершён: ${hours}ч, качество $quality%. Отличный отдых! Сегодня ты в прекрасной форме — задания будут чуть сложнее 💪"
            quality >= 65 -> "🌤️ Доброе утро! Сон: ${hours}ч, качество $quality%. Неплохо! Сегодня будет продуктивный день."
            quality >= 45 -> "😴 Доброе утро, $name. Сон: ${hours}ч, качество $quality%. Недостаточно отдыха. Сегодня задания будут чуть легче — береги силы."
            else -> "⚠️ $name, сон был коротким (${hours}ч, качество $quality%). Это влияет на производительность. Сегодня — лёгкий режим. Постарайся сегодня лечь раньше!"
        }
    }

    // ─── User Profile ─────────────────────────────────────────────────────────

    fun clearLevelUpEvent() { _levelUpEvent.value = null }
    fun clearToast() { _toastMessage.value = null }
    fun clearAIResponse() { _aiResponse.value = null }
}
