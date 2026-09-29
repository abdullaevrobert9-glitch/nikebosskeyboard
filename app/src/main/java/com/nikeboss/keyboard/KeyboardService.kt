package com.nikeboss.keyboard

import android.inputmethodservice.InputMethodService
import android.view.View

class KeyboardService : InputMethodService() {
    private var keyboardView: KeyboardView? = null

    override fun onCreateInputView(): View {
        keyboardView = KeyboardView(this)
        return keyboardView!!
    }

    fun onKeyPress(code: Int) {
        val ic = currentInputConnection ?: return
        when (code) {
            KeyboardView.KEY_DELETE -> ic.deleteSurroundingText(1, 0)
            KeyboardView.KEY_ENTER -> ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER))
            KeyboardView.KEY_SPACE -> ic.commitText(" ", 1)
            else -> {
                val text = code.toChar().toString()
                ic.commitText(text, 1)
            }
        }
    }
}
