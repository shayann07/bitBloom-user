package com.codingEmpire.bitbloom.ui.fragments.support

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.codingEmpire.bitbloom.R
import com.codingEmpire.bitbloom.adapters.support.TicketAdapter
import com.codingEmpire.bitbloom.databinding.FragmentPendingBinding
import com.codingEmpire.bitbloom.models.support.SupportTicket
import com.codingEmpire.bitbloom.ui.fragments.BaseFragment
import com.codingEmpire.bitbloom.utils.support.TicketStatus
import com.codingEmpire.bitbloom.viewModels.support.UserSupportVMFactory
import com.codingEmpire.bitbloom.viewModels.support.UserSupportViewModel

/** Abstract base reused by pending / closed / all tickets */
abstract class TicketListFragment : BaseFragment() {

    abstract val filter: (SupportTicket) -> Boolean
    abstract val screenTitle: String

    private var _vb: FragmentPendingBinding? = null
    private val vb get() = _vb!!

    private val vm: UserSupportViewModel by viewModels {
        UserSupportVMFactory(requireContext())
    }

    private lateinit var adapter: TicketAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _vb = FragmentPendingBinding.inflate(inflater, container, false)
        return vb.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)

        vb.tvTickets.text = screenTitle

        adapter = TicketAdapter { ticket ->
            val args = bundleOf("ticketId" to ticket.id)
            findNavController().navigate(R.id.ticketDetailsFragment, args)
        }
        vb.rvTickets.adapter = adapter
        vb.rvTickets.layoutManager = LinearLayoutManager(requireContext())

        // real-time list
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            vm.myTickets.collect { fullList ->
                val shown = fullList.filter(filter)
                adapter.submitList(shown)

                // ----- empty-state toggle -----
                vb.rvTickets.isVisible = shown.isNotEmpty()
                vb.tvEmpty.isVisible = shown.isEmpty()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _vb = null
    }
}

/* ---------- concrete fragments ---------- */

class PendingTicketsFragment : TicketListFragment() {
    override val filter = { t: SupportTicket ->
        t.status == TicketStatus.PENDING.value
    }
    override val screenTitle = "Pending Tickets"
}

class ClosedTicketsFragment : TicketListFragment() {
    override val filter = { t: SupportTicket ->
        t.status == TicketStatus.CLOSED.value
    }
    override val screenTitle = "Closed Tickets"
}

class AnsweredTicketsFragment : TicketListFragment() {
    override val filter = { t: SupportTicket ->
        t.status == TicketStatus.CLOSED.value          // same data
    }
    override val screenTitle = "Answered Tickets"
}

class AllTicketsFragment : TicketListFragment() {
    override val filter = { _: SupportTicket -> true }
    override val screenTitle = "All Tickets"
}
