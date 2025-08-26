package com.codingEmpire.bitbloom.repos

// ✅ this is the Android Timestamp class

import android.content.Context
import android.util.Log
import com.codingEmpire.bitbloom.models.CryptoPrice
import com.codingEmpire.bitbloom.models.CryptoResponse
import com.codingEmpire.bitbloom.utils.PrefService
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
data class WalletTotals(
    val grandTotal: Double,   // balance + XBLM(USDT) + Lucky-Spin USDT
    val tokenValue: Double,   // XBLM → USDT only
    val spinUsdt:   Double    // Lucky-Spin pot
)
class WalletRepo(context: Context) {

    private val prefService = PrefService(context)
    private val withdrawRepo = WithdrawRepo()
    private val firestore = FirebaseFirestore.getInstance()
    private var walletListener: ListenerRegistration? = null
    private val logsRoot = firestore.collection("daily_reward_logs")

    suspend fun getWalletTotals(): WalletTotals = withContext(Dispatchers.IO) {
        val userId = prefService.getString("user_id") ?: return@withContext WalletTotals(0.0, 0.0, 0.0)

        /* 1️⃣  Balance */
        val wallet   = fetchWalletData()
        val balance  = (wallet?.get("balance") as? Double) ?: 0.0

        /* 2️⃣  XBLM → USDT value */
        val tokensSnap = firestore.collection("daily_reward_logs")
            .document(userId)
            .collection("logs")
            .get()
            .await()
        val totalTokens = tokensSnap.documents.sumOf { (it.getLong("tokens") ?: 0L).toDouble() }

        val rateSnap = firestore.collection("XBLM_rate")
            .document("live")
            .get()
            .await()
        val rate       = rateSnap.getDouble("rate") ?: 0.0
        val tokenValue = totalTokens * rate

        /* 3️⃣  Lucky-Spin USDT pot */
        val spinSnap = firestore.collection("luckySpins")
            .document(userId)
            .get()
            .await()
        val spinUsdt = spinSnap.getDouble("total") ?: 0.0

        /* 4️⃣  Grand total */
        val grandTotal = balance + tokenValue + spinUsdt

        Log.d("WalletRepo", "💰 balance=$balance, tokenValue=$tokenValue, spinUsdt=$spinUsdt, grandTotal=$grandTotal")

        WalletTotals(grandTotal, tokenValue, spinUsdt)
    }
    // Fetch Wallet Data (Total Deposit, Profit, Salary/Income, Recent Withdrawals, Block Status)
    suspend fun fetchWalletData(): Map<String, Any?>? {
        val userId = prefService.getString("user_id")
        if (userId.isNullOrBlank()) {
            Log.e("WalletRepo", "❌ No user ID found in SharedPreferences!")
            return null
        }
        withdrawRepo.getWithdraws(userId)
        // 1) Load account doc
        val snapshot = firestore.collection("accounts")
            .whereEqualTo("user_id", userId)
            .limit(1)
            .get()
            .await()
        if (snapshot.isEmpty) {
            Log.e("WalletRepo", "❌ No account for user_id=$userId")
            return null
        }

        val doc = snapshot.documents.first()
        val data = doc.data ?: return null
        val invest = (data["investment"] as? Map<*, *>)?.mapKeys { it.key.toString() } ?: emptyMap()
        val earn = (data["earnings"] as? Map<*, *>)?.mapKeys { it.key.toString() } ?: emptyMap()

        // 2) Extract numeric fields
        val totalDeposit = (invest["total_deposit"] as? Number)?.toDouble() ?: 0.0
        val buyingProfit = (earn["buying_profit_team"] as? Number)?.toDouble() ?: 0.0
        val dailyProfit = (earn["daily_profit"] as? Number)?.toDouble() ?: 0.0
        val totalEarned = (earn["total_earned"] as? Number)?.toDouble() ?: 0.0
        val totalBalance = (earn["current_balance"] as? Number)?.toDouble() ?: 0.0
        val referralProfit = (earn["referral_profit"] as? Number)?.toDouble() ?: 0.0
        val stakeProfit = (invest["stake_profit"] as? Number)?.toDouble() ?: 0.0
        val teamProfit = (earn["team_profit"] as? Number)?.toDouble() ?: 0.0
        val salary = (earn["salary"] as? Number)?.toDouble() ?: 0.0

        val profit = buyingProfit + dailyProfit + referralProfit + stakeProfit + teamProfit

        // 3) Load sub-lists
        val isBlocked = isUserBlocked(userId)

        // 4) Return full map
        return mapOf(
            "totalDeposit" to totalDeposit,
            "balance" to totalBalance,
            "profit" to profit,
            "salary" to salary,
            "isBlocked" to isBlocked,
            "totalEarnedProfit" to totalEarned,
            "buyingProfit" to buyingProfit,
            "teamProfit" to teamProfit
        )
    }

    // Fetch recent withdrawals from Firestore
    suspend fun fetchRecentWithdrawals(userId: String): List<Map<String, Any?>> {
        return withdrawRepo.getWithdraws(userId)
            ?.map { data ->
                val ts = data["timestamp"] as? Timestamp
                mapOf(
                    "date" to (ts?.toDate()?.toString() ?: "Unknown"),
                    "amount" to (data["amount"] as? Number)?.toDouble(),
                    "status" to data["status"] as? String
                )
            } ?: emptyList()
    }

