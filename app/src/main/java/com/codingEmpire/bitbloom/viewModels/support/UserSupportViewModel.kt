package com.codingEmpire.bitbloom.viewModels.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codingEmpire.bitbloom.models.support.SupportTicket
import com.codingEmpire.bitbloom.repos.support.SupportTicketRepository
import com.codingEmpire.bitbloom.utils.PrefService
import com.codingEmpire.bitbloom.utils.support.TicketStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UserSupportViewModel(
    private val repo: SupportTicketRepository,
    private val pref: PrefService
) : ViewModel() {

    private val _myTickets = MutableStateFlow<List<SupportTicket>>(emptyList())
    val myTickets: StateFlow<List<SupportTicket>> = _myTickets

    val pendingCount = MutableStateFlow(0)
    val closedCount = MutableStateFlow(0)

    init {
        listenMyTickets()
    }

    private fun listenMyTickets() {
        val uid = pref.getUserId() ?: return
        viewModelScope.launch {
            repo.ticketsByUser(uid).collect { list ->
                _myTickets.value = list
                pendingCount.value = list.count { it.status == TicketStatus.PENDING.value }
                closedCount.value = list.count { it.status == TicketStatus.CLOSED.value }
            }
        }
    }

    fun submitTicket(phone: String, subject: String, msg: String) {
        val uid = pref.getUserId() ?: return
        val ticket = SupportTicket(
            userId = uid,
            username = pref.getName().orEmpty(),
            email = pref.getString("email").orEmpty(),
            phone = phone,
            deviceToken = pref.getString("deviceToken").orEmpty(),
            subject = subject,
            message = msg
        )
        viewModelScope.launch { repo.submitTicket(ticket) }
    }
}
