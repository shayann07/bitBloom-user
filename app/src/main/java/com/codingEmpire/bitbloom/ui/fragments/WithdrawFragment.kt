package com.codingEmpire.bitbloom.ui.fragments

import android.animation.Animator
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.airbnb.lottie.LottieAnimationView
import com.codingEmpire.bitbloom.R
import com.codingEmpire.bitbloom.adapters.TransactionAdapter
import com.codingEmpire.bitbloom.adapters.TransactionPagerAdapter
import com.codingEmpire.bitbloom.databinding.FragmentWithdrawBinding
import com.codingEmpire.bitbloom.models.TransactionModel
import com.codingEmpire.bitbloom.repos.TeamLevelRepo
import com.codingEmpire.bitbloom.repos.WithdrawRepo
import com.codingEmpire.bitbloom.utils.PrefService
import com.codingEmpire.bitbloom.utils.SoundManager
import com.codingEmpire.bitbloom.utils.TourHelper
import com.codingEmpire.bitbloom.utils.TourTarget
import com.codingEmpire.bitbloom.utils.TransactionDialogUtil
import com.codingEmpire.bitbloom.utils.WithdrawResult
import com.codingEmpire.bitbloom.viewModels.TransactionViewModel
import com.codingEmpire.bitbloom.viewModels.WithdrawViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator

class WithdrawFragment : BaseFragment() {

    private var _binding: FragmentWithdrawBinding? = null
    private val binding get() = _binding!!

    private lateinit var transactionVM: TransactionViewModel
    private lateinit var withdrawVM: WithdrawViewModel
    private lateinit var adapter: TransactionAdapter

