package com.esde.companion

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.esde.companion.art.LaunchBox.LaunchBoxDao
import com.esde.companion.art.LaunchBox.LaunchBoxGame
import com.esde.companion.art.LaunchBox.LaunchBoxImage
import com.esde.companion.art.mediaoverride.MediaOverride
import com.esde.companion.art.mediaoverride.MediaOverrideDao
import com.esde.companion.metadata.ESGameEntity
import com.esde.companion.metadata.GameDao
import com.esde.companion.metadata.SyncDao
import com.esde.companion.metadata.SyncLog
import com.esde.companion.ost.loudness.LoudnessDao
import com.esde.companion.ost.loudness.LoudnessMetadata
import com.esde.companion.ui.ContentType

@Database(entities = [LoudnessMetadata::class, MediaOverride::class, LaunchBoxGame::class, LaunchBoxImage::class, ESGameEntity::class, SyncLog::class], version = 6, exportSchema = false)
@TypeConverters(AppConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun loudnessDao(): LoudnessDao
    abstract fun mediaOverrideDao(): MediaOverrideDao
    abstract fun launchBoxDao(): LaunchBoxDao

    abstract fun syncDao(): SyncDao
    abstract fun gameDao(): GameDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
            CREATE TABLE IF NOT EXISTS es_games (
                romPath TEXT NOT NULL,
                system TEXT NOT NULL,
                name TEXT NOT NULL,
                description TEXT,
                developer TEXT,
                publisher TEXT,
                genre TEXT,
                releaseDate TEXT,
                PRIMARY KEY(romPath, system)
            )
        """.trimIndent())

                db.execSQL("""
            CREATE TABLE IF NOT EXISTS sync_log (
                system TEXT NOT NULL PRIMARY KEY,
                lastModified INTEGER NOT NULL
            )
        """.trimIndent())
            }
        }

        private fun resolveDatabaseName(context: Context): String {
            val mainDbName = "loudness_cache_db"
            val debugDbName = "companion_debug_db"

            if (BuildConfig.DEBUG) {
                val mainFile = context.getDatabasePath(mainDbName)
                val debugFile = context.getDatabasePath(debugDbName)

                if (!debugFile.exists() && mainFile.exists()) {
                    try {
                        mainFile.copyTo(debugFile, overwrite = true)
                        Log.d("DB_SETUP", "Cloned production database for safe debugging.")
                    } catch (e: Exception) {
                        Log.e("DB_SETUP", "Failed to clone database", e)
                    }
                }
                return debugDbName
            }
            return mainDbName
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val dbName = resolveDatabaseName(context)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    dbName
                )
                    .addMigrations(MIGRATION_5_6)
                    .apply {
                        if (BuildConfig.DEBUG) fallbackToDestructiveMigration()
                    }
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