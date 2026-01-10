package com.jntuh.capfit.data

import android.os.Build
import androidx.annotation.RequiresApi
import java.time.LocalDate

data class SeasonData(
    var uid : String = "",
    var seasonYear : String = "",
    var seasonMonth : String = "",

    var distanceCoveredInThisSeason : Int = 0,
    var areaCoveredInThisSeason : Int = 0,
    var seasonScore : Int = 0,
    var seasonRank : Int = -1,

    var previousSeasons : List<SeasonData>? = null
) {

    @RequiresApi(Build.VERSION_CODES.O)
    constructor(uid: String) : this(
        uid = uid,
        seasonYear = LocalDate.now().year.toString(),
        seasonMonth = LocalDate.now().monthValue.toString().padStart(2, '0'),
        distanceCoveredInThisSeason = 0,
        areaCoveredInThisSeason = 0,
        seasonScore = 0,
        seasonRank = -1,
        previousSeasons = null
    )
}
