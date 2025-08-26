package com.codingEmpire.bitbloom.viewModels

import android.app.Application
import androidx.lifecycle.*
import com.codingEmpire.bitbloom.models.TeamUser
import com.codingEmpire.bitbloom.repos.TeamLevelRepo
import kotlinx.coroutines.launch

class LevelUsersViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TeamLevelRepo(app)

    private val _users   = MutableLiveData<List<TeamUser>>()
    private val _loading = MutableLiveData(false)
    private val _error   = MutableLiveData<String?>()

    val users:   LiveData<List<TeamUser>> get() = _users
    val loading: LiveData<Boolean>        get() = _loading
    val error:   LiveData<String?>        get() = _error

    fun load(level: Int) = viewModelScope.launch {
        _loading.value = true; _error.value = null
        try   { _users.value = repo.fetchLevelUsers(level) }
        catch (e: Exception) { _error.value = e.message }
        finally { _loading.value = false }
    }
}