package com.codingEmpire.bitbloom.repos

import android.util.Log
import com.codingEmpire.bitbloom.utils.PrefService
import com.codingEmpire.bitbloom.utils.Status
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class DepositRepo(
    private val prefService: PrefService,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private suspend fun getUserId(): String? = prefService.getString("user_id")

    suspend fun addDeposit(amount: Double?, password: String): Status {
        try {
            if (amount == null || amount < 0) return Status.INVALID_AMOUNT
            val userId = getUserId() ?: return Status.USER_NOT_FOUND

            // Verify password
            val userQuery = firestore.collection("users")
                .whereEqualTo("id", userId)
                .limit(1)
                .get().await()
            if (userQuery.isEmpty) return Status.USER_NOT_FOUND

            val userDoc = userQuery.documents.first()
            val savedPassword = userDoc.getString("password")
            if (password != savedPassword) return Status.INVALID_PASSWORD

            // Add deposit to transactions
            val deposit = hashMapOf(
                "userId" to userId,
                "amount" to amount,
                "timestamp" to Timestamp.now(),
                "type" to "deposit",
                "status" to "approved",
                "balanceUpdated" to true
            )
            firestore.collection("transactions").add(deposit).await()

            // Update account
            val accountQuery = firestore.collection("accounts")
                .whereEqualTo("user_id", userId)
                .limit(1)
                .get().await()
            if (accountQuery.isEmpty) return Status.USER_NOT_FOUND

            val accountDoc = accountQuery.documents.first()
            val data = accountDoc.data ?: emptyMap<String, Any>()

            val investment = data["investment"] as? Map<String, Any?> ?: emptyMap()
            val earnings = data["earnings"] as? Map<String, Any?> ?: emptyMap()

            val remainingBalance = (investment["remaining_balance"]?.toString()?.toDoubleOrNull() ?: 0.0)
            val totalDeposit = (investment["total_deposit"]?.toString()?.toDoubleOrNull() ?: 0.0)
            val totalEarned = (earnings["total_earned"]?.toString()?.toDoubleOrNull() ?: 0.0)

            val currentBalance = remainingBalance + totalEarned

            val updatedInvestment = mapOf(
                "remaining_balance" to remainingBalance + amount,
                "total_deposit" to totalDeposit + amount
            )
            val updatedEarnings = mapOf("current_balance" to currentBalance)

            accountDoc.reference.set(
                mapOf(
                    "investment" to updatedInvestment,
                    "earnings" to updatedEarnings
                ),
                SetOptions.merge()
            ).await()

            return Status.SUCCESS
        } catch (e: Exception) {
            Log.e("DepositRepo", "Error adding deposit: $e", e)
            return Status.ERROR
        }
    }

    suspend fun getDeposits(): List<Map<String, Any>>? {
        return try {
            val userId = getUserId() ?: return null
            val txSnapshot = firestore.collection("transactions")
                .whereEqualTo("userId", userId)
                .whereEqualTo("type", "deposit")
                .get().await()

            if (txSnapshot.isEmpty) return null

            val deposits = txSnapshot.documents.map { doc ->
                val data = HashMap(doc.data ?: emptyMap<String, Any>())
                data["id"] = doc.id
                data
            }

            // Sort by timestamp descending
            deposits.sortedByDescending { (it["timestamp"] as? Timestamp)?.toDate() }
        } catch (e: Exception) {
            Log.e("DepositRepo", "Error fetching deposits: $e", e)
            null
        }
    }
}
