package com.puretech.dialer

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.graphics.Typeface

/**
 * Renders the lightweight Markdown used in GitHub release notes into styled text
 * for the in-app "What's new" box — headings (`#`/`##`), **bold**, _italic_,
 * `---` dividers and `•`/`-` bullets. Anything else is shown as-is. Kept tiny and
 * dependency-free; it only covers the subset our notes actually use.
 */
object ReleaseNotesFormatter {

    private val INLINE = Regex("""\*\*(.+?)\*\*|_(.+?)_""")
    private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
    private val DIVIDER = Regex("""^-{3,}\s*$""")

    fun format(raw: String): CharSequence {
        val out = SpannableStringBuilder()
        val lines = raw.replace("\r\n", "\n").split("\n")
        lines.forEachIndexed { index, line ->
            if (index > 0) out.append("\n")
            val text = line.trimEnd()

            if (DIVIDER.matches(text)) return@forEachIndexed   // blank separator line

            val heading = HEADING.find(text)
            if (heading != null) {
                val start = out.length
                appendInline(out, heading.groupValues[2])
                out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(RelativeSizeSpan(1.18f), start, out.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                appendInline(out, text)
            }
        }
        return out
    }

    private fun appendInline(out: SpannableStringBuilder, s: String) {
        var last = 0
        for (m in INLINE.findAll(s)) {
            out.append(s.substring(last, m.range.first))
            val bold = m.groupValues[1]
            val italic = m.groupValues[2]
            val start = out.length
            if (bold.isNotEmpty()) {
                out.append(bold)
                out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                out.append(italic)
                out.setSpan(StyleSpan(Typeface.ITALIC), start, out.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            last = m.range.last + 1
        }
        out.append(s.substring(last))
    }
}
