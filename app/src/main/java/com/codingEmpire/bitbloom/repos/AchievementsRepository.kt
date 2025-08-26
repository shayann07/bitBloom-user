package com.codingEmpire.bitbloom.repos

import com.codingEmpire.bitbloom.models.AchievementLevel
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AchievementsRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    /* ───── static data ───── */

    private val LEVEL_NAMES = listOf(
        "Starter Squad", "Growing Gang", "Emerging Unit", "Profit Pioneers",
        "Sales Titans", "Momentum Crew", "Wealth Masters", "Elite Champions",
        "Supreme Syndicate", "Ultimate Force"
    )

    private val DIRECT_BIZ = listOf(
        1_000.0, 3_000.0, 5_000.0, 7_000.0, 10_000.0,
        15_000.0, 25_000.0, 50_000.0, 100_000.0, 500_000.0
    )
    private val INDIRECT_BIZ = listOf(
        3_500.0, 10_000.0, 20_000.0, 30_000.0, 50_000.0,
        80_000.0, 150_000.0, 300_000.0, 500_000.0, 2_000_000.0
    )
    private val REWARDS = listOf(
        100.0, 500.0, 1_000.0, 2_500.0, 5_000.0,
        8_000.0, 10_000.0, 25_000.0, 37_000.0, 200_000.0
    )

    /* ───── public API ───── */

    /** Builds the 10-rank list with current unlock / collect status. */
    suspend fun getAchievements(userId: String): List<AchievementLevel> {
        val collected = fetchCollectedIndices(userId)
        val (direct, indirect) = aggregateBusiness(userId)

        return List(10) { i ->
            val unlocked = direct >= DIRECT_BIZ[i] && indirect >= INDIRECT_BIZ[i]
            AchievementLevel(
                index = i,
                name = LEVEL_NAMES[i],
                salary = if (unlocked) REWARDS[i] else 0.0,
                directThreshold = DIRECT_BIZ[i],
                indirectThreshold = INDIRECT_BIZ[i],
                isUnlocked = unlocked,
                isCollected = collected.contains(i)
            )
        }
    }

    /** Credits a reward once; returns `true` when successful. */
    suspend fun collectSalary(
        userId: String,
        amount: Double,
        levelIndex: Int
    ): Boolean {
        if (amount <= 0) return false

        // locate account doc
        val docRef = db.collection("accounts")
            .whereEqualTo("user_id", userId)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
            ?.reference
            ?: return false

        // atomic transaction
        return try {
            db.runTransaction { tx ->
                val snap = tx.get(docRef)

                if ((snap["collectedAchievements"] as? List<*>)?.contains(levelIndex) == true)
                    return@runTransaction false

                val earnings = snap["earnings"] as? Map<*, *> ?: emptyMap<String, Any>()
                val investment = snap["investment"] as? Map<*, *> ?: emptyMap<String, Any>()

                val newEarnBal =
                    ((earnings["current_balance"] as? Number)?.toDouble() ?: 0.0) + amount
                val newTotEarn = ((earnings["total_earned"] as? Number)?.toDouble() ?: 0.0) + amount
                val newInvBal =
                    ((investment["remaining_balance"] as? Number)?.toDouble() ?: 0.0) + amount

                tx.update(
                    docRef,
                    mapOf(
                        "earnings.current_balance" to newEarnBal,
                        "earnings.total_earned" to newTotEarn,
                        "investment.remaining_balance" to newInvBal,
                        "collectedAchievements" to FieldValue.arrayUnion(levelIndex)
                    )
                )

                val now = FieldValue.serverTimestamp()
                val txnRef = db.collection("achievementsTxns").document()
                val txnData = mapOf(
                    "id" to txnRef.id,
                    "user_id" to userId,
                    "amount" to amount,
                    "type" to "Salary Credit",
                    "status" to "received",
                    "balance_updated" to true,
                    "timestamp" to now
                )
                tx.set(txnRef, txnData)

                true
            }.await()
        } catch (_: Exception) {
            false
        }
    }

    /* ───── helpers ───── */

    private suspend fun fetchCollectedIndices(userId: String): List<Int> {
        val doc = db.collection("accounts")
            .whereEqualTo("user_id", userId)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()

        val raw = doc?.get("collectedAchievements") as? List<*>
        return raw?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
    }

    /**
     * Reads cached business totals; if absent, computes them once and
     * writes the cache asynchronously.
     */
    private suspend fun aggregateBusiness(userId: String): Pair<Double, Double> {
        val cached = db.collection("business_metrics")
            .document(userId)
            .get()
            .await()

        if (cached.exists()) {
            return (cached.getDouble("direct") ?: 0.0) to
                    (cached.getDouble("indirect") ?: 0.0)
        }

        /* legacy traversal (first call or cache miss) */
        var directBiz = 0.0
        var indirectBiz = 0.0
        val processed = mutableSetOf(userId)
        var current = listOf(userId)

        for (level in 1..6) {
            val next = fetchReferrals(current, processed)
            val (sum, _) = statsForUsers(next)
            if (level == 1) directBiz += sum else indirectBiz += sum
            if (next.isEmpty()) break
            current = next
        }

        // write cache in background (fire-and-forget)
        CoroutineScope(Dispatchers.IO).launch {
            db.collection("business_metrics").document(userId).set(
                mapOf(
                    "direct" to directBiz,
                    "indirect" to indirectBiz,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        return directBiz to indirectBiz
    }

    /** Fetches referrals one level deeper, deduplicated. */
    private suspend fun fetchReferrals(
        ids: List<String>,
        processed: MutableSet<String>
    ): List<String> {
        val out = mutableListOf<String>()
        ids.chunked(10).forEach { batch ->
            val snap = db.collection("users")
                .whereIn("referralCode", batch)
                .get()
                .await()
            snap.documents.forEach { d ->
                d.getString("id")?.let { id ->
                    if (processed.add(id)) out.add(id)
                }
            }
        }
        return out
    }

    /** Sums deposits for a group of users; returns (depositSum, activeCount). */
    private suspend fun statsForUsers(ids: List<String>): Pair<Double, Int> {
        var deposit = 0.0
        var active = 0

        ids.chunked(10).forEach { batch ->
            val usersSnap = db.collection("users")
                .whereIn("id", batch)
                .get()
                .await()

            usersSnap.documents.forEach { u ->
                if ((u.getString("status") ?: "active") == "active") active++

                val accountSnap = db.collection("accounts")
                    .whereEqualTo("user_id", u.getString("id"))
                    .limit(1)
                    .get()
                    .await()

                val inv = accountSnap.documents.firstOrNull()?.get("investment") as? Map<*, *>
                deposit += (inv?.get("total_deposit") as? Number)?.toDouble() ?: 0.0
            }
        }
        return deposit to active
    }
}
