package com.jntuh.capfit.data

object AchievementsConfig {

    val achievements = listOf(

        Achievement(1, "Distance Bronze", "Cover 1 km distance",
            AchievementCategory.DISTANCE, 1000, "Bronze", 0),

        Achievement(4, "Area Bronze", "Cover 500 sq.m",
            AchievementCategory.AREA, 500, "Bronze", 0),

        Achievement(7, "Score Bronze", "Earn 500 score",
            AchievementCategory.SCORE, 500, "Bronze", 0),

        Achievement(10, "Streak Bronze", "Maintain a 3-day streak",
            AchievementCategory.STREAK, 3, "Bronze", 0),


        Achievement(2, "Distance Silver", "Cover 5 km distance",
            AchievementCategory.DISTANCE, 5000, "Silver", 0),

        Achievement(5, "Area Silver", "Cover 3000 sq.m",
            AchievementCategory.AREA, 3000, "Silver", 0),

        Achievement(8, "Score Silver", "Earn 3000 score",
            AchievementCategory.SCORE, 3000, "Silver", 0),

        Achievement(11, "Streak Silver", "Maintain a 7-day streak",
            AchievementCategory.STREAK, 7, "Silver", 0),


        Achievement(3, "Distance Gold", "Cover 10 km distance",
            AchievementCategory.DISTANCE, 10000, "Gold", 0),

        Achievement(6, "Area Gold", "Cover 5000 sq.m",
            AchievementCategory.AREA, 5000, "Gold", 0),

        Achievement(9, "Score Gold", "Earn 8000 score",
            AchievementCategory.SCORE, 8000, "Gold", 0),

        Achievement(12, "Streak Gold", "Maintain a 15-day streak",
            AchievementCategory.STREAK, 15, "Gold", 0)
    )
}
