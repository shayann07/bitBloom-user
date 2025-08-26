package com.codingEmpire.bitbloom.repos

import com.codingEmpire.bitbloom.utils.TxnConstants
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

data class SpinData(val total: Double, val lastSpinDate: String?)

class LuckySpinRepo(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val col = firestore.collection("luckySpins")

    /** Fetches both total & lastSpinDate in one go. */
    suspend fun getSpinData(userId: String): SpinData {
        val snap = col.document(userId).get().await()
        val total = snap.getDouble("total") ?: 0.0
        val lastDate = snap.getString("lastSpinDate")
        return SpinData(total, lastDate)
    }

    /**
     * Attempts to add today’s reward.
     * If they’ve already spun **today**, throws an IllegalStateException.
     * Otherwise atomically updates both total + lastSpinDate and returns the new SpinData.
     */
    // LuckySpinRepo.kt  ── modify addReward()
    suspend fun addReward(userId: String, reward: Double, today: String): SpinData {
        return firestore.runTransaction { tx ->
            val ref = col.document(userId)
            val doc = tx.get(ref)
            if (doc.getString("lastSpinDate") == today)      // already spun
                throw IllegalStateException("Already spun today")

            val newTotal = (doc.getDouble("total") ?: 0.0) + reward
            tx.set(
                ref,
                mapOf("total" to newTotal, "lastSpinDate" to today),
                SetOptions.merge()       // <-- same effect, keeps welcomeRewardClaimed
            )

            /* 🔵  ➜ NEW: write into luckySpinLogs */
            val logRef = firestore.collection("luckySpinLogs").document()
            tx.set(
                logRef, mapOf(
                    TxnConstants.FIELD_ID to logRef.id,
                    TxnConstants.FIELD_USER_ID to userId,
                    TxnConstants.FIELD_AMOUNT to reward,
                    TxnConstants.FIELD_TYPE to TxnConstants.TYPE_LUCKY_SPIN,
                    TxnConstants.FIELD_STATUS to TxnConstants.STATUS_RECEIVED,
                    TxnConstants.FIELD_TIMESTAMP to com.google.firebase.Timestamp.now()
                )
            )

            SpinData(newTotal, today)
        }.await()
    }


    // LuckySpinRepo.kt
    suspend fun transferToAccount(userId: String, amount: Double) {
        // fetch account doc outside the transaction
        val accSnap =
            firestore.collection("accounts").whereEqualTo("user_id", userId).limit(1).get().await()
        val accRef =
            accSnap.documents.first().reference ?: throw IllegalStateException("Account not found")
        val spinRef = col.document(userId)

        firestore.runTransaction { tx ->
            val spinDoc = tx.get(spinRef)
            val total = spinDoc.getDouble("total") ?: 0.0
            require(total >= amount)    // sanity

            tx.update(spinRef, "total", total - amount)
            tx.update(
                accRef, mapOf(
                    "earnings.current_balance" to FieldValue.increment(amount),
                    "investment.remaining_balance" to FieldValue.increment(amount)
                )
            )
        }.await()
    }

    /** Reset total back to zero (after invest). */
    suspend fun resetTotal(userId: String) {
        col.document(userId).update("total", 0.0)          // keep lastSpinDate unchanged
            .await()
    }

    suspend fun claimWelcomeBonus(userId: String, amount: Double = 5.0) {
        firestore.runTransaction { tx ->
            val ref = col.document(userId)
            val doc = tx.get(ref)

            // already claimed ⇒ bail out
            if (doc.getBoolean("welcomeRewardClaimed") == true) return@runTransaction

            val newTotal = (doc.getDouble("total") ?: 0.0) + amount
            tx.set(
                ref, mapOf(
                    "total" to newTotal,
                    "welcomeRewardClaimed" to true,          // lock it
                ), SetOptions.merge()
            )

            // optional: log it
            val log = firestore.collection("luckySpinLogs").document()
            tx.set(
                log, mapOf(
                    TxnConstants.FIELD_ID to log.id,
                    TxnConstants.FIELD_USER_ID to userId,
                    TxnConstants.FIELD_AMOUNT to amount,
                    TxnConstants.FIELD_TYPE to "welcomeBonus",
                    TxnConstants.FIELD_STATUS to TxnConstants.STATUS_RECEIVED,
                    TxnConstants.FIELD_TIMESTAMP to com.google.firebase.Timestamp.now()
                )
            )
        }.await()
    }
}
