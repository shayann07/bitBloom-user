package com.codingEmpire.bitbloom.ui.fragments

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import com.codingEmpire.bitbloom.R
import com.codingEmpire.bitbloom.databinding.FragmentLeaderboardBinding
import com.codingEmpire.bitbloom.utils.SoundManager
import com.codingEmpire.bitbloom.viewModels.LeaderboardViewModel
import com.google.android.material.snackbar.Snackbar

class LeaderboardFragment : BaseFragment() {
    private var _b: FragmentLeaderboardBinding? = null
    private val b get() = _b!!
    private val vm: LeaderboardViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _b = FragmentLeaderboardBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)
        setUpReward(view)

        // observe loading / error
        vm.loading.observe(viewLifecycleOwner) { if (it) showLoading() else hideLoading() }
        vm.error.observe(viewLifecycleOwner) { msg ->
            msg?.let { showSnackbar(it, true) }
        }

        // 1) business figures
        vm.directBusiness.observe(viewLifecycleOwner) { amount ->
            b.tvDirectAmount.text = "$${"%,.2f".format(amount)}"
        }
        vm.teamBusiness.observe(viewLifecycleOwner) { amount ->
            b.tvTeamAmount.text = "$${"%,.2f".format(amount)}"
        }

        // 2) top-10 table
        vm.leaders.observe(viewLifecycleOwner) { list ->
            list.forEachIndexed { i, leader ->
                val idRes = resources.getIdentifier(
                    "tvID${i + 1}", "id", requireContext().packageName
                )
                val busRes = resources.getIdentifier(
                    "tvTotalBusiness${i + 1}", "id", requireContext().packageName
                )
                b.root.findViewById<TextView>(idRes)?.text = leader.id
                b.root.findViewById<TextView>(busRes)?.text =
                    "$${"%,.2f".format(leader.totalBusiness)}"
            }
        }

        // Always load on view creation
        vm.loadAll()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
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
