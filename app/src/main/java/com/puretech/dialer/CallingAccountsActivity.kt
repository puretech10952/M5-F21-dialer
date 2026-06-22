package com.puretech.dialer

import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import androidx.appcompat.app.AppCompatActivity
import com.puretech.dialer.databinding.ActivityCallingAccountsBinding

/** Choose the default SIM for outgoing calls (dual-SIM only). */
class CallingAccountsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallingAccountsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallingAccountsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.back.setOnClickListener { finish() }

        val accounts = CallingAccounts.list(this)
        if (accounts.size <= 1) {
            binding.emptyText.visibility = View.VISIBLE
            return
        }

        val current = Prefs.defaultAccountId(this)
        // "Ask every time" maps to id == null.
        val ids = listOf<String?>(null) + accounts.map { it.id }
        val labels = listOf(getString(R.string.account_ask)) +
            accounts.map { CallingAccounts.label(this, it) }

        ids.forEachIndexed { i, accId ->
            val rb = RadioButton(this).apply {
                text = labels[i]
                textSize = 16f
                id = View.generateViewId()
                isChecked = accId == current
                setPadding(paddingLeft, dp(14), paddingRight, dp(14))
                setOnClickListener {
                    Prefs.setDefaultAccountId(this@CallingAccountsActivity, accId)
                }
            }
            binding.accountsGroup.addView(rb)
        }
        // Default the selection to "Ask every time" when nothing matches.
        if (binding.accountsGroup.checkedRadioButtonId == -1) {
            (binding.accountsGroup.getChildAt(0) as? RadioButton)?.isChecked = true
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
