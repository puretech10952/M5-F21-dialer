package com.puretech.dialer

import android.Manifest
import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.PhoneNumberFormattingTextWatcher
import android.text.method.ArrowKeyMovementMethod
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.puretech.dialer.databinding.ActivityMainBinding
import java.util.Locale

/**
 * The dial screen. Keypad-only entry into a custom no-IME field with a blinking
 * cursor + D-pad editing; the first dialed digit is captured from the launch
 * intent. As you type, ranked contact suggestions (number + T9 name matching)
 * appear below: tap = call, long-press = edit / message / copy.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val roleManager by lazy { getSystemService(RoleManager::class.java) }
    private val telecomManager by lazy { getSystemService(TelecomManager::class.java) }

    private var pendingNumber: String? = null
    private var zeroAsPlus = false
    private var callPlaced = false
    /** One-shot per launch: "open keypad by default" should fire only once. */
    private var didAutoOpenKeypad = false

    private var allContacts: List<Contact> = emptyList()
    // Top frequently-called contacts, shown as quick-call shortcuts while the
    // dial pad is empty (replaced by ranked suggestions once the user types).
    private var frequentContacts: List<Contact> = emptyList()
    private lateinit var suggestionAdapter: SuggestionAdapter

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { updateBanner() }

    private val callPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted && pendingNumber != null) placeCall() else pendingNumber = null }

    private val contactsPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) loadContacts() }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* best-effort: the ongoing-call notification just won't show if denied */ }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keypad-only: never show the on-screen IME for the number field.
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        )
        binding.numberInput.apply {
            // NoImeEditText blocks the keyboard; inputType=phone keeps a blinking
            // cursor + D-pad editing. Characters are still fed in manually.
            showSoftInputOnFocus = false
            movementMethod = ArrowKeyMovementMethod.getInstance()
            isCursorVisible = true
            addTextChangedListener(PhoneNumberFormattingTextWatcher(Locale.US.country))
            doAfterTextChanged { onNumberChanged() }
            requestFocus()
        }

        binding.btnBackspace.setOnClickListener { backspace() }
        binding.btnBackspace.setOnLongClickListener { clearAll(); true }
        binding.btnDialMenu.setOnClickListener { showDialMenu(it) }
        setupDialPad()
        binding.btnAddContactDial.setOnClickListener { addDialedToContacts() }
        binding.btnMessageDial.setOnClickListener { messageDialed() }

        suggestionAdapter = SuggestionAdapter(
            onCall = { callContact(it.number) },
            onOptions = { c, v -> showOptions(c, v) }
        )
        binding.suggestions.layoutManager = LinearLayoutManager(this)
        binding.suggestions.adapter = suggestionAdapter

        binding.setDefaultButton.setOnClickListener {
            if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
            }
        }

        ensureContacts()
        maybeRequestNotifications()
        prefillFromIntent(intent)
    }

    /**
     * Optional on-screen dial pad (this is a T9 phone, so it's off by default).
     * The floating button reveals it; tap inserts a digit, long-press 0 inserts
     * '+'; the down chevron hides the pad again so contact suggestions show.
     */
    private fun setupDialPad() {
        val keys = mapOf(
            binding.key0 to '0', binding.key1 to '1', binding.key2 to '2', binding.key3 to '3',
            binding.key4 to '4', binding.key5 to '5', binding.key6 to '6', binding.key7 to '7',
            binding.key8 to '8', binding.key9 to '9',
            binding.keyStar to '*', binding.keyHash to '#'
        )
        for ((view, ch) in keys) view.setOnClickListener { insert(ch) }
        binding.key0.setOnLongClickListener { insert('+'); true }
        binding.btnMinimize.setOnClickListener { setKeypadShown(false) }
        binding.fabKeypad.setOnClickListener { setKeypadShown(true) }
        binding.btnDialBig.setOnClickListener { startCall() }
    }

    /** The 12 dial keys, top-left to bottom-right (used to resize the big keypad). */
    private val dialKeys by lazy {
        listOf(
            binding.key1, binding.key2, binding.key3, binding.key4, binding.key5,
            binding.key6, binding.key7, binding.key8, binding.key9,
            binding.keyStar, binding.key0, binding.keyHash
        )
    }

    /** True while an affordance exists to open / re-open the pad. Big-keypad mode
     *  implies on-screen dialing, so it makes the pad reachable too. */
    private fun keypadAffordance() =
        Prefs.floatingDialButton(this) || Prefs.keypadDefaultOpen(this) || Prefs.bigKeypad(this)

    /** Show/hide the on-screen pad; the FAB is the affordance to bring it back. */
    private fun setKeypadShown(shown: Boolean) {
        binding.dialpadPanel.visibility = if (shown) View.VISIBLE else View.GONE
        binding.fabKeypad.visibility =
            if (!shown && keypadAffordance()) View.VISIBLE else View.GONE
    }

    /**
     * Reflect the keypad Settings on each resume: size the pad (normal vs. the
     * big smartphone layout with a green dial button) and decide what's visible.
     * "Open by default" shows the pad once per launch; it can still be minimized
     * for the session and the FAB brings it back.
     */
    private fun applyKeypadSetting() {
        applyBigKeypad()
        if (Prefs.keypadDefaultOpen(this) && !didAutoOpenKeypad) {
            didAutoOpenKeypad = true
            setKeypadShown(true)
            return
        }
        // Leave an already-open pad alone; otherwise show the FAB only if enabled.
        if (binding.dialpadPanel.visibility == View.VISIBLE) return
        binding.dialpadPanel.visibility = View.GONE
        binding.fabKeypad.visibility = if (keypadAffordance()) View.VISIBLE else View.GONE
    }

    /** Scale the dial keys + reveal the green dial button when "big keypad" is on. */
    private fun applyBigKeypad() {
        val big = Prefs.bigKeypad(this)
        val density = resources.displayMetrics.density
        val keyHeight = ((if (big) 76 else 56) * density).toInt()
        val digitSp = if (big) 32f else 26f
        for (key in dialKeys) {
            key.layoutParams = key.layoutParams.apply { height = keyHeight }
            (key.getChildAt(0) as? android.widget.TextView)
                ?.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, digitSp)
        }
        binding.btnDialBig.visibility = if (big) View.VISIBLE else View.GONE
    }

    /** Leave the dialer when the user goes Home, so stale numbers don't pile up. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (pendingNumber == null && !callPlaced) finish()
    }

    /**
     * Once a call is placed, finish only after the in-call screen has covered us
     * (onStop) — finishing immediately would briefly flash the recents list
     * underneath before the call UI appears.
     */
    override fun onStop() {
        super.onStop()
        if (callPlaced) finish()
    }

    private fun maybeRequestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun onNumberChanged() {
        updateSuggestions()
        val hasText = !binding.numberInput.text.isNullOrEmpty()
        binding.btnBackspace.visibility = if (hasText) View.VISIBLE else View.GONE
        binding.btnDialMenu.visibility = if (hasText) View.VISIBLE else View.GONE
        binding.numberActions.visibility = if (hasText) View.VISIBLE else View.GONE
        autoSizeNumberField()
    }

    /**
     * Shrink the number text so a long number stays fully visible instead of
     * scrolling off the side. Reduces from 32sp down to a floor until it fits.
     */
    private fun autoSizeNumberField() {
        val et = binding.numberInput
        val avail = et.width - et.paddingStart - et.paddingEnd
        if (avail <= 0) { et.post { autoSizeNumberField() }; return }
        val sd = resources.displayMetrics.scaledDensity
        val maxPx = 32f * sd
        val minPx = 15f * sd
        val text = et.text?.toString().orEmpty()
        if (text.isEmpty()) {
            et.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, maxPx); return
        }
        val paint = android.graphics.Paint().apply { typeface = et.typeface }
        var size = maxPx
        while (size > minPx) {
            paint.textSize = size
            if (paint.measureText(text) <= avail) break
            size -= 1f
        }
        et.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size)
    }

    private fun dialedDigits(): String =
        (binding.numberInput.text?.toString() ?: "")
            .filter { it.isDigit() || it == '+' || it == '*' || it == '#' }

    private fun addDialedToContacts() {
        val n = dialedDigits()
        if (n.isEmpty()) return
        try {
            startActivity(
                Intent(ContactsContract.Intents.Insert.ACTION).apply {
                    type = ContactsContract.RawContacts.CONTENT_TYPE
                    putExtra(ContactsContract.Intents.Insert.PHONE, n)
                }
            )
        } catch (_: Exception) {
        }
    }

    private fun messageDialed() {
        val n = dialedDigits()
        if (n.isEmpty()) return
        try {
            startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(n)}")))
        } catch (_: Exception) {
        }
    }

    private fun clearAll() {
        binding.numberInput.text?.clear()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        prefillFromIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Block the keypad until Terms/Privacy are accepted and we're default.
        if (Gates.enforce(this)) return
        updateBanner()
        applyKeypadSetting()
    }

    // --- Contacts + suggestions ------------------------------------------------

    private fun ensureContacts() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            == PackageManager.PERMISSION_GRANTED
        ) loadContacts()
        else contactsPermLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    private fun loadContacts() {
        Thread {
            val list = ContactsRepository.load(applicationContext)
            runOnUiThread {
                allContacts = list
                // Top 5 most-called contacts (counts come from the call log via
                // ContactsRepository), de-duped by number, for the empty-pad shortcuts.
                frequentContacts = list.asSequence()
                    .filter { it.timesContacted > 0 }
                    .sortedWith(
                        compareByDescending<Contact> { it.timesContacted }
                            .thenByDescending { it.lastTimeContacted }
                    )
                    .distinctBy { it.digits.takeLast(10) }
                    .take(5)
                    .toList()
                updateSuggestions()
            }
        }.start()
    }

    private fun updateSuggestions() {
        val q = binding.numberInput.text?.filter { it.isDigit() }?.toString() ?: ""
        // Empty pad → quick-call the most frequently dialled contacts; once a digit
        // is entered, switch to ranked name/number suggestions.
        val showFrequent = q.isEmpty()
        suggestionAdapter.submit(
            if (showFrequent) frequentContacts else ContactsRepository.search(q, allContacts)
        )
        binding.frequentLabel.visibility =
            if (showFrequent && frequentContacts.isNotEmpty()) View.VISIBLE else View.GONE
    }

    // --- First-digit capture from the launch intent ---------------------------

    private fun prefillFromIntent(intent: Intent?) {
        val extras = intent?.extras?.keySet()?.joinToString { "$it=${intent.extras?.get(it)}" }
        Log.d(TAG, "launch intent action=${intent?.action} data=${intent?.data} extras=[$extras]")
        val number = numberFromIntent(intent) ?: return
        binding.numberInput.setText(number)
        binding.numberInput.setSelection(binding.numberInput.text?.length ?: 0)
    }

    private fun numberFromIntent(intent: Intent?): String? {
        intent ?: return null
        // Standard path: the number is in the tel: data.
        val data = intent.data
        val dialActions = setOf(Intent.ACTION_DIAL, Intent.ACTION_VIEW, Intent.ACTION_CALL)
        if (data?.scheme == "tel" && intent.action in dialActions) {
            return Uri.decode(data.schemeSpecificPart)?.trim()
        }
        // M5 launcher path: the digit pressed on the home screen arrives as a
        // "key_value" extra on ACTION_DIAL (no tel: data).
        if (intent.action == Intent.ACTION_DIAL) {
            val kv = intent.extras?.get("key_value")?.toString()?.trim()
            if (!kv.isNullOrEmpty() && kv.all { it.isDigit() || it in "+*#" }) return kv
        }
        return null
    }

    // --- Hardware key handling -------------------------------------------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val kc = event.keyCode
        val firstDown = event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0
        when (kc) {
            KeyEvent.KEYCODE_CALL -> {
                if (event.action == KeyEvent.ACTION_UP) startCall()
                return true
            }

            // 0: tap inserts '0', long-press (first auto-repeat) inserts '+'.
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> {
                when (event.action) {
                    KeyEvent.ACTION_DOWN ->
                        if (event.repeatCount == 0) zeroAsPlus = false
                        else if (!zeroAsPlus) { insert('+'); zeroAsPlus = true }
                    KeyEvent.ACTION_UP -> if (!zeroAsPlus) insert('0')
                }
                return true
            }

            in KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_9 -> {
                if (firstDown) insert('0' + (kc - KeyEvent.KEYCODE_0)); return true
            }
            in KeyEvent.KEYCODE_NUMPAD_1..KeyEvent.KEYCODE_NUMPAD_9 -> {
                if (firstDown) insert('0' + (kc - KeyEvent.KEYCODE_NUMPAD_0)); return true
            }
            KeyEvent.KEYCODE_STAR, KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> {
                if (firstDown) insert('*'); return true
            }
            KeyEvent.KEYCODE_POUND -> { if (firstDown) insert('#'); return true }
            KeyEvent.KEYCODE_PLUS -> { if (firstDown) insert('+'); return true }

            KeyEvent.KEYCODE_DEL -> { if (firstDown) backspace(); return true }

            KeyEvent.KEYCODE_BACK -> {
                if (binding.numberInput.text?.isNotEmpty() == true) {
                    if (firstDown) backspace()
                    return true
                }
                // empty field: fall through to normal Back (leave screen)
            }
            // D-pad LEFT/RIGHT fall through to ArrowKeyMovementMethod (cursor);
            // D-pad UP/DOWN fall through to move focus into the suggestion list.
        }
        return super.dispatchKeyEvent(event)
    }

    private fun insert(c: Char) {
        val et = binding.numberInput
        val text = et.text ?: return
        val start = et.selectionStart.coerceAtLeast(0)
        val end = et.selectionEnd.coerceAtLeast(start)
        text.replace(start, end, c.toString())
        // Real DTMF keypress tone (dial screen only), if enabled in Settings.
        if (Prefs.dialpadTone(this)) DialpadTones.play(c)
    }

    private fun backspace() {
        val et = binding.numberInput
        val text = et.text ?: return
        val start = et.selectionStart
        val end = et.selectionEnd
        if (start != end) {
            text.delete(start.coerceAtMost(end), start.coerceAtLeast(end))
        } else if (start > 0) {
            text.delete(start - 1, start)
        }
    }

    // --- Placing calls ---------------------------------------------------------

    private fun startCall() {
        val mmi = (binding.numberInput.text?.toString() ?: "")
            .filter { it.isDigit() || it in "+*#,;" }
        if (mmi.isEmpty()) return
        dial(normalizeForDial(mmi))
    }

    private fun callContact(raw: String) = dial(normalizeForDial(raw))

    private fun dial(number: String) {
        if (number.isEmpty()) return
        // Dialer short-codes (*#06# IMEI, *#*#…#*#* secret codes) are handled
        // here instead of being placed as a call.
        if (SpecialCodes.handle(this, number)) {
            clearAll()
            return
        }
        pendingNumber = number
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            == PackageManager.PERMISSION_GRANTED
        ) placeCall()
        else callPermLauncher.launch(Manifest.permission.CALL_PHONE)
    }

    private fun placeCall() {
        val n = pendingNumber ?: return
        pendingNumber = null
        // Dialer.place encodes MMI/USSD intact and applies the default SIM.
        Dialer.place(this, n)
        // Don't finish() here: that flashes the recents list before the in-call
        // screen appears. We finish in onStop(), once the call UI covers us.
        callPlaced = true
    }

    /** Strip formatting; pass MMI/USSD through, else apply assisted-dialing country code. */
    private fun normalizeForDial(raw: String): String = Dialer.normalize(this, raw)

    // --- Suggestion long-press options ----------------------------------------

    private fun showOptions(c: Contact, anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, R.string.opt_edit)
            menu.add(0, 2, 1, R.string.opt_message)
            menu.add(0, 3, 2, R.string.opt_copy)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> { editBeforeDial(c.number); true }
                    2 -> { messageNumber(c.number); true }
                    3 -> { copyNumber(c.number); true }
                    else -> false
                }
            }
            show()
        }
    }

    /** 3-dot dial menu: insert a 2-second pause (',') or a wait (';'). */
    private fun showDialMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, R.string.dial_add_pause)
            menu.add(0, 2, 1, R.string.dial_add_wait)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> { insert(','); true }
                    2 -> { insert(';'); true }
                    else -> false
                }
            }
            show()
        }
    }

    private fun editBeforeDial(number: String) {
        binding.numberInput.setText(number)
        binding.numberInput.setSelection(binding.numberInput.text?.length ?: 0)
    }

    private fun messageNumber(number: String) {
        try {
            startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")))
        } catch (e: Exception) {
            Log.w(TAG, "sms failed: ${e.message}")
        }
    }

    private fun copyNumber(number: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("number", number))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    // --- Default-dialer banner -------------------------------------------------

    private fun updateBanner() {
        val held = roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
        binding.defaultBanner.visibility = if (held) View.GONE else View.VISIBLE
    }

    companion object {
        private const val TAG = "M5Dialer"
    }
}
