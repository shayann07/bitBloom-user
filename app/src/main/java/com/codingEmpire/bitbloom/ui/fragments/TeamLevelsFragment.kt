package com.codingEmpire.bitbloom.ui.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.codingEmpire.bitbloom.R
import com.codingEmpire.bitbloom.adapters.TeamLevelAdapter
import com.codingEmpire.bitbloom.databinding.FragmentTeamLevelsBinding
import com.codingEmpire.bitbloom.utils.PrefService
import com.codingEmpire.bitbloom.utils.SoundManager
import com.codingEmpire.bitbloom.viewModels.TeamLevelViewModel
import com.google.android.material.snackbar.Snackbar

class TeamLevelsFragment : BaseFragment() {

    private var _binding: FragmentTeamLevelsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: TeamLevelViewModel by viewModels()
    private val adapter = TeamLevelAdapter { level ->
        if (level.levelUnlocked) {
            findNavController().navigate(
                TeamLevelsFragmentDirections.actionTeamLevelsToLevelUsers(level.level)
            )
        } else {
            showSnackbar("Level locked", isError = true)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTeamLevelsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDrawerTrigger(view)
        setUpReward(view)

        val userId = PrefService(requireContext()).getUserId().orEmpty()
        setupReferral(userId)
        initRecycler()
        observeVm()
        viewModel.loadLevels()
    }

    private fun setupReferral(userId: String) {
        binding.inputReferral.apply {
            val referralLink = "https://bitbloom.uk/?ref=$userId"
            setText(referralLink)
            isFocusable = false
            setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    compoundDrawablesRelative.getOrNull(2)?.let { drawableEnd ->
                        val touchStart = right - paddingEnd - drawableEnd.intrinsicWidth
                        if (event.rawX >= touchStart) {
                            val clipboard =
                                requireContext().getSystemService(ClipboardManager::class.java)
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("Referral Link", referralLink)
                            )
                            showSnackbar("Link copied to clipboard")
                            v.performClick()
                            return@setOnTouchListener true
                        }
                    }
                }
                false
            }
        }
        binding.textReferralCode.text = userId
        binding.copyCodeContainer.setOnClickListener {
            val clipboard = requireContext().getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("Referral Code", userId))
            showSnackbar("Code copied to clipboard")
        }
    }

    private fun initRecycler() = with(binding.teamLevelRv) {
        layoutManager = LinearLayoutManager(requireContext())
        adapter = this@TeamLevelsFragment.adapter
        setHasFixedSize(true)
    }

    private fun observeVm() {
        viewModel.levels.observe(viewLifecycleOwner) { levels ->
            adapter.submitList(levels)
        }
        viewModel.loading.observe(viewLifecycleOwner) { if (it) showLoading() else hideLoading() }
        viewModel.error.observe(viewLifecycleOwner) { msg ->
            msg?.let { showSnackbar(it, true) }
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
