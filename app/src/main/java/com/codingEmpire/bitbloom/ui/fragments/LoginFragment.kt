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
import com.codingEmpire.bitbloom.databinding.FragmentLoginBinding
import com.codingEmpire.bitbloom.repos.AuthRepository
import com.codingEmpire.bitbloom.utils.PrefService
import com.codingEmpire.bitbloom.utils.SoundManager
import com.codingEmpire.bitbloom.viewModels.AuthViewModel
import com.codingEmpire.bitbloom.viewModels.factory.AuthViewModelFactory
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginFragment : BaseFragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    private val authViewModel: AuthViewModel by viewModels {
        AuthViewModelFactory(
            AuthRepository(
                FirebaseAuth.getInstance(),
                FirebaseFirestore.getInstance(),
                PrefService(requireContext())
            )
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.loginBtn.setOnClickListener {
            loginUser()
        }

        binding.signUpLink.setOnClickListener {
            findNavController().navigate(R.id.signupFragment)
        }

        binding.forgotPassword.setOnClickListener {
            findNavController().navigate(R.id.forgetPasswordFragment)
        }

        authViewModel.loginSuccess.observe(viewLifecycleOwner) { success ->
            hideLoading()
            val user = FirebaseAuth.getInstance().currentUser
            val navController = findNavController()

            when {
                user != null && !user.isEmailVerified -> {
                    user.sendEmailVerification()
                    showSnackbar("Email not verified. A new verification link has been sent.", isError = true)
                }

                success -> {
                    PrefService(requireContext()).saveLogin()

                    // Navigate and THEN show snackbar
                    val navOptions = androidx.navigation.NavOptions.Builder()
                        .setPopUpTo(R.id.loginFragment, true)
                        .build()

                    findNavController().navigate(R.id.homeFragment, null, navOptions)
                    findNavController().currentBackStackEntry?.savedStateHandle?.set("login_success", true)

                }

                else -> {
                    showSnackbar("Invalid email or password", isError = true)
                }
            }
        }

    }

    private fun loginUser() {
        val email = binding.emailInput.text.toString().trim()
        val password = binding.passwordInput.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            showSnackbar("Please enter email and password", isError = true)
            return
        }

        showLoading()
        authViewModel.loginUser(email, password)
    }

    private fun showSnackbar(message: String, isError: Boolean = false) {
        if (isError) {
            SoundManager.playFailure(requireContext())
        }
        val snack = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
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
