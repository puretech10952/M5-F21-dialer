package com.puretech.dialer.vvm

import android.util.Base64
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

/**
 * Minimal IMAP client for visual voicemail. Voicemails are stored on the carrier
 * IMAP server as e-mails: a text part plus an audio attachment (AMR/MP3). This
 * speaks just enough IMAP to LOGIN, SELECT INBOX, list UIDs, fetch the audio +
 * headers, and flag messages read/deleted. Not a general-purpose IMAP library.
 *
 * Adapted from the OMTP IMAP handling in the AOSP Dialer (Apache-2.0).
 */
class ImapClient(private val cr: VvmCredentials) {

    private var socket: Socket? = null
    private var input: BufferedInputStream? = null
    private var output: OutputStream? = null
    private var tagSeq = 0

    fun connect() {
        val s = if (cr.sslEnabled) {
            SSLSocketFactory.getDefault().createSocket(cr.host, cr.port)
        } else {
            Socket(cr.host, cr.port)
        }
        s.soTimeout = 30_000
        socket = s
        input = BufferedInputStream(s.getInputStream())
        output = s.getOutputStream()
        readLine() // server greeting: "* OK ..."
    }

    fun login() {
        val resp = command("LOGIN ${quote(cr.username)} ${quote(cr.password)}")
        require(resp.ok) { "IMAP LOGIN failed: ${resp.status}" }
    }

    fun selectInbox() {
        val resp = command("SELECT INBOX")
        require(resp.ok) { "IMAP SELECT failed: ${resp.status}" }
    }

    /** UIDs of every message in the inbox, oldest first. */
    fun listUids(): List<String> {
        val resp = command("UID SEARCH ALL")
        val line = resp.untagged.firstOrNull { it.startsWith("* SEARCH") } ?: return emptyList()
        return line.removePrefix("* SEARCH").trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    }

    /** Download one voicemail (headers + audio attachment) by UID, or null on failure. */
    fun fetchMessage(uid: String): VvmMessage? {
        // 1) Structure + key headers to locate the audio part and read sender/date.
        val meta = command(
            "UID FETCH $uid (BODYSTRUCTURE BODY.PEEK[HEADER.FIELDS " +
                "(FROM DATE CONTENT-DURATION X-CONTENT-DURATION)])"
        )
        if (!meta.ok) return null
        val structure = meta.untagged.joinToString("\n")
        val headers = meta.literals.firstOrNull()?.toString(Charsets.US_ASCII).orEmpty()

        val part = audioPartNumber(structure)
        val mime = audioMime(structure)

        // 2) The audio bytes themselves.
        val body = command("UID FETCH $uid BODY.PEEK[$part]")
        if (!body.ok) return null
        val raw = body.literals.firstOrNull() ?: return null
        val audio = if (isBase64(structure)) {
            try { Base64.decode(raw, Base64.DEFAULT) } catch (_: Exception) { raw }
        } else raw

        return VvmMessage(
            uid = uid,
            sender = parseSender(headers),
            dateMillis = parseDate(headers),
            durationSec = parseDuration(headers),
            mimeType = mime,
            audio = audio
        )
    }

    fun markRead(uid: String) {
        runCatching { command("UID STORE $uid +FLAGS (\\Seen)") }
    }

    fun delete(uid: String) {
        runCatching {
            command("UID STORE $uid +FLAGS (\\Deleted)")
            command("EXPUNGE")
        }
    }

    fun close() {
        runCatching { command("LOGOUT") }
        runCatching { socket?.close() }
        socket = null; input = null; output = null
    }

    // --- IMAP transport -------------------------------------------------------

    private data class Response(
        val status: String,
        val untagged: List<String>,
        val literals: List<ByteArray>
    ) {
        val ok get() = status.contains(" OK", ignoreCase = true)
    }

    private fun command(cmd: String): Response {
        val tag = "a${++tagSeq}"
        val out = output ?: error("not connected")
        out.write("$tag $cmd\r\n".toByteArray(Charsets.US_ASCII))
        out.flush()
        return readResponse(tag)
    }

