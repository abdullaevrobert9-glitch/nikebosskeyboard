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

    // Каждый раз, когда клавиатура показывается: сброс слоя/Shift и применение высоты
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardView?.onShow()
    }

    /**
     * code > 0  -> символ (кодпоинт)
     * KEY_DELETE / KEY_ENTER / KEY_SPACE -> спецклавиши
     * feedback = false нужен для автоповтора Delete (без вибрации и звука на каждый повтор)
     */
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

    private fun giveFeedback() {
        val prefs = getSharedPreferences("nikeboss_keyboard", Context.MODE_PRIVATE)

        if (prefs.getBoolean("vibration", true) && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(20)
            }
        }

        if (prefs.getBoolean("sound", true)) {
            audio.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.5f)
        }
    }

    private fun deleteBackward(ic: InputConnection) {
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            // Есть выделение: удаляем его целиком
            ic.commitText("", 1)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // По кодпоинтам, чтобы не ломать эмодзи (суррогатные пары)
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
            // Поиск / Отправить / Далее и т.п.
            ic.performEditorAction(action)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }
}
