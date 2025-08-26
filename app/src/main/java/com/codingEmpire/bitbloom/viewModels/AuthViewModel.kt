package com.codingEmpire.bitbloom.viewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codingEmpire.bitbloom.repos.AuthRepository
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import kotlinx.coroutines.launch

/**
 * Simple ViewModel to showcase basic MVVM usage for Auth.
 * Typically, you'd also use a sealed class or data class to wrap states (Success/Loading/Error).
 * For brevity this uses LiveData booleans.
 */
class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _loginSuccess = MutableLiveData<Boolean>()
    val loginSuccess: LiveData<Boolean> get() = _loginSuccess

    private val _registrationSuccess = MutableLiveData<Boolean>()
    val registrationSuccess: LiveData<Boolean> get() = _registrationSuccess

    private val _registrationError = MutableLiveData<String?>()
    val registrationError: LiveData<String?> get() = _registrationError

    private val _updatePasswordSuccess = MutableLiveData<Boolean>()
    val updatePasswordSuccess: LiveData<Boolean> get() = _updatePasswordSuccess

    private val _resetEmailSent = MutableLiveData<Boolean>()
    val resetEmailSent: LiveData<Boolean> get() = _resetEmailSent

    private val _checkEmailResult = MutableLiveData<Boolean>()
    val checkEmailResult: LiveData<Boolean> get() = _checkEmailResult

    fun registerUser(
        name: String,
        email: String,
        password: String,
        phoneNo: String,
        referralCode: String
    ) {
        viewModelScope.launch {
            try {
                val ok = authRepository.registerUser(
                    name, email, password, phoneNo, referralCode
                )
                _registrationSuccess.value = ok          // true or false
                _registrationError.value = null
            } catch (e: FirebaseAuthUserCollisionException) {
                _registrationSuccess.value = false
                _registrationError.value = "Email already exists"
            } catch (e: Exception) {
                _registrationSuccess.value = false
                _registrationError.value = e.localizedMessage
            }
        }
    }

    fun loginUser(email: String, password: String) {
        viewModelScope.launch {
            val result = authRepository.loginUser(email, password)
            _loginSuccess.value = result
        }
    }

    fun checkEmailExists(email: String) {
        viewModelScope.launch {
            val result = authRepository.checkEmailExists(email)
            _checkEmailResult.value = result
        }
    }

    fun sendResetEmail(email: String) {
        viewModelScope.launch {
            val result = authRepository.sendPasswordResetEmail(email)
            _resetEmailSent.value = result
        }
    }

    fun updateUserPassword(email: String, newPassword: String) {
        viewModelScope.launch {
            val result = authRepository.updateUserPassword(email, newPassword)
            _updatePasswordSuccess.value = result
        }
    }
}