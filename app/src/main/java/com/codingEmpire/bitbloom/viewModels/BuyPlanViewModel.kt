package com.codingEmpire.bitbloom.viewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codingEmpire.bitbloom.models.BuyPlan
import com.codingEmpire.bitbloom.models.PlanModel
import com.codingEmpire.bitbloom.repos.BuyPlanRepo
import com.codingEmpire.bitbloom.utils.PlanStatus
import kotlinx.coroutines.launch

class BuyPlanViewModel(
    private val buyPlanRepo: BuyPlanRepo
) : ViewModel() {

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _availablePlans = MutableLiveData<List<PlanModel>>()
    val availablePlans: LiveData<List<PlanModel>> get() = _availablePlans

    private val _purchasedPlans = MutableLiveData<List<BuyPlan>>()
    val purchasedPlans: LiveData<List<BuyPlan>> get() = _purchasedPlans

    private val _buyPlanStatus = MutableLiveData<PlanStatus>()
    val buyPlanStatus: LiveData<PlanStatus> get() = _buyPlanStatus

    // --- Repo Calls wrapped for LiveData ---

    fun fetchAvailablePlans() {
        _isLoading.value = true
        viewModelScope.launch {
            val result = buyPlanRepo.getAvailablePlans()
            _availablePlans.value = result
            _isLoading.value = false
        }
    }

    fun fetchPurchasedPlans(userId: String) {
        _isLoading.value = true
        viewModelScope.launch {
            val result = buyPlanRepo.getPurchasedPlans(userId)
            _purchasedPlans.value = result
            _isLoading.value = false
        }
    }

    fun buyPlan(userId: String, amount: Double, planName: String,autoInvest: Boolean) {
        _isLoading.value = true
        viewModelScope.launch {
            val status = buyPlanRepo.buyPlan(userId, amount, planName,autoInvest)
            _buyPlanStatus.value = status
            _isLoading.value = false
        }
    }
}
