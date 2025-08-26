package com.codingEmpire.bitbloom.repos


import android.content.Context
import com.codingEmpire.bitbloom.models.TopLeader
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class LeaderboardRepo(private val context: Context) {
    private val levelRepo = TeamLevelRepo(context)
    private val db = FirebaseFirestore.getInstance()

    /** Fetches direct & team business from cloud function summary. */
    suspend fun fetchBusiness(): Pair<Double, Double> {
        val levels = levelRepo.fetchTeamLevels()
        val direct = levels.firstOrNull { it.level == 1 }?.investedAmount ?: 0.0
        val team = levels.sumOf { it.investedAmount }
        return direct to team
    }

    /** Fetch top‐10 leaders from Firestore, ordered by `rank`. */
    suspend fun fetchTopLeaders(): List<TopLeader> {
        val snap = db.collection("top_leaders")
            .orderBy("rank")
            .limit(10)
            .get()
            .await()
        return snap.documents.mapNotNull { doc ->
            val id = doc.getString("id") ?: return@mapNotNull null
            val rank = doc.getLong("rank")?.toInt() ?: return@mapNotNull null
            val bus = doc.getDouble("total_business") ?: 0.0
            TopLeader(id, rank, bus)
        }
    }
}
