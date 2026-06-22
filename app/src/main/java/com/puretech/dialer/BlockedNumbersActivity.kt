package com.puretech.dialer

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.puretech.dialer.databinding.ActivityBlockedNumbersBinding

/** List, add and remove blocked numbers (backed by BlockedNumberContract). */
class BlockedNumbersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlockedNumbersBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockedNumbersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.back.setOnClickListener { finish() }
        binding.addButton.setOnClickListener {
            val number = binding.addInput.text?.toString()?.trim().orEmpty()
            if (number.isNotEmpty()) {
                binding.addInput.text?.clear()
                Thread { BlockedNumbers.add(applicationContext, number); reload() }.start()
            }
        }
        reload()
    }

    private fun reload() {
        Thread {
            val list = BlockedNumbers.list(applicationContext)
            runOnUiThread { render(list) }
        }.start()
    }

    private fun render(numbers: List<String>) {
        binding.blockedList.removeAllViews()
        binding.emptyText.visibility = if (numbers.isEmpty()) View.VISIBLE else View.GONE
        for (number in numbers) {
            val row = layoutInflater.inflate(R.layout.item_blocked, binding.blockedList, false)
            row.findViewById<TextView>(R.id.blockedNumber).text = number
            row.findViewById<MaterialButton>(R.id.unblock).setOnClickListener {
                Thread { BlockedNumbers.remove(applicationContext, number); reload() }.start()
            }
            binding.blockedList.addView(row)
        }
    }
}
