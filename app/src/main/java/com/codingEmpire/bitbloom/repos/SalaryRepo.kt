package com.codingEmpire.bitbloom.repos

import android.content.Context
import com.codingEmpire.bitbloom.models.SalaryLevel
import com.codingEmpire.bitbloom.utils.PrefService
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Repository to fetch salary-related data:
 *  • current balance from 'accounts' → earnings.current_balance
 *  • self investment sum from 'userPlans' where PlanStatus="active"
 *  • paid levels & collecting salary payouts
 */
class SalaryRepo(context: Context) {
    private val db = FirebaseFirestore.getInstance()
    private val pref = PrefService(context)
    private val acctCol = db.collection("accounts")

    suspend fun getCurrentBalance(): Double = withContext(Dispatchers.IO) {
        val userId = pref.getUserId() ?: error("User ID not found")
        val snap = acctCol
            .whereEqualTo("user_id", userId)
            .limit(1)
            .get()
            .await()
        val earnings = snap.documents.firstOrNull()
            ?.get("earnings") as? Map<*, *> ?: return@withContext 0.0
        return@withContext (earnings["current_balance"] as? Number)?.toDouble() ?: 0.0
    }

    suspend fun getSelfInvestSum(): Double = withContext(Dispatchers.IO) {
        val userId = pref.getUserId() ?: error("User ID not found")
        val snap = db.collection("userPlans")
            .whereEqualTo("user_id", userId)
            .whereEqualTo("PlanStatus", "active")
            .get()
            .await()
        return@withContext snap.documents
            .mapNotNull { it.getDouble("invested_amount") }
            .sum()
    }

    /** Returns the list of salary levels already collected (empty if none). */
    suspend fun getPaidLevels(userId: String): Set<Int> = withContext(Dispatchers.IO) {
        // 1) find the account document by its user_id field
        val snap = acctCol
            .whereEqualTo("user_id", userId)
            .limit(1)
            .get()
            .await()

        val doc = snap.documents.firstOrNull()
            ?: return@withContext emptySet()

        @Suppress("UNCHECKED_CAST")
        val paidList = doc.get("salary.paid_levels") as? List<Long>
            ?: return@withContext emptySet()

        return@withContext paidList.map { it.toInt() }.toSet()
    }

    /**
     * Atomically pays the given *new* levels:
     *  • creates one transaction per level (collection **salaryTxns**)
     *  • bumps current_balance & remaining_balance
     *  • stores the level id inside **salary.paid_levels**
     */
    suspend fun collectLevels(userId: String, levels: List<SalaryLevel>) =
        withContext(Dispatchers.IO) {
            if (levels.isEmpty()) return@withContext

            // 1) Lookup the account document by user_id
            val acctSnap = acctCol
                .whereEqualTo("user_id", userId)
                .limit(1)
                .get()
                .await()

            val acctDoc = acctSnap.documents.firstOrNull()
                ?: error("No account found for user_id $userId")

            val acctRef = acctDoc.reference
            val total = levels.sumOf { it.reward }
            val now = Timestamp.now()

            db.runTransaction { tx ->
                // 2) append paid_levels array
                tx.update(
                    acctRef,
                    "salary.paid_levels",
                    FieldValue.arrayUnion(*levels.map { it.level }.toTypedArray())
                )

                // 3) increment balances
                tx.update(
                    acctRef,
                    mapOf(
                        "earnings.current_balance" to FieldValue.increment(total),
                        "earnings.total_earned" to FieldValue.increment(total),
                        "investment.remaining_balance" to FieldValue.increment(total)
                    )
                )

                // 4) write one salaryTxn per level
                val txCol = db.collection("salaryTxns")
                levels.forEach { lvl ->
                    val ref = txCol.document()
                    tx.set(
                        ref, mapOf(
                            com.codingEmpire.bitbloom.utils.TxnConstants.FIELD_ID to ref.id,
                            com.codingEmpire.bitbloom.utils.TxnConstants.FIELD_USER_ID to userId,
                            com.codingEmpire.bitbloom.utils.TxnConstants.FIELD_AMOUNT to lvl.reward,
                            com.codingEmpire.bitbloom.utils.TxnConstants.FIELD_TYPE to "salary",
                            "level" to lvl.level,
                            com.codingEmpire.bitbloom.utils.TxnConstants.FIELD_STATUS to com.codingEmpire.bitbloom.utils.TxnConstants.STATUS_COLLECTED,
                            com.codingEmpire.bitbloom.utils.TxnConstants.FIELD_TIMESTAMP to now
                        )
                    )
                }
            }.await()
        }
}