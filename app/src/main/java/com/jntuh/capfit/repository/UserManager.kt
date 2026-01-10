package com.jntuh.capfit.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jntuh.capfit.data.User
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

        if (cachedUser == null) {
            try {
                val docRef = db.collection("Users").document(currentUser.uid)
                val snapshot = docRef.get().await()

                if (snapshot.exists()) {
                    cachedUser = snapshot.toObject(User::class.java)
                } else {
                    val newUser = User(
                        uid = currentUser.uid,
                        name = null,
                        email = currentUser.email,
                        phone = null,
                        gender = null,
                        age = null,
                        weight = null,
                        height = null,
                        profile_picture = null
                    )
                    docRef.set(newUser).await()
                    cachedUser = newUser
                }
            } catch (e: Exception) {
                e.printStackTrace()
                cachedUser = null
            }
        }
        return cachedUser
    }


    fun clearUser() {
        cachedUser = null
        firebaseAuth.signOut()
    }

    suspend fun updateUser(){
        db.collection("Users").document(firebaseAuth.currentUser!!.uid).set(cachedUser!!)
        cachedUser = null
        getCurrentUser()
    }
}
