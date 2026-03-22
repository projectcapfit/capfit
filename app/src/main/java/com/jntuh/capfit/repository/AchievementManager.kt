package com.jntuh.capfit.repository

import com.jntuh.capfit.data.Achievement
import com.jntuh.capfit.data.AchievementCategory
import com.jntuh.capfit.data.AchievementsConfig

class AchievementManager {

    fun getAllAchievements(): List<Achievement> {
        return AchievementsConfig.achievements
    }

    // Converts "Xh Ym Zs" totalTimePlayed string to total minutes
    fun parseTotalMinutes(totalTimePlayed: String): Int {
        if (totalTimePlayed.isBlank()) return 0
        var totalSecs = 0L
        Regex("""(\d+)h""").find(totalTimePlayed)?.let { totalSecs += it.groupValues[1].toLong() * 3600 }
        Regex("""(\d+)m""").find(totalTimePlayed)?.let { totalSecs += it.groupValues[1].toLong() * 60 }
        Regex("""(\d+)s""").find(totalTimePlayed)?.let { totalSecs += it.groupValues[1].toLong() }
        return (totalSecs / 60).toInt()
    }

    fun updateAchievementProgress(
        baseList: List<Achievement>,
        distance: Int,
        area: Int,
        streak: Int,
        numberOfWorkouts: Int,
        activeTimeMinutes: Int
    ): List<Achievement> {
        return baseList.map { a ->
            val progress = when (a.category) {
                AchievementCategory.DISTANCE    -> distance
                AchievementCategory.AREA        -> area
                AchievementCategory.STREAK      -> streak
                AchievementCategory.WORKOUTS    -> numberOfWorkouts
                AchievementCategory.ACTIVE_TIME -> activeTimeMinutes
            }
            a.copy(
                currentProgress = progress,
                isUnlocked      = progress >= a.threshold
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