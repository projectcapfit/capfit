package com.jntuh.capfit.repository

import android.os.Build
import androidx.annotation.RequiresApi
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jntuh.capfit.data.SeasonData
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeasonDataManager @Inject constructor(
    val firebaseAuth: FirebaseAuth,
    private val db: FirebaseFirestore
) {

    private var cachedSeasonList: MutableList<SeasonData>? = null

    private fun getUserSeasonDoc() =
        db.collection("userSeasons").document(firebaseAuth.currentUser!!.uid)

    suspend fun getAllSeasons(): List<SeasonData> {
        val user = firebaseAuth.currentUser ?: return emptyList()

        if (cachedSeasonList == null) {
            try {
                val doc = getUserSeasonDoc().get().await()

                if (doc.exists()) {
                    cachedSeasonList =
                        (doc.get("seasonList") as? List<Map<String, Any>>)
                            ?.map { map ->
                                SeasonData(
                                    uid = map["uid"] as? String ?: "",
                                    seasonYear = map["seasonYear"] as? String ?: "",
                                    seasonMonth = map["seasonMonth"] as? String ?: "",
                                    distanceCoveredInThisSeason = (map["distanceCoveredInThisSeason"] as? Long)?.toInt()
                                        ?: 0,
                                    areaCoveredInThisSeason = (map["areaCoveredInThisSeason"] as? Long)?.toInt()
                                        ?: 0,
                                    seasonScore = (map["seasonScore"] as? Long)?.toInt() ?: 0,
                                    seasonRank = (map["seasonRank"] as? Long)?.toInt() ?: -1,

                                    previousSeasons = null
                                )
                            }
                            ?.toMutableList() ?: mutableListOf()
                } else {
                    cachedSeasonList = mutableListOf()
                    getUserSeasonDoc().set(mapOf("seasonList" to cachedSeasonList!!)).await()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                cachedSeasonList = mutableListOf()
            }
        }

        return cachedSeasonList!!
    }

    suspend fun addSeason(season: SeasonData): Boolean {
        return try {
            val list = getAllSeasons().toMutableList()

            if (list.isNotEmpty()) {
                season.previousSeasons = list.toList()
            }

            list.add(season)
            cachedSeasonList = list
            getUserSeasonDoc().set(mapOf("seasonList" to list)).await()
            true

        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun updateSeason(updated: SeasonData): Boolean {
        return try {
            val list = getAllSeasons().toMutableList()

            val index = list.indexOfFirst {
                it.uid == updated.uid &&
                        it.seasonYear == updated.seasonYear &&
                        it.seasonMonth == updated.seasonMonth
            }

            if (index != -1) {
                list[index] = updated
                cachedSeasonList = list
                getUserSeasonDoc().set(mapOf("seasonList" to list)).await()

                return true
            }

            false

        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getOrCreateCurrentSeason(): SeasonData {
        val list = getAllSeasons().toMutableList()
        val now = LocalDate.now()

        val currentYear = now.year.toString()
        val currentMonth = now.monthValue.toString().padStart(2, '0')

        val latest = list.lastOrNull()

        // NO SEASON EXISTS → CREATE ONE
        if (latest == null) {
            val newSeason = SeasonData(uid = firebaseAuth.currentUser!!.uid)
            addSeason(newSeason)
            return newSeason
        }

        // SEASON EXISTS BUT MONTH CHANGED → CREATE NEW SEASON
        if (latest.seasonYear != currentYear || latest.seasonMonth != currentMonth) {

            val newSeason = SeasonData(uid = firebaseAuth.currentUser!!.uid)
            addSeason(newSeason)
            return newSeason
        }

        // CURRENT SEASON IS VALID
        return latest
    }

    fun clearCache() {
        cachedSeasonList = null
    }
}
