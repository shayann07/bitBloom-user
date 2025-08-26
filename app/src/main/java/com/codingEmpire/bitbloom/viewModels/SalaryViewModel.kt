package com.codingEmpire.bitbloom.viewModels


import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.codingEmpire.bitbloom.models.SALARY_LEVELS
import com.codingEmpire.bitbloom.models.SalaryData
import com.codingEmpire.bitbloom.models.SalaryLevel
import com.codingEmpire.bitbloom.repos.SalaryRepo
import com.codingEmpire.bitbloom.repos.TeamLevelRepo
import com.codingEmpire.bitbloom.utils.PrefService
import com.codingEmpire.bitbloom.utils.SoundManager
import kotlinx.coroutines.launch

class SalaryViewModel(app: Application) : AndroidViewModel(app) {
    private val pref = PrefService(app)
    private val salaryRepo = SalaryRepo(app)
    private val teamRepo = TeamLevelRepo(app)

    private val _salaryData = MutableLiveData<SalaryData>()
    private val _loading = MutableLiveData(false)
    private val _error = MutableLiveData<String?>()

    val salaryData: LiveData<SalaryData> = _salaryData
    val loading: LiveData<Boolean> = _loading
    val error: LiveData<String?> = _error

    private val _collectEnabled = MutableLiveData(false)
    val collectEnabled: LiveData<Boolean> = _collectEnabled

    private val _collectSuccess = MutableLiveData<Unit>()      // ①
    val collectSuccess: LiveData<Unit> = _collectSuccess

    private val _eligibleLevels = MutableLiveData<List<SalaryLevel>>(emptyList())
    val eligibleLevels: LiveData<List<SalaryLevel>> = _eligibleLevels

    fun loadSalary() {
        viewModelScope.launch {
            _loading.value = true; _error.value = null
            try {
                val balance = salaryRepo.getCurrentBalance()
                val selfInvest = salaryRepo.getSelfInvestSum()
                val levels = teamRepo.fetchTeamLevels()
                val direct = levels.firstOrNull()?.activeUsers ?: 0
                val indirect = levels.drop(1).sumOf { it.activeUsers }
                val paid = salaryRepo.getPaidLevels(pref.getUserId().orEmpty())

                // figure out which levels are *now* eligible and not yet paid
                val newlyEligible = SALARY_LEVELS.filter { lvl ->
                    lvl.level !in paid &&
                            selfInvest >= lvl.requiredSelf &&
                            direct >= lvl.requiredDirect &&
                            indirect >= lvl.requiredIndirect
                }

                _eligibleLevels.value = newlyEligible
                _collectEnabled.value = newlyEligible.isNotEmpty()

                _salaryData.value = SalaryData(balance, selfInvest, direct, indirect)

            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    /** Called from the fragment’s *Collect Salary* button */
    fun collect() = viewModelScope.launch {
        val userId = pref.getUserId() ?: return@launch
        val levels = _eligibleLevels.value ?: emptyList()
        if (levels.isEmpty()) return@launch

        _loading.value = true
        try {
            salaryRepo.collectLevels(userId, levels)
            SoundManager.playSuccess(getApplication())
            _collectSuccess.postValue(Unit)                    // ③ ⬅ trigger the event
            loadSalary()                                       // refresh UI
        } catch (e: Exception) {
            _error.value = e.message
        } finally {
            _loading.value = false
        }
    }
}