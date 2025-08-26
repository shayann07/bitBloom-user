package com.codingEmpire.bitbloom.ui.fragments

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.codingEmpire.bitbloom.R
import com.codingEmpire.bitbloom.databinding.FragmentPlanRecieptBinding
import com.codingEmpire.bitbloom.utils.SoundManager
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlanRecieptFragment : BaseFragment() {

    private var _binding: FragmentPlanRecieptBinding? = null
    private val binding get() = _binding!!

    private var userId: String? = null
    private var userName: String? = null
    private var planName: String? = null
    private var amount: Double? = null
    private var timestamp: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            userId = it.getString("userId")
            userName = it.getString("userName")
            planName = it.getString("planName")
            amount = it.getDouble("amount")
            timestamp = it.getLong("timestamp")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlanRecieptBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)
        setUpReward(view)

        binding.userId.text = userId.orEmpty()
        binding.userName.text = userName.orEmpty()
        binding.planName.text = planName.orEmpty()
        binding.investedAmount.text = "$${amount?.toInt() ?: 0}"
        binding.timestamp.text = formatTimestamp(timestamp ?: System.currentTimeMillis())

        binding.backBtn.setOnClickListener {
            val options = NavOptions.Builder()
                .setPopUpTo(R.id.planRecieptFragment, true)
                .build()
            findNavController().navigate(R.id.homeFragment, null, options)
        }
    }

    private fun formatTimestamp(millis: Long): String {
        val sdf = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
        return sdf.format(Date(millis))
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
