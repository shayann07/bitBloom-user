package com.codingEmpire.bitbloom.viewModels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.codingEmpire.bitbloom.models.AnnouncementModel
import com.codingEmpire.bitbloom.repos.AccountRepository
import kotlinx.coroutines.launch

class AccountViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = AccountRepository(application)

    private val _profileData = MutableLiveData<Map<String, Any?>?>()
    val profileData: LiveData<Map<String, Any?>> = _profileData as LiveData<Map<String, Any?>>

    private val _announcements = MutableLiveData<List<AnnouncementModel>?>()
    val announcements: LiveData<List<AnnouncementModel>> = _announcements as LiveData<List<AnnouncementModel>>

    private val _announcementImageUrls = MutableLiveData<List<String>?>()
    val announcementImageUrls: LiveData<List<String>?> get() = _announcementImageUrls

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading


    fun loadProfile() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val data = repo.fetchUserProfile()
                if (data != null) {
                    _profileData.value = HashMap(data)
                } else {
                    _error.value = "No profile data found"
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadAnnouncements() {
        _isLoading.value = true
        repo.getAnnouncements { list ->
            _isLoading.value = false
            if (list != null) {
                _announcements.value = list
            } else {
                _error.value = "Failed to load announcements"
            }
        }
    }
    fun getAnnouncementImageUrls() {
        repo.
        getAnnouncementImageUrls { urls ->
            _announcementImageUrls.postValue(urls)
        }
    }

    fun clear() {
        _profileData.value = null
        _announcements.value = null
    }
}
