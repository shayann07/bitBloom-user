package com.codingEmpire.bitbloom.repos

import android.content.Context
import android.util.Log
import com.codingEmpire.bitbloom.models.TeamLevel
import com.codingEmpire.bitbloom.models.TeamUser
import com.codingEmpire.bitbloom.utils.PrefService
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

class TeamLevelRepo(private val context: Context) {

    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()

    /** Fetches the 6-level tree from the Cloud Function and maps it to Kotlin models. */
    suspend fun fetchTeamLevels(): List<TeamLevel> = withContext(Dispatchers.IO) {
        val pref = PrefService(context)
        val userId = pref.getUserId() ?: error("User ID not found in PrefService")

        val raw = functions
            .getHttpsCallable("getTeamLevels")
            .call(hashMapOf("userId" to userId))
            .await()
            .data as Map<*, *>

        (1..6).map { lvl ->
            val node = (raw[lvl.toString()] as? Map<*, *>) ?: emptyMap<String, Any>()
            TeamLevel(
                level = lvl,
                totalUsers = num(node["totalUsers"]),
                activeUsers = num(node["activeUsers"]),
                inactiveUsers = num(node["inactiveUsers"]),
                totalDeposit = dbl(node["totalDeposit"]),
                totalBuyingProfit = dbl(node["totalBuyingProfit"]),
                investedAmount = dbl(node["investedAmount"]),  // ← NEW
                levelUnlocked = node["levelUnlocked"] as? Boolean ?: false
            )
        }
    }

    suspend fun fetchLevelUsers(level: Int): List<TeamUser> = withContext(Dispatchers.IO) {
        require(level in 1..6) { "level must be 1–6" }
        val userId = PrefService(context).getUserId() ?: error("User ID not found")
        val res = functions.getHttpsCallable("getTeamLevels")
            .call(mapOf("userId" to userId, "level" to level))
            .await().data as Map<*, *>

        val rawUsers = res["users"] as List<Map<*, *>>
        rawUsers.map {
            TeamUser(
                userId = it["userId"] as String,
                name = it["name"] as String,
                status = it["status"] as String
            )
        }
    }

    suspend fun canUserWithdraw(): Boolean = withContext(Dispatchers.IO) {
        try {
            val userId = PrefService(context).getUserId() ?: return@withContext false

            val res = functions.getHttpsCallable("getTeamLevels")
                .call(mapOf("userId" to userId, "level" to 1))
                .await()
                .data as Map<*, *>

            @Suppress("UNCHECKED_CAST")
            val users = res["users"] as? List<Map<*, *>> ?: return@withContext false

            // 08-Jul-2025 00:00:00 (local timezone doesn’t matter – we compare dates, not times)
            val cutoff = Calendar.getInstance().apply {
                set(2025, Calendar.JULY, 8, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.time

            users.any { u ->
                // ── robust Timestamp deserialisation ───────────────────────────
                val ts = when (val raw = u["createdAt"]) {
                    is com.google.firebase.Timestamp -> raw.toDate()                      // direct
                    is Map<*, *> -> {                                                    // _seconds / _nanoseconds
                        val secs  = (raw["seconds"] ?: raw["_seconds"]) as? Number
                        val nanos = (raw["nanoseconds"] ?: raw["_nanoseconds"]) as? Number ?: 0
                        if (secs != null) com.google.firebase.Timestamp(secs.toLong(), nanos.toInt()).toDate()
                        else null
                    }
                    is Number -> Date(raw.toLong())                                      // millis
                    is String -> runCatching { Date(raw.toLong()) }.getOrNull()          // millis as String
                    else -> null
                }
                val active = (u["status"] as? String)?.equals("active", true) == true
                ts != null && !ts.before(cutoff) && active
            }
        } catch (e: Exception) {
            Log.e("TeamLevelRepo", "eligibility check failed", e)
            false
        }
    }

    private fun num(v: Any?) = (v as? Number)?.toInt() ?: 0
    private fun dbl(v: Any?) = (v as? Number)?.toDouble() ?: .0
}