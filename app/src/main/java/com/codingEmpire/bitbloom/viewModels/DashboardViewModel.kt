package com.codingEmpire.bitbloom.viewModels


import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.codingEmpire.bitbloom.models.DashboardMetrics
import com.codingEmpire.bitbloom.models.TransactionModel
import com.codingEmpire.bitbloom.repos.DashboardRepo
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = DashboardRepo(app)

    private val _metrics = MutableLiveData<DashboardMetrics>()
    val metrics: LiveData<DashboardMetrics> = _metrics

    private val _transactions = MutableLiveData<List<TransactionModel>>()
    val transactions: LiveData<List<TransactionModel>> = _transactions

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    @RequiresApi(Build.VERSION_CODES.O)
    fun loadDashboard() {
        _loading.value = true          // ► show overlay immediately
        _error.value = null

        viewModelScope.launch {
            try {
                // run the two firestore calls in parallel
                val metricsDefer = async { repo.fetchMetrics() }
                val txnsDefer = async { repo.fetchAllTransactions() }

                _metrics.value = metricsDefer.await()
                _transactions.value = txnsDefer.await()   // first list render

            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Unknown error"
            } finally {
                _loading.value = false           // ► hide overlay *after both* await()
            }
        }
    }
}