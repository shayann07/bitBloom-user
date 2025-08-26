package com.codingEmpire.bitbloom.repos.support

import com.codingEmpire.bitbloom.models.support.SupportTicket
import com.codingEmpire.bitbloom.utils.support.TicketStatus
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

class SupportTicketRepository private constructor(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private val col = db.collection("tickets")

    /* ------------ singleton ------------ */
    companion object {
        @Volatile
        private var INSTANCE: SupportTicketRepository? = null
        fun getInstance(): SupportTicketRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SupportTicketRepository().also { INSTANCE = it }
            }
    }

    /* ------------ USER ------------ */

    suspend fun submitTicket(ticket: SupportTicket) {
        val doc = col.document()
        // Write all user fields first …
        doc.set(ticket.copy(id = doc.id, status = TicketStatus.PENDING.value))
        // … then let Firestore stamp the server time
        doc.update("createdAt", FieldValue.serverTimestamp())
    }

    fun ticketsByUser(userId: String) =
        col.whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .asFlow()

    /* ------------ ADMIN helpers kept for later ------------ */
    // ...

    /* ------------ utils ------------ */

    private fun Query.asFlow() = callbackFlow<List<SupportTicket>> {
        val reg = addSnapshotListener { snap, _ ->
            trySend(snap?.toObjects(SupportTicket::class.java) ?: emptyList())
        }
        awaitClose { reg.remove() }
    }

    /** Exposed for TicketDetailsFragment real-time listener */
    fun ticketsCollection() = col
}
