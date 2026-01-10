package com.jntuh.capfit.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jntuh.capfit.data.Achievement
import com.jntuh.capfit.data.AchievementCategory

@Dao
interface AchievementDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(achievements: List<Achievement>)

    @Query("SELECT * FROM achievements")
    suspend fun getAll(): List<Achievement>

    @Query("SELECT * FROM achievements WHERE category = :category")
    suspend fun getByCategory(category: AchievementCategory): List<Achievement>
}
