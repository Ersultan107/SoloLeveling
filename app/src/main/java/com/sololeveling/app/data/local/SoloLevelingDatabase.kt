package com.sololeveling.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sololeveling.app.data.model.*

@Database(
    entities = [
        UserProfile::class,
        Quest::class,
        ChatMessage::class,
        SleepRecord::class,
        ActivityRecord::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class SoloLevelingDatabase : RoomDatabase() {

    abstract fun userProfileDao(): UserProfileDao
    abstract fun questDao(): QuestDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun sleepRecordDao(): SleepRecordDao
    abstract fun activityRecordDao(): ActivityRecordDao

    companion object {
        @Volatile private var INSTANCE: SoloLevelingDatabase? = null

        fun getDatabase(context: Context): SoloLevelingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SoloLevelingDatabase::class.java,
                    "solo_leveling_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
