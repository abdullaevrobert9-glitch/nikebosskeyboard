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

    // true, если последним мы сами поставили пробел после запятой
    private var autoSpaceAfterComma = false

    // true, если предыдущий символ был пробелом (для «двойной пробел → точка»)
    private var lastCharWasSpace = false

    // Подсказки и словарь
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
        // Корневой вертикальный контейнер: подсказки сверху, клавиатура снизу
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

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
        setBackgroundColor(android.graphics.Color.RED)
    }
    
    suggestionAdapter = SuggestionAdapter { word ->
        commitSuggestion(word)
    }
    recycler.adapter = suggestionAdapter
    suggestionRecyclerView = recycler
    suggestionAdapter.updateList(listOf("тест", "слово", "привет"))

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
                    // Двойной пробел → точка + пробел
                    if (lastCharWasSpace) {
                        ic.deleteSurroundingText(1, 0) // удаляем последний пробел
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
                if (commaSpace && code == 44) { // 44 = ','
                    ic.commitText(", ", 1)
                    autoSpaceAfterComma = true
                } else {
                    ic.commitText(String(Character.toChars(code)), 1)
                }
            }
        }

        // Обновляем подсказки по текущему слову
        updateSuggestionsFromCursor()

        // Shift считаем по реальному тексту
        keyboardView?.syncShift(needsCapital())
        if (code == KeyboardView.KEY_ENTER) {
            keyboardView?.postDelayed({ keyboardView?.syncShift(needsCapital()) }, 120)
        }
    }

    /** Вставка готовой строки (смайлы). */
    fun onTextCommit(text: String, feedback: Boolean = true) {
        val ic = currentInputConnection ?: return
        if (feedback) giveFeedback()
        autoSpaceAfterComma = false
        lastCharWasSpace = false
        ic.commitText(text, 1)
        updateSuggestionsFromCursor()
    }

    /** Берём слово перед курсором и просим подсказки. */
    private fun updateSuggestionsFromCursor() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(30, 0)?.toString() ?: ""
        // Отрезаем всё до последнего пробела/знака
        val word = before.split(' ', '\n', '\t', '.', ',', '!', '?', '…')
            .lastOrNull()?.lowercase() ?: ""
        updateSuggestions(word)
    }

    /** Публичный метод — можно вызвать снаружи. */
    fun updateSuggestions(text: String) {
        if (text.isBlank()) {
            suggestionAdapter.updateList(emptyList())
            return
        }
        val words = dictionaryHelper.getSuggestions(text)
        suggestionAdapter.updateList(words)
    }

    /** Пользователь нажал на подсказку. */
    private fun commitSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        // Находим слово перед курсором и заменяем его
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
