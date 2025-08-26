package com.codingEmpire.bitbloom.viewModels.factory


import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.codingEmpire.bitbloom.repos.BuyPlanRepo
import com.codingEmpire.bitbloom.viewModels.BuyPlanViewModel

class BuyPlanViewModelFactory(
    private val repo: BuyPlanRepo
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BuyPlanViewModel::class.java)) {
            return BuyPlanViewModel(repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
