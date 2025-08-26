package com.codingEmpire.bitbloom.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.codingEmpire.bitbloom.R
import com.codingEmpire.bitbloom.adapters.TransactionAdapter
import com.codingEmpire.bitbloom.databinding.FragmentPurchasedPlansHistoryBinding
import com.codingEmpire.bitbloom.utils.PrefService
import com.codingEmpire.bitbloom.utils.TransactionDialogUtil
import com.codingEmpire.bitbloom.utils.TxnFilterDialogUtil
import com.codingEmpire.bitbloom.viewModels.TransactionViewModel
import com.google.android.material.snackbar.Snackbar
import android.content.res.ColorStateList
import com.codingEmpire.bitbloom.utils.SoundManager

class PurchasedPlansHistory : BaseFragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var profileTitle: TextView
    private lateinit var filterBtn: ImageButton
    private lateinit var adapter: TransactionAdapter
    private lateinit var viewModel: TransactionViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_purchased_plans_history, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDrawerTrigger(view)

        recyclerView = view.findViewById(R.id.rvPurchasedPlans)
        profileTitle = view.findViewById(R.id.profileTitle)
        filterBtn = view.findViewById(R.id.iv_filter)

        adapter = TransactionAdapter(emptyList()) { txn ->
            TransactionDialogUtil.showTransactionDialog(requireContext(), txn)
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        viewModel = ViewModelProvider(requireActivity())[TransactionViewModel::class.java]

        val userId = PrefService(requireContext()).getUserId().orEmpty()
        viewModel.fetchPlanTransactions(userId)

        viewModel.planTransactions.observe(viewLifecycleOwner) { list ->
            val sorted = list.sortedByDescending { it.timestamp }
            adapter.submitList(sorted)
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
}
