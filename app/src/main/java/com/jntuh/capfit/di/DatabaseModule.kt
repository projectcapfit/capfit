package com.jntuh.capfit.di

import android.content.Context
import com.jntuh.capfit.room.AppDatabase
import com.jntuh.capfit.room.AchievementDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideAchievementDao(db: AppDatabase): AchievementDao {
        return db.achievementDao()
    }
}
