package com.codingEmpire.bitbloom.repos

import android.util.Log
import com.codingEmpire.bitbloom.models.Account
import com.codingEmpire.bitbloom.models.Earnings
import com.codingEmpire.bitbloom.models.Investment
import com.codingEmpire.bitbloom.models.User
import com.codingEmpire.bitbloom.utils.PrefService
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.util.Date
import kotlin.random.Random

class AuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val prefService: PrefService
) {

    /**
     * Creates a random unique user ID with prefix "U" followed by a random 5-digit number.
     * Checks Firestore to ensure it does not exist.
     */
    private suspend fun generateUniqueUserId(): String {
        val prefix = "U"
        var userId: String
        var exists: Boolean

        do {
            val randomDigits = Random.nextInt(10000, 99999)
            userId = "$prefix$randomDigits"

            val querySnapshot =
                firestore.collection("users").whereEqualTo("id", userId).get().await()

            exists = !querySnapshot.isEmpty
        } while (exists)

        return userId
    }

    /**
     * Fully‑hardened registration routine.
     *
     * ❶  Verify the email is not already registered in *either* FirebaseAuth or Firestore.
     * ❷  Write *placeholder* user + account documents in a single Firestore transaction.
     * ❸  Create the FirebaseAuth account.
     * ❹  Patch the Firestore user document with the real `firebaseUid`.
     * ❺  If *any* step after the placeholder write fails, perform a two‑way rollback:
     *     • Delete the placeholder Firestore docs.
     *     • Delete the FirebaseAuth user (if it was created but patching failed).
     *
     *  All cleanup helpers are `suspend`, so we never call a suspend function from a non‑suspend
     *  context (fixes the “Suspension functions can only be called within coroutine body” error).
     */
    @Suppress("RedundantSuspendModifier")
    @Throws(FirebaseAuthUserCollisionException::class)
    suspend fun registerUser(
        name: String,
        email: String,
        password: String,
        phoneNo: String,
        referralCode: String
    ): Boolean {

        /* 0️⃣  Firestore duplicate check */
        if (!firestore.collection("users")
                .whereEqualTo("email", email)
                .limit(1)
                .get()
                .await()
                .isEmpty
        ) {
            throw FirebaseAuthUserCollisionException(
                "ERROR_EMAIL_ALREADY_IN_USE", "Email already in use"
            )
        }

        /* 1️⃣  Create Auth user (fast-fail on duplicate in Auth) */
        val authResult = try {
            auth.createUserWithEmailAndPassword(email, password).await()
        } catch (e: FirebaseAuthUserCollisionException) {
            throw e
        }
        val firebaseUser = authResult.user
            ?: throw IllegalStateException("FirebaseAuth returned null user")

        /* Roll-back helper */
        suspend fun rollbackAuth() {
            try {
                firebaseUser.delete().await()
            } catch (_: Exception) {
            }
        }

        /* 2️⃣  Prep data BEFORE the transaction (no suspend calls inside TX) */
        val generatedUserId = generateUniqueUserId()        // ← suspend OK here
        val userDoc = firestore.collection("users").document(firebaseUser.uid)
        val accountDoc = firestore.collection("accounts").document()

        val user = User(
            id = generatedUserId,
            firebaseUid = firebaseUser.uid,
            name = name,
            email = email,
            password = password,   // consider hashing / removing in prod
            phoneNo = phoneNo,
            referralCode = referralCode,
            status = "inactive",
            dob = "",
            address = null,
            createdAt = Date(),
            isBlocked = false,
            createdByAdmin = false,
            deviceToken = ""
        )

        val account = Account(
            userId = generatedUserId,
            accountId = accountDoc.id,
            status = "inactive",
            createdAt = Date(),
            investment = Investment(0.0, 0.0, 0.0),
            earnings = Earnings(0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
            plans = emptyList()
        )

        return try {
            /* 3️⃣  Single Firestore transaction */
            firestore.runTransaction { txn ->
                txn.set(userDoc, user.toMap())
                txn.set(accountDoc, account.toMap())
            }.await()

            /* 4️⃣  Post-commit side effects */
            firebaseUser.sendEmailVerification().await()

            prefService.setString("user_id", generatedUserId)
            prefService.setString("email", email)
            prefService.setString("name", name)

            true
        } catch (e: Exception) {
            rollbackAuth()                          // keep system clean
            Log.e("AuthRepository", "Registration failed, rolled back", e)
            false
        }
    }

    /**
     * Logs in an existing user, checks Firestore, and updates stored password if needed.
     * On success, saves user data (user_id, email) into SharedPreferences as well.
     */
    suspend fun loginUser(email: String, password: String): Boolean {
        return try {
            // 1) Sign in via Firebase
            val credential = auth.signInWithEmailAndPassword(email.trim(), password).await()
            val firebaseUser = credential.user ?: return false

            if (credential.user == null) {
                return false
            } else if (!firebaseUser.isEmailVerified) {
                firebaseUser.sendEmailVerification().await()
                return false
            }

            // 2) Check user doc in Firestore
            val querySnapshot =
                firestore.collection("users").whereEqualTo("email", email.trim()).get().await()

            if (querySnapshot.isEmpty) {
                return false
            }


            // 3) Update Firestore password
            val userDoc = querySnapshot.documents.first()
            userDoc.reference.update("password", password).await()

            // 4) Save user data to prefs
            val userId = userDoc.getString("id") ?: ""
            val userName = userDoc.getString("name") ?: ""
            prefService.setString("user_id", userId)
            prefService.setString("email", email)
            prefService.setString("name", userName)
            prefService.setString("password", password)
            prefService.setBoolean("is_logged_in", true)
            prefService.setString("firebase_uid", credential.user?.uid ?: "")
            prefService.saveUserProfile(userDoc.data ?: emptyMap())

            val storageRef = FirebaseStorage.getInstance().reference
            val profileImageRef = storageRef.child("profile_pics/$userId.jpg")
            profileImageRef.downloadUrl
                .addOnSuccessListener { uri ->
                    prefService.saveProfileImageUrl(uri.toString())
                }
                .addOnFailureListener {
                    // Log or ignore
                }

            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                if (!token.isNullOrEmpty()) {
                    firestore.collection("users").document(userDoc.id).update("deviceToken", token)
                }
            }

            Log.d("AuthRepository", "Login successful")
            Log.d("AuthRepository", "User ID: $userId")
            Log.d("AuthRepository", "Email: $email")
            Log.d("AuthRepository", "Name: $userName")
            Log.d("AuthRepository", "Firebase UID: ${credential.user?.uid}")
            Log.d("AuthRepository", "Password: $password")
            Log.d("AuthRepository", "Is logged in: ${prefService.getBoolean("is_logged_in")}")

            true
        } catch (e: FirebaseAuthWeakPasswordException) {
            Log.e("AuthRepository", "Weak password login: ${e.message}")
            false
        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Log.e("AuthRepository", "Invalid credentials login: ${e.message}")
            false
        } catch (e: FirebaseAuthUserCollisionException) {
            Log.e("AuthRepository", "Email already in use login: ${e.message}")
            false
        } catch (e: FirebaseAuthException) {
            Log.e("AuthRepository", "Auth failure login: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e("AuthRepository", "General error login: ${e.message}")
            false
        }
    }

    /**
     * Checks if a user with [email] exists in Firestore
     */
    suspend fun checkEmailExists(email: String): Boolean {
        return try {
            val snapshot =
                firestore.collection("users").whereEqualTo("email", email).get().await()
            !snapshot.isEmpty
        } catch (e: Exception) {
            false
        }
    }

    suspend fun sendPasswordResetEmail(email: String): Boolean {
        return try {
            auth.sendPasswordResetEmail(email).await()
            true
        } catch (e: Exception) {
            Log.e("AuthRepository", "Reset email error: ${e.message}")
            false
        }
    }

    /**
     * Updates user password in Firebase Auth + Firestore.
     */
    suspend fun updateUserPassword(email: String, newPassword: String): Boolean {
        return try {
            val user = auth.currentUser ?: return false

            // Re-authenticate with old password from prefs
            val oldPassword = prefService.getString("password") ?: ""
            val credential = EmailAuthProvider.getCredential(email, oldPassword)
            user.reauthenticate(credential).await()

            // Update in FirebaseAuth
            user.updatePassword(newPassword).await()

            // Update in Firestore
            val snapshot = firestore
                .collection("users")
                .whereEqualTo("email", email)
                .get()
                .await()
            if (!snapshot.isEmpty) {
                snapshot.documents.first().reference
                    .update("password", newPassword)
                    .await()
            }

            // Persist new password locally
            prefService.setString("password", newPassword)
            true

        } catch (e: FirebaseAuthWeakPasswordException) {
            Log.e("AuthRepository", "Weak password update: ${e.message}")
            false

        } catch (e: FirebaseAuthInvalidCredentialsException) {
            Log.e("AuthRepository", "Invalid credentials update: ${e.message}")
            false

        } catch (e: FirebaseAuthException) {
            Log.e("AuthRepository", "Auth failure update: ${e.message}")
            false

        } catch (e: Exception) {
            Log.e("AuthRepository", "General error update: ${e.message}", e)
            false
        }
    }
}