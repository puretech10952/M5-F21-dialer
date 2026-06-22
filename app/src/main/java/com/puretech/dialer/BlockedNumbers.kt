package com.puretech.dialer

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import android.util.Log

/**
 * Blocked-number storage backed by the system [BlockedNumberContract]. Reading
 * and writing requires being the default dialer (which this app is); every call
 * is wrapped defensively so a missing role never crashes the UI.
 */
object BlockedNumbers {

    fun list(context: Context): List<String> {
        val out = ArrayList<String>()
        try {
            context.contentResolver.query(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER),
                null, null, null
            )?.use { c -> while (c.moveToNext()) c.getString(0)?.let { out.add(it) } }
        } catch (e: Exception) {
            Log.w(TAG, "list failed: ${e.message}")
        }
        return out
    }

    fun isBlocked(context: Context, number: String): Boolean {
        if (number.isBlank()) return false
        return try {
            BlockedNumberContract.isBlocked(context, number)
        } catch (e: Exception) {
            false
        }
    }

    fun add(context: Context, number: String): Boolean {
        if (number.isBlank() || isBlocked(context, number)) return false
        return try {
            val values = ContentValues().apply {
                put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number)
            }
            context.contentResolver.insert(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI, values
            ) != null
        } catch (e: Exception) {
            Log.w(TAG, "add failed: ${e.message}")
            false
        }
    }

    fun remove(context: Context, number: String) {
        try {
            context.contentResolver.delete(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                "${BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER} = ?",
                arrayOf(number)
            )
        } catch (e: Exception) {
            Log.w(TAG, "remove failed: ${e.message}")
        }
    }

    private const val TAG = "M5Blocked"
}
