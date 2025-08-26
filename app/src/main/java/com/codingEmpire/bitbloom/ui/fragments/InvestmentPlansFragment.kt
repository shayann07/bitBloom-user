package com.codingEmpire.bitbloom.ui.fragments

import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.codingEmpire.bitbloom.R
import com.codingEmpire.bitbloom.adapters.AvailablePlansAdapter
import com.codingEmpire.bitbloom.databinding.FragmentInvestmentPlansBinding
import com.codingEmpire.bitbloom.models.PlanModel
import com.codingEmpire.bitbloom.repos.BuyPlanRepo
import com.codingEmpire.bitbloom.repos.LuckySpinRepo
import com.codingEmpire.bitbloom.utils.PlanStatus
import com.codingEmpire.bitbloom.utils.PrefService
import com.codingEmpire.bitbloom.utils.SoundManager
import com.codingEmpire.bitbloom.viewModels.AccountViewModel
import com.codingEmpire.bitbloom.viewModels.BuyPlanViewModel
import com.codingEmpire.bitbloom.viewModels.LuckySpinViewModel
import com.codingEmpire.bitbloom.viewModels.factory.BuyPlanViewModelFactory
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class InvestmentPlansFragment : BaseFragment() {

    private var _binding: FragmentInvestmentPlansBinding? = null
    private val binding get() = _binding!!

    private val buyPlanVm: BuyPlanViewModel by viewModels {
        BuyPlanViewModelFactory(
            BuyPlanRepo(
                FirebaseFirestore.getInstance(),
                PrefService(requireContext())
            )
        )
    }
    private val accountVm: AccountViewModel by viewModels()
    private val spinVm: LuckySpinViewModel by activityViewModels()

    private var selectedPlan: PlanModel? = null
    private val userId by lazy { PrefService(requireContext()).getUserId().orEmpty() }
    private var userName = ""
    private var prefill: Double = -1.0


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = FragmentInvestmentPlansBinding
        .inflate(inflater, container, false)
        .also { _binding = it }
        .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)
        setUpReward(view)
        SoundManager.init(requireContext())

        // 1) Setup plans list
        binding.rvPlans.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvPlans.adapter = AvailablePlansAdapter(emptyList()) { onPlanSelected(it) }

        buyPlanVm.availablePlans.observe(viewLifecycleOwner) { plans ->
            binding.rvPlans.adapter = AvailablePlansAdapter(plans) { onPlanSelected(it) }
        }
        buyPlanVm.fetchAvailablePlans()

        // 2) Load user profile (for receipt)
        accountVm.profileData.observe(viewLifecycleOwner) {
            userName = it["name"].toString()
        }
        accountVm.loadProfile()

        // 3) Configure loading state
        buyPlanVm.isLoading.observe(viewLifecycleOwner) { loading ->
            if (loading) showLoading() else hideLoading()
            binding.btnPurchase.isEnabled = !loading
            binding.btnPurchase.text = if (loading) "Purchasing…" else "Purchase Now"
            if (!loading && selectedPlan == null) {
                binding.etAmount.text?.clear()
                binding.etSelectSetup.text?.clear()
                binding.etDuration.text?.clear()
            }
        }

        prefill = arguments?.getFloat("prefillAmount", -1f)?.toDouble() ?: -1.0
        if (prefill >= 0) {
            binding.etAmount.setText("%.2f".format(prefill))
            binding.etAmount.isEnabled = false
            binding.cbReinvest.visibility = View.GONE

            // bump top margin…
            val params = binding.etDurationContainer.layoutParams
                    as ViewGroup.MarginLayoutParams
            params.topMargin = resources.getDimensionPixelSize(R.dimen.dp_10)
            binding.etDurationContainer.layoutParams = params
        }

        // 5) On successful purchase: reset spin total if this was a prefill flow
        buyPlanVm.buyPlanStatus.observe(viewLifecycleOwner, Observer { status ->
            if (status == PlanStatus.Success) {
                if (prefill >= 0) {
                    spinVm.resetTotal(userId)
                }
                navigateToReceipt()
            } else if (status == PlanStatus.InvalidAmount) {
                showSnackbar("Invalid amount.", isError = true)
            } else if (status == PlanStatus.NotEnoughBalance) {
                showSnackbar("Insufficient balance.", isError = true)
            } else if (status != null) {
                showSnackbar("Purchase failed.", isError = true)
            }
        })

        // 6) Purchase button logic
        binding.btnPurchase.setOnClickListener {
            if (prefill >= 0) {
                if (selectedPlan == null) {
                    showSnackbar("Please select a plan first", true)
                    return@setOnClickListener
                }

                showLoading()
                binding.btnPurchase.isEnabled = false

                lifecycleScope.launch {
                    try {
                        spinVm.loadSpinData(userId)                    // refresh
                        val latest = LuckySpinRepo().getSpinData(userId).total
                        val epsilon = 1e-6                 // six decimal places is plenty here
                        if (latest + epsilon < prefill) {  // only fire when *meaningfully* smaller
                            hideLoading()
                            binding.btnPurchase.isEnabled = true
                            showSnackbar(
                                "Your Lucky USDT is now $${"%.2f".format(latest)}. Please try again.",
                                true
                            )
                            return@launch
                        }
                        LuckySpinRepo().transferToAccount(userId, prefill)
                        buyPlanVm.buyPlan(userId, prefill, selectedPlan!!.name, false)
                    } catch (e: Exception) {
                        hideLoading()
                        showSnackbar("Transfer failed: ${e.message}", true)
                    }
                }
                return@setOnClickListener
            } else {
                // normal flow
                val plan = selectedPlan
                if (plan == null) {
                    showSnackbar("Please select a plan first", isError = true)
                    return@setOnClickListener
                }
                val amt = binding.etAmount.text.toString().toDoubleOrNull()
                if (amt == null || amt < plan.minInvestment) {
                    showSnackbar("Enter a valid amount ≥ ${plan.minInvestment}", isError = true)
                    return@setOnClickListener
                }
                val autoInvest = binding.cbReinvest.isChecked
                buyPlanVm.buyPlan(userId, amt, plan.name, autoInvest)
            }
        }
    }

    // onPlanSelected – NEVER overwrite amount when prefill is active
    private fun onPlanSelected(plan: PlanModel) {
        selectedPlan = plan
        binding.etSelectSetup.setText(plan.name)
        binding.etDuration.apply {
            setText(plan.durationDays.toString())
            isEnabled = false
        }
        if (prefill >= 0) {
            binding.etAmount.setText("%.2f".format(prefill))
        } else {
            binding.etAmount.setText(plan.minInvestment.toString())
            binding.etAmount.requestFocus()
        }
    }

    private fun navigateToReceipt() {
        val originalAmount = binding.etAmount.text.toString().toDoubleOrNull() ?: return
        val bonusPercent = selectedPlan?.bonusPercentage ?: 0.0
        val adjustedAmount = if (bonusPercent > 0) {
            originalAmount + (originalAmount * bonusPercent / 100)
        } else originalAmount

        Log.d("InvestmentPlansFragment", "bonus: $bonusPercent")
        Log.d("InvestmentPlansFragment", "adjustedAmount: $adjustedAmount")
        val args = Bundle().apply {
            putString("userId", userId)
            putString("userName", userName)
            putString("planName", selectedPlan?.name)
            putDouble("amount", adjustedAmount)
            putLong("timestamp", System.currentTimeMillis())
        }

        val options = NavOptions.Builder()
            .setPopUpTo(R.id.investmentPlansFragment, true)
            .build()
        findNavController().navigate(R.id.planRecieptFragment, args, options)
        SoundManager.playSuccess(requireContext())
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showSnackbar(message: String, isError: Boolean = false) {
        if (isError) SoundManager.playFailure(requireContext())
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .also {
                it.view.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        requireContext(),
                        if (isError) R.color.snackbar_error else R.color.snackbar_success
                    )
                )
                it.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
            }
            .show()
    }
}
