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
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class KeyboardService : InputMethodService() {

    private var keyboardView: KeyboardView? = null
    private var autoSpaceAfterComma = false
    private var lastCharWasSpace = false

    private lateinit var dictionaryHelper: DictionaryHelper
    private lateinit var suggestionAdapter: SuggestionAdapter
    private var suggestionRecyclerView: RecyclerView? = null

    private val vibrator: Vibrator by lazy {
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    private val audio: AudioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onCreate() {
        super.onCreate()
        dictionaryHelper = DictionaryHelper(this)
        dictionaryHelper.loadDictionary()
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Полоска подсказок
        val recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(
                this@KeyboardService,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        suggestionAdapter = SuggestionAdapter { word ->
            commitSuggestion(word)
        }
        recycler.adapter = suggestionAdapter
        suggestionRecyclerView = recycler

        // Твоя клавиатура
        val view = KeyboardView(this)
        keyboardView = view
        view.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        )

        root.addView(recycler)
        root.addView(view)
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        autoSpaceAfterComma = false
        lastCharWasSpace = false
        keyboardView?.onShow()
    }

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

        if (before.isEmpty()) return true
        if (before.last() == '\n') return true

        val trimmed = before.trimEnd(' ', '\t')
        if (trimmed.length == before.length) return false
        if (trimmed.isEmpty()) return true
        return trimmed.last() in ".!?…\n"
    }

    fun onKeyPress(code: Int, feedback: Boolean = true, commaSpace: Boolean = false) {
        val ic = currentInputConnection ?: return
        if (feedback) giveFeedback()

        val hadAutoSpace = autoSpaceAfterComma
        autoSpaceAfterComma = false

        when (code) {
            KeyboardView.KEY_DELETE -> {
                lastCharWasSpace = false
                deleteBackward(ic)
            }
            KeyboardView.KEY_ENTER -> {
                lastCharWasSpace = false
                sendEnter(ic)
            }
            KeyboardView.KEY_SPACE -> {
                if (!hadAutoSpace) {
                    if (lastCharWasSpace) {
                        ic.deleteSurroundingText(1, 0)
                        ic.commitText(". ", 1)
                        lastCharWasSpace = false
                    } else {
                        ic.commitText(" ", 1)
                        lastCharWasSpace = true
                    }
                }
            }
            else -> if (code > 0) {
                lastCharWasSpace = false
                val char = Character.toChars(code)[0]
                if (Character.isLetter(char) || char == '\'') {
                    ic.commitText(char.toString(), 1)
                } else {
                    ic.commitText("$char ", 1)
                    lastCharWasSpace = true
                }
            }
        }

        updateSuggestionsFromCursor()
        keyboardView?.syncShift(needsCapital())
        if (code == KeyboardView.KEY_ENTER) {
            keyboardView?.postDelayed({ keyboardView?.syncShift(needsCapital()) }, 120)
        }
    }

    fun onTextCommit(text: String, feedback: Boolean = true) {
        val ic = currentInputConnection ?: return
        if (feedback) giveFeedback()
        autoSpaceAfterComma = false
        lastCharWasSpace = false
        ic.commitText(text, 1)
        updateSuggestionsFromCursor()
    }

    private fun updateSuggestionsFromCursor() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(30, 0)?.toString() ?: ""
        val word = before.split(' ', '\n', '\t', '.', ',', '!', '?', '…')
            .lastOrNull()?.lowercase() ?: ""
        updateSuggestions(word)
    }

    fun updateSuggestions(text: String) {
        if (text.isBlank()) {
            suggestionAdapter.updateList(emptyList())
            suggestionRecyclerView?.visibility = View.GONE
            return
        }
        val words = dictionaryHelper.getSuggestions(text)
        suggestionAdapter.updateList(words)
        suggestionRecyclerView?.visibility = if (words.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun commitSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(30, 0)?.toString() ?: ""
        val prefixLen = before.split(' ', '\n', '\t', '.', ',', '!', '?', '…')
            .lastOrNull()?.length ?: 0
        if (prefixLen > 0) {
            ic.deleteSurroundingTextInCodePoints(prefixLen, 0)
        }
        ic.commitText(word, 1)
        suggestionAdapter.updateList(emptyList())
        keyboardView?.syncShift(needsCapital())
    }

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
            } catch (e: Exception) { }
        }
        if (prefs.getBoolean("sound", true)) {
            try {
                audio.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.5f)
            } catch (e: Exception) { }
        }
    }

    private fun deleteBackward(ic: InputConnection) {
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
            return
        }
        val info = currentInputEditorInfo
        val isTerminal = info?.packageName?.contains("termux") == true || info?.inputType == InputType.TYPE_NULL
        if (isTerminal) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        } else {
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
        if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }
}
