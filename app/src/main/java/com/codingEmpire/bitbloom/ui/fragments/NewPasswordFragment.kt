package com.codingEmpire.bitbloom.ui.fragments

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.codingEmpire.bitbloom.R
import com.codingEmpire.bitbloom.databinding.FragmentNewPasswordBinding
import com.codingEmpire.bitbloom.repos.AuthRepository
import com.codingEmpire.bitbloom.utils.PrefService
import com.codingEmpire.bitbloom.utils.SoundManager
import com.codingEmpire.bitbloom.viewModels.AuthViewModel
import com.codingEmpire.bitbloom.viewModels.factory.AuthViewModelFactory
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class NewPasswordFragment : BaseFragment() {

    private var _binding: FragmentNewPasswordBinding? = null
    private val binding get() = _binding!!

    private val prefService by lazy { PrefService(requireContext()) }

    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModelFactory(
            AuthRepository(
                FirebaseAuth.getInstance(),
                FirebaseFirestore.getInstance(),
                prefService
            )
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.backBtn.setOnClickListener {
            findNavController().popBackStack()
        }

        authViewModel.updatePasswordSuccess.observe(viewLifecycleOwner) { success ->
            hideLoading()
            if (success) {
                showSnackbar("Password updated successfully!")
                findNavController().popBackStack()
            } else {
                showSnackbar("Failed to update password.", isError = true)
            }
        }

        binding.resetBtn.setOnClickListener {
            val oldPass = binding.oldPasswordInput.text.toString().trim()
            val newPass = binding.newPasswordInput.text.toString().trim()
            val confirm = binding.confirmPasswordInput.text.toString().trim()
            val savedOld = prefService.getString("password").orEmpty()

            when {
                oldPass.length < 6 || newPass.length < 6 || confirm.length < 6 ->
                    showSnackbar("Password must be at least 6 characters.", isError = true)

                oldPass != savedOld ->
                    showSnackbar("Old password is incorrect.", isError = true)

                newPass != confirm ->
                    showSnackbar("New passwords do not match.", isError = true)

                newPass == oldPass ->
                    showSnackbar("New password must be different from old password.", isError = true)

                else -> {
                    showLoading()
                    authViewModel.updateUserPassword(
                        prefService.getString("email").orEmpty(),
                        newPass
                    )
                }
            }
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
