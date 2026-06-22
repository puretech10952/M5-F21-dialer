package com.puretech.dialer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import com.puretech.dialer.databinding.ActivityLegalBinding

/** Shows the Terms of Service or Privacy Policy from a bundled HTML document. */
class LegalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLegalBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLegalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.back.setOnClickListener { finish() }

        val privacy = intent.getStringExtra(EXTRA_DOC) == DOC_PRIVACY
        binding.title.setText(if (privacy) R.string.legal_privacy else R.string.legal_terms)
        val raw = if (privacy) R.raw.privacy_policy else R.raw.terms_of_service
        binding.body.text = HtmlCompat.fromHtml(readRaw(raw), HtmlCompat.FROM_HTML_MODE_COMPACT)
    }

    private fun readRaw(resId: Int): String =
        resources.openRawResource(resId).bufferedReader().use { it.readText() }

    companion object {
        const val EXTRA_DOC = "doc"
        const val DOC_TERMS = "terms"
        const val DOC_PRIVACY = "privacy"
    }
}
