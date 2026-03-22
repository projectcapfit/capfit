package com.jntuh.capfit.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jntuh.capfit.data.SeasonData
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeasonDataManager @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val db: FirebaseFirestore
) {

    private var cachedSeasonList: MutableList<SeasonData>? = null

    private fun seasonsCollection() =
        db.collection("users")
            .document(firebaseAuth.currentUser!!.uid)
            .collection("seasons")

    private fun currentYear(): String =
        Calendar.getInstance().get(Calendar.YEAR).toString()

    private fun currentMonth(): String =
        (Calendar.getInstance().get(Calendar.MONTH) + 1)
            .toString()
            .padStart(2, '0')

    private fun seasonId(year: String, month: String) =
        "${year}_${month}"

    suspend fun getAllSeasons(): List<SeasonData> {

        if (cachedSeasonList != null)
            return cachedSeasonList!!

        return try {
            val snapshot = seasonsCollection()
                .orderBy("seasonYear", Query.Direction.DESCENDING)
                .orderBy("seasonMonth", Query.Direction.DESCENDING)
                .get()
                .await()

            val list = snapshot.documents.mapNotNull {
                it.toObject(SeasonData::class.java)
            }.toMutableList()

            cachedSeasonList = list
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun addSeason(season: SeasonData): Boolean {
        return try {

            val id = seasonId(season.seasonYear, season.seasonMonth)

            seasonsCollection()
                .document(id)
                .set(season)
                .await()

            cachedSeasonList?.add(0, season)
            true

        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun updateSeason(updated: SeasonData): Boolean {
        return try {

            val id = seasonId(updated.seasonYear, updated.seasonMonth)

            seasonsCollection()
                .document(id)
                .set(updated)
                .await()

            cachedSeasonList?.let { list ->
                val index = list.indexOfFirst {
                    it.seasonYear == updated.seasonYear &&
                            it.seasonMonth == updated.seasonMonth
                }
                if (index != -1) list[index] = updated
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getOrCreateCurrentSeason(): SeasonData {

        val year = currentYear()
        val month = currentMonth()
        val id = seasonId(year, month)

        // Check cache first — avoids Firestore read on every workout
        cachedSeasonList?.firstOrNull {
            it.seasonYear == year && it.seasonMonth == month
        }?.let { return it }

        return try {

            val doc = seasonsCollection()
                .document(id)
                .get()
                .await()

            if (doc.exists()) {
                doc.toObject(SeasonData::class.java)!!
            } else {
                val newSeason = SeasonData(
                    uid = firebaseAuth.currentUser!!.uid,
                    seasonYear = year,
                    seasonMonth = month
                )

                seasonsCollection()
                    .document(id)
                    .set(newSeason)
                    .await()

                cachedSeasonList?.add(0, newSeason)
                newSeason
            }

        } catch (e: Exception) {
            e.printStackTrace()

            SeasonData(
                uid = firebaseAuth.currentUser!!.uid,
                seasonYear = year,
                seasonMonth = month
            )
        }
    }

    // Called after every workout completes — increments all season stats
    suspend fun updateWorkoutStats(
        distanceM: Double,
        areaM2: Double,
        durationMs: Long
    ): Boolean {
        return try {
            val season = getOrCreateCurrentSeason()

            // Add new duration to existing totalTimePlayed
            val existingMs = parseDurationToMs(season.totalTimePlayed)
            val combinedMs = existingMs + durationMs
            val cs   = (combinedMs / 1000).toInt()
            val ch   = cs / 3600
            val cm   = (cs % 3600) / 60
            val csec = cs % 60
            val combinedStr = when {
                ch   > 0 -> "${ch}h ${cm}m ${csec}s"
                cm   > 0 -> "${cm}m ${csec}s"
                else     -> "${csec}s"
            }

            val updated = season.copy(
                distanceCoveredInThisSeason = season.distanceCoveredInThisSeason + distanceM.toInt(),
                areaCoveredInThisSeason     = season.areaCoveredInThisSeason + areaM2.toInt(),
                totalTimePlayed             = combinedStr,
                numberOfWorkouts            = season.numberOfWorkouts + 1
            )

            updateSeason(updated)

        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Parses "Xh Ym Zs" / "Ym Zs" / "Zs" / "" back to milliseconds
    private fun parseDurationToMs(duration: String): Long {
        if (duration.isBlank()) return 0L
        var totalSecs = 0L
        val hMatch = Regex("""(\d+)h""").find(duration)
        val mMatch = Regex("""(\d+)m""").find(duration)
        val sMatch = Regex("""(\d+)s""").find(duration)
        hMatch?.let { totalSecs += it.groupValues[1].toLong() * 3600 }
        mMatch?.let { totalSecs += it.groupValues[1].toLong() * 60 }
        sMatch?.let { totalSecs += it.groupValues[1].toLong() }
        return totalSecs * 1000
    }

    fun clearCache() {
        cachedSeasonList = null
    }
}