package com.codingEmpire.bitbloom.repos

import android.util.Log
import com.codingEmpire.bitbloom.models.TransactionModel
import com.codingEmpire.bitbloom.utils.TxnConstants
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class TransactionRepo {
    private val firestore = FirebaseFirestore.getInstance()


    suspend fun getTransactionsForUser(userId: String): List<TransactionModel>? {
        return try {
            val snapshot =
                firestore.collection("transactions").whereEqualTo("userId", userId).get().await()

            snapshot.documents.mapNotNull {
                it.toObject(TransactionModel::class.java)?.copy(id = it.id)
            }.sortedByDescending { it.timestamp?.toDate()?.time ?: 0L }
        } catch (e: Exception) {
            Log.e("TransactionRepo", "Error fetching transactions: $e", e)
            null
        }
    }

    suspend fun getWithdrawForUser(userId: String): List<TransactionModel>? {
        return try {
            val snapshot = firestore.collection("withdraw_requests")
                .whereEqualTo(TxnConstants.FIELD_USER_ID, userId).get().await()

            snapshot.documents.mapNotNull {
                it.toObject(TransactionModel::class.java)?.copy(id = it.id)
            }.sortedByDescending { it.timestamp?.toDate()?.time ?: 0L }
        } catch (e: Exception) {
            Log.e("TransactionRepo", "Error fetching withdraws: $e", e)
            null
        }
    }

    suspend fun getPlanTransactionsForUser(userId: String): List<TransactionModel>? {
        return try {
            val snapshot = firestore.collection("plansTransactions")
                .whereEqualTo(TxnConstants.FIELD_USER_ID, userId)
                .whereEqualTo(TxnConstants.FIELD_TYPE, TxnConstants.TYPE_INVESTMENT_BOUGHT).get()
                .await()

            snapshot.documents.mapNotNull {
                it.toObject(TransactionModel::class.java)?.copy(id = it.id)
            }.sortedByDescending { it.timestamp?.toDate()?.time ?: 0L }
        } catch (e: Exception) {
            Log.e("TransactionRepo", "Error fetching plan transactions: $e", e)
            null
        }
    }

    /** ROI profits (collection: roiTransactions) */
    suspend fun getRoiTransactionsForUser(userId: String): List<TransactionModel>? = try {
        firestore.collection("roiTransactions").whereEqualTo(TxnConstants.FIELD_USER_ID, userId)
            .get().await().documents.mapNotNull {
                it.toObject(TransactionModel::class.java)?.copy(id = it.id)
            }.sortedByDescending { it.timestamp?.toDate()?.time ?: 0L }
    } catch (e: Exception) {
        Log.e("TransactionRepo", "ROI fetch error", e); null
    }

    /** Team-level profits (collection: teamTransactions) */
    suspend fun getTeamTransactionsForUser(userId: String): List<TransactionModel>? = try {
        firestore.collection("teamTransactions").whereEqualTo(TxnConstants.FIELD_USER_ID, userId)
            .get().await().documents.mapNotNull {
                it.toObject(TransactionModel::class.java)?.copy(id = it.id)
            }.sortedByDescending { it.timestamp?.toDate()?.time ?: 0L }
    } catch (e: Exception) {
        Log.e("TransactionRepo", "Team fetch error", e); null
    }

    suspend fun getReferralProfitForUser(userId: String): List<TransactionModel>? {
        return try {
            firestore.collection("referralProfitTxns")
                .whereEqualTo(TxnConstants.FIELD_USER_ID, userId).get()
                .await().documents.mapNotNull {
                    it.toObject(TransactionModel::class.java)?.copy(id = it.id)
                }.sortedByDescending { it.timestamp?.toDate()?.time ?: 0L }
        } catch (e: Exception) {
            Log.e("TransactionRepo", "Error fetching referral profits: $e", e)
            null
        }
    }

    suspend fun getLuckySpinLogs(userId: String): List<TransactionModel>? = try {
        firestore.collection("luckySpinLogs").whereEqualTo(TxnConstants.FIELD_USER_ID, userId).get()
            .await().documents.mapNotNull {
                it.toObject(TransactionModel::class.java)?.copy(id = it.id)
            }.sortedByDescending { it.timestamp?.toDate()?.time ?: 0L }
    } catch (e: Exception) {
        Log.e("TransactionRepo", "LuckySpin fetch error", e); null
    }

    suspend fun getSalaryLevelTxns(userId: String): List<TransactionModel>? = try {
        firestore.collection("salaryTxns").whereEqualTo(TxnConstants.FIELD_USER_ID, userId).get()
            .await().documents.mapNotNull {
                it.toObject(TransactionModel::class.java)?.copy(id = it.id)
            }.sortedByDescending { it.timestamp?.toDate()?.time ?: 0L }
    } catch (e: Exception) {
        Log.e("TransactionRepo", "Salary txns fetch error", e); null
    }

    suspend fun getAchievementTxns(userId: String): List<TransactionModel>? = try {
        firestore.collection("achievementsTxns").whereEqualTo(TxnConstants.FIELD_USER_ID, userId)
            .get().await().documents.mapNotNull {
                it.toObject(TransactionModel::class.java)?.copy(id = it.id)
            }.sortedByDescending { it.timestamp?.toDate()?.time ?: 0L }
    } catch (e: Exception) {
        Log.e("TransactionRepo", "Achievement txns fetch error", e); null
    }

}