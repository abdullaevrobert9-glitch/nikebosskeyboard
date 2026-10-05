package com.nikeboss.keyboard

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

class KeyboardService : InputMethodService() {

    private var keyboardView: KeyboardView? = null

    // true, если последним мы сами поставили пробел после запятой
    private var autoSpaceAfterComma = false

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
        autoSpaceAfterComma = false
        keyboardView?.onShow()
    }

    /** Нужна ли заглавная буква, судя по тексту перед курсором. */
    fun needsCapital(): Boolean {
        val info = currentInputEditorInfo ?: return false
        val type = info.inputType
        if ((type and InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_TEXT) return false
        when (type and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_FILTER -> return false
        }
        val ic = currentInputConnection ?: return false
        val before = ic.getTextBeforeCursor(16, 0)?.toString() ?: return false

        if (before.isEmpty()) return true            // начало поля
        if (before.last() == '\n') return true       // новая строка

        val trimmed = before.trimEnd(' ', '\t')
        if (trimmed.length == before.length) return false // после знака нет пробела
        if (trimmed.isEmpty()) return true
        return trimmed.last() in ".!?…\n"
    }

    fun onKeyPress(code: Int, feedback: Boolean = true, commaSpace: Boolean = false) {
        val ic = currentInputConnection ?: return
        if (feedback) giveFeedback()

        val hadAutoSpace = autoSpaceAfterComma
        autoSpaceAfterComma = false

        when (code) {
            KeyboardView.KEY_DELETE -> deleteBackward(ic)
            KeyboardView.KEY_ENTER -> sendEnter(ic)
            KeyboardView.KEY_SPACE -> if (!hadAutoSpace) ic.commitText(" ", 1)
            else -> if (code > 0) {
                if (commaSpace && code == 44) { // 44 = ','
                    ic.commitText(", ", 1)
                    autoSpaceAfterComma = true
                } else {
                    ic.commitText(String(Character.toChars(code)), 1)
                }
            }
        }

        // Shift считаем по реальному тексту
        keyboardView?.syncShift(needsCapital())
        if (code == KeyboardView.KEY_ENTER) {
            // чат мог очистить поле после отправки: проверяем ещё раз чуть позже
            keyboardView?.postDelayed({ keyboardView?.syncShift(needsCapital()) }, 120)
        }
    }

    /** Вставка готовой строки (смайлы). */
    fun onTextCommit(text: String, feedback: Boolean = true) {
        val ic = currentInputConnection ?: return
        if (feedback) giveFeedback()
        autoSpaceAfterComma = false
        ic.commitText(text, 1)
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
        } else {
            // смайл вроде сердца состоит из двух кодовых точек (символ + FE0F): удаляем целиком
            val before = ic.getTextBeforeCursor(2, 0)
            val n = if (before != null && before.isNotEmpty() && before.last() == '\uFE0F') 2 else 1
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ic.deleteSurroundingTextInCodePoints(n, 0)
            } else {
                ic.deleteSurroundingText(n, 0)
            }
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
