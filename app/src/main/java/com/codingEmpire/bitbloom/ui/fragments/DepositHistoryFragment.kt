package com.codingEmpire.bitbloom.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.codingEmpire.bitbloom.R
import com.codingEmpire.bitbloom.adapters.TransactionPagerAdapter
import com.codingEmpire.bitbloom.models.TransactionModel
import com.codingEmpire.bitbloom.utils.PrefService
import com.codingEmpire.bitbloom.utils.TransactionDialogUtil
import com.codingEmpire.bitbloom.utils.TxnConstants
import com.codingEmpire.bitbloom.utils.TxnFilterDialogUtil
import com.codingEmpire.bitbloom.viewModels.TransactionViewModel
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class DepositHistoryFragment : BaseFragment() {

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var profileTitle: TextView
    private lateinit var filterBtn: ImageButton
    private lateinit var tabUnderLine: View
    private lateinit var viewModel: TransactionViewModel
    private var showingPlans = false

    private var allDeposits = emptyList<TransactionModel>()
    private var approvedDeposits = emptyList<TransactionModel>()
    private var expiredDeposits = emptyList<TransactionModel>()
    private var planTransactions = emptyList<TransactionModel>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_deposit_history, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDrawerTrigger(view)

        tabLayout = view.findViewById(R.id.tabStatusDeposit)
        viewPager = view.findViewById(R.id.viewPager)
        profileTitle = view.findViewById(R.id.profileTitle)
        filterBtn = view.findViewById(R.id.iv_filter)
        tabUnderLine = view.findViewById(R.id.tabUnderLine)

        viewModel = ViewModelProvider(requireActivity())[TransactionViewModel::class.java]

        val currentUserId = PrefService(requireContext()).getUserId().orEmpty()
        viewModel.fetchDeposits(currentUserId)
        viewModel.fetchPlanTransactions(currentUserId)
        viewModel.deposits.observe(viewLifecycleOwner) { deposits ->
            if (showingPlans) return@observe  // ✅ Prevent overwriting

            allDeposits = deposits
            approvedDeposits =
                deposits.filter { it.status.equals(TxnConstants.STATUS_APPROVED, true) }
            expiredDeposits = deposits.filter { it.status.equals("expired", true) }

            val pages = listOf(allDeposits, approvedDeposits, expiredDeposits)
            viewPager.adapter = TransactionPagerAdapter(pages) { txn ->
                TransactionDialogUtil.showTransactionDialog(requireContext(), txn)
            }

            TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                tab.text = when (position) {
                    0 -> "All Deposits"
                    1 -> "Approved"
                    2 -> "Expired"
                    else -> "Unknown"
                }
            }.attach()
        }


        viewModel.planTransactions.observe(viewLifecycleOwner) {
            planTransactions = it
        }

        viewModel.loading.observe(viewLifecycleOwner) {
            if (it) showLoading() else hideLoading()
        }

        filterBtn.setOnClickListener {
            TxnFilterDialogUtil.show(
                context = requireContext(), navController = findNavController()
            )

        }
    }
}
