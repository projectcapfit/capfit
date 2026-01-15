package com.jntuh.capfit.data

data class Achievement(
    val id: Int,
    val title: String,
    val description: String,
    val category: AchievementCategory,
    val threshold: Int,
    val tier: String,
    val icon: Int,
    var isUnlocked: Boolean = false,
    var currentProgress: Int = 0
)
