package com.codingEmpire.bitbloom.repos

import android.content.Context
import android.util.Log
import com.codingEmpire.bitbloom.models.AnnouncementModel
import com.codingEmpire.bitbloom.utils.PrefService
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class AccountRepository(context: Context) {

    private val prefService = PrefService(context)
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun fetchUserProfile(): Map<String, Any?>? {
        val userId = prefService.getString("user_id")
        if (userId.isNullOrEmpty()) {
            Log.e("AccountRepository", "No user ID in Prefs")
            return null
        }

        return try {
            val snap = firestore.collection("users")
                .whereEqualTo("id", userId)
                .limit(1)
                .get()
                .await()

            if (!snap.isEmpty) {
                val data = snap.documents.first().data
                if (data != null) {
                    prefService.saveUserProfile(data)

                    val storageRef = com.google.firebase.storage.FirebaseStorage.getInstance().reference
                    val profileImageRef = storageRef.child("profile_pics/$userId.jpg")
                    profileImageRef.downloadUrl
                        .addOnSuccessListener { uri ->
                            prefService.saveProfileImageUrl(uri.toString())
                        }
                        .addOnFailureListener {
                            // optional: prefService.saveProfileImageUrl("") or log error
                        }
                }
                data
            } else null

        } catch (e: Exception) {
            Log.e("AccountRepository", "Error fetching profile: ${e.message}")
            null
        }
    }


    fun getAnnouncements(callback: (List<AnnouncementModel>?) -> Unit) {


        val db = FirebaseFirestore.getInstance()


        // Query Firestore for announcements within the last week
        db.collection("announcements")
            .orderBy("time", Query.Direction.DESCENDING)  // Order by "time" in descending order
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot != null && !snapshot.isEmpty) {
                    // Map documents to AnnouncementModel
                    val announcements = snapshot.documents.mapNotNull {
                        it.toObject(AnnouncementModel::class.java)
                    }
                    callback(announcements)  // Pass the list of announcements
                } else {
                    callback(emptyList())  // No announcements found
                }
            }
            .addOnFailureListener { exception ->
                callback(null)  // Return null if there's an error
            }
    }
    fun getAnnouncementImageUrls(callback: (List<String>?) -> Unit) {
        firestore.collection("announcement_images")
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot != null && !snapshot.isEmpty) {
                    val urls = snapshot.documents.mapNotNull { doc ->
                        doc.getString("imageUrl")
                    }
                    callback(urls)
                } else {
                    callback(emptyList())
                }
            }
            .addOnFailureListener {
                callback(null)
            }
    }
}
