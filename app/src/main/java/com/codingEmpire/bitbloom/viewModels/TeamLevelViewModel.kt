package com.codingEmpire.bitbloom.viewModels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.codingEmpire.bitbloom.models.TeamLevel
import com.codingEmpire.bitbloom.repos.TeamLevelRepo
import kotlinx.coroutines.launch

class TeamLevelViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TeamLevelRepo(app)

    private val _levels = MutableLiveData<List<TeamLevel>>()
    private val _loading = MutableLiveData(false)
    private val _error = MutableLiveData<String?>()

    val levels: LiveData<List<TeamLevel>> get() = _levels
    val loading: LiveData<Boolean> get() = _loading
    val error: LiveData<String?> get() = _error

    fun loadLevels() = viewModelScope.launch {
        _loading.value = true
        _error.value = null
        try {
            _levels.value = repo.fetchTeamLevels()
        } catch (e: Exception) {
            _error.value = e.message
        } finally {
            _loading.value = false
        }
    }
}