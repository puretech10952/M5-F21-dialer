package com.puretech.dialer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.puretech.dialer.databinding.ActivityAboutBinding

/** About screen: app name, version/build, copyright, and links to the legal docs. */
class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.back.setOnClickListener { finish() }

        val info = try {
            packageManager.getPackageInfo(packageName, 0)
        } catch (e: Exception) {
            null
        }
        val name = info?.versionName ?: ""
        val code = info?.longVersionCode ?: 0L
        binding.aboutVersion.text = getString(R.string.about_version, name, code)

        binding.rowFeedback.setOnClickListener { sendFeedback(name) }
        binding.rowTerms.setOnClickListener { openLegal(LegalActivity.DOC_TERMS) }
        binding.rowPrivacy.setOnClickListener { openLegal(LegalActivity.DOC_PRIVACY) }
    }

    private fun sendFeedback(version: String) {
        val email = getString(R.string.about_feedback_email)
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$email")
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.about_feedback_subject, version))
        }
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.about_feedback)))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.about_feedback_none, Toast.LENGTH_LONG).show()
        }
    }

    private fun openLegal(doc: String) {
        startActivity(
            Intent(this, LegalActivity::class.java).putExtra(LegalActivity.EXTRA_DOC, doc)
        )
    }
}
