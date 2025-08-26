package com.codingEmpire.bitbloom.viewModels.support

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.codingEmpire.bitbloom.repos.support.SupportTicketRepository
import com.codingEmpire.bitbloom.utils.PrefService

class UserSupportVMFactory(private val ctx: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UserSupportViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return UserSupportViewModel(
                SupportTicketRepository.getInstance(),
                PrefService(ctx.applicationContext)
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
