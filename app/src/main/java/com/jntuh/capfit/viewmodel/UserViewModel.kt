package com.jntuh.capfit.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jntuh.capfit.repository.UserManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor(
    private val userManager: UserManager
) : ViewModel() {


    fun getUser() {
        viewModelScope.launch {
            userManager.getCurrentUser()
        }
    }

    fun signOut(){
        userManager.clearUser()
    }

    fun updateUser(){
        viewModelScope.launch {
            userManager.updateUser()
        }
    }
}