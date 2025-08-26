package com.codingEmpire.bitbloom.viewModels.factory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.codingEmpire.bitbloom.repos.AchievementsRepository
import com.codingEmpire.bitbloom.viewModels.AchievementsViewModel

class AchievementsVMFactory(
    private val repo: AchievementsRepository,
    private val userId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AchievementsViewModel(repo, userId) as T
}