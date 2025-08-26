package com.codingEmpire.bitbloom.ui.fragments

import android.animation.Animator
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.codingEmpire.bitbloom.R
import com.codingEmpire.bitbloom.adapters.AchievementsAdapter
import com.codingEmpire.bitbloom.repos.AchievementsRepository
import com.codingEmpire.bitbloom.utils.PrefService
import com.codingEmpire.bitbloom.utils.SoundManager
import com.codingEmpire.bitbloom.viewModels.AchievementsViewModel
import com.codingEmpire.bitbloom.viewModels.factory.AchievementsVMFactory
import com.google.android.material.snackbar.Snackbar

class AchievementsFragment : BaseFragment() {

    private val pref by lazy { PrefService(requireContext()) }
    private val repo by lazy { AchievementsRepository() }
    private val vm: AchievementsViewModel by viewModels {
        AchievementsVMFactory(repo, pref.getUserId() ?: "")
    }

    private lateinit var adapter: AchievementsAdapter
    private lateinit var rv: RecyclerView

    // animation overlay
    private lateinit var animationContainer: FrameLayout
    private lateinit var lottie: LottieAnimationView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_achievements, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)

        // init SoundManager
        SoundManager.init(requireContext())

        // find your animation views
        animationContainer = view.findViewById(R.id.animationContainer)
        lottie = view.findViewById(R.id.lottieAnimationView)

        // adapter with click logic
        adapter = AchievementsAdapter { level ->
            when {
                !level.isUnlocked -> {
                    showSnackbar(
                        "Locked: reach \$${level.directThreshold.toInt()} direct\n" +
                                "and \$${level.indirectThreshold.toInt()} indirect to unlock “${level.name}.”",
                        isError = true
                    )
                }

                level.isCollected -> {
                    showSnackbar("You’ve already collected “${level.name}.”", isError = true)
                }

                else -> {
                    vm.collect(level)
                }
            }
        }

        rv = view.findViewById<RecyclerView>(R.id.teamRankingRv).apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@AchievementsFragment.adapter
        }

        vm.levels.observe(viewLifecycleOwner) { adapter.submitList(it) }
        vm.loading.observe(viewLifecycleOwner) { if (it) showLoading() else hideLoading() }
        vm.event.observe(viewLifecycleOwner) { handleEvent(it) }

        vm.load()
    }

    private fun handleEvent(e: AchievementsViewModel.Event) {
        when (e) {
            is AchievementsViewModel.Event.Snack ->
                showSnackbar(e.msg, isError = true)

            AchievementsViewModel.Event.ShowCongrats ->
                onRewardCollected()
        }
    }

    private fun onRewardCollected() {
        // 1) play success sound
        SoundManager.playSuccess(requireContext())

        // 2) show confetti animation
        animationContainer.visibility = View.VISIBLE
        lottie.apply {
            visibility = View.VISIBLE
            playAnimation()
            // when done, hide container
            addAnimatorListener(object : Animator.AnimatorListener {
                override fun onAnimationRepeat(animator: Animator) {}
                override fun onAnimationCancel(animator: Animator) {}
                override fun onAnimationStart(animator: Animator) {}
                override fun onAnimationEnd(animator: Animator) {
                    animationContainer.visibility = View.GONE
                    lottie.removeAllAnimatorListeners()
                }
            })
        }
    }

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
