package com.jntuh.capfit.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jntuh.capfit.data.*
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserGameDataManager @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val db: FirebaseFirestore
) {

    private var cachedUserGameData: UserGameData? = null

    private fun getUserDocRef() =
        db.collection("userGameData").document(firebaseAuth.currentUser!!.uid)

    suspend fun getUserGameData(): UserGameData? {
        val user = firebaseAuth.currentUser ?: return null
        Log.d("GameDataManager", "CurrentUser = ${firebaseAuth.currentUser}")

        if (cachedUserGameData == null) {
            try {
                val docRef = getUserDocRef()
                val snapshot = docRef.get().await()

                cachedUserGameData = if (snapshot.exists()) {
                    snapshot.toObject(UserGameData::class.java)
                } else {
                    // Create new document for user
                    val newData = UserGameData(
                        uid = user.uid,
                        userName = user.displayName ?: "Unknown"
                    )
                    docRef.set(newData).await()
                    newData
                }

            } catch (e: Exception) {
                e.printStackTrace()
                cachedUserGameData = null
            }
        }

        return cachedUserGameData
    }

    suspend fun updateUserGameData(updated: UserGameData): Boolean {
        return try {
            getUserDocRef().set(updated).await()
            cachedUserGameData = updated
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun updateAchievementProgress(id: Int, value: Int): Boolean {
        return try {
            val fieldPath = "achievementProgress.$id"

            getUserDocRef().update(fieldPath, value).await()

            val currentProgress =
                cachedUserGameData?.achievementProgress?.toMutableMap() ?: mutableMapOf()
            currentProgress[id] = value

            cachedUserGameData =
                cachedUserGameData?.copy(achievementProgress = currentProgress)

            true

        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun addFriend(friendUid: String): Boolean {
        return try {
            getUserDocRef().set(
                mapOf("friendsList" to FieldValue.arrayUnion(friendUid)),
                SetOptions.merge()
            ).await()

            cachedUserGameData?.friendsList =
                cachedUserGameData?.friendsList?.plus(friendUid) ?: listOf(friendUid)

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun removeFriend(friendUid: String): Boolean {
        return try {
            getUserDocRef().set(
                mapOf("friendsList" to FieldValue.arrayRemove(friendUid)),
                SetOptions.merge()
            ).await()

            cachedUserGameData?.friendsList =
                cachedUserGameData?.friendsList?.filterNot { it == friendUid }
                    ?: emptyList()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun searchUsersByName(query: String): List<UserGameData> {
        return try {
            if (query.isBlank()) return emptyList()

            val end = query + "~"

            db.collection("userGameData")
                .whereGreaterThanOrEqualTo("userName", query)
                .whereLessThanOrEqualTo("userName", end)
                .limit(15)
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