    private var userId: String = ""
    private var allWithdrawals = emptyList<TransactionModel>()
    private var approvedWithdrawals = emptyList<TransactionModel>()
    private var rejectedWithdrawals = emptyList<TransactionModel>()
    private lateinit var animationContainer: View
    private lateinit var lottieView: LottieAnimationView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWithdrawBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)
        setUpReward(view)



        animationContainer = binding.root.findViewById(R.id.animationContainer)
        lottieView = binding.root.findViewById(R.id.lottieAnimationView)
        startTour()
        SoundManager.init(requireContext())
        userId = PrefService(requireContext()).getUserId().orEmpty()
        if (userId.isEmpty()) {
            showSnackbar("User ID not found", true)
            return
        }

        adapter = TransactionAdapter(emptyList()) { txn ->
            TransactionDialogUtil.showTransactionDialog(requireContext(), txn)
        }


        // ViewModels
        transactionVM = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST") return TransactionViewModel() as T
            }
        })[TransactionViewModel::class.java]

        withdrawVM = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val ctx = requireContext().applicationContext
                @Suppress("UNCHECKED_CAST") return WithdrawViewModel(
                    WithdrawRepo(), TeamLevelRepo(ctx)
                ) as T
            }
        })[WithdrawViewModel::class.java]

        // ─── Eligibility first ─────────────────────────────────────────────

        withdrawVM.eligibility.observe(viewLifecycleOwner) { ok ->
            if (ok) {                         // ✅ eligible
                binding.withdrawBtn.apply {
                    isEnabled = true
                    isClickable = true        // default
                    text = "Withdraw Now"
                    backgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.blue)
                    )
                }
            } else {                          // ❌ NOT eligible
                binding.withdrawBtn.apply {
                    isEnabled = true        // ← keep enabled so clicks arrive
                    isClickable = true
                    text = "Withdraw Disabled"
                    alpha = 0.5f              // visual cue (dimmed)
                    backgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.grey)
                    )
                    setOnClickListener {
                        showSnackbar(
                            "You need one active direct member (joined on or after July 8, 2025) to withdraw.",
                            true
                        )
                    }
                }
            }
        }

        withdrawVM.checkWithdrawEligibility()

        // Observe withdrawals
        transactionVM.withdrawals.observe(viewLifecycleOwner) { list ->
            val sorted = list.sortedByDescending { it.timestamp?.toDate()?.time ?: 0L }

            allWithdrawals = sorted
            approvedWithdrawals = sorted.filter { it.status.equals("approved", true) }
            rejectedWithdrawals = sorted.filter { it.status.equals("rejected", true) }

            // 🔄 Set up pages
            val pages = listOf(allWithdrawals, approvedWithdrawals, rejectedWithdrawals)

            // 🆕 Attach ViewPager2 adapter
            binding.viewPager.adapter = TransactionPagerAdapter(pages) { txn ->
                TransactionDialogUtil.showTransactionDialog(requireContext(), txn)
            }

            // 🆕 Sync TabLayout with ViewPager2
            TabLayoutMediator(binding.tabStatus, binding.viewPager) { tab, position ->
                tab.text = when (position) {
                    0 -> "All Withdrawals"
                    1 -> "Approved"
                    2 -> "Rejected"
                    else -> "Unknown"
                }
            }.attach()

            binding.walletAddress.isEnabled = false
            binding.amount.isEnabled = false/*  binding.withdrawBtn.text = "Withdraw Paused"
              binding.withdrawBtn.setOnClickListener {
                  showSnackbar("Withdrawals are paused", true)
              }*/

            // 🧠 Handle pending state logic (same as before)
            val pending = sorted.firstOrNull { it.status.equals("pending", true) }
            if (pending != null) {
                binding.withdrawBtn.text = "Cancel Withdrawal"
                binding.withdrawBtn.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), R.color.red)
                )
                binding.walletAddress.isEnabled = false
                binding.amount.isEnabled = false
                binding.withdrawBtn.setOnClickListener {
                    MaterialAlertDialogBuilder(
                        requireContext(), R.style.ThemeOverlay_Custom_Dialog_Background
                    ).setTitle("Cancel Withdrawal")
                        .setMessage("Are you sure you want to cancel your pending withdrawal?")
                        .setPositiveButton("Yes") { dialog, _ ->
                            binding.withdrawBtn.setBackgroundColor(
                                ContextCompat.getColor(requireContext(), R.color.blue)
                            )
                            dialog.dismiss()
                            showLoading()
                            withdrawVM.cancelWithdrawal(pending.id)
                        }.setNegativeButton("No") { dialog, _ -> dialog.dismiss() }.show()
                }
            } else {
                binding.withdrawBtn.text = "Withdraw Now"
                binding.walletAddress.isEnabled = true
                binding.amount.isEnabled = true
                binding.withdrawBtn.setOnClickListener {
                    binding.walletInputLayout.error = null
                    binding.amountInputLayout.error = null

                    val wallet = binding.walletAddress.text?.toString()?.trim().orEmpty()
                    val amountText = binding.amount.text?.toString()?.trim().orEmpty()
                    val amount = amountText.toDoubleOrNull()

                    var valid = true

                    if (wallet.isEmpty()) {
                        binding.walletInputLayout.error = "Wallet address is required"
                        SoundManager.playFailure(requireContext())
                        valid = false
                    }

                    if (amount == null) {
                        binding.amountInputLayout.error = "Enter a valid amount"
                        SoundManager.playFailure(requireContext())
                        valid = false
                    } else if (amount < 30.0) {
                        binding.amountInputLayout.error = "Minimum withdrawal is 30"
                        SoundManager.playFailure(requireContext())
                        valid = false
                    }

                    if (valid) {
                        showLoading()
                        withdrawVM.submitWithdrawal(userId, amount!!, wallet)
                    }
                }
            }
        }

        // Observe submission/cancellation results
        withdrawVM.withdrawalStatus.observe(viewLifecycleOwner) { result ->
            hideLoading()
            when (result) {
                is WithdrawResult.Success -> {
                    animationContainer.visibility = View.VISIBLE
                    lottieView.playAnimation()
                    // only one success sound here:
                    SoundManager.playWithdrawSuccess(requireContext())
                    lottieView.addAnimatorListener(object : Animator.AnimatorListener {
                        override fun onAnimationEnd(animation: Animator) {
                            animationContainer.visibility = View.GONE
                            transactionVM.fetchWithdrawals(userId)
                            lottieView.removeAnimatorListener(this)
                        }

                        override fun onAnimationStart(animation: Animator) {}
                        override fun onAnimationCancel(animation: Animator) {
                            animationContainer.visibility = View.GONE
                        }

                        override fun onAnimationRepeat(animation: Animator) {}
                    })
                    binding.walletAddress.setText("")
                    binding.amount.setText("")
                    transactionVM.fetchWithdrawals(userId)
                }

                is WithdrawResult.PendingExists -> {
                    showSnackbar("Pending request already exists", true)
                }

                is WithdrawResult.NotEnoughBalance -> {
                    showSnackbar("Insufficient balance", true)
                }

                is WithdrawResult.UserBlocked -> {
                    showSnackbar("User is blocked", true)
                }

                is WithdrawResult.AccountNotFound -> {
                    showSnackbar("Account not found", true)
                }

                is WithdrawResult.Error -> {
                    // only play failure on *real* errors
                    SoundManager.playFailure(requireContext())
                    showSnackbar("Error: ${result.message}", true)
                }

                else -> Unit
            }
        }

        transactionVM.fetchWithdrawals(userId)
    }

    private fun startTour() {
        TourHelper.startTour(
            fragment = this, tourKey = "withdraw", targets = listOf(
                TourTarget(
                    binding.amount, "Withdraw Amount", "Enter the amount you want to withdraw."
                ),
                TourTarget(
                    binding.walletAddress, "Wallet Address", "Enter the wallet address."
                ),
                TourTarget(
                    binding.withdrawBtn, "Withdraw Now", "Tap here to initiate withdraw."
                ),
            ), onMessage = { message, isError ->
                showSnackbar(message, isError)
            })
    }

    private fun showSnackbar(message: String, isError: Boolean = false) {
        val snack = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
        val bgColor = ContextCompat.getColor(
            requireContext(), if (isError) R.color.snackbar_error else R.color.snackbar_success
        )
        snack.view.backgroundTintList = ColorStateList.valueOf(bgColor)
        snack.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
        snack.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
