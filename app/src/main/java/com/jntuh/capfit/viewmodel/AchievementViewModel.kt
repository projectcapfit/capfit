package com.jntuh.capfit.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jntuh.capfit.data.Achievement
import com.jntuh.capfit.repository.AchievementManager
import com.jntuh.capfit.repository.UserGameDataManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AchievementViewModel @Inject constructor(
    private val achievementManager: AchievementManager,
    private val userGameDataManager: UserGameDataManager
) : ViewModel() {

    private val _achievements = MutableStateFlow<List<Achievement>>(emptyList())
    val achievements: StateFlow<List<Achievement>> = _achievements

    private val _newlyUnlocked = MutableStateFlow<List<Achievement>>(emptyList())
    val newlyUnlocked: StateFlow<List<Achievement>> = _newlyUnlocked

    init {
        Log.d("ACH_VM", "ViewModel created — loading achievements")
        loadInitialAchievements()
    }

    private fun loadInitialAchievements() {
        viewModelScope.launch {
            Log.d("ACH_VM", "Loading user game data…")
            val userData = userGameDataManager.getUserGameData()
            Log.d("ACH_VM", "UserData: $userData")

            val base = achievementManager.getAllAchievements()
            Log.d("ACH_VM", "Base Achievements Loaded = ${base.size}")

            val updated = achievementManager.updateAchievementProgress(
                base,
                userData.highestDistanceCovered,
                userData.highestAreaCovered,
                userData.highestScore,
                userData.currentStreak
            )

            Log.d("ACH_VM", "Updated achievements list size = ${updated.size}")
            updated.forEach {
                Log.d("ACH_VM", "ACH => ${it.title} | unlocked=${it.isUnlocked} | progress=${it.currentProgress}/${it.threshold}")
            }

            _achievements.value = updated
        }
    }
}
