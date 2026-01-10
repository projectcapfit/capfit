package com.jntuh.capfit.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.jntuh.capfit.data.User
import com.jntuh.capfit.repository.UserManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor(
    private val userManager: UserManager,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _userState = MutableStateFlow<User?>(null)
    val userState: StateFlow<User?> = _userState

    init {
        loadUser()
    }

    suspend fun getUser(): User? {
        return withContext(Dispatchers.IO) {
            userManager.getCurrentUser()
        }
    }

    fun signOut() {
        userManager.clearUser()
    }

    suspend fun updateUser(updatedUser: User): Boolean {
        return withContext(Dispatchers.IO) {
            userManager.updateUser(updatedUser)
        }
    }
    fun loadUser() {
        viewModelScope.launch {
            var user = userManager.getCurrentUser()

            // 🔥 Retry if FirebaseAuth or Firestore is slow (OPPO fix)
            if (user == null) {
                kotlinx.coroutines.delay(500)
                user = userManager.getCurrentUser()
            }

            if (user == null) {
                kotlinx.coroutines.delay(500)
                user = userManager.getCurrentUser()
            }

            _userState.value = user
        }
    }

}
