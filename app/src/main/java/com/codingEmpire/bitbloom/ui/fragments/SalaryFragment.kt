package com.codingEmpire.bitbloom.ui.fragments

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import com.airbnb.lottie.LottieAnimationView
import com.codingEmpire.bitbloom.R
import com.codingEmpire.bitbloom.databinding.FragmentSalaryBinding
import com.codingEmpire.bitbloom.models.SalaryData
import com.codingEmpire.bitbloom.utils.SoundManager
import com.codingEmpire.bitbloom.viewModels.SalaryViewModel
import com.google.android.material.snackbar.Snackbar

class SalaryFragment : BaseFragment() {
    private var _binding: FragmentSalaryBinding? = null
    private val binding get() = _binding!!
    private val vm: SalaryViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSalaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)
        setUpReward(view)

        // Observe loading, error, and data
        vm.loading.observe(viewLifecycleOwner) { if (it) showLoading() else hideLoading() }
        vm.error.observe(viewLifecycleOwner) { it?.let { msg -> showSnackbar(msg, true) } }
        vm.salaryData.observe(viewLifecycleOwner) { data -> bindData(data) }

        // Always load salary data
        vm.loadSalary()

        vm.collectEnabled.observe(viewLifecycleOwner) { enabled ->

            binding.btnContact.apply {
                // Always clickable so we can notify the user
                isEnabled = true

                // Save the real eligibility flag on the view itself
                setTag(R.id.tag_salary_eligible, enabled)

                // Visual cue (opacity + tint)
                alpha = if (enabled) 1f else 0.45f
                backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        context,
                        if (enabled) R.color.blue else R.color.grey   // add a grey in colors.xml
                    )
                )
            }
        }

        binding.btnContact.setOnClickListener { btn ->
            val eligible = (btn.getTag(R.id.tag_salary_eligible) as? Boolean) == true
            if (eligible) {
                vm.collect()                         // plays success sound inside ViewModel
            } else {
                // still play the “failure” sound + snackbar
                SoundManager.playFailure(requireContext())
                showSnackbar("You haven’t unlocked a new salary level yet.", true)
            }
        }

        vm.collectSuccess.observe(viewLifecycleOwner) {
            playConfetti()                         // ⬅ animation + success sound
            showSnackbar("Salary credited to your wallet!")
        }
    }

    private fun bindData(data: SalaryData) = with(binding) {
        tvBalanceAmount.text = "$${"%,.2f".format(data.currentBalance)}"
        tvSelfInvestAmount.text = "$${"%,.2f".format(data.selfInvestSum)}"
        tvDirectActive.text = data.directActive.toString()
        indirectActive.text = data.indirectActive.toString()
    }

    /* Paste once – e.g. inside SalaryFragment or BaseFragment */
    private fun playConfetti(onEnd: () -> Unit = {}) {
        val root = requireView() as ViewGroup
        val lottie = LottieAnimationView(requireContext()).apply {
            setAnimation(R.raw.confetti)       // same JSON you use on Home screen
            repeatCount = 0
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(lottie)
        SoundManager.playSuccess(requireContext())             // 👂 extra “whoosh” 🎉
        lottie.playAnimation()
        lottie.postDelayed({
            root.removeView(lottie)
            onEnd()
        }, 2000)                                               // match the JSON length
    }

    // Optionally keep this for toast/snackbar feedback elsewhere
    private fun showSnackbar(message: String, isError: Boolean = false) {
        if (isError) SoundManager.playFailure(requireContext())
        val snack = Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG)
        val bgColor = ContextCompat.getColor(
            requireContext(), if (isError) R.color.snackbar_error else R.color.snackbar_success
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
