package com.jntuh.capfit.repository

import android.util.Log
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

    public var seasonChanged : Boolean = false

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
                // New season doc doesn't exist — this is either the user's very first season
                // or they skipped 1+ months. In both cases we reset territories so the
                // leaderboard starts fresh.
                seasonChanged = true
                val uid = firebaseAuth.currentUser!!.uid
                clearTerritoriesAndSessionState(uid)
                deleteOldNotifications()
                val newSeason = SeasonData(
                    uid = uid,
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

    suspend fun deleteOldNotifications() {
        try {
            val uid = firebaseAuth.currentUser?.uid ?: return

            val notificationsRef = db.collection("userGameData")
                .document(uid)
                .collection("notifications")

            val snapshot = notificationsRef
                .orderBy("timestamp")
                .limit(50)
                .get()
                .await()
            if (snapshot.isEmpty) return

            val batch = db.batch()
            var deleteCount = 0

            for (doc in snapshot.documents) {
                val isRead = doc.getBoolean("isRead") ?: false
                val status = doc.getString("actionStatus") ?: "PENDING"

                if (isRead || status != "PENDING") {
                    batch.delete(doc.reference)
                    deleteCount++
                }
            }

            if (deleteCount > 0) {
                batch.commit().await()
                Log.d("Notifications", "Deleted $deleteCount notifications")
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    /**
     * Problem 2: Called when a new season starts (including after skipped months).
     * Clears:
     *   1. All session docs belonging to the user (their captured territories)
     *   2. userSessionState — sessions[], capturedArea reset to 0
     *   3. userGameData.capturedArea reset to 0 (leaderboard fresh start)
     *
     * Runs in batches of 500 (Firestore batch limit) to handle users with many sessions.
     * If the user has no sessions this is a no-op (getAllSessionIds returns empty).
     */
    private suspend fun clearTerritoriesAndSessionState(uid: String) {
        try {
            // Step 1: fetch all session IDs for this user
            val sessionIds = mutableListOf<String>()
            val stateSnap = db.collection("users").document(uid)
                .collection("userSessionState").document("data")
                .get().await()
            if (stateSnap.exists()) {
                @Suppress("UNCHECKED_CAST")
                val ids = stateSnap.get("sessions") as? List<String> ?: emptyList()
                sessionIds.addAll(ids)
            }

            // Step 2: delete all session docs in chunks of 500
            sessionIds.chunked(500).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { sessionId ->
                    batch.delete(db.collection("sessions").document(sessionId))
                }
                batch.commit().await()
            }

            // Step 3: reset userSessionState (clear sessions[], capturedArea = 0)
            db.collection("users").document(uid)
                .collection("userSessionState").document("data")
                .set(mapOf(
                    "sessions"      to emptyList<String>(),
                    "groups"        to emptyList<String>(),
                    "isSessionLive" to false,
                    "isGroupLive"   to false
                ), com.google.firebase.firestore.SetOptions.merge())
                .await()

            // Step 4: reset capturedArea in userGameData to 0 (fresh leaderboard for new season)
            db.collection("userGameData").document(uid)
                .set(mapOf("capturedArea" to 0.0), com.google.firebase.firestore.SetOptions.merge())
                .await()


        } catch (e: Exception) {
            e.printStackTrace()
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