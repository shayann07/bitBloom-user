package com.codingEmpire.bitbloom.viewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codingEmpire.bitbloom.models.TransactionModel
import com.codingEmpire.bitbloom.repos.TransactionRepo
import kotlinx.coroutines.launch

class TransactionViewModel : ViewModel() {

    private val repo = TransactionRepo()

    private val _withdrawals = MutableLiveData<List<TransactionModel>>()
    val withdrawals: LiveData<List<TransactionModel>> = _withdrawals

    private val _deposits = MutableLiveData<List<TransactionModel>>()
    val deposits: LiveData<List<TransactionModel>> = _deposits

    private val _allTransactions = MutableLiveData<List<TransactionModel>>()
    val allTransactions: LiveData<List<TransactionModel>> = _allTransactions

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _planTransactions = MutableLiveData<List<TransactionModel>>()
    val planTransactions: LiveData<List<TransactionModel>> = _planTransactions

    private val _roiTxns = MutableLiveData<List<TransactionModel>>()
    val roiTxns: LiveData<List<TransactionModel>> = _roiTxns

    private val _referralTxns = MutableLiveData<List<TransactionModel>>()
    val referralTxns: LiveData<List<TransactionModel>> = _referralTxns

    private val _teamTxns = MutableLiveData<List<TransactionModel>>()
    val teamTxns: LiveData<List<TransactionModel>> = _teamTxns

    private val _luckySpinTxns = MutableLiveData<List<TransactionModel>>()
    val luckySpinTxns: LiveData<List<TransactionModel>> = _luckySpinTxns

    private val _salaryTxns = MutableLiveData<List<TransactionModel>>()
    val salaryTxns: LiveData<List<TransactionModel>> = _salaryTxns

    private val _achievementsTxns = MutableLiveData<List<TransactionModel>>()
    val achievementsTxns: LiveData<List<TransactionModel>> = _achievementsTxns

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    fun fetchWithdrawals(userId: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val withdrawList = repo.getWithdrawForUser(userId) ?: emptyList()
                _withdrawals.value = withdrawList
            } catch (e: Exception) {
                _error.value = "Failed to load withdrawals: ${e.localizedMessage}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun fetchDeposits(userId: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val txList = repo.getTransactionsForUser(userId) ?: emptyList()
                _deposits.value = txList
            } catch (e: Exception) {
                _error.value = "Failed to load deposits: ${e.localizedMessage}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun fetchAllTransactions(userId: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val txList = repo.getTransactionsForUser(userId) ?: emptyList()
                val withdrawList = repo.getWithdrawForUser(userId) ?: emptyList()

                val mergedList = (txList + withdrawList).sortedByDescending {
                    it.timestamp?.toDate()
                }


                _allTransactions.value = mergedList
            } catch (e: Exception) {
                _error.value = "Failed to load all transactions: ${e.localizedMessage}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun fetchPlanTransactions(userId: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val list = repo.getPlanTransactionsForUser(userId) ?: emptyList()
                _planTransactions.value = list
            } catch (e: Exception) {
                _error.value = "Failed to load plan purchases: ${e.localizedMessage}"
            } finally {
                _loading.value = false
            }
        }
    }

    /* ── fetchers ── */
    fun fetchRoi(userId: String) = viewModelScope.launch {
        _loading.value = true
        _roiTxns.value = repo.getRoiTransactionsForUser(userId) ?: emptyList()
        _loading.value = false
    }

    fun fetchTeam(userId: String) = viewModelScope.launch {
        _loading.value = true
        _teamTxns.value = repo.getTeamTransactionsForUser(userId) ?: emptyList()
        _loading.value = false
    }

    fun fetchReferral(userId: String) = viewModelScope.launch {
        _loading.value = true
        try {
            _referralTxns.value =
                repo.getReferralProfitForUser(userId)?.sortedByDescending { it.timestamp?.toDate() }
                    ?: emptyList()
        } catch (e: Exception) {
            _error.value = "Failed to load direct profits: ${e.localizedMessage}"
        } finally {
            _loading.value = false
        }
    }

    fun fetchLuckySpin(userId: String) = viewModelScope.launch {
        _loading.value = true
        _luckySpinTxns.value = repo.getLuckySpinLogs(userId) ?: emptyList()
        _loading.value = false
    }

    fun fetchSalaryTxns(userId: String) = viewModelScope.launch {
        _loading.value = true
        _salaryTxns.value = repo.getSalaryLevelTxns(userId) ?: emptyList()
        _loading.value = false
    }

    fun fetchAchievementsTxns(userId: String) = viewModelScope.launch {
        _loading.value = true
        _achievementsTxns.value = repo.getAchievementTxns(userId) ?: emptyList()
        _loading.value = false
    }
}