    private val literalRe = Regex("\\{(\\d+)}\\s*$")

    private fun readResponse(tag: String): Response {
        val untagged = ArrayList<String>()
        val literals = ArrayList<ByteArray>()
        while (true) {
            val line = readLine() ?: return Response("$tag NO timeout", untagged, literals)
            val m = literalRe.find(line)
            if (m != null) {
                val n = m.groupValues[1].toInt()
                literals.add(readBytes(n))
                untagged.add(line)
                continue
            }
            if (line.startsWith("$tag ")) {
                return Response(line, untagged, literals)
            }
            untagged.add(line)
        }
    }

    private fun readLine(): String? {
        val inp = input ?: return null
        val buf = ByteArrayOutputStream()
        while (true) {
            val b = inp.read()
            if (b == -1) return if (buf.size() == 0) null else buf.toString("US-ASCII")
            if (b == '\n'.code) break
            if (b != '\r'.code) buf.write(b)
        }
        return buf.toString("US-ASCII")
    }

    private fun readBytes(n: Int): ByteArray {
        val inp = input ?: return ByteArray(0)
        val out = ByteArrayOutputStream(n)
        var remaining = n
        val chunk = ByteArray(8192)
        while (remaining > 0) {
            val read = inp.read(chunk, 0, minOf(chunk.size, remaining))
            if (read == -1) break
            out.write(chunk, 0, read)
            remaining -= read
        }
        return out.toByteArray()
    }

    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    // --- BODYSTRUCTURE / header parsing (best-effort) -------------------------

    /** Index of the audio part within a multipart message. VVM is normally
     *  [text, audio] so the audio is part 2; fall back to that. */
    private fun audioPartNumber(structure: String): String {
        // A single-part audio message fetches as BODY[1]; multipart as BODY[2].
        return if (structure.contains("\"MIXED\"", ignoreCase = true) ||
            structure.contains("\"ALTERNATIVE\"", ignoreCase = true)
        ) "2" else if (structure.contains("AUDIO", ignoreCase = true)) "2" else "1"
    }

    private fun audioMime(structure: String): String = when {
        structure.contains("AMR", ignoreCase = true) -> "audio/amr"
        structure.contains("MPEG", ignoreCase = true) || structure.contains("MP3", ignoreCase = true) -> "audio/mpeg"
        structure.contains("WAV", ignoreCase = true) -> "audio/wav"
        else -> "audio/amr"
    }

    private fun isBase64(structure: String) = structure.contains("BASE64", ignoreCase = true)

    private fun parseSender(headers: String): String {
        val from = headerValue(headers, "From") ?: return ""
        // "From: 18005551234@vmail.carrier.com" or "From: \"x\" <num@host>"
        val at = from.substringAfterLast('<', from).substringBefore('@')
        return at.filter { it.isDigit() || it == '+' }.ifBlank {
            from.filter { it.isDigit() || it == '+' }
        }
    }

    private fun parseDate(headers: String): Long {
        val raw = headerValue(headers, "Date") ?: return System.currentTimeMillis()
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss zzz"
        )
        for (p in patterns) {
            try {
                return java.text.SimpleDateFormat(p, java.util.Locale.US).parse(raw)?.time
                    ?: continue
            } catch (_: Exception) {
            }
        }
        return System.currentTimeMillis()
    }

    private fun parseDuration(headers: String): Long {
        val raw = headerValue(headers, "Content-Duration")
            ?: headerValue(headers, "X-Content-Duration") ?: return 0L
        return raw.trim().toLongOrNull() ?: 0L
    }

    private fun headerValue(headers: String, name: String): String? {
        for (line in headers.split("\r\n", "\n")) {
            if (line.startsWith("$name:", ignoreCase = true)) {
                return line.substringAfter(':').trim()
            }
        }
        return null
    }
}
