package com.codingEmpire.bitbloom.models

import com.google.firebase.Timestamp

data class AnnouncementModel(
    val id: String = "",
    val announcementTitlte: String = "",
    val message: String = "",
    val time: Timestamp = Timestamp.now()
)
