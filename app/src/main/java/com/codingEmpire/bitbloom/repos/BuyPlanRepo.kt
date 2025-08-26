package com.codingEmpire.bitbloom.repos

import android.util.Log
import com.codingEmpire.bitbloom.fcm.AccessToken
import com.codingEmpire.bitbloom.fcm.Fcm
import com.codingEmpire.bitbloom.models.BuyPlan
import com.codingEmpire.bitbloom.models.PlanModel
import com.codingEmpire.bitbloom.utils.PlanStatus
import com.codingEmpire.bitbloom.utils.PrefService
import com.codingEmpire.bitbloom.utils.TxnConstants
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Date

class BuyPlanRepo(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val prefService: PrefService
) {

    /**
     * Transactionally adds referral profit to a referrer if they are active,
     * and logs the payout in `referralProfitTxns`.
     */
    suspend fun updateReferrerReferralProfit(refCode: String, amount: Double) {
        val triggeredById = prefService.getUserId() ?: "unknown"

        try {
            // 1) Fetch the referrer's account & user doc
            val accountSnap = firestore.collection("accounts")
                .whereEqualTo("user_id", refCode)
                .limit(1)
                .get()
                .await()
            val userSnap = firestore.collection("users")
                .whereEqualTo("id", refCode)
                .limit(1)
                .get()
                .await()

            if (accountSnap.isEmpty || userSnap.isEmpty) {
                Log.w("BuyPlanRepo", "❌ Referrer not found: $refCode")
                return
            }

            val accountRef = accountSnap.documents.first().reference
            val userData = userSnap.documents.first().data ?: return

            // 2) Ensure referrer is active
            val status = userData["status"]?.toString()?.lowercase() ?: "inactive"
            if (status != "active") {
                val name = userData["name"]?.toString() ?: "unknown"
                Log.w("BuyPlanRepo", "❌ Referrer inactive ($name) — skipping profit update.")
                return
            }

            // 3) Run one transaction: update balances + write txn log
            firestore.runTransaction { tx ->
                // 3a) Load existing earnings/investment
                val snap = tx.get(accountRef)
                val data = snap.data ?: return@runTransaction
                val earnings =
                    (data["earnings"] as? Map<String, Any>)?.toMutableMap() ?: mutableMapOf()
                val invest =
                    (data["investment"] as? Map<String, Any>)?.toMutableMap() ?: mutableMapOf()

                val prevRefProfit = (earnings["referral_profit"] as? Number)?.toDouble() ?: 0.0
                val prevTotalEarn = (earnings["total_earned"] as? Number)?.toDouble() ?: 0.0
                val prevBalance = (earnings["current_balance"] as? Number)?.toDouble() ?: 0.0
                val prevRemBalance = (invest["remaining_balance"] as? Number)?.toDouble() ?: 0.0

                // 3b) Prevent duplicate
                if (prevRefProfit + amount - prevRefProfit < 0.01) {
                    Log.w("BuyPlanRepo", "⚠️ Duplicate referral update detected — skipping.")
                    return@runTransaction
                }

                // 3c) Update earnings & investment
                tx.update(
                    accountRef, mapOf(
                        "earnings.referral_profit" to prevRefProfit + amount,
                        "earnings.total_earned" to prevTotalEarn + amount,
                        "earnings.current_balance" to prevBalance + amount,
                        "investment.remaining_balance" to prevRemBalance + amount,
                        "earnings.lifetime_referral_income" to FieldValue.increment(amount),
                    )
                )

                // 3d) Log the referral-profit transaction
                val now = Timestamp.now()
                val txnRef = firestore.collection("referralProfitTxns").document()
                val txnData = mapOf(
                    TxnConstants.FIELD_ID to txnRef.id,
                    TxnConstants.FIELD_USER_ID to refCode,
                    TxnConstants.FIELD_AMOUNT to amount,
                    TxnConstants.FIELD_TYPE to "Plan Bonus ",
                    TxnConstants.FIELD_STATUS to "received",
                    TxnConstants.TRIGGERED_BY to triggeredById,
                    TxnConstants.FIELD_TIMESTAMP to now
                )
                tx.set(txnRef, txnData)

            }.await()

            Log.d(
                "BuyPlanRepo",
                "✅ Added $$amount referral bonus to $refCode and logged in referralProfitTxns"
            )

            // 4) Send FCM notification if deviceToken exists
            userData["deviceToken"]?.toString()?.takeIf { it.isNotBlank() }?.let { token ->
                val referrerName = userData["name"]?.toString().orEmpty()
                val triggeredByName = prefService.getName().orEmpty()
                val title = "Referral Bonus Received"
                val body =
                    "Hi $referrerName! You earned $.$amount as a Plan Bonus from $triggeredByName (ID: $triggeredById)."
                 Log.d("BuyPlanRepo", "Sending FCM notification to Hi $referrerName! You earned Rs.$amount as a Plan Bonus from $triggeredByName (ID: $triggeredById).")
                AccessToken.getAccessTokenAsync(object : AccessToken.AccessTokenCallback {
                    override fun onAccessTokenReceived(tokenStr: String?) {
                        tokenStr?.let {
                            Fcm().sendFCMNotification(
                                targetDeviceToken = token,
                                title = title,
                                body = body,
                                accessToken = it
                            )
                        }
                    }
                })
            }

        } catch (e: Exception) {
            Log.e("BuyPlanRepo", "❌ Error updating referrer referral profit: $e", e)
        }
    }

    /**
     * Fetches plan details by name.
     */
    private suspend fun getPlanDetails(planName: String): Map<String, Any>? {
        return try {
            Log.d("BuyPlanRepo", "Fetching plan details for plan: $planName")
            val snapshot = firestore.collection("plans")
                .whereEqualTo("name", planName)
                .limit(1)
                .get()
                .await()

            if (snapshot.documents.isNotEmpty()) {
                snapshot.documents.first().data
            } else {
                Log.w("BuyPlanRepo", "❌ Plan not found for plan: $planName")
                null
            }
        } catch (e: Exception) {
            Log.e("BuyPlanRepo", "❌ Error fetching plan details: $e", e)
            null
        }
    }

    /**
     * Returns a list of all available plans.
     */
    suspend fun getAvailablePlans(): List<PlanModel> {
        return try {
            val snap = firestore.collection("plans")
                .get()
                .await()

            // map each Firestore doc into your PlanModel
            snap.documents.map { doc ->
                PlanModel(
                    name = doc.getString("name") ?: "",
                    minInvestment = doc.getDouble("minInvestment") ?: 0.0,
                    durationDays = (doc.getLong("durationDays") ?: 0L).toInt(),
                    percentage = doc.getDouble("percentage") ?: 0.0,
                    directProfit = doc.getDouble("directProfit") ?: 0.0,
                    bonusPercentage = doc.getDouble("bonusPercentage") ?: 0.0,
                    updatedAt = doc.get("updatedAt")
                )
            }
        } catch (e: Exception) {
            Log.e("BuyPlanRepo", "❌ getAvailablePlans error → $e", e)
            emptyList()
        }
    }

    /**
     * Purchases a plan for the user. Returns status of the operation.
     */
    suspend fun buyPlan(userId: String, amount: Double, planName: String, autoInvest: Boolean): PlanStatus {
        if (userId.isBlank()) return PlanStatus.NoUserFound

        val planDetails = getPlanDetails(planName) ?: return PlanStatus.NoPlanFound

        val durationDays = (planDetails["durationDays"] as? Number)?.toInt()
            ?: planDetails["durationDays"]?.toString()?.toIntOrNull() ?: 0
        val directProfitPercent = (planDetails["directProfit"] as? Number)?.toDouble() ?: 0.0
        val dailyProfitPercent = (planDetails["percentage"] as? Number)?.toDouble() ?: 0.0
        val minInvestment = (planDetails["minInvestment"] as? Number)?.toDouble() ?: 0.0
        val bonusPercent = (planDetails["bonusPercentage"] as? Number)?.toDouble() ?: 0.0

        val adjustedAmount = if (bonusPercent > 0) {
            amount + (amount * bonusPercent / 100)
        } else amount

        val calculatedDirectProfit = adjustedAmount * directProfitPercent / 100
        val calculatedDailyProfit = adjustedAmount * dailyProfitPercent / 100

        val accSnap = firestore.collection("accounts")
            .whereEqualTo("user_id", userId)
            .limit(1)
            .get()
            .await()
        val accDoc = accSnap.documents.firstOrNull() ?: return PlanStatus.NoUserFound
        val accRef = accDoc.reference

        val userSnap = firestore.collection("users")
            .whereEqualTo("id", userId)
            .limit(1)
            .get()
            .await()
        val userDoc = userSnap.documents.firstOrNull() ?: return PlanStatus.NoUserFound
        val userRef = userDoc.reference
        val referralCode = userDoc.getString("referralCode")

        var status: PlanStatus = PlanStatus.Error

        try {
            firestore.runTransaction { tx ->
                val freshAccDoc = tx.get(accRef)
                val investment = freshAccDoc.get("investment") as? Map<*, *> ?: emptyMap<String, Any>()
                val earnings = freshAccDoc.get("earnings") as? Map<*, *> ?: emptyMap<String, Any>()
                val remainingBalance = (investment["remaining_balance"] as? Number)?.toDouble() ?: 0.0
                val currentBalance = (earnings["current_balance"] as? Number)?.toDouble() ?: 0.0

                if (amount < minInvestment) {
                    status = PlanStatus.InvalidAmount
                    return@runTransaction
                }
                if (remainingBalance < amount) {
                    status = PlanStatus.NotEnoughBalance
                    return@runTransaction
                }

                val now = Timestamp.now()
                val expiry = Timestamp(Date().apply {
                    time = now.toDate().time + durationDays * 24L * 60L * 60L * 1000L
                })
                val newPlanRef = firestore.collection("userPlans").document()
                tx.set(
                    newPlanRef, mapOf(
                        "user_id" to userId,
                        "plan_name" to planName,
                        "invested_amount" to adjustedAmount,
                        "direct_profit" to calculatedDirectProfit,
                        "daily_profit" to calculatedDailyProfit,
                        "percentage" to dailyProfitPercent,
                        "directProfitPercent" to directProfitPercent,
                        "start_date" to now,
                        "expiry_date" to expiry,
                        "PlanStatus" to "active",
                        "lastCollectedDate" to now,
                        "durationDays" to durationDays,
                        "autoInvest" to autoInvest,
                    )
                )

                tx.update(
                    accRef, mapOf(
                        "earnings.current_balance" to (currentBalance - amount),
                        "investment.remaining_balance" to (remainingBalance - amount)
                    )
                )

                if (durationDays >= 30) {
                    tx.update(
                        userRef, mapOf(
                            "PlanStatus" to "active",
                            "status" to "active"
                        )
                    )
                }

                val txnRef = firestore.collection("plansTransactions").document()
                tx.set(
                    txnRef, mapOf(
                        TxnConstants.FIELD_ID to txnRef.id,
                        TxnConstants.FIELD_USER_ID to userId,
                        TxnConstants.FIELD_AMOUNT to amount,
                        TxnConstants.FIELD_TYPE to TxnConstants.TYPE_INVESTMENT_BOUGHT,
                        TxnConstants.FIELD_ADDRESS to newPlanRef.id,
                        TxnConstants.FIELD_STATUS to TxnConstants.STATUS_BOUGHT,
                        TxnConstants.FIELD_BALANCE_UPDATED to true,
                        TxnConstants.FIELD_TIMESTAMP to now,
                        TxnConstants.FIELD_PLAN_NAME to planName,
                    )
                )

                status = PlanStatus.Success
            }.await()

            Log.d("BuyPlanRepo", "✅ Plan purchased successfully for $userId")

            if (!referralCode.isNullOrBlank() && calculatedDirectProfit > 0 && status== PlanStatus.Success) {
                updateReferrerReferralProfit(referralCode, calculatedDirectProfit)
            }

            return status
        } catch (e: Exception) {
            Log.e("BuyPlanRepo", "❌ Error in buyPlan", e)
            return PlanStatus.Error
        }
    }

    /**
     * Updates the user’s PlanStatus and status fields to “active”.
     */
    private suspend fun updateUserPlanStatus(userId: String) {
        try {
            // 1. Query the user document
            val userSnap = firestore.collection("users")
                .whereEqualTo("id", userId)
                .limit(1)
                .get()
                .await()

            // 2. If no such user, bail out
            val userDoc = userSnap.documents.firstOrNull() ?: return

            // 3. Update both fields atomically
            userDoc.reference
                .update(
                    mapOf(
                        "PlanStatus" to "active",
                        "status" to "active"
                    )
                )
                .await()

            Log.d("BuyPlanRepo", "✅ User PlanStatus and status set to active.")
        } catch (e: Exception) {
            Log.e("BuyPlanRepo", "❌ User PlanStatus update failed: $e", e)
        }
    }

    /**
     * Returns a list of all purchased plans for the given user.
     */
    suspend fun getPurchasedPlans(userId: String): List<BuyPlan> {
        return try {
            if (userId.isBlank()) return emptyList()
            val snap = firestore.collection("userPlans")
                .whereEqualTo("user_id", userId)
                .get()
                .await()
            snap.documents.mapNotNull { BuyPlan.Companion.fromDocument(it) }.sortedByDescending { it.startDate }
        } catch (e: Exception) {
            Log.e("BuyPlanRepo", "❌ Error fetching purchased plans: $e", e)
            emptyList()
        }
    }
}