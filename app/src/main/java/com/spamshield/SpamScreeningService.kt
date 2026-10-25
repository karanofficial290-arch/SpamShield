package com.spamshield

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService

/**
 * Android har incoming call yahan bhejta hai, phone ring hone se PEHLE.
 * Block hui call na ring karti hai, na screen pe aati hai, na notification deti hai.
 */
class SpamScreeningService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        val allow = CallResponse.Builder().build()

        if (details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, allow)
            return
        }

        val number = details.handle?.schemeSpecificPart
        val reason = try {
            val store = Store(this)
            Rules.decide(store, number, isInContacts(number)).also {
                if (it != null) store.addLog(number ?: "Hidden", it)
            }
        } catch (e: Exception) {
            null // Kuch bhi gadbad ho to call aane do — important call kabhi miss na ho
        }

        val response = if (reason == null) allow else
            CallResponse.Builder()
                .setDisallowCall(true)     // call aane hi mat do
                .setRejectCall(true)       // caller ko busy/cut
                .setSkipCallLog(true)      // phone ki call history me na dikhe
                .setSkipNotification(true) // missed-call notification bhi nahi
                .build()

        respondToCall(details, response)
    }

    private fun isInContacts(number: String?): Boolean {
        if (number.isNullOrBlank()) return false
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return false
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
                ?.use { it.count > 0 } ?: false
        } catch (e: Exception) {
            false
        }
    }
}
