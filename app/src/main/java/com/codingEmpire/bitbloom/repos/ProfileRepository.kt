package com.codingEmpire.bitbloom.repos

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class ProfileRepository(
    private val firestore: FirebaseFirestore
) {
    /**
     * Fetch the user document whose `id` field matches [userCode].
     * Returns the data map (or null if not found).
     */
    suspend fun fetchProfile(userCode: String): Map<String, Any?>? {
        val snap = firestore.collection("users")
            .whereEqualTo("id", userCode)
            .get()
            .await()
        return snap.documents.firstOrNull()?.data
    }

    /**
     * Update name, dob, and phoneNo fields on the user whose `id` == [userCode].
     * Returns true on success.
     */
    suspend fun updateProfile(
        userCode: String,
        newName: String,
        newDob: String,
        newPhone: String
    ): Boolean {
        val snap = firestore.collection("users")
            .whereEqualTo("id", userCode)
            .get()
            .await()
        val doc = snap.documents.firstOrNull()?.reference ?: return false
        doc.update(
            mapOf(
                "name" to newName,
                "dob" to newDob,
                "phoneNo" to newPhone
            )
        ).await()
        return true
    }
}
