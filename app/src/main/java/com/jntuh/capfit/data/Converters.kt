package com.jntuh.capfit.data

import androidx.room.TypeConverter

class Converters {

    @TypeConverter
    fun fromCategory(value: AchievementCategory): String {
        return value.name
    }

    @TypeConverter
    fun toCategory(value: String): AchievementCategory {
        return AchievementCategory.valueOf(value)
    }
}
