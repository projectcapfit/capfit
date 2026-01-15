package com.jntuh.capfit.data

data class UserGameData(
    var uid: String = "",
    var userName: String = "",
    var highestDistanceCovered: Int = 0,
    var highestAreaCovered: Int = 0,
    var highestScore: Int = 0,
    var currentStreak: Int = 0,
    var achievements: List<Int> = emptyList(),
    var achievementProgress: Map<Int, Int> = emptyMap(),
    var friendsList: List<String> = emptyList()
)
