package com.esde.companion

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.esde.companion.art.LaunchBox.LaunchBoxDao
import com.esde.companion.art.LaunchBox.LaunchBoxGame
import com.esde.companion.art.LaunchBox.LaunchBoxImage
import com.esde.companion.art.MediaOverride
import com.esde.companion.art.MediaOverrideDao
import com.esde.companion.ost.loudness.LoudnessDao
import com.esde.companion.ost.loudness.LoudnessMetadata
import com.esde.companion.ui.ContentType

@Database(entities = [LoudnessMetadata::class, MediaOverride::class, LaunchBoxGame::class, LaunchBoxImage::class], version = 4, exportSchema = false)
@TypeConverters(AppConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun loudnessDao(): LoudnessDao
    abstract fun mediaOverrideDao(): MediaOverrideDao
    abstract fun launchBoxDao(): LaunchBoxDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "loudness_cache_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class AppConverters {
    @TypeConverter
    fun fromContentType(value: ContentType) = value.name

    @TypeConverter
    fun toContentType(value: String) = enumValueOf<ContentType>(value)

    @TypeConverter
    fun fromMediaSlot(slot: OverlayWidget.MediaSlot) = slot.index

    @TypeConverter
    fun toMediaSlot(value: Int) = OverlayWidget.MediaSlot.fromInt(value)
}