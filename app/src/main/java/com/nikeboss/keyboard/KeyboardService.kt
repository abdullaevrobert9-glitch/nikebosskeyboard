package com.nikeboss.keyboard

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

class KeyboardService : InputMethodService() {

    private var keyboardView: KeyboardView? = null

    private val vibrator: Vibrator by lazy {
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    private val audio: AudioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onCreateInputView(): View {
        val view = KeyboardView(this)
        keyboardView = view
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardView?.onShow()
    }

    fun onKeyPress(code: Int, feedback: Boolean = true) {
        val ic = currentInputConnection ?: return
        if (feedback) giveFeedback()

        when (code) {
            KeyboardView.KEY_DELETE -> deleteBackward(ic)
            KeyboardView.KEY_ENTER -> sendEnter(ic)
            KeyboardView.KEY_SPACE -> ic.commitText(" ", 1)
            else -> if (code > 0) ic.commitText(String(Character.toChars(code)), 1)
        }
    }

    // Вибрация и звук никогда не должны ронять набор текста
    private fun giveFeedback() {
        val prefs = getSharedPreferences("nikeboss_keyboard", Context.MODE_PRIVATE)

        if (prefs.getBoolean("vibration", true)) {
            try {
                if (vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(20)
                    }
                }
            } catch (e: Exception) {
                // нет разрешения VIBRATE или вибромотора: просто пропускаем
            }
        }

        if (prefs.getBoolean("sound", true)) {
            try {
                audio.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.5f)
            } catch (e: Exception) {
            }
        }
    }

    private fun deleteBackward(ic: InputConnection) {
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ic.deleteSurroundingTextInCodePoints(1, 0)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
    }

    private fun sendEnter(ic: InputConnection) {
        val info: EditorInfo? = currentInputEditorInfo
        val imeOptions = info?.imeOptions ?: EditorInfo.IME_ACTION_NONE
        val action = imeOptions and EditorInfo.IME_MASK_ACTION
        val noEnterAction = (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0

        if (!noEnterAction &&
            action != EditorInfo.IME_ACTION_NONE &&
            action != EditorInfo.IME_ACTION_UNSPECIFIED
        ) {
            ic.performEditorAction(action)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }
}
