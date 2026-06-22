package com.puretech.dialer

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone

/** A single callable phone entry, with precomputed data for T9 matching/ranking. */
data class Contact(
    val name: String,
    val number: String,            // original, for display
    val digits: String,            // digits only, for number matching
    val nameT9: String,            // T9 of all letters in the name (anywhere match)
    val wordT9: List<String>,      // T9 of each name word (first/last-name prefix match)
    val photoUri: Uri?,
    val timesContacted: Int,
    val lastTimeContacted: Long
)

/** Maps letters to their T9 keypad digit. */
object T9 {
    fun digitFor(c: Char): Char? = when (c.lowercaseChar()) {
        in 'a'..'c' -> '2'
        in 'd'..'f' -> '3'
        in 'g'..'i' -> '4'
        in 'j'..'l' -> '5'
        in 'm'..'o' -> '6'
        in 'p'..'s' -> '7'
        in 't'..'v' -> '8'
        in 'w'..'z' -> '9'
        else -> null
    }

    /** All letters of [s] mapped to T9 digits (non-letters dropped). */
    fun encode(s: String): String = buildString {
        for (c in s) digitFor(c)?.let { append(it) }
    }
}

object ContactsRepository {

    /** Load every phone number with its contact name + usage stats. */
    fun load(context: Context): List<Contact> {
        val out = ArrayList<Contact>()
        // Before we're the default dialer the contacts permission isn't granted;
        // querying anyway throws SecurityException on this background thread and
        // crashes the app. Bail out quietly until we have access.
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return out
        val projection = arrayOf(
            Phone.DISPLAY_NAME,
            Phone.NUMBER,
            Phone.PHOTO_THUMBNAIL_URI,
            ContactsContract.Contacts.TIMES_CONTACTED,
            ContactsContract.Contacts.LAST_TIME_CONTACTED
        )
        try {
        context.contentResolver.query(
            Phone.CONTENT_URI, projection, null, null, null
        )?.use { c ->
            val nameIdx = c.getColumnIndex(Phone.DISPLAY_NAME)
            val numIdx = c.getColumnIndex(Phone.NUMBER)
            val photoIdx = c.getColumnIndex(Phone.PHOTO_THUMBNAIL_URI)
            val timesIdx = c.getColumnIndex(ContactsContract.Contacts.TIMES_CONTACTED)
            val lastIdx = c.getColumnIndex(ContactsContract.Contacts.LAST_TIME_CONTACTED)
            while (c.moveToNext()) {
                val name = if (nameIdx >= 0) c.getString(nameIdx) ?: "" else ""
                val number = if (numIdx >= 0) c.getString(numIdx) ?: "" else ""
                if (number.isBlank()) continue
                val photo = if (photoIdx >= 0) c.getString(photoIdx)?.let { Uri.parse(it) } else null
                val times = if (timesIdx >= 0) c.getInt(timesIdx) else 0
                val last = if (lastIdx >= 0) c.getLong(lastIdx) else 0L
                val words = name.split(Regex("[^\\p{L}]+")).filter { it.isNotBlank() }
                out.add(
                    Contact(
                        name = name,
                        number = number,
                        digits = number.filter { it.isDigit() },
                        nameT9 = T9.encode(name),
                        wordT9 = words.map { T9.encode(it) },
                        photoUri = photo,
                        timesContacted = times,
                        lastTimeContacted = last
                    )
                )
            }
        }
        } catch (e: SecurityException) {
            // Permission revoked between the check and the query — ignore.
        }
        return out
    }

    /**
     * Rank contacts against the dialed digit string [query] (digits only).
     * Matches by phone number (prefix/substring) and by T9 of the name
     * (per-word prefix, or anywhere), then ranks by match quality, then by
     * how often / how recently the contact was called.
     */
    fun search(query: String, contacts: List<Contact>, limit: Int = 40): List<Contact> {
        if (query.isEmpty()) return emptyList()
        val scored = ArrayList<Pair<Contact, Int>>()
        for (c in contacts) {
            val s = score(c, query)
            if (s > 0) scored.add(c to s)
        }
        scored.sortWith(
            compareByDescending<Pair<Contact, Int>> { it.second }
                .thenByDescending { it.first.timesContacted }
                .thenByDescending { it.first.lastTimeContacted }
                .thenBy { it.first.name.lowercase() }
        )
        return scored.take(limit).map { it.first }
    }

    /** Quick contact-name lookup for a single number (used by the call notification). */
    fun displayName(context: Context, number: String): String? {
        if (number.isBlank()) return null
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return null
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)
        )
        return try {
            context.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (e: SecurityException) {
            null
        }
    }

    /** Starred contacts, for the Favorites strip. */
    fun loadFavorites(context: Context, limit: Int = 30): List<Contact> {
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return emptyList()
        val projection = arrayOf(
            Phone.DISPLAY_NAME, Phone.NUMBER, Phone.PHOTO_THUMBNAIL_URI
        )
        val out = ArrayList<Contact>()
        val seen = HashSet<String>()
        context.contentResolver.query(
            Phone.CONTENT_URI, projection, "${Phone.STARRED} = 1", null, "${Phone.DISPLAY_NAME} ASC"
        )?.use { c ->
            val nameIdx = c.getColumnIndex(Phone.DISPLAY_NAME)
            val numIdx = c.getColumnIndex(Phone.NUMBER)
            val photoIdx = c.getColumnIndex(Phone.PHOTO_THUMBNAIL_URI)
            while (c.moveToNext() && out.size < limit) {
                val name = if (nameIdx >= 0) c.getString(nameIdx) ?: "" else ""
                if (name.isBlank() || !seen.add(name)) continue
                val number = if (numIdx >= 0) c.getString(numIdx) ?: "" else ""
                val photo = if (photoIdx >= 0) c.getString(photoIdx)?.let { Uri.parse(it) } else null
                out.add(Contact(name, number, number.filter { it.isDigit() }, "", emptyList(), photo, 0, 0L))
            }
        }
        return out
    }

    /** Plain text search by name (or digits) — for the call-log search bar. */
    fun searchByText(query: String, contacts: List<Contact>, limit: Int = 50): List<Contact> {
        if (query.isBlank()) return emptyList()
        val q = query.trim().lowercase()
        val digits = query.filter { it.isDigit() }
        return contacts.asSequence()
            .filter { c ->
                c.name.lowercase().contains(q) || (digits.isNotEmpty() && c.digits.contains(digits))
            }
            .sortedBy { it.name.lowercase() }
            .take(limit)
            .toList()
    }

    private fun score(c: Contact, q: String): Int {
        return when {
            c.digits.startsWith(q) -> 100
            c.digits.contains(q) -> 80
            c.wordT9.any { it.startsWith(q) } -> 70
            c.nameT9.startsWith(q) -> 65
            c.nameT9.contains(q) -> 40
            else -> 0
        }
    }
}