    // Fetch recent deposits from Firestore
    suspend fun fetchRecentDeposits(userId: String): List<Map<String, Any?>> {
        val snap = firestore.collection("deposits")
            .whereEqualTo("user_id", userId)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(5)
            .get()
            .await()

        return snap.documents.map { doc ->
            val ts = doc.getTimestamp("timestamp")    // uses firestore.Timestamp
            mapOf(
                "date" to (ts?.toDate()?.toString() ?: "Unknown"),
                "amount" to doc.getDouble("amount"),
                "status" to doc.getString("status")
            )
        }
    }

    fun listenToWalletUpdates(onUpdate: (Map<String, Any?>?) -> Unit) {
        val userId = prefService.getString("user_id")

        firestore.collection("accounts")
            .whereEqualTo("user_id", userId)
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || snapshot.isEmpty) {
                    onUpdate(null)
                    return@addSnapshotListener
                }

                val data = snapshot.documents.first().data ?: return@addSnapshotListener
                val invest =
                    (data["investment"] as? Map<*, *>)?.mapKeys { it.key.toString() } ?: emptyMap()
                val earn =
                    (data["earnings"] as? Map<*, *>)?.mapKeys { it.key.toString() } ?: emptyMap()

                val totalDeposit = (invest["total_deposit"] ?: 0) as Number
                val buyingProfit = (earn["buying_profit_team"] ?: 0) as Number
                val dailyProfit = (earn["daily_profit"] ?: 0) as Number
                val totalEarned = (earn["total_earned"] ?: 0) as Number
                val totalBalance = (earn["current_balance"] ?: 0) as Number
                val referralProfit = (earn["referral_profit"] ?: 0) as Number
                val stakeProfit = (invest["stake_profit"] ?: 0) as Number
                val teamProfit = (earn["team_profit"] ?: 0) as Number
                val salary = (earn["salary"] ?: 0) as Number

                val profit =
                    buyingProfit.toDouble() + dailyProfit.toDouble() + referralProfit.toDouble() +
                            stakeProfit.toDouble() + teamProfit.toDouble()

                onUpdate(
                    mapOf(
                        "totalDeposit" to totalDeposit.toDouble(),
                        "balance" to totalBalance.toDouble(),
                        "profit" to profit,
                        "salary" to salary.toDouble(),
                        "totalEarnedProfit" to totalEarned.toDouble(),
                        "buyingProfit" to buyingProfit.toDouble(),
                        "teamProfit" to teamProfit.toDouble()
                    )
                )
            }
    }
    suspend fun getTotalWalletAndTokenValue(): Pair<Double, Double> {
        val userId = prefService.getString("user_id") ?: return Pair(0.0, 0.0)

        // 1. Get balance
        val wallet = fetchWalletData()
        val balance = (wallet?.get("balance") as? Double) ?: 0.0

        // 2. Get total tokens from logs
        val tokensSnap = firestore.collection("daily_reward_logs")
            .document(userId)
            .collection("logs")
            .get()
            .await()
        val totalTokens = tokensSnap.documents.sumOf { (it.getLong("tokens") ?: 0L).toDouble() }

        // 3. Get XBLM rate
        val rateSnap = firestore.collection("XBLM_rate")
            .document("live")
            .get()
            .await()
        val rate = rateSnap.getDouble("rate") ?: 0.0
        val tokenValue = totalTokens * rate
        val total = balance + tokenValue

        Log.d("WalletRepo", "💰 balance=$balance, tokens=$totalTokens, rate=$rate")
        Log.d("WalletRepo", "💰 tokenValue=$tokenValue, totalWallet=$total")

        return Pair(total, tokenValue)
    }

    fun listenToXBLMRateAndPct(onUpdate: (Double, Double) -> Unit) {
        firestore.collection("XBLM_rate")
            .document("live")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("WalletRepo", "❌ Snapshot error for XBLM_rate", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val rate = snapshot.getDouble("rate") ?: 0.0
                    val pct = snapshot.getDouble("pct") ?: 0.0
                    Log.d("WalletRepo", "📈 Live XBLM rate = $rate, pct = $pct")
                    onUpdate(rate, pct)
                }
            }
    }



    fun removeWalletListener() {
        walletListener?.remove()
        walletListener = null
    }

    // Fetch whether the user is blocked
    suspend fun isUserBlocked(userId: String): Boolean {
        return try {
            val userDoc = firestore.collection("users").document(userId).get().await()
            userDoc.getBoolean("isBlocked") ?: false
        } catch (e: Exception) {
            Log.e("WalletRepo", "Error fetching block status: $e")
            false
        }
    }

    suspend fun fetchCryptoPrices(): CryptoResponse? = withContext(Dispatchers.IO) {
        val url =
            "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,ethereum,tether&vs_currencies=usd&include_24hr_change=true"

        try {
            val request = Request.Builder().url(url).build()
            val response = OkHttpClient().newCall(request).execute()

            if (!response.isSuccessful) {
                Log.e("WalletRepo", "❌ API request failed: ${response.code}")
                return@withContext null
            }

            val json = response.body?.string()
            if (json.isNullOrEmpty()) {
                Log.e("WalletRepo", "❌ Empty JSON response")
                return@withContext null
            }

            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()

            val type = Types.newParameterizedType(
                Map::class.java,
                String::class.java,
                CryptoPrice::class.java
            )
            val adapter = moshi.adapter<CryptoResponse>(type)

            return@withContext adapter.fromJson(json)
        } catch (e: Exception) {
            Log.e("WalletRepo", "❌ Exception while fetching crypto prices", e)
            return@withContext null
        }
    }
}
