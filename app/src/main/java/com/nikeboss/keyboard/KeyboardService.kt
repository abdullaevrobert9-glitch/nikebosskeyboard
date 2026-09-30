package com.nikeboss.keyboard

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View

class KeyboardService : InputMethodService() {
    private var keyboardView: KeyboardView? = null

    override fun onCreateInputView(): View {
        keyboardView = KeyboardView(this)
        return keyboardView!!
    }

    fun onKeyPress(code: Int) {
        val ic = currentInputConnection ?: return

        // Вибрация
        val prefs = getSharedPreferences("nikeboss_keyboard", Context.MODE_PRIVATE)
        if (prefs.getBoolean("vibration", true)) {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(20)
            }
        }

        // Звук
        if (prefs.getBoolean("sound", true)) {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.5f)
        }

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
