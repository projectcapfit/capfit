package com.jntuh.capfit.viewmodel

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jntuh.capfit.data.SeasonData
import com.jntuh.capfit.repository.SeasonDataManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.O)
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
        autoLoad()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun autoLoad() {
        viewModelScope.launch {
            _loading.value = true
            try {
                // load all seasons
                val list = seasonManager.getAllSeasons()
                _seasons.value = list

                val latest = list.lastOrNull()

                val now = LocalDate.now()
                val currentYear = now.year.toString()
                val currentMonth = now.monthValue.toString().padStart(2, '0')

                if (
                    latest == null ||
                    latest.seasonYear != currentYear ||
                    latest.seasonMonth != currentMonth
                ) {
                    val uid = seasonManager.firebaseAuth.currentUser!!.uid
                    val newSeason = SeasonData(uid = uid)
                    seasonManager.addSeason(newSeason)

                    // reload list after adding
                    val updatedList = seasonManager.getAllSeasons()
                    _seasons.value = updatedList
                    _currentSeason.value = updatedList.lastOrNull()

                } else {
                    // Use existing latest season
                    _currentSeason.value = latest
                }

            } catch (e: Exception) {
                _error.value = e.message
            }
            _loading.value = false
        }
    }

    fun loadSeasons() {
        viewModelScope.launch {
            try {
                _loading.value = true

                val list = seasonManager.getAllSeasons()
                _seasons.value = list
                _currentSeason.value = list.lastOrNull()

            } catch (e: Exception) {
                _error.value = e.message
            }
            _loading.value = false
        }
    }

    fun addSeason(season: SeasonData) {
        viewModelScope.launch {
            _loading.value = true

            val success = seasonManager.addSeason(season)
            if (success) loadSeasons()
            else _error.value = "Failed to add season"

            _loading.value = false
        }
    }

    fun updateSeason(season: SeasonData) {
        viewModelScope.launch {
            _loading.value = true

            val success = seasonManager.updateSeason(season)
            if (success) loadSeasons()
            else _error.value = "Failed to update season"

            _loading.value = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun ensureCurrentSeason(uid: String) {
        autoLoad()
    }
}
