// ui/fragments/SalaryHistoryFragment.kt
package com.codingEmpire.bitbloom.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.codingEmpire.bitbloom.R
import com.codingEmpire.bitbloom.adapters.TransactionAdapter
import com.codingEmpire.bitbloom.databinding.FragmentSalaryHistoryBinding
import com.codingEmpire.bitbloom.utils.PrefService
import com.codingEmpire.bitbloom.utils.TxnFilterDialogUtil
import com.codingEmpire.bitbloom.viewModels.TransactionViewModel

class SalaryHistoryFragment : BaseFragment() {

    private var _b: FragmentSalaryHistoryBinding? = null
    private val b get() = _b!!

    private val vm: TransactionViewModel by viewModels({ requireActivity() })

    private lateinit var adapter: TransactionAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _b = FragmentSalaryHistoryBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)

        b.ivFilter.setOnClickListener {
            TxnFilterDialogUtil.show(requireContext(), findNavController())
        }

        /* ---------- list ---------- */
        adapter = TransactionAdapter(emptyList()) { txn ->
            // you already have the receipt-dialog util
            com.codingEmpire.bitbloom.utils.TransactionDialogUtil
                .showTransactionDialog(requireContext(), txn)
        }
        b.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        b.rvHistory.adapter = adapter

        /* ---------- data ---------- */
        val userId = PrefService(requireContext()).getUserId().orEmpty()
        vm.fetchSalaryTxns(userId)

        vm.salaryTxns.observe(viewLifecycleOwner) { list ->
            adapter.submitList(list)
            // Optional empty-state:
            b.rvHistory.isVisible = list.isNotEmpty()
        }
        vm.loading.observe(viewLifecycleOwner) { if (it) showLoading() else hideLoading() }
        vm.error.observe(viewLifecycleOwner) { msg ->
            msg?.let { showSnackbar(it, true) }
        }
    }

    private fun showSnackbar(message: String, isError: Boolean = false) {
        if (isError) com.codingEmpire.bitbloom.utils.SoundManager
            .playFailure(requireContext())

        val snack = com.google.android.material.snackbar.Snackbar
            .make(requireView(), message, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)

        val bgColor = androidx.core.content.ContextCompat.getColor(
            requireContext(),
            if (isError) R.color.snackbar_error else R.color.snackbar_success
        )
        snack.view.backgroundTintList = android.content.res.ColorStateList.valueOf(bgColor)
        snack.setTextColor(
            androidx.core.content.ContextCompat.getColor(requireContext(), android.R.color.white)
        )
        snack.show()
    }

    override fun onDestroyView() {
        super.onDestroyView(); _b = null
    }
}
