package com.jntuh.capfit.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jntuh.capfit.data.UserGameData
import com.jntuh.capfit.repository.UserGameDataManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val userGameDataManager: UserGameDataManager
) : ViewModel() {

    // Global or Friends tab
    enum class Tab { FRIENDS, GLOBAL }

    private val _tab = MutableLiveData(Tab.FRIENDS)
    val tab: LiveData<Tab> = _tab

    // Leaderboard: UserGameData + rank position
    private val _leaderboard = MutableLiveData<List<Pair<UserGameData, Int>>>()
    val leaderboard: LiveData<List<Pair<UserGameData, Int>>> = _leaderboard

    // Current user's rank + area
    private val _myRank = MutableLiveData<Pair<Int, Double>>()  // rank to area
    val myRank: LiveData<Pair<Int, Double>> = _myRank

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _emptyMessage = MutableLiveData("No data yet")
    val emptyMessage: LiveData<String> = _emptyMessage

    fun setTab(tab: Tab) {
        if (_tab.value == tab) return
        _tab.value = tab
        // Clear stale data immediately so old tab's list doesn't flash
        _leaderboard.value = emptyList()
        loadLeaderboard()
    }

    fun loadLeaderboard() {
        when (_tab.value) {
            Tab.GLOBAL  -> loadGlobal()
            Tab.FRIENDS -> loadFriends()
            else        -> loadFriends()
        }
    }

    private fun loadGlobal() {
        viewModelScope.launch {
            _loading.value = true
            _emptyMessage.value = "No data yet"
            try {
                val myUid = auth.currentUser?.uid ?: return@launch

                // Fetch top 50 users sorted by capturedArea descending
                val snap = db.collection("userGameData")
                    .orderBy("capturedArea", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(50)
                    .get().await()

                val users = snap.toObjects(UserGameData::class.java)
                    .filter { it.capturedArea > 0.0 }

                val ranked = users.mapIndexed { i, u -> u to (i + 1) }
                _leaderboard.value = ranked

                // Find my rank
                val myIndex = users.indexOfFirst { it.uid == myUid }
                if (myIndex != -1) {
                    _myRank.value = (myIndex + 1) to users[myIndex].capturedArea
                } else {
                    // I'm not in top 50 — fetch my own data separately
                    val myData = userGameDataManager.getUserGameData()
                    val totalAboveMe = db.collection("userGameData")
                        .whereGreaterThan("capturedArea", myData.capturedArea)
                        .get().await().size()
                    _myRank.value = (totalAboveMe + 1) to myData.capturedArea
                }

            } catch (e: Exception) {
                _leaderboard.value = emptyList()
            }
            _loading.value = false
        }
    }

    private fun loadFriends() {
        viewModelScope.launch {
            _loading.value = true
            _emptyMessage.value = "No friends yet — add friends to see their rankings!"
            try {
                val myUid = auth.currentUser?.uid ?: return@launch
                val myData = userGameDataManager.getUserGameData()
                val friendIds = myData.friendsList

                if (friendIds.isEmpty()) {
                    _leaderboard.value = emptyList()
                    _myRank.value = 1 to myData.capturedArea
                    _emptyMessage.value = "You have no friends yet"
                    _loading.value = false
                    return@launch
                }

                // Fetch friends' data in chunks of 10 (Firestore whereIn limit)
                val allUsers = mutableListOf<UserGameData>()
                for (chunk in friendIds.chunked(10)) {
                    val snap = db.collection("userGameData")
                        .whereIn("uid", chunk)
                        .get().await()
                    allUsers.addAll(snap.toObjects(UserGameData::class.java))
                }

                // Include myself in the friends leaderboard
                allUsers.add(myData)
                val sorted = allUsers
                    .sortedWith(
                        compareByDescending<UserGameData> { it.capturedArea }
                            .thenBy { it.userName.lowercase() }
                    )

                val ranked = sorted.mapIndexed { i, u -> u to (i + 1) }
                _leaderboard.value = ranked

                // My rank among friends
                val myIndex = sorted.indexOfFirst { it.uid == myUid }
                _myRank.value = if (myIndex != -1) (myIndex + 1) to myData.capturedArea
                else (sorted.size + 1) to myData.capturedArea

            } catch (e: Exception) {
                _leaderboard.value = emptyList()
            }
            _loading.value = false
        }
    }
}