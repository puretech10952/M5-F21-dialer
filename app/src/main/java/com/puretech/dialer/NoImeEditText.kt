package com.puretech.dialer

import android.content.Context
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.appcompat.widget.AppCompatEditText

/**
 * An EditText that never brings up the soft keyboard (keypad-only dialing) but
 * KEEPS the blinking cursor and D-pad editing. Returning null from
 * onCreateInputConnection blocks the IME while leaving the field a normal text
 * editor, so the cursor still renders. Characters are fed in manually from
 * hardware keys.
 */
class NoImeEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? = null
}
