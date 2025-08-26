package com.codingEmpire.bitbloom.repos

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.codingEmpire.bitbloom.models.DashboardMetrics
import com.codingEmpire.bitbloom.models.TransactionModel
import com.codingEmpire.bitbloom.utils.PrefService
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

class DashboardRepo(private val context: Context) {

    private val db = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance()
    private val pref = PrefService(context)
    private val txnRepo = TransactionRepo()
    private val achieveRepo = AchievementsRepository()   // you already have this

    /* ─────────────────────────── Public API ─────────────────────────── */

    /** Pulls every metric used by the cards – **single network round-trip** per source. */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun fetchMetrics(): DashboardMetrics = coroutineScope {
        val uid = pref.getUserId() ?: error("UID missing in PrefService")

        // ► Fetch account doc
        val accountDeferred = async {
            db.collection("accounts").whereEqualTo("user_id", uid).limit(1)
                .get().await().documents.firstOrNull()
        }

        // ► Achievements (for Rank-Reward total)
        val rankDeferred = async {
            achieveRepo.getAchievements(uid)
                .filter { it.isCollected }
                .sumOf { it.salary }
        }

        // ► Today’s referral-bonus total
        val referralDeferred = async { sumTodayReferralBonus(uid) }

        // ► Daily earnings (ROI + Team for *today*)
        val dailyDeferred = async { calcTodayEarnings(uid) }
        val salaryBonusDeferred = async { calcTotalSalaryBonus(uid) }


        /* Wait for all parallel tasks */
        val accountSnap = accountDeferred.await()
        val rankRewardTotal = rankDeferred.await()
        val directBonusAmt = referralDeferred.await()
        val todayEarnings = dailyDeferred.await()
        val salaryBonusTotal = salaryBonusDeferred.await()

        /* Safe parsing of numbers from account doc */
        val earn = (accountSnap?.get("earnings") as? Map<*, *>)?.mapKeys { it.key.toString() }
            ?: emptyMap<String, Any>()
        val balance = earn["current_balance"] as? Number ?: 0
        val roiIncome = earn["buying_profit_team"] as? Number ?: 0
        val teamIncome = earn["lifetime_team_income"] as? Number ?: 0
        val totalEarned = earn["total_earned"] as? Number ?: 0
        val referralIncome = earn["referral_profit"] as? Number ?: 0

        DashboardMetrics(
            balance = balance.toDouble(),
            roiIncome = roiIncome.toDouble(),
            rankReward = rankRewardTotal,
            directActiveDeposit = directBonusAmt,
            teamIncome = teamIncome.toDouble(),
            salaryBonus = salaryBonusTotal,
            totalEarned = totalEarned.toDouble(),
            dailyEarnings = todayEarnings,
            referralIncome = referralIncome.toDouble()
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun sumTodayReferralBonus(userId: String): Double =
        withContext(Dispatchers.IO) {
            // build today’s start/end timestamps
            val zone = ZoneId.systemDefault()
            val todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant()
            val tomorrow = todayStart.plusSeconds(86_400)
            val startTS = Timestamp(Date.from(todayStart))
            val endTS = Timestamp(Date.from(tomorrow))

            // query referralProfitTxns
            val snap = db.collection("referralProfitTxns")
                .whereEqualTo("userId", userId)
                .whereEqualTo("status", "received")
                .whereGreaterThanOrEqualTo("timestamp", startTS)
                .whereLessThan("timestamp", endTS)
                .get()
                .await()

            // sum the amount field
            snap.documents
                .mapNotNull { (it["amount"] as? Number)?.toDouble() }
                .sum()
        }

    /** Merged list for the “Last Transaction” Recycler – *all time*, desc by date. */
    suspend fun fetchAllTransactions(): List<TransactionModel> = withContext(Dispatchers.IO) {
        val uid = pref.getUserId() ?: return@withContext emptyList()
        val all = mutableListOf<TransactionModel>()

        // Parallel fetch
        coroutineScope {
            listOf(
                async { txnRepo.getTransactionsForUser(uid) },
                async { txnRepo.getWithdrawForUser(uid) },
                async { txnRepo.getPlanTransactionsForUser(uid) },
                async { txnRepo.getRoiTransactionsForUser(uid) },
                async { txnRepo.getTeamTransactionsForUser(uid) }
            ).forEach { task -> task.await()?.let(all::addAll) }
        }
        all.sortedByDescending { it.timestamp?.toDate() }
    }

    /* ─────────────────────────── Helpers ─────────────────────────── */

    /** Calculates today’s earnings from roiTransactions + teamTransactions */
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun calcTodayEarnings(userId: String): Double = withContext(Dispatchers.IO) {
        val zone = ZoneId.systemDefault()
        val todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val tomorrow = todayStart.plusSeconds(86_400)
        val startTS = Timestamp(Date.from(todayStart))
        val endTS = Timestamp(Date.from(tomorrow))

        suspend fun sumToday(
            collection: String,
            okStatuses: List<String>,
            excludeType: String? = null
        ): Double {
            val snap = db.collection(collection)
                .whereEqualTo("userId", userId)
                .whereGreaterThanOrEqualTo("timestamp", startTS)
                .whereLessThan("timestamp", endTS)
                .get().await()

            return snap.documents
                .filter { doc ->
                    val status = (doc["status"] as? String).orEmpty()
                    val type = (doc["type"] as? String).orEmpty()
                    status in okStatuses
                            && (excludeType == null || type != excludeType)
                }
                .sumOf { (it["amount"] as? Number)?.toDouble() ?: .0 }
        }

        val roi = sumToday("roiTransactions", listOf("collected"), excludeType = "roiRefund")
        val team = sumToday("teamTransactions", listOf("received"))
        val referral = sumToday("referralProfitTxns", listOf("received"))

        roi + team + referral
    }

    /** NEW helper: sum all collected salary transactions for this user */
    private suspend fun calcTotalSalaryBonus(userId: String): Double = withContext(Dispatchers.IO) {
        val snap = db.collection("salaryTxns")
            .whereEqualTo("userId", userId)
            .whereEqualTo("status", "collected")
            .get().await()

        snap.documents
            .mapNotNull { (it["amount"] as? Number)?.toDouble() }
            .sum()
    }
}