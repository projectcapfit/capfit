package com.jntuh.capfit.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jntuh.capfit.data.UserGameData
import com.jntuh.capfit.data.Trackings
import com.jntuh.capfit.repository.UserGameDataManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserGameDataViewModel @Inject constructor(
    private val userGameDataManager: UserGameDataManager
) : ViewModel() {

    private val _userGameData = MutableStateFlow<UserGameData?>(null)
    val userGameData: StateFlow<UserGameData?> = _userGameData

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _searchResults = MutableStateFlow<List<UserGameData>>(emptyList())
    val searchResults: StateFlow<List<UserGameData>> = _searchResults

    init {
        loadUserGameData()
    }

    fun loadUserGameData() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val data = userGameDataManager.getUserGameData()
                _userGameData.value = data
            } catch (e: Exception) {
                _error.value = e.message
            }
            _loading.value = false
        }
    }

    fun updateUserGameData(updated: UserGameData) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val success = userGameDataManager.updateUserGameData(updated)
                if (success) {
                    _userGameData.value = updated
                } else {
                    _error.value = "Failed to update user game data"
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
            _loading.value = false
        }
    }

    fun addFriend(friendUid: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val success = userGameDataManager.addFriend(friendUid)
                if (success) {
                    val updatedList =
                        _userGameData.value?.friendsList?.plus(friendUid)
                            ?: listOf(friendUid)

                    _userGameData.value =
                        _userGameData.value?.copy(friendsList = updatedList)
                } else {
                    _error.value = "Unable to add friend"
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
            _loading.value = false
        }
    }

    fun removeFriend(friendUid: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val success = userGameDataManager.removeFriend(friendUid)
                if (success) {
                    val updatedList =
                        _userGameData.value?.friendsList?.filterNot { it == friendUid }
                            ?: emptyList()

                    _userGameData.value =
                        _userGameData.value?.copy(friendsList = updatedList)
                } else {
                    _error.value = "Unable to remove friend"
                }
            } catch (e: Exception) {
                _error.value = e.message
            }
            _loading.value = false
        }
    }

    fun searchUsers(query: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val results = userGameDataManager.searchUsersByName(query)
                _searchResults.value = results
            } catch (e: Exception) {
                _error.value = e.message
            }
            _loading.value = false
        }
    }

    fun clearCache() {
        userGameDataManager.clearCache()
    }
}
