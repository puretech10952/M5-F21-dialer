package com.puretech.dialer

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.puretech.dialer.databinding.ActivitySetDefaultBinding

/**
 * Full-screen gate shown whenever PureTech isn't the default phone app. It
 * blocks the rest of the app (recents, settings, keypad) until the user sets us
 * as default — pressing Back sends them home rather than into the app.
 */
class SetDefaultActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetDefaultBinding
    private val roleManager by lazy { getSystemService(RoleManager::class.java) }

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { dismissIfDefault() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetDefaultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.setDefault.setOnClickListener { requestDefault() }

        // Back doesn't let them slip past the gate — it just leaves the app.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })
    }

    override fun onResume() {
        super.onResume()
        dismissIfDefault()
    }

    private fun dismissIfDefault() {
        if (isDefault(this)) finish()
    }

    private fun requestDefault() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            roleManager?.isRoleAvailable(RoleManager.ROLE_DIALER) == true
        ) {
            roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
        }
    }

    companion object {
        fun isDefault(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
            val rm = context.getSystemService(RoleManager::class.java) ?: return true
            return rm.isRoleHeld(RoleManager.ROLE_DIALER)
        }

        /** Launch the gate if we're not the default dialer. @return true if gated. */
        fun gateIfNeeded(activity: Activity): Boolean {
            if (isDefault(activity)) return false
            activity.startActivity(Intent(activity, SetDefaultActivity::class.java))
            return true
        }
    }
}
