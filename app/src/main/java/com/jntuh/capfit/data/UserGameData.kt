package com.jntuh.capfit.data

data class UserGameData(
    var uid: String = "",
    var userName: String = "",

    // Summary stats
    var highestDistanceCovered: Int = 0,
    var highestAreaCovered: Int = 0,
    var highestScore: Int = 0,

    // Achievements (list of unlocked achievement IDs)
    var achievements: List<Int> = emptyList(),

    // Progress map → stores incremental progress values
    // Example: {201 = 12} means user has captured 12/20 areas
    var achievementProgress: Map<Int, Int> = emptyMap(),

    // Social
    var friendsList: List<String> = emptyList()
)
