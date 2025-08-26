package com.codingEmpire.bitbloom.ui.fragments

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.codingEmpire.bitbloom.R
import com.codingEmpire.bitbloom.adapters.TransactionPagerAdapter
import com.codingEmpire.bitbloom.adapters.TransactionAdapter
import com.codingEmpire.bitbloom.databinding.FragmentProfitHistoryBinding
import com.codingEmpire.bitbloom.models.TransactionModel
import com.codingEmpire.bitbloom.utils.PrefService
import com.codingEmpire.bitbloom.utils.SoundManager
import com.codingEmpire.bitbloom.utils.TransactionDialogUtil
import com.codingEmpire.bitbloom.utils.TxnFilterDialogUtil
import com.codingEmpire.bitbloom.viewModels.TransactionViewModel
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator

class ProfitHistoryFragment : BaseFragment() {

    private lateinit var binding: FragmentProfitHistoryBinding
    private lateinit var adapter: TransactionAdapter
    private lateinit var viewModel: TransactionViewModel

    private var roiList = emptyList<TransactionModel>()
    private var referralList = emptyList<TransactionModel>()
    private var teamList = emptyList<TransactionModel>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentProfitHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDrawerTrigger(view)

        // RecyclerView + Adapter
        adapter = TransactionAdapter(emptyList()) { txn ->
            TransactionDialogUtil.showTransactionDialog(requireContext(), txn)
        }

        viewModel = ViewModelProvider(requireActivity())[TransactionViewModel::class.java]
        val uid = PrefService(requireContext()).getUserId().orEmpty()

        // Always fetch fresh data
        viewModel.fetchRoi(uid)
        viewModel.fetchReferral(uid)
        viewModel.fetchTeam(uid)

        viewModel.roiTxns.observe(viewLifecycleOwner) {
            roiList = it.sortedByDescending { t -> t.timestamp?.toDate() }
            setupViewPager()
        }
        viewModel.referralTxns.observe(viewLifecycleOwner) {
            referralList = it
            setupViewPager()
        }
        viewModel.teamTxns.observe(viewLifecycleOwner) {
            teamList = it.sortedByDescending { t -> t.timestamp?.toDate() }
            setupViewPager()
        }

        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            if (loading) showLoading() else hideLoading()
        }

        binding.ivFilter.setOnClickListener {
            TxnFilterDialogUtil.show(
                context = requireContext(),
                navController = findNavController()
            )
        }
    }

    private fun setupViewPager() {
        val pages = listOf(roiList, referralList, teamList)
        binding.viewPager.adapter = TransactionPagerAdapter(pages) { txn ->
            TransactionDialogUtil.showTransactionDialog(requireContext(), txn)
        }

        TabLayoutMediator(binding.tabStatusDeposit, binding.viewPager) { tab, pos ->
            tab.text = when (pos) {
                0 -> "ROI"
                1 -> "Referral"
                2 -> "Team"
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
