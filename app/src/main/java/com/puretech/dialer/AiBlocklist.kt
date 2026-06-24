package com.puretech.dialer

/**
 * Hidden, bundled blocklist of known AI phone-call services (numbers you can dial
 * to talk to an AI). When the "Block AI numbers" setting is on, [Dialer.place]
 * refuses to call any of these — even from a contact. The list is small on purpose:
 * very few AIs expose a real dialable number (most are app-only). It can be grown
 * in app updates.
 *
 * Entries are stored as bare 10-digit NANP numbers (no country code). [isBlocked]
 * canonicalises whatever the user dialled — stripping caller-ID/vertical service
 * prefixes (*67, *82, #31#…), a leading +1 / 1 country code, and all formatting —
 * so a blocked number is caught however it's entered.
 */
object AiBlocklist {

    private val BLOCKED = setOf(
        "8002428478",  // 1-800-CHATGPT — OpenAI's "Call ChatGPT" voice line
        "3252255264",  // Grok "Ani" companion (xAI)
        "6072255825",  // Grok "Valentine" companion (xAI)
        "6205434765",  // Grok "Rudi" companion (xAI)
        "4422557834"   // Grok "Bad Rudi" companion (xAI)
    )

    fun isBlocked(dialed: String): Boolean {
        val n = canonical(dialed)
        if (n.length < 10) return false
        return BLOCKED.any { n.endsWith(it) }
    }

    /** Strip dialing prefixes + country code + formatting down to a national number. */
    private fun canonical(raw: String): String {
        // Drop one or more leading vertical service codes (*67, *82, *70, #31#, *31#…).
        val noPrefix = raw.trim().replace(Regex("^([*#]\\d{2,4}[*#]?)+"), "")
        var d = noPrefix.filter { it.isDigit() }
        if (d.length == 11 && d.startsWith("1")) d = d.substring(1)
        return d
    }
}
