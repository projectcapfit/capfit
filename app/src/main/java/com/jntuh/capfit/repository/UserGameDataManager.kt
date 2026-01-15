package com.jntuh.capfit.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jntuh.capfit.data.User
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

            val data = doc.toObject(UserGameData::class.java) ?: UserGameData(uid = uid)
            cachedUserGameData = data
            data

        } catch (e: Exception) {
            e.printStackTrace()
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

    suspend fun updateScore(score: Int) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val userData = getUserGameData()

        val updated = userData.copy(highestScore = score)

        try {
            db.collection("userGameData")
                .document(uid)
                .set(mapOf("highestScore" to score), SetOptions.merge())
                .await()

            cachedUserGameData = updated

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun updateStreak(streak: Int) {
        val uid = firebaseAuth.currentUser?.uid ?: return
        val userData = getUserGameData()

        val updated = userData.copy(currentStreak = streak)

        try {
            db.collection("userGameData")
                .document(uid)
                .set(mapOf("currentStreak" to streak), SetOptions.merge())
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
                .get()
                .await()
                .toObjects(UserGameData::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }



    fun clearCache() {
        cachedUserGameData = null
    }
}
