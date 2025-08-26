package com.codingEmpire.bitbloom.ui.fragments.support

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.codingEmpire.bitbloom.databinding.FragmentTicketDetailsBinding
import com.codingEmpire.bitbloom.models.support.SupportTicket
import com.codingEmpire.bitbloom.repos.support.SupportTicketRepository
import com.codingEmpire.bitbloom.ui.fragments.BaseFragment
import com.google.android.material.textfield.TextInputEditText

class TicketDetailsFragment : BaseFragment() {

    private var _vb: FragmentTicketDetailsBinding? = null
    private val vb get() = _vb!!

    private val repo = SupportTicketRepository.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _vb = FragmentTicketDetailsBinding.inflate(inflater, container, false)
        return vb.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)

        val id = requireArguments().getString("ticketId")!!
        repo.ticketsCollection().document(id)
            .addSnapshotListener { snap, _ ->
                snap?.toObject(SupportTicket::class.java)?.let { bind(it) }
            }
        vb.remindBtn.isVisible = false
    }

    private fun bind(t: SupportTicket) = with(vb) {
        etUserID.setText(t.userId)
        etEmail.setText(t.email)
        etTicketStatus.setText(t.status.replaceFirstChar { it.uppercase() })
        etSubject.setText(t.subject)
        editMessage.setText(t.message)
        etReply.setText(if (t.reply.isBlank()) "No reply yet" else t.reply)
        makeReadOnlyAndScrollable(editMessage, etReply)
    }

    @SuppressLint("ClickableViewAccessibility")     // we call performClick() manually
    private fun makeReadOnlyAndScrollable(vararg et: TextInputEditText) {
        et.forEach { view ->
            // 1.  Disable editing
            view.keyListener = null
            view.isLongClickable = false
            view.setTextIsSelectable(false)

            // 2.  Allow internal scrolling
            view.movementMethod = ScrollingMovementMethod.getInstance()

            // 3.  Coordinate scroll with parent & preserve click for a11y
            view.setOnTouchListener { v, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN ->               // finger starts
                        v.parent.requestDisallowInterceptTouchEvent(true)

                    MotionEvent.ACTION_UP,                   // finger lifts
                    MotionEvent.ACTION_CANCEL -> {
                        v.parent.requestDisallowInterceptTouchEvent(false)
                        v.performClick()                    // <-- keeps accessibility happy
                    }
                }
                false   // let EditText handle scrolling
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView(); _vb = null
    }
}
