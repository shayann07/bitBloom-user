package com.codingEmpire.bitbloom.viewModels

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codingEmpire.bitbloom.repos.DailyRewardRepo
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class DailyRewardViewModel(
    private val repo: DailyRewardRepo = DailyRewardRepo()
) : ViewModel() {

    private val tokensPerDay = listOf(2, 4, 6, 8, 10, 12, 14)

    /** 0 = already claimed today; 1–7 = eligible day */
    private val _eligibleDay = MutableLiveData<Int>()
    val eligibleDay: LiveData<Int> = _eligibleDay

    sealed class ButtonState {
        data class Eligible(val day: Int) : ButtonState()
        object Collected : ButtonState()
    }

    private val _buttonState = MutableLiveData<ButtonState>()
    val buttonState: LiveData<ButtonState> = _buttonState

    private val _totalTokens = MutableLiveData<Int>()
    val totalTokens: LiveData<Int> = _totalTokens

    /** Call on fragment start or after claim to refresh everything */
    @RequiresApi(Build.VERSION_CODES.O)
    fun loadStatus(userId: String) {
        viewModelScope.launch {
            val (lastDate, lastDay) = repo.getProgress(userId)
            val today = LocalDate.now()
            val daysSince = lastDate?.let { ChronoUnit.DAYS.between(it, today) } ?: Long.MAX_VALUE

            // compute nextDay in [1..7] or 0 if already claimed today
            val nextDay = when {
                lastDate == null -> 1
                daysSince == 0L -> 0
                daysSince == 1L && lastDay < 7 -> lastDay + 1
                daysSince == 1L && lastDay == 7 -> 1
                else -> 1
            }

            _eligibleDay.value = nextDay
            _buttonState.value = if (nextDay == 0) ButtonState.Collected
            else ButtonState.Eligible(nextDay)

            // fetch total tokens
            _totalTokens.value = repo.getTotalTokens(userId)
        }
    }

    /** User taps “Collect” */
    @RequiresApi(Build.VERSION_CODES.O)
    fun claim(userId: String) {
        val day = _eligibleDay.value ?: return
        if (day == 0) return  // already claimed
        viewModelScope.launch {
            repo.claimReward(userId, day, tokensPerDay[day - 1], LocalDate.now())
            // refresh UI & total
            loadStatus(userId)
        }
    }
}