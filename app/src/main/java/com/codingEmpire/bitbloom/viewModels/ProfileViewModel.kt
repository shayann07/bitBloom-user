package com.codingEmpire.bitbloom.viewModels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.codingEmpire.bitbloom.repos.ProfileRepository
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val repo: ProfileRepository,
    private val userCode: String
) : ViewModel() {

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _profileData = MutableLiveData<Map<String, Any?>?>()
    val profileData: MutableLiveData<Map<String, Any?>?> = _profileData

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _updateSuccess = MutableLiveData<Boolean>()
    val updateSuccess: LiveData<Boolean> = _updateSuccess

    fun loadProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val data = repo.fetchProfile(userCode)
                if (data != null) {
                    _profileData.value = data
                } else {
                    _error.value = "Profile not found"
                }
            } catch (ex: Exception) {
                _error.value = ex.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateProfile(name: String, dob: String, phone: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val ok = repo.updateProfile(userCode, name, dob, phone)
                _updateSuccess.value = ok
                if (!ok) _error.value = "Update failed"
            } catch (ex: Exception) {
                _error.value = ex.message
                _updateSuccess.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }
}

class ProfileViewModelFactory(
    private val repo: ProfileRepository,
    private val userCode: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(repo, userCode) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
