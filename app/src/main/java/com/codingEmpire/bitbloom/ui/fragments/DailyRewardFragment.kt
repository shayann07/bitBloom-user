package com.codingEmpire.bitbloom.ui.fragments

import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import com.codingEmpire.bitbloom.R
import com.codingEmpire.bitbloom.databinding.FragmentDailyRewardBinding
import com.codingEmpire.bitbloom.utils.PrefService
import com.codingEmpire.bitbloom.utils.SoundManager
import com.codingEmpire.bitbloom.viewModels.DailyRewardViewModel
import com.google.android.material.snackbar.Snackbar

class DailyRewardFragment : BaseFragment() {
    private var _b: FragmentDailyRewardBinding? = null
    private val b get() = _b!!
    private val vm: DailyRewardViewModel by viewModels()

    // Track current eligibility
    private var eligible = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _b = FragmentDailyRewardBinding.inflate(inflater, container, false)
        return b.root
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // SoundManager singleton should be initialized once in app, but safe to call again
        SoundManager.init(requireContext())

        val userId = PrefService(requireContext()).getString("user_id")!!

        setupDrawerTrigger(view)

        // Load reward state
        vm.loadStatus(userId)

        // Observe reward state and update UI, including eligibility
        vm.buttonState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is DailyRewardViewModel.ButtonState.Eligible -> {
                    eligible = true
                    b.btnContact.apply {
                        text = "Collect Day ${state.day}/7"
                        alpha = 1f
                    }
                }

                DailyRewardViewModel.ButtonState.Collected -> {
                    // Just went from eligible to collected: play animation/sound
                    if (eligible) {
                        onRewardCollected()
                    }
                    eligible = false
                    b.btnContact.apply {
                        text = "Collected"
                        alpha = 0.5f // visually "disabled"
                    }
                }
            }
        }

        // Unified click handler — always enabled, check our own "eligible" flag
        b.btnContact.setOnClickListener {
            if (!eligible) {
                // Not eligible, play failure sound
                showSnackbar("Reward already collected today", true)
            } else {
                // Eligible, attempt to claim reward
                vm.claim(userId)
            }
        }
    }

    private fun onRewardCollected() {
        // Play success sound
        SoundManager.playSuccess(requireContext())

        // Show confetti animation
        b.animationContainer.visibility = View.VISIBLE
        b.lottieAnimationView.apply {
            visibility = View.VISIBLE
            playAnimation()
            // Hide after animation ends
            addAnimatorUpdateListener {
                if (!isAnimating) {
                    b.animationContainer.visibility = View.GONE
                }
            }
        }
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
