package com.codingEmpire.bitbloom.ui.fragments

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.codingEmpire.bitbloom.R
import com.codingEmpire.bitbloom.adapters.TransactionAdapter
import com.codingEmpire.bitbloom.adapters.TransactionPagerAdapter
import com.codingEmpire.bitbloom.databinding.FragmentWithdrawHistoryBinding
import com.codingEmpire.bitbloom.models.TransactionModel
import com.codingEmpire.bitbloom.utils.PrefService
import com.codingEmpire.bitbloom.utils.SoundManager
import com.codingEmpire.bitbloom.utils.TransactionDialogUtil
import com.codingEmpire.bitbloom.utils.TxnConstants
import com.codingEmpire.bitbloom.utils.TxnFilterDialogUtil
import com.codingEmpire.bitbloom.viewModels.TransactionViewModel
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator

class WithdrawHistoryFragment : BaseFragment() {

    private var _binding: FragmentWithdrawHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var filterBtn: ImageButton
    private lateinit var profileTitle: TextView
    private lateinit var adapter: TransactionAdapter
    private lateinit var viewModel: TransactionViewModel

    private var allWithdrawals = emptyList<TransactionModel>()
    private var approvedWithdrawals = emptyList<TransactionModel>()
    private var rejectedWithdrawals = emptyList<TransactionModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWithdrawHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDrawerTrigger(view)
        filterBtn = binding.ivFilter
        profileTitle = binding.profileTitleW

        adapter = TransactionAdapter(emptyList()) { txn ->
            TransactionDialogUtil.showTransactionDialog(requireContext(), txn)
        }

        viewModel = ViewModelProvider(requireActivity())[TransactionViewModel::class.java]
        val uid = PrefService(requireContext()).getUserId().orEmpty()

        // Always fetch fresh data
        viewModel.fetchWithdrawals(uid)
        viewModel.fetchPlanTransactions(uid)

        viewModel.withdrawals.observe(viewLifecycleOwner) { withdrawals ->
            allWithdrawals = withdrawals
            approvedWithdrawals = withdrawals.filter {
                it.status.equals(TxnConstants.STATUS_APPROVED, true)
            }
            rejectedWithdrawals = withdrawals.filter {
                it.status.equals(TxnConstants.STATUS_REJECTED, true)
            }

            binding.viewPager.adapter = TransactionPagerAdapter(
                listOf(allWithdrawals, approvedWithdrawals, rejectedWithdrawals)
            ) { txn ->
                TransactionDialogUtil.showTransactionDialog(requireContext(), txn)
            }
            TabLayoutMediator(binding.tabStatusDeposit, binding.viewPager) { tab, position ->
                tab.text = when (position) {
                    0 -> "All Withdrawals"
                    1 -> "Approved"
                    2 -> "Rejected"
                    else -> "Unknown"
                }
            }.attach()
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            if (loading) showLoading() else hideLoading()
        }
        viewModel.error.observe(viewLifecycleOwner) { msg ->
            msg?.let { showSnackbar(it, true) }
        }

        filterBtn.setOnClickListener {
            TxnFilterDialogUtil.show(requireContext(), findNavController())
        }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
