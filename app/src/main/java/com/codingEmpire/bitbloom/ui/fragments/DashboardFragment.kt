package com.codingEmpire.bitbloom.ui.fragments

import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.codingEmpire.bitbloom.R
import com.codingEmpire.bitbloom.adapters.TransactionAdapter
import com.codingEmpire.bitbloom.databinding.FragmentDashboardBinding
import com.codingEmpire.bitbloom.utils.SoundManager
import com.codingEmpire.bitbloom.utils.TransactionDialogUtil
import com.codingEmpire.bitbloom.viewModels.DashboardViewModel
import com.google.android.material.snackbar.Snackbar

class DashboardFragment : BaseFragment() {
    private lateinit var binding: FragmentDashboardBinding
    private val vm: DashboardViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDrawerTrigger(view)
        setUpReward(view)

        // RecyclerView & Adapter setup
        val adapter = TransactionAdapter(emptyList()) { txn ->
            TransactionDialogUtil.showTransactionDialog(requireContext(), txn)
        }
        binding.rvWithdraw.layoutManager = LinearLayoutManager(requireContext())
        binding.rvWithdraw.adapter = adapter

        // Navigate to transactions screen
        binding.txnArrow.setOnClickListener {
            findNavController().navigate(R.id.action_dashboardFragment_to_transactionsFragment)
        }

        // Observe loading & errors
        vm.loading.observe(viewLifecycleOwner) { loading ->
            if (loading) showLoading() else hideLoading()
        }
        vm.error.observe(viewLifecycleOwner) { errorMsg ->
            errorMsg?.let { showSnackbar(it, true) }
        }

        // Observe metrics and update UI
        vm.metrics.observe(viewLifecycleOwner) { m ->
            with(binding) {
                tvBalanceAmount.text = formatMoney(m.balance)
                ROIAmount.text = formatMoney(m.roiIncome)
                rankRewardAmt.text = formatMoney(m.rankReward)
                directIncomeAmt.text = formatMoney(m.directActiveDeposit)
                teamRewardAmt.text = formatMoney(m.teamIncome)
                salaryBonusAmt.text = formatMoney(m.salaryBonus)
                totalEarnedAmt.text = formatMoney(m.totalEarned)
                dailyEarningAmt.text = formatMoney(m.dailyEarnings)
                referralIncomeAmt.text = formatMoney(m.referralIncome)
            }
        }

        // Observe transactions list
        vm.transactions.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
        }

        // Always load dashboard data
        vm.loadDashboard()
    }

    private fun formatMoney(v: Double) = "$" + "%,.2f".format(v)

    // Optionally keep this for toast/snackbar feedback elsewhere
    private fun showSnackbar(message: String, isError: Boolean = false) {
        if (isError) SoundManager.playFailure(requireContext())
        val snack = Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG)
        val bgColor = ContextCompat.getColor(
            requireContext(),
            if (isError) R.color.snackbar_error else R.color.snackbar_success
        )
        snack.view.backgroundTintList = ColorStateList.valueOf(bgColor)
        snack.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
        snack.show()
    }
}
