package com.jntuh.capfit.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "achievements")
data class Achievement(
    @PrimaryKey val id: Int,
    val title: String,
    val description: String,
    val icon: Int,                 // drawable resource ID
    val category: AchievementCategory,
    val targetValue: Int           // e.g., 5000 meters, 10 areas, etc.
)
