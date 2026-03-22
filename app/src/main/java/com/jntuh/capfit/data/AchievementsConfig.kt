package com.jntuh.capfit.data

object AchievementsConfig {

    val achievements = listOf(

        // ── Distance (metres) ─────────────────────────────────────────────────
        Achievement(1,  "First Steps",     "Cover 1 km total distance",
            AchievementCategory.DISTANCE, 1000,  "Bronze", 0),
        Achievement(2,  "Road Runner",     "Cover 5 km total distance",
            AchievementCategory.DISTANCE, 5000,  "Silver", 0),
        Achievement(3,  "Marathon Walker", "Cover 10 km total distance",
            AchievementCategory.DISTANCE, 10000, "Gold",   0),

        // ── Area (square metres) ─────────────────────────────────────────────
        Achievement(4,  "Territory Rookie",  "Capture 500 m²",
            AchievementCategory.AREA, 500,   "Bronze", 0),
        Achievement(5,  "Land Grabber",      "Capture 3000 m²",
            AchievementCategory.AREA, 3000,  "Silver", 0),
        Achievement(6,  "Zone Dominator",    "Capture 10000 m²",
            AchievementCategory.AREA, 10000, "Gold",   0),

        // ── Streak (days) ─────────────────────────────────────────────────────
        Achievement(7,  "On a Roll",       "Maintain a 3-day streak",
            AchievementCategory.STREAK, 3,  "Bronze", 0),
        Achievement(8,  "Week Warrior",    "Maintain a 7-day streak",
            AchievementCategory.STREAK, 7,  "Silver", 0),
        Achievement(9,  "Unstoppable",     "Maintain a 15-day streak",
            AchievementCategory.STREAK, 15, "Gold",   0),

        // ── Number of Workouts ────────────────────────────────────────────────
        Achievement(10, "Just Started",    "Complete your first workout",
            AchievementCategory.WORKOUTS, 1,  "Bronze", 0),
        Achievement(11, "Getting Serious", "Complete 10 workouts",
            AchievementCategory.WORKOUTS, 10, "Silver", 0),
        Achievement(12, "Dedicated",       "Complete 25 workouts",
            AchievementCategory.WORKOUTS, 25, "Gold",   0),

        // ── Active Time (minutes) ─────────────────────────────────────────────
        Achievement(13, "Warm Up",         "Stay active for 30 minutes total",
            AchievementCategory.ACTIVE_TIME, 30,  "Bronze", 0),
        Achievement(14, "In The Zone",     "Stay active for 2 hours total",
            AchievementCategory.ACTIVE_TIME, 120, "Silver", 0),
        Achievement(15, "Iron Legs",       "Stay active for 5 hours total",
            AchievementCategory.ACTIVE_TIME, 300, "Gold",   0)
    )
}