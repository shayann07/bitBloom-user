package com.codingEmpire.bitbloom.viewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codingEmpire.bitbloom.repos.TeamLevelRepo
import com.codingEmpire.bitbloom.repos.WithdrawRepo
import com.codingEmpire.bitbloom.utils.WithdrawResult
import kotlinx.coroutines.launch

class WithdrawViewModel(
    private val repo: WithdrawRepo,
    private val teamRepo: TeamLevelRepo
) : ViewModel() {

    val withdrawalStatus = MutableLiveData<WithdrawResult>()
    val withdrawHistory = MutableLiveData<List<Map<String, Any>>?>()

    private val _eligibility = MutableLiveData<Boolean>()
    val eligibility: LiveData<Boolean> = _eligibility

    val isLoading = MutableLiveData<Boolean>()

    fun submitWithdrawal(userId: String, amount: Double, walletAddress: String) {
        isLoading.value = true
        viewModelScope.launch {
            val result = repo.submitWithdrawalRequest(userId, amount, walletAddress)
            withdrawalStatus.value = result
            isLoading.value = false
        }
    }

    fun loadWithdrawHistory(userId: String) {
        isLoading.value = true
        viewModelScope.launch {
            val result = repo.getWithdraws(userId)
            withdrawHistory.value = result
            isLoading.value = false
        }
    }

    fun refundIfRejected(requestId: String) {
        isLoading.value = true
        viewModelScope.launch {
            val result = repo.refundRejectedWithdrawal(requestId)
            withdrawalStatus.value = result
            isLoading.value = false
        }
    }

    fun cancelWithdrawal(requestId: String) {
        isLoading.value = true
        viewModelScope.launch {
            val result = repo.cancelWithdrawal(requestId)
            withdrawalStatus.value = result
            isLoading.value = false
        }
    }

    fun checkWithdrawEligibility() = viewModelScope.launch {
        val ok = teamRepo.canUserWithdraw()
        _eligibility.postValue(ok)
    }
}
