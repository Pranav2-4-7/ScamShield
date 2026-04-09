package com.scamshield.utils

import android.content.Context
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.util.Log

object ContactsHelper {

    private const val TAG = "ContactsHelper"

    /**
     * Check if a phone number is in the user's contacts.
     * Returns true = known contact, false = unknown caller.
     * No data is stored or transmitted.
     */
    fun isKnownContact(context: Context, phoneNumber: String): Boolean {
        if (phoneNumber.isBlank() || phoneNumber == "Unknown") return false

        return try {
            val uri = android.net.Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(phoneNumber)
            )
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )
            val found = (cursor?.count ?: 0) > 0
            cursor?.close()
            Log.d(TAG, "Number $phoneNumber known: $found")
            found
        } catch (e: Exception) {
            Log.e(TAG, "Contact lookup error: ${e.message}")
            false
        }
    }

    fun getContactName(context: Context, phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        return try {
            val uri = android.net.Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(phoneNumber)
            )
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )
            val name = if (cursor?.moveToFirst() == true) {
                cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
            } else null
            cursor?.close()
            name
        } catch (e: Exception) {
            null
        }
    }

    fun formatNumber(number: String): String {
        return PhoneNumberUtils.formatNumber(number, "IN") ?: number
    }
}
