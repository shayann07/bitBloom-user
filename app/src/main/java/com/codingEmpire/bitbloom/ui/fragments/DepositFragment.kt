package com.codingEmpire.bitbloom.ui.fragments

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues.TAG
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.compose.ui.res.colorResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.codingEmpire.bitbloom.R
import com.codingEmpire.bitbloom.adapters.TransactionAdapter
import com.codingEmpire.bitbloom.adapters.TransactionPagerAdapter
import com.codingEmpire.bitbloom.databinding.FragmentDepositBinding
import com.codingEmpire.bitbloom.models.TransactionModel
import com.codingEmpire.bitbloom.utils.PrefService
import com.codingEmpire.bitbloom.utils.SoundManager
import com.codingEmpire.bitbloom.utils.TourHelper
import com.codingEmpire.bitbloom.utils.TourTarget
import com.codingEmpire.bitbloom.utils.TransactionDialogUtil
import com.codingEmpire.bitbloom.viewModels.TransactionViewModel
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class DepositFragment : BaseFragment() {

    private lateinit var viewPager: androidx.viewpager2.widget.ViewPager2

    private lateinit var tabLayout: TabLayout
    private lateinit var adapter: TransactionAdapter
    private lateinit var viewModel: TransactionViewModel
    private lateinit var binding: FragmentDepositBinding
    private var allDeposits: List<TransactionModel> = emptyList()
    private var approvedDeposits: List<TransactionModel> = emptyList()
    private var expiredDeposits: List<TransactionModel> = emptyList()
    private lateinit var currentUserId: String


    // Shared prefs for active txn
    private val prefs by lazy {
        requireContext().getSharedPreferences("deposit_prefs$currentUserId", Context.MODE_PRIVATE)
    }

    private fun keyTxnId() = "activeTxnId_$currentUserId"
    private fun keyAddress() = "activeAddress_$currentUserId"
    private fun keyAmount() = "activeAmount_$currentUserId"
    private fun keyExpiryMs() = "activeExpiryMs_$currentUserId"
    private fun keyDialogShown() = "dialogShown_$currentUserId"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentDepositBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        setupDrawerTrigger(view)
        setUpReward(view)
        view.post {
            startTour()
        }
        currentUserId = PrefService(requireContext()).getUserId() ?: ""
        SoundManager.init(requireContext())
        viewPager = view.findViewById(R.id.viewPager)

        tabLayout = view.findViewById(R.id.tabStatusDeposit)
        adapter = TransactionAdapter(emptyList()) { txn ->
            TransactionDialogUtil.showTransactionDialog(requireContext(), txn)
        }


        viewModel = ViewModelProvider(requireActivity())[TransactionViewModel::class.java]

        val currentUserId =
            PrefService(requireContext()).getUserId() ?: "" // Replace with actual user ID logic
        viewModel.fetchDeposits(currentUserId)


        // Initial button/timer state
        restoreActiveTransaction()
        binding.walletAddress.setText("USDT.BEP20")
        binding.walletAddress.isEnabled = false

        binding.withdrawBtn.setOnClickListener {
            if (isTransactionActive()) {
                // reopen dialog
                showPaymentDialog()
            } else {
                // new deposit

                val amt = binding.amount.text.toString().toDoubleOrNull()
                val email = FirebaseAuth.getInstance().currentUser?.email ?: ""
                if (amt == null) {
                    showSnackbar("Enter a valid amount", true)
                    SoundManager.playFailure(requireContext())
                    return@setOnClickListener
                }
                if (amt < 20) {
                    SoundManager.playFailure(requireContext())
                    showSnackbar("Enter a valid amount ≥ 20", true)
                } else {
                    createDepositRequest(amt, email, currentUserId)
                    binding.amount.isEnabled = false
                    binding.amount.setText("")
                }
            }
        }
        viewModel.deposits.observe(viewLifecycleOwner) { deposits ->
            // if txn approved or expired, clear it
            val txnId = prefs.getString(keyTxnId(), null)
            deposits.find { it.coinpaymentsId == txnId }?.let {
                if (it.status.equals("approved", true) || it.status.equals("expired", true)) {
                    clearActiveTransaction()
                    restoreActiveTransaction()
                }
            }

            // update lists...
            allDeposits = deposits
            approvedDeposits = deposits.filter { it.status.equals("approved", true) }
            expiredDeposits = deposits.filter { it.status.equals("expired", true) }

            val pages = listOf(allDeposits, approvedDeposits, expiredDeposits)

            viewPager.adapter = TransactionPagerAdapter(pages) { txn ->
                TransactionDialogUtil.showTransactionDialog(requireContext(), txn)
            }

            TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                tab.text = when (position) {
                    0 -> "All Deposit"
                    1 -> "Approved"
                    2 -> "Expired"
                    else -> "Unknown"
                }
            }.attach()

            restoreActiveTransaction()
        }

    }

    private fun isTransactionActive(): Boolean {
        val expiry = prefs.getLong(keyExpiryMs(), 0L)
        return prefs.contains(keyTxnId()) && System.currentTimeMillis() < expiry
    }

    private fun restoreActiveTransaction() {
        val deposits = viewModel.deposits.value.orEmpty()
        val hasPendingInFirestore = deposits.any { it.status.equals("pending", true) }
        val txnIdExistsInPrefs = prefs.contains(keyTxnId())

        when {
            // Case 1: App was reinstalled and user has a pending deposit in Firestore
            !txnIdExistsInPrefs && hasPendingInFirestore -> {
                binding.withdrawBtn.text = "Pending Deposit"
                binding.withdrawBtn.isEnabled = false
                binding.amount.isEnabled = false
                binding.tvTimerScreen.visibility = View.GONE
            }

            // Case 2: Existing active transaction (already in prefs)
            txnIdExistsInPrefs && isTransactionActive() -> {
                binding.withdrawBtn.text = "View Deposit Address"
                binding.withdrawBtn.isEnabled = true
                binding.amount.isEnabled = false
                binding.tvTimerScreen.visibility = View.VISIBLE
                startScreenTimer(prefs.getLong(keyExpiryMs(), 0L))
            }

            // Case 3: No pending txn, allow new deposit
            else -> {
                clearActiveTransaction()
                binding.withdrawBtn.text = "Deposit Now"
                binding.withdrawBtn.isEnabled = true
                binding.amount.isEnabled = true
                binding.tvTimerScreen.visibility = View.GONE
            }
        }
    }


    private fun clearActiveTransaction() {
        prefs.edit().clear().apply()
        binding.tvTimerScreen.visibility = View.GONE
    }

    private fun createDepositRequest(amount: Double, email: String, userId: String) {
        showLoading()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject().apply {
                    put("amount", amount.toString())
                    put("currency1", "USDT.BEP20")
                    put("currency2", "USDT.BEP20")
                    put("buyer_email", email)
                    put("custom", userId)
                }

                val request = Request.Builder()
                    .url("https://coin-payments-backend.onrender.com/api/create-transaction")
                    .post(json.toString().toRequestBody("application/json".toMediaType())).build()

                val response = OkHttpClient().newCall(request).execute()
                val body = response.body?.string()

                if (response.isSuccessful && body != null) {
                    val result = JSONObject(body).getJSONObject("result")
                    val address = result.getString("address")
                    val amountToSend = result.getString("amount")
                    val qrUrl = result.getString("qrcode_url")
                    val txnId = result.getString("txn_id")
                    // CoinPayments returns "timeout" in seconds
                    val timeoutSec = result.getLong("timeout")
                    val expiresMs = System.currentTimeMillis() + (timeoutSec * 1000L)
                    val cleanAddress = extractCleanAddress(address)

                    // save active txn
                    prefs.edit().putString(keyTxnId(), txnId).putString(keyAddress(), cleanAddress)
                        .putString(keyAmount(), amountToSend).putLong(keyExpiryMs(), expiresMs)
                        .putBoolean(keyDialogShown(), false).apply()

                    // ✅ Clean up address

                    withContext(Dispatchers.Main) {
                        hideLoading()
                        restoreActiveTransaction()
                        showPaymentDialog()
                        SoundManager.playSuccess(requireContext())
                    }
                } else {
                    showSnackbar("Error: ${response.code}", true)
                    SoundManager.playFailure(requireContext())
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideLoading()
                    showSnackbar("Exception: ${e.message}", true)
                    SoundManager.playFailure(requireContext())
                }
            }
        }
    }/*  private suspend fun showToastOnMain(message: String) {
          withContext(Dispatchers.Main) {
              Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
          }
      }*/

    private fun extractCleanAddress(address: String): String {
        if (address.contains("?address=")) {
            val startIndex = address.indexOf("?address=") + 9
            val endIndex = address.indexOf("&", startIndex).takeIf { it > 0 } ?: address.length
            return address.substring(startIndex, endIndex)
        }

        if (address.startsWith("ethereum:") && address.contains("/transfer")) {
            val addressPart = address.split("/transfer").first().removePrefix("ethereum:")
            if (addressPart.matches(Regex("0x[a-fA-F0-9]{40}"))) {
                return addressPart
            }
        }

        if (address.startsWith("ethereum:")) {
            val addressPart = address.removePrefix("ethereum:")
            if (addressPart.matches(Regex("0x[a-fA-F0-9]{40}"))) {
                return addressPart
            }
        }

        if (address.matches(Regex("0x[a-fA-F0-9]{40}"))) {
            return address
        }

        return address
    }

    private fun showPaymentDialog() {


        val address = prefs.getString(keyAddress(), "") ?: ""
        val amount = prefs.getString(keyAmount(), "0.00") ?: "0.00"
        val expiryMs = prefs.getLong(keyExpiryMs(), 0L)

        val ctx = context ?: return  // Prevent crash if context is null

        val dialog = Dialog(ctx)
        dialog.setContentView(R.layout.dialog_qr_scan)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.window?.setDimAmount(0.6f)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(true)

        // Set QR Code image
        val ivQRCode = dialog.findViewById<ImageView>(R.id.ivQRCode)
        generateLocalQrCode(address, ivQRCode)

        // Set address
        val tvAddress = dialog.findViewById<TextView>(R.id.sendingAddress)
        tvAddress.text = address

        // Set amount
        val tvAmount = dialog.findViewById<TextView>(R.id.amountVal)
        tvAmount.text = "Amount: $amount".format("%.2f", amount.toDouble())

        val tvTimer = dialog.findViewById<TextView>(R.id.tvTimerDialog)

        startDialogTimer(expiryMs, tvTimer)

        // mark as shown
        prefs.edit().putBoolean(keyDialogShown(), true).apply()

//        // Optional: Set Transaction ID
//        val tvTxnId = dialog.findViewById<TextView?>(R.id.txnId)
//        tvTxnId?.text = txnId

        // Copy to clipboard
        val copyButton = dialog.findViewById<ImageView>(R.id.copyButton)
        copyButton.setOnClickListener {
            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Payment Address", address)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(ctx, "Address copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        dialog.show()

    }

    private fun startScreenTimer(expiryMs: Long) {
        object : CountDownTimer(expiryMs - System.currentTimeMillis(), 1000) {
            override fun onTick(millis: Long) {
                binding.tvTimerScreen.text = formatMillis(millis)
            }

            override fun onFinish() {
                binding.tvTimerScreen.text = "Expired"
                clearActiveTransaction()
                binding.withdrawBtn.text = "Deposit Now"
            }
        }.start()
    }

    private fun startDialogTimer(expiryMs: Long, tv: TextView) {
        object : CountDownTimer(expiryMs - System.currentTimeMillis(), 1000) {
            override fun onTick(millis: Long) {
                tv.text = formatMillis(millis)
            }

            override fun onFinish() {
                tv.text = "Expired"
            }
        }.start()
    }

    private fun formatMillis(ms: Long): String {
        val sec = (ms / 1000) % 60
        val min = (ms / 1000 / 60) % 60
        val hr = ms / 1000 / 3600
        return String.format("Time left: %02d:%02d:%02d", hr, min, sec)
    }


    private fun generateLocalQrCode(address: String, ivQRCode: ImageView) {

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val hints = hashMapOf<EncodeHintType, Any>(
                    EncodeHintType.MARGIN to 1,
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
                )

                val bitMatrix =
                    QRCodeWriter().encode(address, BarcodeFormat.QR_CODE, 512, 512, hints)
                val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)

                for (x in 0 until 512) {
                    for (y in 0 until 512) {
                        bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                    }
                }

                withContext(Dispatchers.Main) {
                    ivQRCode.setImageBitmap(bitmap)
                    ivQRCode.visibility = View.VISIBLE
                }

            } catch (e: Exception) {
                Log.e(TAG, "QR generation error: ${e.message}", e)
                withContext(Dispatchers.Main) {}
            }
        }
    }

    private fun startTour() {
        TourHelper.startTour(
            fragment = this,
            tourKey = "deposit",
            targets = listOf(
                TourTarget(binding.amount, "Deposit Amount", "Enter the amount you want to deposit."),
                TourTarget(binding.withdrawBtn, "Deposit Now", "Tap here to get details."),
            ),
            onMessage = { message, isError ->
                showSnackbar(message, isError)
            }
        )
    }

    private fun showSnackbar(message: String, isError: Boolean = false) {
        val snack = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)

        // Pull the color from resources
        val bgColor = ContextCompat.getColor(
            requireContext(), if (isError) R.color.snackbar_error else R.color.snackbar_success
        )

        // Apply as tint
        snack.view.backgroundTintList = ColorStateList.valueOf(bgColor)

        // Ensure the text is legible
        snack.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))

        snack.show()
    }
}
