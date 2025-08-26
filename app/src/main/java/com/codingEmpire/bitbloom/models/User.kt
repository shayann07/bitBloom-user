package com.codingEmpire.bitbloom.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

data class User(
    val id: String,
    val firebaseUid: String,
    val name: String,
    val email: String,
    val password: String,
    val phoneNo: String,
    val referralCode: String,
    val status: String,
    val dob: String,
    val address: String?,
    val createdAt: Date,
    val isBlocked: Boolean,
    val createdByAdmin: Boolean,
    val deviceToken: String
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "firebaseUid" to firebaseUid,
        "name" to name,
        "email" to email,
        "phoneNo" to phoneNo,
        "password" to password,
        "referralCode" to referralCode,
        "status" to status,
        "dob" to dob,
        "address" to address,
        "createdAt" to Timestamp(createdAt),
        "isBlocked" to isBlocked,
        "createdByAdmin" to createdByAdmin,
        "deviceToken" to deviceToken

    )

    companion object {
        fun fromDocument(doc: DocumentSnapshot): User = User(
            id = doc.getString("id") ?: "",
            firebaseUid = doc.getString("firebaseUid") ?: "",
            name = doc.getString("name") ?: "",
            email = doc.getString("email") ?: "",
            password = doc.getString("password") ?: "",
            phoneNo = doc.getString("phoneNo") ?: "",
            referralCode = doc.getString("referralCode") ?: "",
            status = doc.getString("status") ?: "inactive",
            dob = doc.getString("dob") ?: "",
            address = doc.getString("address"),
            createdAt = doc.getTimestamp("createdAt")?.toDate() ?: Date(),
            isBlocked = doc.getBoolean("isBlocked") ?: false,
            createdByAdmin = doc.getBoolean("createdByAdmin") ?: false,
            deviceToken = doc.getString("deviceToken") ?: ""
        )
    }
}