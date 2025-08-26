package com.codingEmpire.bitbloom.repos


import android.os.Build
import androidx.annotation.RequiresApi
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val STARTER_PACK_TOKENS = 50

class DailyRewardRepo(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val logsRoot = firestore.collection("daily_reward_logs")
    private val progressRoot = firestore.collection("daily_reward_progress")

    @RequiresApi(Build.VERSION_CODES.O)
    private val fmt = DateTimeFormatter.ISO_LOCAL_DATE

    /** Returns (lastClaimDate, lastDay) or (null,0) if no record */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getProgress(userId: String): Pair<LocalDate?, Int> {
        val doc = progressRoot.document(userId).get().await()
        return if (doc.exists()) {
            val dateString = doc.getString("lastClaimDate")
            val d = dateString?.let { LocalDate.parse(it, fmt) }
            val day = (doc.getLong("lastDay") ?: 0L).toInt()
            d to day
        } else {
            null to 0
        }
    }

    /** Sum of all tokens ever logged */
    suspend fun getTotalTokens(userId: String): Int {
        val snap = logsRoot.document(userId).collection("logs").get().await()
        return snap.documents.sumOf { (it.getLong("tokens") ?: 0L).toInt() }
    }

    /** Claim today’s reward: writes a log and updates progress atomicly */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun claimReward(
        userId: String, day: Int, tokens: Int, today: LocalDate
    ) {
        val batch = firestore.batch()
        val logRef = logsRoot.document(userId).collection("logs").document()
        // Use client timestamp (local date at midnight)
        val instant = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
        batch.set(
            logRef, mapOf(
                "day" to day, "tokens" to tokens, "claimedAt" to Timestamp(instant.epochSecond, 0)
            )
        )
        val progRef = progressRoot.document(userId)
        batch.set(
            progRef, mapOf(
                "lastClaimDate" to today.format(fmt), "lastDay" to day
            ), SetOptions.merge()
        )
        batch.commit().await()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun hasClaimedStarterPack(uid: String): Boolean =
        progressRoot.document(uid).get().await().getBoolean("welcomeTokensClaimed") == true

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun claimStarterPack(uid: String) {
        firestore.runTransaction { tx ->
            val progRef = progressRoot.document(uid)
            val progDoc = tx.get(progRef)

            if (progDoc.getBoolean("welcomeTokensClaimed") == true) return@runTransaction

            // 1️⃣ log the credit
            val logRef = logsRoot.document(uid).collection("logs").document()
            tx.set(
                logRef, mapOf(
                    "day" to 0,                      // special code – not part of 7-day streak
                    "tokens" to STARTER_PACK_TOKENS, "claimedAt" to Timestamp.now()
                )
            )

            // 2️⃣ flip the flag
            tx.set(
                progRef, mapOf("welcomeTokensClaimed" to true), SetOptions.merge()
            )
        }.await()
    }
}