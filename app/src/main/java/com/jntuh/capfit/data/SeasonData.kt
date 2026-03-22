package com.jntuh.capfit.data

import java.util.Calendar

data class SeasonData(
    var uid: String = "",
    var seasonYear: String = "",
    var seasonMonth: String = "",

    var seasonScore: Int = 0,
    var seasonRank: Int = -1,

    var distanceCoveredInThisSeason: Int = 0,
    var areaCoveredInThisSeason: Int = 0,
    var totalTimePlayed : String = "",
    var numberOfWorkouts : Int = 0
) {

    constructor(uid: String) : this(
        uid = uid,
        seasonYear = getCurrentYear(),
        seasonMonth = getCurrentMonth()
    )

    companion object {

        private fun getCurrentYear(): String {
            return Calendar.getInstance().get(Calendar.YEAR).toString()
        }

        private fun getCurrentMonth(): String {
            val month = Calendar.getInstance().get(Calendar.MONTH) + 1
            return month.toString().padStart(2, '0')
        }
    }
}