package com.jntuh.capfit.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jntuh.capfit.data.SeasonData
import com.jntuh.capfit.repository.SeasonDataManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SeasonViewModel @Inject constructor(
    private val seasonManager: SeasonDataManager
) : ViewModel() {

    private val _seasons = MutableStateFlow<List<SeasonData>>(emptyList())
    val seasons: StateFlow<List<SeasonData>> = _seasons

    private val _currentSeason = MutableStateFlow<SeasonData?>(null)
    val currentSeason: StateFlow<SeasonData?> = _currentSeason

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _loading.value = true

            try {
                val current = seasonManager.getOrCreateCurrentSeason()
                _currentSeason.value = current

                val list = seasonManager.getAllSeasons()
                _seasons.value = list

            } catch (e: Exception) {
                _error.value = e.message
            }

            _loading.value = false
        }
    }

    fun seasonChange(): Boolean {
        if (seasonManager.seasonChanged){
            seasonManager.seasonChanged = false
            return true
        }
        return false

    }

    fun refreshSeasons() {
        viewModelScope.launch {
            _loading.value = true

            try {
                val list = seasonManager.getAllSeasons()
                _seasons.value = list
                _currentSeason.value = list.firstOrNull()
            } catch (e: Exception) {
                _error.value = e.message
            }

            _loading.value = false
        }
    }

    fun updateSeason(season: SeasonData) {
        viewModelScope.launch {
            _loading.value = true

            val success = seasonManager.updateSeason(season)

            if (success) {
                refreshSeasons()
            } else {
                _error.value = "Failed to update season"
            }

            _loading.value = false
        }
    }

    fun refreshCurrentSeason() {
        viewModelScope.launch {
            try {
                seasonManager.clearCache()
                val current = seasonManager.getOrCreateCurrentSeason()
                _currentSeason.value = current
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun updateCurrentSeasonRank(rank: Int) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null

            val current = _currentSeason.value
            if (current != null) {
                val updatedSeason = current.copy(seasonRank = rank)
                val success = seasonManager.updateSeason(updatedSeason)

                if (success) {
                    _currentSeason.value = updatedSeason
                } else {
                    _error.value = "Failed to update current season's rank"
                }
            } else {
                _error.value = "No current season available to update rank."
            }
            _loading.value = false
        }
    }

    fun clearCache() {
        seasonManager.clearCache()
    }
}