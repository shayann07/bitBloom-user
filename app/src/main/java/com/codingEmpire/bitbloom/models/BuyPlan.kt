package com.codingEmpire.bitbloom.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

data class BuyPlan(
    val userId: String,
    val planName: String,
    val investedAmount: Double,
    val percentage: Double,            // Firestore “percentage” (daily %)
    val dailyProfit: Double,           // Firestore “daily_profit”
    val directProfitPercent: Double,   // Firestore “directProfitPercent”
    val directProfit: Double,          // Firestore “direct_profit”
    val durationDays: Int,             // Firestore “durationDays”
    val startDate: Date,               // Firestore “start_date”
    val expiryDate: Date,              // Firestore “expiry_date”
    val lastCollectedDate: Date?,      // Firestore “lastCollectedDate”
    val planStatus: String             // Firestore “PlanStatus”
) {
    fun toMap(): Map<String, Any> = mapOf(
        "user_id"              to userId,
        "plan_name"            to planName,
        "invested_amount"      to investedAmount,
        "percentage"           to percentage,
        "daily_profit"         to dailyProfit,
        "directProfitPercent"  to directProfitPercent,
        "direct_profit"        to directProfit,
        "durationDays"         to durationDays,
        "start_date"           to Timestamp(startDate),
        "expiry_date"          to Timestamp(expiryDate),
        "lastCollectedDate"    to (lastCollectedDate?.let { Timestamp(it) } ?: Timestamp(startDate)),
        "PlanStatus"           to planStatus
    )

    companion object {
        fun fromDocument(doc: DocumentSnapshot): BuyPlan = BuyPlan(
            userId               = doc.getString("user_id") ?: "",
            planName             = doc.getString("plan_name") ?: "",
            investedAmount       = doc.getDouble("invested_amount") ?: 0.0,
            percentage           = doc.getDouble("percentage") ?: 0.0,
            dailyProfit          = doc.getDouble("daily_profit") ?: 0.0,
            directProfitPercent  = doc.getDouble("directProfitPercent") ?: 0.0,
            directProfit         = doc.getDouble("direct_profit") ?: 0.0,
            durationDays         = (doc.getLong("durationDays") ?: 0L).toInt(),
            startDate            = doc.getTimestamp("start_date")?.toDate() ?: Date(),
            expiryDate           = doc.getTimestamp("expiry_date")?.toDate() ?: Date(),
            lastCollectedDate    = doc.getTimestamp("lastCollectedDate")?.toDate(),
            planStatus           = doc.getString("PlanStatus") ?: ""
        )
    }
}
