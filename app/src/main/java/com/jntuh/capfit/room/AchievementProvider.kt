package com.jntuh.capfit.room

import com.jntuh.capfit.R
import com.jntuh.capfit.data.Achievement
import com.jntuh.capfit.data.AchievementCategory

object AchievementProvider {

    val achievements = listOf(

        // -------------------------
        // DISTANCE ACHIEVEMENTS
        // -------------------------
        Achievement(
            id = 101,
            title = "Run 5 km",
            description = "Complete a total of 5 km",
            icon = R.drawable.ic_run,
            category = AchievementCategory.DISTANCE,
            targetValue = 5000
        ),
        Achievement(
            id = 102,
            title = "Run 10 km",
            description = "Complete a total of 10 km",
            icon = R.drawable.ic_run,
            category = AchievementCategory.DISTANCE,
            targetValue = 10000
        ),
        Achievement(
            id = 103,
            title = "Run 21 km",
            description = "Complete a total of 21 km",
            icon = R.drawable.ic_sprint,
            category = AchievementCategory.DISTANCE,
            targetValue = 21000
        ),

        // -------------------------
        // AREA ACHIEVEMENTS
        // -------------------------
        Achievement(
            id = 201,
            title = "Capture 3 Areas",
            description = "Capture a total of 3 map areas",
            icon = R.drawable.ic_capture_small,
            category = AchievementCategory.AREA,
            targetValue = 3
        ),
        Achievement(
            id = 202,
            title = "Capture 10 Areas",
            description = "Capture a total of 10 map areas",
            icon = R.drawable.ic_capture_many,
            category = AchievementCategory.AREA,
            targetValue = 10
        ),

        // -------------------------
        // MILESTONE ACHIEVEMENTS
        // -------------------------
        Achievement(
            id = 301,
            title = "First Activity",
            description = "Complete your first activity",
            icon = R.drawable.ic_dumble,
            category = AchievementCategory.MILESTONE,
            targetValue = 1
        )
    )
}
