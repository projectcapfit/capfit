package com.jntuh.capfit.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.jntuh.capfit.data.Achievement
import com.jntuh.capfit.data.Converters

@Database(
    entities = [Achievement::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun achievementDao(): AchievementDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "capfit.db"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)

                            // Pre-populate achievements
                            CoroutineScope(Dispatchers.IO).launch {
                                val dao = getInstance(context).achievementDao()
                                dao.insertAll(AchievementProvider.achievements)
                            }
                        }
                    })
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}
