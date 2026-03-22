package com.jntuh.capfit.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jntuh.capfit.data.User
import com.jntuh.capfit.data.UserGameData
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserManager @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val db: FirebaseFirestore
) {

    private var cachedUser: User? = null

    suspend fun getCurrentUser(): User? {

        val currentUser = firebaseAuth.currentUser ?: return null

        if (cachedUser != null)
            return cachedUser

        return try {

            val docRef = db.collection("users").document(currentUser.uid)
            val snapshot = docRef.get().await()

            cachedUser =
                if (snapshot.exists()) {
                    snapshot.toObject(User::class.java)
                } else {

                    val newUser = User(
                        uid = currentUser.uid,
                        name = currentUser.displayName ?: "Unknown",
                        email = currentUser.email,
                        phone = null,
                        gender = null,
                        age = null,
                        weight = null,
                        height = null,
                        profilePicture = null
                    )

                    docRef.set(newUser).await()
                    newUser
                }

            cachedUser

        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun ensureUserInitialized() {

        val uid = firebaseAuth.currentUser?.uid ?: return
        val userGameRef = db.collection("userGameData").document(uid)

        val snapshot = userGameRef.get().await()

        if (!snapshot.exists()) {
            // First time — create with displayName as initial userName
            userGameRef.set(
                UserGameData(
                    uid      = uid,
                    userName = firebaseAuth.currentUser?.displayName ?: "Unknown"
                )
            ).await()
        } else {
            // Doc already exists — only ensure uid is set, never touch userName
            userGameRef.set(
                mapOf("uid" to uid),
                SetOptions.merge()
            ).await()
        }

        // SeasonDataManager handles season creation automatically
    }

    suspend fun updateUser(updatedUser: User): Boolean {
        return try {

            val currentUser = firebaseAuth.currentUser ?: return false

            db.collection("users")
                .document(currentUser.uid)
                .set(updatedUser)
                .await()

            cachedUser = updatedUser
            true

        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun clearUser() {
        cachedUser = null
        firebaseAuth.signOut()
    }
}