package com.puretech.dialer

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.puretech.dialer.databinding.ActivityQuickResponsesBinding

/** Edit the canned replies offered when declining a call with a message. */
class QuickResponsesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuickResponsesBinding
    private val fields = ArrayList<EditText>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuickResponsesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.back.setOnClickListener { finish() }

        val pad = (12 * resources.displayMetrics.density).toInt()
        Prefs.quickResponses(this).forEach { text ->
            val field = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                gravity = Gravity.TOP or Gravity.START
                setText(text)
                setPadding(0, pad, 0, pad)
            }
            fields.add(field)
            binding.responsesList.addView(field)
        }
    }

    override fun onPause() {
        super.onPause()
        fields.forEachIndexed { i, f ->
            Prefs.setQuickResponse(this, i, f.text.toString().trim())
        }
    }
}
