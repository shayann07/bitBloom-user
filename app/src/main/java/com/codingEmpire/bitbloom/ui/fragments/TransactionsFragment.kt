package com.codingEmpire.bitbloom.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.codingEmpire.bitbloom.databinding.FragmentTransactionsBinding
import com.codingEmpire.bitbloom.adapters.TransactionAdapter
import com.codingEmpire.bitbloom.adapters.TransactionPagerAdapter
import com.codingEmpire.bitbloom.models.TransactionModel
import com.codingEmpire.bitbloom.utils.PrefService
import com.codingEmpire.bitbloom.utils.TransactionDialogUtil
import com.codingEmpire.bitbloom.utils.TxnFilterDialogUtil
import com.codingEmpire.bitbloom.viewModels.TransactionViewModel
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import androidx.core.content.ContextCompat
import android.content.res.ColorStateList
import com.codingEmpire.bitbloom.R
import com.codingEmpire.bitbloom.utils.SoundManager

class TransactionsFragment : BaseFragment() {

    private lateinit var binding: FragmentTransactionsBinding
    private lateinit var viewModel: TransactionViewModel
    private lateinit var adapter: TransactionAdapter

    private var allTransactions = listOf<TransactionModel>()
    private var depositTransactions = listOf<TransactionModel>()
    private var withdrawTransactions = listOf<TransactionModel>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentTransactionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDrawerTrigger(view)

        adapter = TransactionAdapter(emptyList()) { txn ->
            TransactionDialogUtil.showTransactionDialog(requireContext(), txn)
        }

        viewModel = ViewModelProvider(this)[TransactionViewModel::class.java]
        val uid = PrefService(requireContext()).getUserId().orEmpty()

        // Always fetch fresh
        viewModel.fetchAllTransactions(uid)
        viewModel.fetchDeposits(uid)
        viewModel.fetchWithdrawals(uid)

        viewModel.allTransactions.observe(viewLifecycleOwner) {
            allTransactions = it
            setupViewPager()
        }
        viewModel.deposits.observe(viewLifecycleOwner) {
            depositTransactions = it
            setupViewPager()
        }
        viewModel.withdrawals.observe(viewLifecycleOwner) {
            withdrawTransactions = it
            setupViewPager()
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            if (loading) showLoading() else hideLoading()
        }
        viewModel.error.observe(viewLifecycleOwner) { msg ->
            msg?.let { showSnackbar(it, true) }
        }

        binding.ivFilter.setOnClickListener {
            TxnFilterDialogUtil.show(requireContext(), findNavController())
        }
    }

    private fun setupViewPager() {
        val pages = listOf(allTransactions, depositTransactions, withdrawTransactions)
        binding.viewPager.adapter = TransactionPagerAdapter(pages) { txn ->
            TransactionDialogUtil.showTransactionDialog(requireContext(), txn)
        }
        TabLayoutMediator(binding.tabStatusDeposit, binding.viewPager) { tab, pos ->
            tab.text = when (pos) {
                0 -> "All"
                1 -> "Deposits"
                2 -> "Withdrawals"
                else -> "Unknown"
            }
        }.attach()
    }

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
