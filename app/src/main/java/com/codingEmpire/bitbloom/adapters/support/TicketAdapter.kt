package com.codingEmpire.bitbloom.adapters.support

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.codingEmpire.bitbloom.R
import com.codingEmpire.bitbloom.databinding.ItemTicketsBinding
import com.codingEmpire.bitbloom.models.support.SupportTicket
import com.codingEmpire.bitbloom.utils.support.TicketStatus

class TicketAdapter(
    private val onClick: (SupportTicket) -> Unit
) : ListAdapter<SupportTicket, TicketAdapter.VH>(DIFF) {

    object DIFF : DiffUtil.ItemCallback<SupportTicket>() {
        override fun areItemsTheSame(a: SupportTicket, b: SupportTicket) = a.id == b.id
        override fun areContentsTheSame(a: SupportTicket, b: SupportTicket) = a == b
    }

    inner class VH(private val vb: ItemTicketsBinding) : RecyclerView.ViewHolder(vb.root) {

        fun bind(t: SupportTicket) = with(vb) {
            userId.text     = t.userId
            userName.text   = t.username
            ticketStatus.text = t.status.replaceFirstChar { it.uppercase() }

            // ‼ Set color based on status
            val colorRes = when (t.status.lowercase()) {
                TicketStatus.PENDING.value -> R.color.ticket_pending     // yellow/orange
                TicketStatus.CLOSED.value  -> R.color.ticket_closed      // green
                else                       -> android.R.color.white
            }
            ticketStatus.setTextColor(ContextCompat.getColor(root.context, colorRes))

            root.setOnClickListener { onClick(t) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemTicketsBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(getItem(position))
}