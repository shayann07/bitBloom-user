package com.codingEmpire.bitbloom.viewModels


import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.codingEmpire.bitbloom.models.TopLeader
import com.codingEmpire.bitbloom.repos.LeaderboardRepo
import kotlinx.coroutines.launch

class LeaderboardViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = LeaderboardRepo(app)

    private val _direct = MutableLiveData<Double>()
    private val _team = MutableLiveData<Double>()
    private val _leaders = MutableLiveData<List<TopLeader>>()
    private val _loading = MutableLiveData(false)
    private val _error = MutableLiveData<String?>()

    val directBusiness: LiveData<Double> get() = _direct
    val teamBusiness: LiveData<Double> get() = _team
    val leaders: LiveData<List<TopLeader>> get() = _leaders
    val loading: LiveData<Boolean> get() = _loading
    val error: LiveData<String?> get() = _error

    fun loadAll() = viewModelScope.launch {
        _loading.value = true
        _error.value = null
        try {
            // 1) fetch business figures
            val (direct, team) = repo.fetchBusiness()
            _direct.value = direct
            _team.value = team

            // 2) fetch leaderboard
            _leaders.value = repo.fetchTopLeaders()
        } catch (e: Exception) {
            _error.value = e.message
        } finally {
            _loading.value = false
        }
    }
}