package com.codingEmpire.bitbloom.viewModels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.codingEmpire.bitbloom.models.CryptoResponse
import com.codingEmpire.bitbloom.repos.DailyRewardRepo
import com.codingEmpire.bitbloom.repos.WalletRepo
import com.codingEmpire.bitbloom.repos.WithdrawRepo
import kotlinx.coroutines.launch

class WalletViewModel(application: Application) : AndroidViewModel(application) {

    /* ───── repositories ───── */
    private val repo          = WalletRepo(application)
    private val withdrawRepo  = WithdrawRepo()
    private val dailyRepo     = DailyRewardRepo()

    /* ───── generic UI state ───── */
    private val _walletData = MutableLiveData<Map<String, Any?>?>()
    val walletData: LiveData<Map<String, Any?>?> = _walletData

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    /* ───── numbers on the Wallet screen ───── */
    val totalWalletAmount = MutableLiveData<Double>()   // blue “Wallet Balance”
    val tokenOnlyAmount   = MutableLiveData<Double>()   // XBLM → USDT
    val spinUsdtAmount    = MutableLiveData<Double>()   // Lucky-Spin USDT ← NEW

    /* ───── other widgets (unchanged) ───── */
    private val _totalRewardTokens = MutableLiveData<Int>()
    val totalRewardTokens: LiveData<Int> = _totalRewardTokens

    val xblmAmount     = MutableLiveData<Double>()
    val cryptoPrices   = MutableLiveData<CryptoResponse?>()
    val xblmLiveRate   = MutableLiveData<Pair<Double, Double>>() // (rate, pct)

    /* ─────────────────────────────────────── */

    fun observeXBLMLiveRate() {
        repo.listenToXBLMRateAndPct { rate, pct ->
            xblmLiveRate.postValue(Pair(rate, pct))
        }
    }

    /** Loads balance + XBLM value + Lucky-Spin pot */
    fun loadWalletAndTokenValues() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val totals              = repo.getWalletTotals()
                totalWalletAmount.value = totals.grandTotal
                tokenOnlyAmount.value   = totals.tokenValue
                spinUsdtAmount.value    = totals.spinUsdt   // ← NEW line
            } catch (e: Exception) {
                Log.e("WalletVM", "❌ Failed to load wallet totals", e)
                _error.value = e.localizedMessage
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Original helper – full wallet map */
    fun loadWallet() {
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                val data = repo.fetchWalletData()
                if (data != null) {
                    _walletData.value = data
                } else {
                    _error.value = "No wallet data found"
                }
            } catch (t: Throwable) {
                _error.value = t.message ?: "Unknown error"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /* ───── live Firestore listener passthroughs (unchanged) ───── */
    fun startListeningToWalletUpdates() {
        repo.listenToWalletUpdates { data ->
            if (data != null) {
                _walletData.postValue(data)
            } else {
                _error.postValue("Failed to fetch live wallet data.")
            }
        }
    }
    fun stopListeningToWalletUpdates() = repo.removeWalletListener()

    /* ───── ancillary loaders (unchanged) ───── */
    fun loadCryptoPrices() {
        viewModelScope.launch {
            cryptoPrices.postValue(repo.fetchCryptoPrices())
        }
    }
    fun loadTotalRewardTokens(userId: String) {
        viewModelScope.launch {
            _totalRewardTokens.value = dailyRepo.getTotalTokens(userId)
        }
    }
}
