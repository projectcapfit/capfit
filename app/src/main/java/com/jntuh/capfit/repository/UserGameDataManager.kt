package com.jntuh.capfit.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jntuh.capfit.data.NotificationType
import com.jntuh.capfit.data.UserGameData
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserGameDataManager @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val db: FirebaseFirestore
) {

    private var cachedUserGameData: UserGameData? = null

    suspend fun getUserGameData(): UserGameData {
        val uid = firebaseAuth.currentUser?.uid ?: return UserGameData()
        cachedUserGameData?.let { return it }

        return try {
            val doc = db.collection("userGameData")
                .document(uid)
                .get()
                .await()

            Log.d("asasas", "UserGameDataManager data ${doc}")
            val data = doc.toObject(UserGameData::class.java) ?: UserGameData(uid = uid)
            cachedUserGameData = data

            Log.d("asasas", "UserGameDataManager data ${data}")
            data

        } catch (e: Exception) {
            e.printStackTrace()

            Log.d("asasas", "UserGameDataManager error part")
            UserGameData(uid = uid)
        }
    }

    suspend fun updateUserGameData(updatedData: UserGameData): Boolean {
        val uid = firebaseAuth.currentUser?.uid ?: return false

        return try {
            db.collection("userGameData")
                .document(uid)
                .set(updatedData, SetOptions.merge())
                .await()

            cachedUserGameData = updatedData
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun updateUserAchievements(ids: List<Int>) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val userData = getUserGameData()
        val updated = userData.copy(achievements = ids)

        try {
            db.collection("userGameData")
                .document(uid)
                .set(mapOf("achievements" to ids), SetOptions.merge())
                .await()

            cachedUserGameData = updated

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun updateDistance(distance: Int) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val userData = getUserGameData()

        val updated = userData.copy(highestDistanceCovered = distance)

        try {
            db.collection("userGameData")
                .document(uid)
                .set(mapOf("highestDistanceCovered" to distance), SetOptions.merge())
                .await()

            cachedUserGameData = updated

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun updateArea(area: Int) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val userData = getUserGameData()

        val updated = userData.copy(highestAreaCovered = area)

        try {
            db.collection("userGameData")
                .document(uid)
                .set(mapOf("highestAreaCovered" to area), SetOptions.merge())
                .await()

            cachedUserGameData = updated

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun addFriend(friendUid: String): Boolean {
        val uid = firebaseAuth.currentUser?.uid ?: return false
        val userData = getUserGameData()

        if (userData.friendsList.contains(friendUid)) return true

        return try {
            db.collection("userGameData")
                .document(uid)
                .update("friendsList", FieldValue.arrayUnion(friendUid))
                .await()

            cachedUserGameData = userData.copy(
                friendsList = userData.friendsList + friendUid
            )

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun removeFriend(friendUid: String): Boolean {
        val uid = firebaseAuth.currentUser?.uid ?: return false
        val userData = getUserGameData()

        if (!userData.friendsList.contains(friendUid)) return true

        return try {
            db.collection("userGameData")
                .document(uid)
                .update("friendsList", FieldValue.arrayRemove(friendUid))
                .await()

            cachedUserGameData = userData.copy(
                friendsList = userData.friendsList - friendUid
            )

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getFriendsList(): List<String> {
        return getUserGameData().friendsList
    }

    suspend fun updateFriendsList(list: List<String>) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val userData = getUserGameData()
        val updated = userData.copy(friendsList = list)

        try {
            db.collection("userGameData")
                .document(uid)
                .update("friendsList", list)
                .await()

            cachedUserGameData = updated

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun searchUsersByName(query: String): List<UserGameData> {
        return try {
            db.collection("userGameData")
                .whereGreaterThanOrEqualTo("userName", query)
                .whereLessThanOrEqualTo("userName", query + "\uf8ff")
                .orderBy("userName")
                .get()
                .await()
                .toObjects(UserGameData::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun loadFriends(): List<UserGameData> {

        val userData = getUserGameData()
        val resultList = mutableListOf<UserGameData>()

        for (chunk in userData.friendsList.chunked(10)) {

            val snap = db.collection("userGameData")
                .whereIn("uid", chunk)
                .get()
                .await()

//            Log.d("asasas", "Docs size = ${snap.size()}")
//            snap.documents.forEach {
//                Log.d("asasas", "DocId=${it.id} data=${it.data}")
//            }


            resultList.addAll(
                snap.toObjects(UserGameData::class.java)
            )
        }
        return resultList
    }

    suspend fun sendFriendRequestNotification(receiverUid: String) {

        val senderUid = firebaseAuth.currentUser?.uid ?: return

        Log.v("asasas", "Sending friend request to $receiverUid from userGameDataManager")

        try {

            // Prevent duplicate pending request
            val existing = db.collection("userGameData")
                .document(receiverUid)
                .collection("notifications")
                .whereEqualTo("fromUserId", senderUid)
                .whereEqualTo("type", NotificationType.FRIEND_REQUEST.name)
                .whereEqualTo("actionStatus", "PENDING")
                .get()
                .await()

            if (!existing.isEmpty) return

            val notifRef = db.collection("userGameData")
                .document(receiverUid)
                .collection("notifications")
                .document()

            Log.d("asasas",
                "Writing to: userGameData/$receiverUid/notifications/${notifRef.id}")
            val payload = hashMapOf(
                "id" to notifRef.id,
                "type" to NotificationType.FRIEND_REQUEST.name,
                "title" to "Friend Request",
                "message" to "sent you a friend request",
                "fromUserId" to senderUid,
                "timestamp" to System.currentTimeMillis(),
                "isRead" to false,
                "actionStatus" to "PENDING",

                "data" to mapOf(
                    "subtitle" to "${cachedUserGameData?.userName ?: "Someone"} wants to be your friend"
                )
            )
            notifRef.set(payload).await()

        }
        catch (e: Exception) {
            Log.d("asasas" , e.toString())
        }
    }

    // Called after every workout — updates streak based on lastWorkoutDate
    suspend fun updateStreakAfterWorkout(todayDate: String): Boolean {
        val uid = firebaseAuth.currentUser?.uid ?: return false
        val userData = getUserGameData()

        val newStreak = when (userData.lastWorkoutDate) {
            ""        -> 1                          // first ever workout
            todayDate -> userData.currentStreak     // already worked out today — no change
            getYesterday(todayDate) -> userData.currentStreak + 1  // consecutive day
            else      -> 1                          // gap > 1 day — reset
        }

        val updated = userData.copy(
            currentStreak   = newStreak,
            highestStreak   = maxOf(userData.highestStreak, newStreak),
            lastWorkoutDate = todayDate
        )

        return try {
            db.collection("userGameData")
                .document(uid)
                .set(mapOf(
                    "currentStreak"   to updated.currentStreak,
                    "highestStreak"   to updated.highestStreak,
                    "lastWorkoutDate" to updated.lastWorkoutDate
                ), SetOptions.merge())
                .await()
            cachedUserGameData = updated
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Called on app open — resets streak to 0 if user missed yesterday
    suspend fun checkAndResetStreakIfNeeded(todayDate: String) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val userData = getUserGameData()
        if (userData.lastWorkoutDate.isBlank()) return
        if (userData.currentStreak == 0) return

        // Streak is still alive if last workout was today or yesterday
        val stillAlive = userData.lastWorkoutDate == todayDate ||
                userData.lastWorkoutDate == getYesterday(todayDate)
        if (stillAlive) return

        // Gap detected — reset streak
        val updated = userData.copy(currentStreak = 0)
        try {
            db.collection("userGameData")
                .document(uid)
                .set(mapOf("currentStreak" to 0), SetOptions.merge())
                .await()
            cachedUserGameData = updated
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getYesterday(todayDate: String): String {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val cal = java.util.Calendar.getInstance()
            cal.time = sdf.parse(todayDate)!!
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
            sdf.format(cal.time)
        } catch (e: Exception) {
            ""
        }
    }

    fun clearCache() {
        cachedUserGameData = null
    }
}