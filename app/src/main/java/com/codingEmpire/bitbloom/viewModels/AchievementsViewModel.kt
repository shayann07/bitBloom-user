package com.codingEmpire.bitbloom.viewModels


import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codingEmpire.bitbloom.models.AchievementLevel
import com.codingEmpire.bitbloom.repos.AchievementsRepository
import kotlinx.coroutines.launch

class AchievementsViewModel(
    private val repo: AchievementsRepository,
    private val userId: String
) : ViewModel() {

    private val _levels = MutableLiveData<List<AchievementLevel>>()
    val levels: LiveData<List<AchievementLevel>> = _levels

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _event = MutableLiveData<Event>()
    val event: LiveData<Event> = _event

    sealed interface Event {
        data class Snack(val msg: String) : Event
        object ShowCongrats : Event
    }

    /* ---------------------------------------------------- */

    fun load() = viewModelScope.launch {
        _loading.value = true
        _levels.value = repo.getAchievements(userId)
        _loading.value = false
    }

    fun collect(level: AchievementLevel) = viewModelScope.launch {
        if (level.isCollected || !level.isUnlocked) return@launch

        _loading.value = true
        val ok = repo.collectSalary(userId, level.salary, level.index)
        _loading.value = false

        if (ok) {
            level.isCollected = true
            _levels.value = _levels.value     // trigger re-bind
            _event.value = Event.ShowCongrats
        } else {
            _event.value = Event.Snack("Already collected or failed. Try again.")
        }
    }
}