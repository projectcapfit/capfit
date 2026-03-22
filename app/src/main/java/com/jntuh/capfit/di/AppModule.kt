package com.jntuh.capfit.di

import android.content.Context
import android.content.SharedPreferences
import com.jntuh.capfit.repository.AchievementManager
import com.jntuh.capfit.repository.UserGameDataManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSharedPreferences( @ApplicationContext app : Context) =
        app.getSharedPreferences("capfit", Context.MODE_PRIVATE)

    @Provides
    fun provideSharedPreferencesEditor(sharedPreferences: SharedPreferences) =
        sharedPreferences.edit()


    @Provides
    @Singleton
    fun provideAchievementManager(): AchievementManager {
        return AchievementManager()
    }

}

