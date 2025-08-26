package com.codingEmpire.bitbloom.models

data class PlanModel(
    val name: String = "",
    val minInvestment: Double = 0.0,
    val durationDays: Int = 0,
    val percentage: Double = 0.0,
    val directProfit: Double = 0.0,
    val bonusPercentage: Double = 0.0,
    val updatedAt: Any? = null
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "name" to name,
            "minInvestment" to minInvestment,
            "durationDays" to durationDays,
            "percentage" to percentage,
            "directProfit" to directProfit,
            "bonusPercentage" to bonusPercentage,
            "updatedAt" to updatedAt
        )
    }
}
