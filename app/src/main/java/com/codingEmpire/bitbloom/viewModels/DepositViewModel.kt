package com.codingEmpire.bitbloom.viewModels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codingEmpire.bitbloom.repos.DepositRepo
import com.codingEmpire.bitbloom.utils.Status
import kotlinx.coroutines.launch

class DepositViewModel(
    private val repo: DepositRepo
) : ViewModel() {
    val deposits = MutableLiveData<List<Map<String, Any>>?>()
    val status = MutableLiveData<Status>()

    fun addDeposit(amount: Double?, password: String) {
        viewModelScope.launch {
            status.value = repo.addDeposit(amount, password)
        }
    }

    fun loadDeposits() {
        viewModelScope.launch {
            deposits.value = repo.getDeposits()
        }
    }
}
