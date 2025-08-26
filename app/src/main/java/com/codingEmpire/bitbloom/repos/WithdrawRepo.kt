package com.codingEmpire.bitbloom.repos

import android.util.Log
import com.codingEmpire.bitbloom.fcm.AccessToken
import com.codingEmpire.bitbloom.fcm.Fcm
import com.codingEmpire.bitbloom.utils.TxnConstants
import com.codingEmpire.bitbloom.utils.WithdrawResult
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class WithdrawRepo(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun submitWithdrawalRequest(
        userId: String,
        amount: Double,
        walletAddress: String
    ): WithdrawResult {
        try {
            // 1) Blocked?
            if (isUserBlocked(userId)) return WithdrawResult.UserBlocked

            // 2) Load account
            val accountSnap = firestore.collection("accounts")
                .whereEqualTo("user_id", userId)
                .limit(1)
                .get()
                .await()

            if (accountSnap.isEmpty) return WithdrawResult.AccountNotFound
            val accountDoc = accountSnap.documents.first()

            // 3) Check for an existing pending withdraw (outside the transaction)
            val pendingSnap = firestore.collection("withdraw_requests")
                .whereEqualTo("userId", userId)
                .whereEqualTo("status", TxnConstants.STATUS_PENDING)
                .get()
                .await()

            if (!pendingSnap.isEmpty) {
                return WithdrawResult.PendingExists
            }

            // 4) Atomic deduction + request creation
            firestore.runTransaction { tx ->
                // re-read the account
                val freshAccount = tx.get(accountDoc.reference)
                val earnings = freshAccount.get("earnings") as? Map<*, *> ?: emptyMap<String, Any>()
                val balance = (earnings["current_balance"] as? Number)?.toDouble() ?: 0.0

                if ((amount ?: 0.0) > balance) {
                    throw Exception("Insufficient balance.")
                }

                // create the withdraw request doc
                val reqRef = firestore.collection("withdraw_requests").document()
                tx.set(reqRef, mapOf(
                    "userId"        to userId,
                    "amount"        to amount,
                    "walletAddress" to walletAddress,
                    "status"        to TxnConstants.STATUS_PENDING,
                    "timestamp"     to Timestamp.now(),
                    "refunded"      to false,
                    "type"          to TxnConstants.TYPE_WITHDRAW
                ))

                // deduct balance atomically
                tx.update(accountDoc.reference, mapOf(
                    "earnings.current_balance"       to FieldValue.increment(-amount),
                    "investment.remaining_balance"   to FieldValue.increment(-amount)
                ))
            }.await()

            sendWithdrawNotificationToAdmin(userId, amount)
            return WithdrawResult.Success

        } catch (e: Exception) {
            Log.e("WithdrawRepo", "Withdrawal failed: ${e.localizedMessage}", e)
            return WithdrawResult.Error(e.localizedMessage ?: "Transaction failed.")
        }
    }


    suspend fun getWithdraws(userId: String): List<Map<String, Any>>? {
        try {
            Log.d("WithdrawRepo", "Fetching withdrawals for userId: $userId")

            val query = firestore.collection("withdraw_requests")
                .whereEqualTo("userId", userId)
                .get().await()

            if (query.isEmpty) return null

            // Refund rejected & not yet refunded withdrawals
            for (doc in query.documents) {
                val data = doc.data ?: continue
                val status = data["status"]
                val refunded = (data["refunded"] as? Boolean) == true
                val id = doc.id

                if (status == "rejected" && !refunded) {
                    refundRejectedWithdrawal(id)
                }
            }

            // Return updated list after refunds
            val updatedQuery = firestore.collection("withdraw_requests")
                .whereEqualTo("userId", userId)
                .get().await()

            return updatedQuery.documents.map {
                val d = it.data?.toMutableMap() ?: mutableMapOf()
                d["id"] = it.id
                d
            }
        } catch (e: Exception) {
            Log.e("WithdrawRepo", "❌ Error fetching withdrawals: $e", e)
            return null
        }
    }

    suspend fun isUserBlocked(uid: String): Boolean {
        try {
            val query = firestore.collection("users")
                .whereEqualTo("id", uid)
                .limit(1)
                .get().await()

            if (!query.isEmpty) {
                val userDoc = query.documents.first()
                return userDoc.getBoolean("isBlocked") == true
            }
        } catch (e: Exception) {
            Log.e("WithdrawRepo", "❌ Error checking block status: $e", e)
        }
        return false
    }

    suspend fun refundRejectedWithdrawal(requestId: String): WithdrawResult {
        try {
            val requestRef = firestore.collection("withdraw_requests").document(requestId)
            val requestSnap = requestRef.get().await()

            if (!requestSnap.exists()) return WithdrawResult.Error("Withdraw request not found.")
            val data = requestSnap.data ?: return WithdrawResult.Error("No data in request.")
            val userId = data["userId"] as? String ?: return WithdrawResult.Error("Missing userId.")
            val amount = (data["amount"] as? Number)?.toDouble() ?: return WithdrawResult.Error("Invalid amount.")

            val accountSnap = firestore.collection("accounts")
                .whereEqualTo("user_id", userId)
                .limit(1)
                .get().await()

            if (accountSnap.isEmpty) return WithdrawResult.Error("Account not found.")
            val accountRef = accountSnap.documents.first().reference

            firestore.runTransaction { transaction ->
                val freshRequest = transaction.get(requestRef)
                val status = freshRequest.getString("status")
                val refunded = freshRequest.getBoolean("refunded") == true

                if (status != TxnConstants.STATUS_REJECTED || refunded) {
                    throw Exception("Invalid refund state.")
                }

                // Refund balance
                transaction.update(accountRef, mapOf(
                    "earnings.current_balance" to FieldValue.increment(amount),
                    "investment.remaining_balance" to FieldValue.increment(amount)
                ))

                // Mark as refunded
                transaction.update(requestRef, "refunded", true)
            }.await()

            return WithdrawResult.Success

        } catch (e: Exception) {
            Log.e("WithdrawRepo", "Refund error: ${e.localizedMessage}", e)
            return WithdrawResult.Error(e.localizedMessage ?: "Refund transaction failed.")
        }
    }
    suspend fun cancelWithdrawal(requestId: String): WithdrawResult {
        try {
            val reqRef = firestore.collection("withdraw_requests").document(requestId)
            val reqSnap = reqRef.get().await()
            if (!reqSnap.exists()) return WithdrawResult.Error("Request not found")

            val data     = reqSnap.data!!
            val status   = data["status"] as? String
            val refunded = data["refunded"] as? Boolean ?: false
            if (status != TxnConstants.STATUS_PENDING || refunded) {
                return WithdrawResult.Error("Cannot cancel this request")
            }

            val userId = data["userId"] as? String ?: return WithdrawResult.Error("No userId")
            val amount = (data["amount"] as? Number)?.toDouble() ?: return WithdrawResult.Error("Invalid amount")

            val acctSnap = firestore.collection("accounts")
                .whereEqualTo("user_id", userId)
                .limit(1)
                .get().await()
            if (acctSnap.isEmpty) return WithdrawResult.Error("Account not found")
            val acctRef = acctSnap.documents.first().reference

            firestore.runTransaction { tx ->
                val fresh = tx.get(reqRef)
                if (fresh.getString("status") != TxnConstants.STATUS_PENDING
                    || fresh.getBoolean("refunded") == true) {
                    throw Exception("Already processed")
                }

                tx.update(acctRef, mapOf(
                    "earnings.current_balance"     to FieldValue.increment(amount),
                    "investment.remaining_balance" to FieldValue.increment(amount)
                ))
                tx.update(reqRef, mapOf(
                    "status"   to TxnConstants.STATUS_CANCELLED,
                    "refunded" to true
                ))
            }.await()

            return WithdrawResult.Success

        } catch (e: Exception) {
            Log.e("WithdrawRepo", "Cancel failed: ${e.message}", e)
            return WithdrawResult.Error(e.message ?: "Cancel failed.")
        }
    }

    private fun sendWithdrawNotificationToAdmin(userId: String, amount: Double) {
        val db = FirebaseFirestore.getInstance()

        // Fetch admin document (assuming only one admin)
        db.collection("Admin").limit(1).get().addOnSuccessListener { snapshot ->
            val adminDoc = snapshot.documents.firstOrNull()
            val adminToken = adminDoc?.getString("deviceToken")

            if (adminToken.isNullOrEmpty()) {
                Log.e("FCM", "Admin device token not found")
                return@addOnSuccessListener
            }

            val title = "New Withdrawal Request"
            val body = "User $userId requested $$amount withdrawal."

            AccessToken.getAccessTokenAsync(object : AccessToken.AccessTokenCallback {
                override fun onAccessTokenReceived(token: String?) {
                    if (!token.isNullOrEmpty()) {
                        Fcm().sendFCMNotification(adminToken, title, body, token)
                    } else {
                        Log.e("FCM", "Access token was null or empty")
                    }
                }
            })
        }.addOnFailureListener {
            Log.e("FCM", "Failed to fetch admin token", it)
        }
    }
}
