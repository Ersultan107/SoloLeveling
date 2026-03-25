package com.sololeveling.app.ai

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.sololeveling.app.data.model.*
import com.sololeveling.app.data.repository.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AIEngine(
    private val context: Context,
    private val userRepository: UserRepository
) {

    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "AIEngine"
        private const val API_URL = "https://api.anthropic.com/v1/messages"
        // Replace with your actual key in BuildConfig or secure storage
        private const val API_KEY = "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"
        private const val MODEL = "claude-sonnet-4-20250514"
    }

    // ─── Build AI System Prompt ───────────────────────────────────────────────

    private fun buildSystemPrompt(
        aiName: String,
        userName: String,
        personality: AIPersonality,
        rank: Rank,
        level: Int,
        currentStreak: Int,
        recentAnalysis: AIAnalysis?
    ): String {
        val personalityDesc = when (personality) {
            AIPersonality.FRIENDLY -> "Ты дружелюбный, поддерживающий друг. Используешь тёплый тон, смайлики, шутки. Всегда поддерживаешь пользователя."
            AIPersonality.STRICT -> "Ты строгий тренер и наставник. Требователен, но справедлив. Не принимаешь оправданий, но ценишь настоящие усилия."
            AIPersonality.BALANCED -> "Ты сбалансированный ментор — можешь быть и добрым, и строгим в зависимости от ситуации."
        }

        val analysisContext = recentAnalysis?.let {
            "Последний анализ: сон ${it.sleepScore}%, продуктивность ${it.productivityScore}%, фитнес ${it.fitnessScore}%. Рекомендация: ${it.recommendation}"
        } ?: ""

        return """
Ты — $aiName, персональный ИИ-ассистент системы саморазвития Solo Leveling.
Твой пользователь: $userName (${rank.displayName}, уровень $level, серия $currentStreak дней).

ЛИЧНОСТЬ: $personalityDesc

ТВОИ ЗАДАЧИ:
1. Общаться как живой друг/ментор на русском языке
2. Спрашивать о самочувствии, делах, настроении
3. Анализировать прогресс и давать умные советы
4. Управлять системой квестов
5. Поддерживать мотивацию

ПРАВИЛА ОБЩЕНИЯ:
- Обращайся к пользователю по имени "$userName"
- Называй себя "$aiName"
- Будь живым, не роботизированным
- Используй контекст предыдущих сообщений
- Давай конкретные, полезные советы
- Отмечай достижения с энтузиазмом
- При необходимости предупреждай о переутомлении

$analysisContext

ФОРМАТ ОТВЕТА:
Отвечай естественно. Если нужно вернуть структурированные данные для квестов, 
используй JSON-блок в конце: {"action": "...", "data": {...}}

Доступные действия:
- {"action": "create_quest", "data": {"title": "...", "description": "...", "type": "DAILY/RANDOM/BOSS", "category": "HEALTH/PRODUCTIVITY/etc", "difficulty": "EASY/NORMAL/HARD/EPIC", "xpReward": 50}}
- {"action": "analysis_complete", "data": {"sleepScore": 80, "productivityScore": 70, "fitnessScore": 60, "recommendation": "...", "suggestedMultiplier": 1.0}}
- {"action": "level_up_message", "data": {"message": "..."}}
""".trimIndent()
    }

    // ─── Send Message to AI ───────────────────────────────────────────────────

    suspend fun sendMessage(
        userMessage: String,
        conversationHistory: List<ChatMessage>,
        userProfile: UserProfile,
        recentAnalysis: AIAnalysis? = null
    ): AIResponse = withContext(Dispatchers.IO) {

        try {
            val systemPrompt = buildSystemPrompt(
                aiName = userProfile.aiName,
                userName = userProfile.name,
                personality = userProfile.aiPersonality,
                rank = userProfile.rank,
                level = userProfile.level,
                currentStreak = userProfile.currentStreak,
                recentAnalysis = recentAnalysis
            )

            // Build messages array
            val messages = JSONArray()

            // Add conversation history (last 10 messages)
            conversationHistory.takeLast(10).forEach { msg ->
                val msgObj = JSONObject()
                msgObj.put("role", if (msg.role == MessageRole.USER) "user" else "assistant")
                msgObj.put("content", msg.content)
                messages.put(msgObj)
            }

            // Add current user message
            val userMsg = JSONObject()
            userMsg.put("role", "user")
            userMsg.put("content", userMessage)
            messages.put(userMsg)

            val requestBody = JSONObject().apply {
                put("model", MODEL)
                put("max_tokens", 1000)
                put("system", systemPrompt)
                put("messages", messages)
            }.toString()

            val request = Request.Builder()
                .url(API_URL)
                .addHeader("x-api-key", API_KEY)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("content-type", "application/json")
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e(TAG, "API Error: ${response.code}")
                return@withContext AIResponse(
                    text = getFallbackResponse(userProfile.aiName, userProfile.aiPersonality),
                    emotion = AIEmotion.NEUTRAL,
                    action = null
                )
            }

            val responseBody = response.body?.string() ?: ""
            val jsonResponse = JSONObject(responseBody)
            val content = jsonResponse
                .getJSONArray("content")
                .getJSONObject(0)
                .getString("text")

            parseAIResponse(content)

        } catch (e: Exception) {
            Log.e(TAG, "AI request failed", e)
            AIResponse(
                text = getFallbackResponse(userProfile.aiName, userProfile.aiPersonality),
                emotion = AIEmotion.NEUTRAL,
                action = null
            )
        }
    }

    // ─── Generate Greeting ────────────────────────────────────────────────────

    suspend fun generateGreeting(
        userProfile: UserProfile,
        timeOfDay: TimeOfDay,
        analysis: AIAnalysis?
    ): AIResponse = withContext(Dispatchers.IO) {

        val contextPrompt = buildGreetingPrompt(userProfile, timeOfDay, analysis)

        sendMessage(
            userMessage = contextPrompt,
            conversationHistory = emptyList(),
            userProfile = userProfile,
            recentAnalysis = analysis
        )
    }

    private fun buildGreetingPrompt(
        profile: UserProfile,
        timeOfDay: TimeOfDay,
        analysis: AIAnalysis?
    ): String {
        val timeContext = when (timeOfDay) {
            TimeOfDay.MORNING -> "Сейчас утро. Поприветствуй пользователя, спроси как он спал."
            TimeOfDay.AFTERNOON -> "Сейчас день. Спроси как дела, как продуктивность."
            TimeOfDay.EVENING -> "Сейчас вечер. Подведи итоги дня, похвали за достижения."
            TimeOfDay.NIGHT -> "Сейчас ночь. Мягко напомни о важности сна."
        }

        val analysisContext = analysis?.let {
            "Анализ состояния: сон ${it.sleepScore}%, продуктивность ${it.productivityScore}%."
        } ?: ""

        return "СИСТЕМНЫЙ ЗАПРОС: $timeContext Серия дней: ${profile.currentStreak}. $analysisContext Дай короткое приветствие (2-3 предложения)."
    }

    // ─── Analyze User State ───────────────────────────────────────────────────

    suspend fun analyzeUserState(
        userProfile: UserProfile,
        sleepRecords: List<SleepRecord>,
        activityRecords: List<ActivityRecord>,
        completedQuestsCount: Int
    ): AIAnalysis = withContext(Dispatchers.IO) {

        val sleepScore = calculateSleepScore(sleepRecords)
        val productivityScore = calculateProductivityScore(activityRecords, completedQuestsCount)
        val fitnessScore = calculateFitnessScore(activityRecords)

        val overallScore = ((sleepScore + productivityScore + fitnessScore) / 3)

        val multiplier = when {
            overallScore < 30 -> 0.6f
            overallScore < 50 -> 0.8f
            overallScore < 70 -> 1.0f
            overallScore < 85 -> 1.2f
            else -> 1.5f
        }

        val recommendation = generateRecommendation(sleepScore, productivityScore, fitnessScore)
        val mood = when {
            overallScore >= 80 -> "Отличное состояние! 🔥"
            overallScore >= 60 -> "Хорошее состояние ✅"
            overallScore >= 40 -> "Нормальное состояние 😐"
            else -> "Нужен отдых и восстановление 😴"
        }

        AIAnalysis(
            overallScore = overallScore,
            sleepScore = sleepScore,
            productivityScore = productivityScore,
            fitnessScore = fitnessScore,
            recommendation = recommendation,
            suggestedDifficultyMultiplier = multiplier,
            mood = mood
        )
    }

    // ─── Generate Daily Quests ────────────────────────────────────────────────

    suspend fun generateDailyQuests(
        userProfile: UserProfile,
        analysis: AIAnalysis
    ): List<Quest> = withContext(Dispatchers.IO) {

        val difficulty = getDifficultyFromAnalysis(analysis)

        val questTemplates = getDailyQuestTemplates(difficulty, userProfile.level)

        questTemplates.map { template ->
            val xpBase = when (difficulty) {
                QuestDifficulty.EASY -> 30
                QuestDifficulty.NORMAL -> 50
                QuestDifficulty.HARD -> 80
                QuestDifficulty.EPIC -> 120
                QuestDifficulty.LEGENDARY -> 200
            }

            Quest(
                title = template.first,
                description = template.second,
                type = QuestType.DAILY,
                difficulty = difficulty,
                xpReward = (xpBase * difficulty.multiplier).toInt(),
                coinReward = (xpBase * 0.5 * difficulty.multiplier).toInt(),
                category = template.third,
                targetValue = template.fourth,
                expiresAt = getMidnightTimestamp()
            )
        }
    }

    suspend fun generateBossQuest(userProfile: UserProfile): Quest = withContext(Dispatchers.IO) {
        val bossQuests = listOf(
            Triple("Железная воля", "Выполни все задания дня без пропусков и не открывай соцсети 6 часов", QuestCategory.PRODUCTIVITY),
            Triple("Покоритель сна", "Ложись спать до 23:00 и вставай до 7:00 три дня подряд", QuestCategory.SLEEP),
            Triple("Физическая элита", "Сделай 100 отжиманий, 100 приседаний и 5 км прогулки за день", QuestCategory.FITNESS),
            Triple("Мозговой штурм", "Изучи новую тему 2 часа и создай конспект", QuestCategory.LEARNING),
            Triple("Мастер фокуса", "Проведи 4 сессии по 25 минут без отвлечений", QuestCategory.PRODUCTIVITY)
        )

        val selected = bossQuests.random()
        Quest(
            title = "👑 БОСС-КВЕСТ: ${selected.first}",
            description = selected.second,
            type = QuestType.BOSS,
            difficulty = QuestDifficulty.EPIC,
            xpReward = 300,
            coinReward = 150,
            category = selected.third,
            targetValue = 1,
            isSpecial = true,
            expiresAt = System.currentTimeMillis() + (3 * 24 * 60 * 60 * 1000L) // 3 days
        )
    }

    // ─── Helper Functions ─────────────────────────────────────────────────────

    private fun parseAIResponse(rawText: String): AIResponse {
        // Check for JSON action block
        val jsonRegex = Regex("""\{"action"[^}]+\}""", RegexOption.DOT_MATCHES_ALL)
        val jsonMatch = jsonRegex.find(rawText)

        val action = jsonMatch?.let {
            try {
                val json = JSONObject(it.value)
                AIAction(
                    type = json.getString("action"),
                    data = json.optJSONObject("data")?.toString()
                )
            } catch (e: Exception) { null }
        }

        val textWithoutJson = if (jsonMatch != null) {
            rawText.replace(jsonMatch.value, "").trim()
        } else rawText

        val emotion = detectEmotion(textWithoutJson)

        return AIResponse(
            text = textWithoutJson,
            emotion = emotion,
            action = action
        )
    }

    private fun detectEmotion(text: String): AIEmotion {
        return when {
            text.contains(Regex("[!🎉🔥🏆⚡🌟]")) -> AIEmotion.EXCITED
            text.contains(Regex("[😊😄🤗👍✨]")) -> AIEmotion.HAPPY
            text.contains(Regex("[😟😔⚠️🚨]")) -> AIEmotion.CONCERNED
            text.contains(Regex("[💪👊🎯⚔️]")) -> AIEmotion.PROUD
            text.contains(Regex("[😤💢⚡🔥]")) && text.length < 100 -> AIEmotion.STERN
            else -> AIEmotion.NEUTRAL
        }
    }

    private fun calculateSleepScore(records: List<SleepRecord>): Int {
        if (records.isEmpty()) return 60
        val avgQuality = records.take(7).map { it.quality }.average()
        return avgQuality.toInt().coerceIn(0, 100)
    }

    private fun calculateProductivityScore(
        records: List<ActivityRecord>,
        completedQuests: Int
    ): Int {
        val baseScore = (completedQuests * 20).coerceAtMost(60)
        val focusBonus = records.sumOf { it.focusSessionCount } * 5
        return (baseScore + focusBonus).coerceIn(0, 100)
    }

    private fun calculateFitnessScore(records: List<ActivityRecord>): Int {
        if (records.isEmpty()) return 40
        val avgSteps = records.map { it.steps }.average()
        return when {
            avgSteps >= 10000 -> 100
            avgSteps >= 7000 -> 80
            avgSteps >= 5000 -> 60
            avgSteps >= 3000 -> 40
            else -> 20
        }
    }

    private fun generateRecommendation(sleep: Int, productivity: Int, fitness: Int): String {
        return when {
            sleep < 50 -> "Приоритет: улучшить режим сна. Попробуй лечь раньше сегодня."
            fitness < 40 -> "Добавь небольшую прогулку — это поднимет энергию и настроение."
            productivity < 50 -> "Попробуй технику Помодоро: 25 минут работы, 5 минут отдыха."
            sleep > 80 && fitness > 70 && productivity > 70 -> "Ты в отличной форме! Можно взяться за сложный квест."
            else -> "Поддерживай текущий ритм. Маленькие шаги ведут к большим результатам."
        }
    }

    private fun getDifficultyFromAnalysis(analysis: AIAnalysis): QuestDifficulty {
        return when {
            analysis.overallScore < 30 -> QuestDifficulty.EASY
            analysis.overallScore < 55 -> QuestDifficulty.NORMAL
            analysis.overallScore < 75 -> QuestDifficulty.HARD
            analysis.overallScore < 90 -> QuestDifficulty.EPIC
            else -> QuestDifficulty.LEGENDARY
        }
    }

    private fun getDailyQuestTemplates(
        difficulty: QuestDifficulty,
        level: Int
    ): List<Quadruple<String, String, QuestCategory, Int>> {
        val templates = mutableListOf<Quadruple<String, String, QuestCategory, Int>>()

        when (difficulty) {
            QuestDifficulty.EASY -> {
                templates.add(Quadruple("Утренняя зарядка", "Сделай 10 минут лёгкой зарядки", QuestCategory.FITNESS, 1))
                templates.add(Quadruple("Стакан воды", "Выпей 6 стаканов воды за день", QuestCategory.HEALTH, 6))
                templates.add(Quadruple("Чтение", "Прочитай 5 страниц книги", QuestCategory.LEARNING, 5))
            }
            QuestDifficulty.NORMAL -> {
                templates.add(Quadruple("Прогулка", "Пройди 5000 шагов", QuestCategory.FITNESS, 5000))
                templates.add(Quadruple("Фокус-сессия", "Проведи 2 сессии по 25 минут", QuestCategory.PRODUCTIVITY, 2))
                templates.add(Quadruple("Медитация", "Медитируй 10 минут", QuestCategory.MINDFULNESS, 10))
            }
            QuestDifficulty.HARD -> {
                templates.add(Quadruple("Тренировка", "Выполни полноценную тренировку 30 минут", QuestCategory.FITNESS, 1))
                templates.add(Quadruple("Глубокая работа", "3 фокус-сессии без отвлечений", QuestCategory.PRODUCTIVITY, 3))
                templates.add(Quadruple("Обучение", "Изучай новое 45 минут", QuestCategory.LEARNING, 45))
            }
            else -> {
                templates.add(Quadruple("Элитная тренировка", "Тренировка высокой интенсивности 45 минут", QuestCategory.FITNESS, 1))
                templates.add(Quadruple("Мастер-фокус", "4 глубоких рабочих сессии", QuestCategory.PRODUCTIVITY, 4))
                templates.add(Quadruple("Эксперт дня", "Освой новый навык за день", QuestCategory.LEARNING, 1))
            }
        }

        return templates.take(3)
    }

    private fun getFallbackResponse(aiName: String, personality: AIPersonality): String {
        return when (personality) {
            AIPersonality.FRIENDLY -> "Привет! 😊 Кажется, у меня небольшие технические трудности. Но я здесь и готов помочь тебе!"
            AIPersonality.STRICT -> "Небольшой технический сбой. Но задания никто не отменял!"
            AIPersonality.BALANCED -> "Временные технические трудности. Продолжай работать над своими целями!"
        }
    }

    private fun getMidnightTimestamp(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
        cal.set(java.util.Calendar.MINUTE, 59)
        cal.set(java.util.Calendar.SECOND, 59)
        return cal.timeInMillis
    }

    // Helper data class
    data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}

enum class TimeOfDay { MORNING, AFTERNOON, EVENING, NIGHT }

data class AIResponse(
    val text: String,
    val emotion: AIEmotion,
    val action: AIAction?
)

data class AIAction(
    val type: String,
    val data: String?
)
