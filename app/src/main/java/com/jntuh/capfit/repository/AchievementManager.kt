package com.jntuh.capfit.repository

import com.jntuh.capfit.data.Achievement
import com.jntuh.capfit.data.AchievementCategory
import com.jntuh.capfit.data.AchievementsConfig

class AchievementManager {

    fun getAllAchievements(): List<Achievement> {
        return AchievementsConfig.achievements
    }

    fun updateAchievementProgress(
        baseList: List<Achievement>,
        distance: Int,
        area: Int,
        score: Int,
        streak: Int
    ): List<Achievement> {

        return baseList.map { a ->
            val progress = when (a.category) {
                AchievementCategory.DISTANCE -> distance
                AchievementCategory.AREA -> area
                AchievementCategory.SCORE -> score
                AchievementCategory.STREAK -> streak
            }

            a.copy(
                currentProgress = progress,
                isUnlocked = progress >= a.threshold
            )
        }
    }

    fun getNewlyUnlockedAchievements(
        old: List<Achievement>,
        updated: List<Achievement>
    ): List<Achievement> {
        return updated.filter { u ->
            u.isUnlocked && (old.find { it.id == u.id }?.isUnlocked == false)
        }
    }

    fun getUnlockedIds(list: List<Achievement>): List<Int> {
        return list.filter { it.isUnlocked }.map { it.id }
    }
}
