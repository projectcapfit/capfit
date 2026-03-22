package com.jntuh.capfit.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jntuh.capfit.data.Achievement
import com.jntuh.capfit.repository.AchievementManager
import com.jntuh.capfit.repository.SeasonDataManager
import com.jntuh.capfit.repository.UserGameDataManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AchievementViewModel @Inject constructor(
    private val achievementManager: AchievementManager,
    private val userGameDataManager: UserGameDataManager,
    private val seasonDataManager: SeasonDataManager
) : ViewModel() {

    private val _achievements = MutableStateFlow<List<Achievement>>(emptyList())
    val achievements: StateFlow<List<Achievement>> = _achievements

    private val _newlyUnlocked = MutableStateFlow<List<Achievement>>(emptyList())
    val newlyUnlocked: StateFlow<List<Achievement>> = _newlyUnlocked

    init {
        loadAchievements()
    }

    fun loadAchievements() {
        viewModelScope.launch {
            val userData    = userGameDataManager.getUserGameData()
            val season      = seasonDataManager.getOrCreateCurrentSeason()
            val base        = achievementManager.getAllAchievements()
            val savedIds    = userData.achievements.toSet()
            val activeMinutes = achievementManager.parseTotalMinutes(season.totalTimePlayed)

            val updated = achievementManager.updateAchievementProgress(
                baseList         = base,
                distance         = userData.highestDistanceCovered,
                area             = userData.highestAreaCovered,
                streak           = userData.currentStreak,
                numberOfWorkouts = season.numberOfWorkouts,
                activeTimeMinutes = activeMinutes
            ).map { a ->
                // Always honour Firestore-saved unlocks (progress may have reset between seasons)
                if (savedIds.contains(a.id)) a.copy(isUnlocked = true) else a
            }

            Log.d("asasas", "Achievements loaded: ${updated.size}, unlocked: ${updated.count { it.isUnlocked }}")
            _achievements.value = updated
        }
    }

    fun checkAndUnlockAchievements() {
        viewModelScope.launch {
            val userData      = userGameDataManager.getUserGameData()
            val season        = seasonDataManager.getOrCreateCurrentSeason()
            val base          = achievementManager.getAllAchievements()
            val savedIds      = userData.achievements.toSet()
            val activeMinutes = achievementManager.parseTotalMinutes(season.totalTimePlayed)

            val oldList = base.map { a -> a.copy(isUnlocked = savedIds.contains(a.id)) }

            val updatedList = achievementManager.updateAchievementProgress(
                baseList          = base,
                distance          = userData.highestDistanceCovered,
                area              = userData.highestAreaCovered,
                streak            = userData.currentStreak,
                numberOfWorkouts  = season.numberOfWorkouts,
                activeTimeMinutes = activeMinutes
            ).map { a ->
                if (savedIds.contains(a.id)) a.copy(isUnlocked = true) else a
            }

            val newlyUnlocked = achievementManager.getNewlyUnlockedAchievements(oldList, updatedList)

            if (newlyUnlocked.isNotEmpty()) {
                Log.d("asasas", "Newly unlocked: ${newlyUnlocked.map { it.title }}")
                val allUnlockedIds = achievementManager.getUnlockedIds(updatedList)
                userGameDataManager.updateUserAchievements(allUnlockedIds)
                _newlyUnlocked.value = newlyUnlocked
            }

            _achievements.value = updatedList
        }
    }
}