package com.codingEmpire.bitbloom.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.codingEmpire.bitbloom.adapters.TransactionAdapter
import com.codingEmpire.bitbloom.databinding.FragmentLuckySpinHistoryBinding
import com.codingEmpire.bitbloom.utils.PrefService
import com.codingEmpire.bitbloom.utils.TransactionDialogUtil
import com.codingEmpire.bitbloom.utils.TxnFilterDialogUtil
import com.codingEmpire.bitbloom.viewModels.TransactionViewModel

class LuckySpinHistoryFragment : BaseFragment() {

    private lateinit var binding: FragmentLuckySpinHistoryBinding
    private val vm: TransactionViewModel by viewModels()
    private val uid by lazy { PrefService(requireContext()).getUserId().orEmpty() }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        binding = FragmentLuckySpinHistoryBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        super.onViewCreated(v, s)
        setupDrawerTrigger(v)

        // ➜ add this
        binding.ivFilter.setOnClickListener {
            TxnFilterDialogUtil.show(requireContext(), findNavController())
        }

        val adapter = TransactionAdapter(emptyList()) { txn ->
            TransactionDialogUtil.showTransactionDialog(requireContext(), txn)
        }

        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = adapter

        vm.fetchLuckySpin(uid)
        vm.luckySpinTxns.observe(viewLifecycleOwner) { adapter.submitList(it) }
        vm.loading.observe(viewLifecycleOwner) { if (it) showLoading() else hideLoading() }
    }
}