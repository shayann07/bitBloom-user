package com.codingEmpire.bitbloom.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

data class Account(
    val userId: String,
    val accountId: String,
    val status: String,
    val createdAt: Date,
    val investment: Investment,
    val earnings: Earnings,
    val plans: List<Map<String, Any>>
) {
    fun toMap(): Map<String, Any> = mapOf(
        "user_id" to userId,
        "account_id" to accountId,
        "status" to status,
        "created_at" to Timestamp(createdAt),
        "investment" to investment.toMap(),
        "earnings" to earnings.toMap(),
        "plans" to plans
    )

    companion object {
        fun fromDocument(doc: DocumentSnapshot): Account = Account(
            userId = doc.getString("user_id") ?: "",
            accountId = doc.getString("account_id") ?: "",
            status = doc.getString("status") ?: "",
            createdAt = doc.getTimestamp("created_at")?.toDate() ?: Date(),
            investment = Investment.fromMap(
                doc.get("investment") as? Map<String, Any> ?: emptyMap()
            ),
            earnings = Earnings.fromMap(doc.get("earnings") as? Map<String, Any> ?: emptyMap()),
            plans = doc.get("plans") as? List<Map<String, Any>> ?: emptyList()
        )
    }
}

data class Investment(
    val totalDeposit: Double,
    val remainingBalance: Double,
    val stakeProfit: Double
) {
    fun toMap(): Map<String, Any> = mapOf(
        "total_deposit" to totalDeposit,
        "remaining_balance" to remainingBalance,
        "stake_profit" to stakeProfit
    )

    companion object {
        fun fromMap(data: Map<String, Any>): Investment = Investment(
            totalDeposit = (data["total_deposit"] as? Number)?.toDouble() ?: 0.0,
            remainingBalance = (data["remaining_balance"] as? Number)?.toDouble() ?: 0.0,
            stakeProfit = (data["stake_profit"] as? Number)?.toDouble() ?: 0.0
        )
    }
}

data class Earnings(
    val dailyProfit: Double,
    val currentBalance: Double,
    val buyingProfit: Double,
    val referralProfit: Double,
    val totalEarned: Double,
    val teamProfit: Double
) {
    fun toMap(): Map<String, Any> = mapOf(
        "daily_profit" to dailyProfit,
        "current_balance" to currentBalance,
        "buying_profit" to buyingProfit,
        "referral_profit" to referralProfit,
        "total_earned" to totalEarned,
        "team_profit" to teamProfit
    )

    companion object {
        fun fromMap(data: Map<String, Any>): Earnings = Earnings(
            dailyProfit = (data["daily_profit"] as? Number)?.toDouble() ?: 0.0,
            currentBalance = (data["current_balance"] as? Number)?.toDouble() ?: 0.0,
            buyingProfit = (data["buying_profit"] as? Number)?.toDouble() ?: 0.0,
            referralProfit = (data["referral_profit"] as? Number)?.toDouble() ?: 0.0,
            totalEarned = (data["total_earned"] as? Number)?.toDouble() ?: 0.0,
            teamProfit = (data["team_profit"] as? Number)?.toDouble() ?: 0.0
        )
    }
}