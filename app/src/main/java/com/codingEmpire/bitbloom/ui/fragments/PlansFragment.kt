package com.codingEmpire.bitbloom.ui.fragments

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import com.codingEmpire.bitbloom.R
import com.codingEmpire.bitbloom.adapters.PlansPagerAdapter
import com.codingEmpire.bitbloom.databinding.FragmentPlansBinding
import com.codingEmpire.bitbloom.models.BuyPlan
import com.codingEmpire.bitbloom.repos.BuyPlanRepo
import com.codingEmpire.bitbloom.utils.PrefService
import com.codingEmpire.bitbloom.utils.SoundManager
import com.codingEmpire.bitbloom.viewModels.BuyPlanViewModel
import com.codingEmpire.bitbloom.viewModels.factory.BuyPlanViewModelFactory
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.firestore.FirebaseFirestore

class PlansFragment : BaseFragment() {

    private var _binding: FragmentPlansBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BuyPlanViewModel by viewModels {
        BuyPlanViewModelFactory(
            BuyPlanRepo(FirebaseFirestore.getInstance(), PrefService(requireContext()))
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = FragmentPlansBinding.inflate(inflater, container, false)
        .also { _binding = it }
        .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)
        setUpReward(view)

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            if (loading) showLoading() else hideLoading()
        }

        viewModel.purchasedPlans.observe(viewLifecycleOwner) { allPlans ->
            val all = allPlans
            val active = allPlans.filter { it.planStatus.equals("active", true) }
            val expired = allPlans.filter { it.planStatus.equals("expired", true) }

            binding.viewPager.adapter = PlansPagerAdapter(listOf(all, active, expired))

            TabLayoutMediator(binding.tabStatusDeposit, binding.viewPager) { tab, position ->
                tab.text = when (position) {
                    0 -> "All"
                    1 -> "Active"
                    2 -> "Expired"
                    else -> "Unknown"
                }
            }.attach()
        }

        val userId = PrefService(requireContext()).getUserId()
        if (userId != null) {
            viewModel.fetchPurchasedPlans(userId)
        } else {
            showSnackbar("User not logged in", isError = true)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
