package com.sololeveling.app.data.local

import androidx.room.TypeConverter
import com.sololeveling.app.data.model.*

class Converters {
    @TypeConverter fun fromRank(rank: Rank) = rank.name
    @TypeConverter fun toRank(name: String) = Rank.valueOf(name)

    @TypeConverter fun fromAIPersonality(p: AIPersonality) = p.name
    @TypeConverter fun toAIPersonality(name: String) = AIPersonality.valueOf(name)

    @TypeConverter fun fromQuestType(t: QuestType) = t.name
    @TypeConverter fun toQuestType(name: String) = QuestType.valueOf(name)

    @TypeConverter fun fromQuestDifficulty(d: QuestDifficulty) = d.name
    @TypeConverter fun toQuestDifficulty(name: String) = QuestDifficulty.valueOf(name)

    @TypeConverter fun fromQuestStatus(s: QuestStatus) = s.name
    @TypeConverter fun toQuestStatus(name: String) = QuestStatus.valueOf(name)

    @TypeConverter fun fromQuestCategory(c: QuestCategory) = c.name
    @TypeConverter fun toQuestCategory(name: String) = QuestCategory.valueOf(name)

    @TypeConverter fun fromMessageRole(r: MessageRole) = r.name
    @TypeConverter fun toMessageRole(name: String) = MessageRole.valueOf(name)

    @TypeConverter fun fromAIEmotion(e: AIEmotion) = e.name
    @TypeConverter fun toAIEmotion(name: String) = AIEmotion.valueOf(name)

    @TypeConverter fun fromMessageType(t: MessageType) = t.name
    @TypeConverter fun toMessageType(name: String) = MessageType.valueOf(name)
}
