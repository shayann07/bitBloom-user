package com.codingEmpire.bitbloom.viewModels

import android.os.Build
import android.support.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codingEmpire.bitbloom.repos.DailyRewardRepo
import com.codingEmpire.bitbloom.repos.LuckySpinRepo
import com.codingEmpire.bitbloom.repos.SpinData
import kotlinx.coroutines.launch

class LuckySpinViewModel(
    private val repo: LuckySpinRepo = LuckySpinRepo()
) : ViewModel() {

    private val _spinData = MutableLiveData<SpinData>()
    val spinData: LiveData<SpinData> = _spinData

    /** Load both total & lastSpinDate from Firestore. */
    fun loadSpinData(userId: String) = viewModelScope.launch {
        _spinData.value = repo.getSpinData(userId)
    }

    /**
     * Try to add reward for `today`. On success pushes new SpinData,
     * on failure the coroutine will throw.
     */
    fun addReward(userId: String, reward: Double, today: String) = viewModelScope.launch {
        val updated = repo.addReward(userId, reward, today)
        _spinData.value = updated
    }

    /** Reset both fields back to zero/null after invest. */
    fun resetTotal(userId: String) = viewModelScope.launch {
        repo.resetTotal(userId)
        _spinData.value = SpinData(0.0, null)
    }

    fun claimWelcomeBonus(userId: String) = viewModelScope.launch {
        repo.claimWelcomeBonus(userId)
        // refresh UI after credit
        _spinData.value = repo.getSpinData(userId)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun claimStarterPack(uid: String) = viewModelScope.launch {
        DailyRewardRepo().claimStarterPack(uid)
    }
}
