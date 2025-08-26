package com.codingEmpire.bitbloom.utils.support

enum class TicketStatus(val value: String) {
    PENDING("pending"),
    CLOSED("closed");

    companion object {
        fun from(s: String) =
            entries.firstOrNull { it.value == s } ?: PENDING
    }
}