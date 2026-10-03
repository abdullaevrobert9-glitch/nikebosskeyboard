package com.nikeboss.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
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

    // ДИАГНОСТИКА (временно): при зависании или краше пишет место в буфер обмена
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var lastTick = SystemClock.uptimeMillis()
    @Volatile private var watching = false
    @Volatile private var reported = false

    private val ticker = object : Runnable {
        override fun run() {
            lastTick = SystemClock.uptimeMillis()
            mainHandler.postDelayed(this, 300)
        }
    }

    override fun onCreate() {
        super.onCreate()

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            report("CRASH in thread " + thread.name + "\n" + Log.getStackTraceString(error))
            previous?.uncaughtException(thread, error)
        }

        mainHandler.post(ticker)

        val watchdog = Thread {
            while (!reported) {
                try {
                    Thread.sleep(300)
                } catch (e: InterruptedException) {
                    return@Thread
                }
                if (watching && SystemClock.uptimeMillis() - lastTick > 2500) {
                    val trace = Looper.getMainLooper().thread.stackTrace
                        .joinToString("\n") { "  at $it" }
                    report("MAIN THREAD STUCK more than 2.5s\n$trace")
                }
            }
        }
        watchdog.isDaemon = true
        watchdog.start()
    }

    private fun report(text: String) {
        if (reported) return
        reported = true
        Log.e("NikeBoss", text)
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("nikeboss-debug", text))
        } catch (ignored: Throwable) {
        }
    }

    override fun onCreateInputView(): View {
        lastTick = SystemClock.uptimeMillis()
        watching = true
        val view = KeyboardView(this)
        keyboardView = view
        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lastTick = SystemClock.uptimeMillis()
        watching = true
        keyboardView?.onShow()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        watching = false
        super.onFinishInputView(finishingInput)
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
