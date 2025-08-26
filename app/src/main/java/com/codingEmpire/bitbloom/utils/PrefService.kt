package com.codingEmpire.bitbloom.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Example SharedPreferences-based service for storing user data, analogous to Dart's PrefService.
 */


class PrefService(context: Context) {

    // PrefService.kt
    companion object {
        fun clearAllPrefs(context: Context) {

            context.getSharedPreferences("my_prefs", Context.MODE_PRIVATE)
                .edit(commit = true) { clear() }


        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("my_prefs", Context.MODE_PRIVATE)

    fun setString(key: String, value: String) {
        prefs.edit { putString(key, value) }
    }


    fun saveUserProfile(data: Map<String, Any?>) {
        prefs.edit {

            data["id"]?.toString()?.let { putString("user_id", it) }
            data["name"]?.toString()?.let { putString("name", it) }
            data["email"]?.toString()?.let { putString("email", it) }
            data["status"]?.toString()?.let { putString("status", it) }
            data["deviceToken"]?.toString()?.let { putString("deviceToken", it) }


        }
    }

    fun getName(): String? {
        return prefs.getString("name", null)
    }


    fun saveLogin() {
        setBoolean("is_logged_in", true)
    }

    fun checkLogin(): Boolean = getBoolean("is_logged_in", false)


    fun getString(key: String): String? {
        return prefs.getString(key, null)
    }

    fun setBoolean(key: String, value: Boolean) {
        prefs.edit { putBoolean(key, value) }
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }

    fun getUserId(): String? {
        return getString("user_id")
    }

    fun saveProfileImageUrl(url: String) {
        prefs.edit { putString("profile_img_url", url) }
    }

    fun getProfileImageUrl(): String? {
        return prefs.getString("profile_img_url", null)
    }

    fun getReferralFromLink(): String? {
        return prefs.getString("referrerId", null)
    }
}