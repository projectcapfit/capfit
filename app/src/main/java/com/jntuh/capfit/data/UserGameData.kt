package com.jntuh.capfit.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class UserGameData(
    var uid: String = "",
    var userName: String = "",
    var favoriteColor: String = "#3F51B5",

    var highestDistanceCovered: Int = 0,
    var highestAreaCovered: Int = 0,
    var highestStreak: Int = 0,

    var currentStreak: Int = 0,
    var lastWorkoutDate: String = "",

    var capturedArea: Double = 0.0,

    var achievements: List<Int> = emptyList(),
    var achievementProgress: Map<String, Int> = emptyMap(),
    var friendsList: List<String> = emptyList()
) : Parcelable