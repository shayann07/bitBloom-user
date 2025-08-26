package com.codingEmpire.bitbloom.ui.fragments

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bluehomestudio.luckywheel.LuckyWheel
import com.bluehomestudio.luckywheel.WheelItem
import com.codingEmpire.bitbloom.R
import com.codingEmpire.bitbloom.repos.LuckySpinRepo
import com.codingEmpire.bitbloom.repos.SpinData
import com.codingEmpire.bitbloom.utils.PrefService
import com.codingEmpire.bitbloom.utils.SoundManager
import com.codingEmpire.bitbloom.viewModels.LuckySpinViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import kotlin.random.Random

private const val SHIFT = -1    // library’s built-in slice offset

class LuckySpinFragment : BaseFragment() {
    private lateinit var wheel: LuckyWheel
    private lateinit var btnSpin: MaterialButton
    private lateinit var btnInvest: MaterialButton
    private lateinit var tvPrize: TextView
    private lateinit var animationContainer: View
    private lateinit var lottieView: com.airbnb.lottie.LottieAnimationView

    private val spinVm: LuckySpinViewModel by viewModels()
    private val repo = LuckySpinRepo()
    private val userId: String by lazy {
        PrefService(requireContext()).getUserId().orEmpty()
    }
    private var hasSpunToday = false


    private val labels = listOf("$0.1", "$0.3", "$0.05", "$0", "$0.03", "$0.2")
    private val colors = listOf(
        "#06D6A0", "#118AB2", "#8338EC",
        "#FFD166", "#EF476F", "#3A86FF"
    )

    private var targetIndex = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_lucky_spin, container, false)

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)
        setUpReward(view)

        wheel = view.findViewById(R.id.luckyWheel)
        btnSpin = view.findViewById(R.id.btnSpin)
        btnInvest = view.findViewById(R.id.investInPlanBtn)
        tvPrize = view.findViewById(R.id.tvResult)
        animationContainer = view.findViewById(R.id.animationContainer)
        lottieView = view.findViewById(R.id.lottieAnimationView)
        animationContainer.visibility = View.GONE

        val today = LocalDate.now().toString()

        // 1) Load spin data (total + lastSpinDate)
        spinVm.loadSpinData(userId)
        spinVm.spinData.observe(viewLifecycleOwner) { data: SpinData ->
            // show running total
            tvPrize.text = "$${"%.2f".format(data.total)}"
            // show Invest button once ≥ $35
            btnInvest.isVisible = data.total >= 35.0
        }

        initWheel()

        // ──────────────────────────────────────────────────────────────────────────────
// LuckySpinFragment.kt  –  new click-listener for btnSpin
// ──────────────────────────────────────────────────────────────────────────────
        btnSpin.setOnClickListener {
            lifecycleScope.launch {

                /* ── 1) Welcome-bonus guard ───────────────────────────────────────── */
                val serverClaimed = FirebaseFirestore.getInstance()
                    .collection("luckySpins")
                    .document(userId)
                    .get()
                    .await()
                    .getBoolean("welcomeRewardClaimed") == true

                val localHasFive = (spinVm.spinData.value?.total ?: 0.0) >= 5.0

                if (!serverClaimed && !localHasFive) {
                    spinVm.claimWelcomeBonus(userId)                // credit silently
                    showSnackbar("🎉 Welcome bonus \$5 added to Lucky USDT")
                }

                /* ── 2) Only one spin per day ─────────────────────────────────────── */
                if (hasSpunToday) {
                    showSnackbar("You’ve already spun today!", true)
                    return@launch
                }

                val lastDate = spinVm.spinData.value?.lastSpinDate
                if (lastDate == today) {                            // server date check
                    hasSpunToday = true
                    showSnackbar("You’ve already spun today!", true)
                    return@launch
                }

                /* ── 3) Start the wheel ───────────────────────────────────────────── */
                btnSpin.isEnabled = false                           // lock UI
                targetIndex = Random.nextInt(labels.size)
                val libIndex = (targetIndex - SHIFT + labels.size) % labels.size
                wheel.rotateWheelTo(libIndex)
            }
        }


        // LuckySpinFragment.kt  (inside wheel.setLuckyWheelReachTheTarget { … })
        wheel.setLuckyWheelReachTheTarget {
            val reward = labels[targetIndex].removePrefix("$").toDouble()
            lifecycleScope.launch {
                try {
                    spinVm.addReward(userId, reward, today)
                    hasSpunToday = true
                    btnSpin.isEnabled = true

                    if (reward == 0.0) {
                        SoundManager.playFailure(requireContext())
                        showSnackbar("Better luck next time!", true)
                    } else {
                        /** 🔥 NEW: success effects */
                        showWinEffects("$${"%.2f".format(reward)}")
                    }

                } catch (ise: IllegalStateException) {
                    SoundManager.playFailure(requireContext())
                    showSnackbar("You’ve already spun today!", true)
                    hasSpunToday = true
                    btnSpin.isEnabled = true
                }
            }
        }

        btnInvest.setOnClickListener {
            // Extract the Double total, then coerce to Float
            val totalF = spinVm.spinData.value?.total?.toFloat() ?: 0f
            val args = bundleOf("prefillAmount" to totalF)
            findNavController().navigate(
                R.id.action_luckySpinFragment_to_investmentPlansFragment,
                args
            )
        }
    }

    private fun initWheel() {
        val blankBmp = createBitmap(1, 1)
        val items = labels.mapIndexed { i, txt ->
            WheelItem(colors[i].toColorInt(), blankBmp, txt)
        }
        wheel.addWheelItems(items)
    }

    private fun showSnackbar(message: String, isError: Boolean = false) {
        if (isError) SoundManager.playFailure(requireContext())
        Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).apply {
            view.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(
                    requireContext(),
                    if (isError) R.color.snackbar_error else R.color.snackbar_success
                )
            )
            setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
            show()
        }
    }

    private fun showWinEffects(msg: String) {
        SoundManager.playSuccess(requireContext())

        animationContainer.visibility = View.VISIBLE
        lottieView.apply {
            progress = 0f
            playAnimation()

            // the only callback we need is onAnimationEnd
            addAnimatorListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animationContainer.visibility = View.GONE
                }
            })
        }

        showSnackbar("You won $msg!", false)
    }
}