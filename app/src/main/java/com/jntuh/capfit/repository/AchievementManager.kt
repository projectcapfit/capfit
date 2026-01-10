package com.jntuh.capfit.repository

import com.jntuh.capfit.data.Achievement
import com.jntuh.capfit.room.AchievementProvider
import com.jntuh.capfit.data.UserGameData

class AchievementManager {

    fun all(): List<Achievement> = AchievementProvider.achievements

    fun getUnlocked(user: UserGameData): List<Achievement> {
        return all().filter { user.achievements.contains(it.id) }
    }

    fun getLocked(user: UserGameData): List<Achievement> {
        return all().filter { !user.achievements.contains(it.id) }
    }

    fun getProgressPercent(user: UserGameData): Int {
        val total = all().size
        val unlocked = getUnlocked(user).size
        return if (total == 0) 0 else (unlocked * 100 / total)
    }

    fun getProgressText(user: UserGameData): String {
        val total = all().size
        val unlocked = getUnlocked(user).size
        return "$unlocked of $total unlocked"
    }
}
