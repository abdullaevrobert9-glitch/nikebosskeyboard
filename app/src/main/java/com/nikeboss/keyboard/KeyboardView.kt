package com.nikeboss.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

class KeyboardView(context: Context) : View(context) {

    companion object {
        const val KEY_DELETE = -1
        const val KEY_ENTER = -2
        const val KEY_SPACE = -3
        const val KEY_SHIFT = -4
        const val KEY_LANG = -5

        private const val PREFS = "nikeboss_keyboard"
        private const val PREF_LANG_RU = "lang_ru"
        private const val LONG_PRESS_MS = 350L

        // Палитра NikeBoss
        private val COLOR_BG = Color.parseColor("#0A0A12")
        private val COLOR_KEY = Color.parseColor("#1A1A2E")
        private val COLOR_SPECIAL = Color.parseColor("#24243F")
        private val COLOR_PRESSED = Color.parseColor("#34345A")
        private val COLOR_TEXT = Color.parseColor("#E8E8F0")
        private val COLOR_PURPLE = Color.parseColor("#B026FF")
        private val COLOR_BLUE = Color.parseColor("#00D4FF")
        private val COLOR_RED = Color.parseColor("#FF2E4C")
    }

    private enum class Kind { CHAR, SHIFT, DELETE, ENTER, SPACE, LANG, SYMBOLS, LETTERS, SPACER }

    private enum class ShiftState { OFF, ONCE, LOCK }

    private class Key(val kind: Kind, val label: String = "", val weight: Float = 1f) {
        val rect = RectF()
    }

    private val ruRows = listOf(
        listOf("й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х", "ъ"),
        listOf("ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э"),
        listOf("я", "ч", "с", "м", "и", "т", "ь", "б", "ю", "ё")
    )
    private val enRows = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("z", "x", "c", "v", "b", "n", "m")
    )
    private val symRows = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("@", "#", "₽", "%", "&", "-", "+", "(", ")", "/"),
        listOf("*", "\"", "'", ":", ";", "!", "?")
    )

    // Дополнительные символы по долгому нажатию (цифры верхнего ряда добавляются отдельно)
    private val extraAlts = mapOf(
        "." to listOf("!", "?", ":", ";", "…", "-"),
        "," to listOf(";", ":"),
        "a" to listOf("à", "á", "â", "ä", "ã", "å"),
        "e" to listOf("è", "é", "ê", "ë"),
        "i" to listOf("ì", "í", "î", "ï"),
        "o" to listOf("ò", "ó", "ô", "ö", "õ"),
        "u" to listOf("ù", "ú", "û", "ü"),
        "c" to listOf("ç"),
        "n" to listOf("ñ")
    )

    private var isRussian = true
    private var symbols = false
    private var shift = ShiftState.OFF
    private var lastShiftTap = 0L

    private var rows: List<List<Key>> = emptyList()
    private val active = HashMap<Int, Key>() // id пальца -> нажатая клавиша

    private val dp = resources.displayMetrics.density
    private val pad = 5f * dp
    private val radius = 8f * dp

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * dp
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val tmpRect = RectF()

    // Долгое нажатие: меню выбора символа
    private var pendingKey: Key? = null
    private var pendingPointer = -1
    private var pendingX = 0f
    private var popupKey: Key? = null
    private var popupPointer = -1
    private var popupItems: List<String> = emptyList()
    private val popupRect = RectF()
    private var popupItemW = 0f
    private var popupSel = 0

    private val handler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable { showPopup() }

    // Автоповтор Delete при удержании
    private val repeatRunnable = object : Runnable {
        override fun run() {
            (context as? KeyboardService)?.onKeyPress(KEY_DELETE, feedback = false)
            handler.postDelayed(this, 50)
        }
    }

    init {
        setBackgroundColor(COLOR_BG)
        isRussian = prefs().getBoolean(PREF_LANG_RU, true)
        rows = buildRows()
    }

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ───────────────────────── раскладка ─────────────────────────

    private fun charKeys(labels: List<String>): List<Key> = labels.map { Key(Kind.CHAR, it) }

    private fun spacer(weight: Float) = Key(Kind.SPACER, weight = weight)

    private fun buildRows(): List<List<Key>> {
        val result = ArrayList<List<Key>>()
        if (symbols) {
            result.add(charKeys(symRows[0]))
            result.add(charKeys(symRows[1]))
            result.add(listOf(spacer(1.4f)) + charKeys(symRows[2]) + Key(Kind.DELETE, "⌫", 1.4f))
            result.add(
                listOf(
                    Key(Kind.LETTERS, "ABC", 1.4f),
                    Key(Kind.CHAR, ","),
                    Key(Kind.SPACE, "", 4.4f),
                    Key(Kind.CHAR, "."),
                    Key(Kind.ENTER, "➤", 1.6f)
                )
            )
        } else {
            val letters = if (isRussian) ruRows else enRows
            result.add(charKeys(letters[0]))
            result.add(listOf(spacer(0.5f)) + charKeys(letters[1]) + spacer(0.5f))
            result.add(listOf(Key(Kind.SHIFT, "⇧", 1.4f)) + charKeys(letters[2]) + Key(Kind.DELETE, "⌫", 1.4f))
            result.add(
                listOf(
                    Key(Kind.SYMBOLS, "123", 1.4f),
                    Key(Kind.LANG, "", 1.2f),
                    Key(Kind.CHAR, ","),
                    Key(Kind.SPACE, "", 4.2f),
                    Key(Kind.CHAR, "."),
                    Key(Kind.ENTER, "➤", 1.6f)
                )
            )
        }
        return result
    }

    private fun layoutKeys() {
        if (width == 0 || height == 0 || rows.isEmpty()) return
        val w = width.toFloat()
        val h = height.toFloat()
        val keyH = (h - pad * (rows.size + 1)) / rows.size

        rows.forEachIndexed { r, row ->
            val totalWeight = row.fold(0f) { acc, k -> acc + k.weight }
            val unit = (w - pad * (row.size + 1)) / totalWeight
            val top = pad + r * (keyH + pad)
            var x = pad
            for (k in row) {
                val kw = unit * k.weight
                k.rect.set(x, top, x + kw, top + keyH)
                x += kw + pad
            }
        }
    }

    private fun rebuild() {
        rows = buildRows()
        layoutKeys()
        invalidate()
    }

    /** Вызывается сервисом при каждом показе клавиатуры. */
    fun onShow() {
        symbols = false
        shift = ShiftState.OFF
        active.clear()
        stopRepeat()
        cancelKeyLongPress()
        closePopup()
        rebuild()
        requestLayout() // подхватить высоту из настроек
        (context as? KeyboardService)?.let { syncShift(it.needsCapital()) }
    }

    /** Сервис сообщает, нужна ли заглавная по тексту перед курсором. Caps Lock не трогаем. */
    fun syncShift(needCapital: Boolean) {
        if (shift == ShiftState.LOCK) return
        val target = if (needCapital) ShiftState.ONCE else ShiftState.OFF
        if (shift != target) {
            shift = target
            invalidate()
        }
    }

    // ───────────────────────── размеры и отрисовка ─────────────────────────

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val heightPercent = prefs().getInt("height", 60) / 100f
        val height = (width * heightPercent).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutKeys()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pressed = active.values
        for (row in rows) {
            for (k in row) {
                if (k.kind == Kind.SPACER) continue
                drawKey(canvas, k, pressed.contains(k))
            }
        }
        drawPopup(canvas)
    }

    private fun drawKey(canvas: Canvas, k: Key, pressed: Boolean) {
        val special = k.kind != Kind.CHAR && k.kind != Kind.SPACE
        var fill = if (special) COLOR_SPECIAL else COLOR_KEY
        var textColor = COLOR_TEXT
        var stroke = 0

        when (k.kind) {
            Kind.ENTER -> {
                fill = COLOR_BLUE
                textColor = COLOR_BG
            }
            Kind.SHIFT -> {
                when (shift) {
                    ShiftState.ONCE -> fill = COLOR_PURPLE
                    ShiftState.LOCK -> {
                        fill = COLOR_PURPLE
                        stroke = COLOR_BLUE
                    }
                    ShiftState.OFF -> {}
                }
            }
            Kind.SPACE -> textColor = COLOR_PURPLE
            else -> {}
        }

        if (pressed) {
            if (k.kind != Kind.ENTER) fill = COLOR_PRESSED
            stroke = if (k.kind == Kind.DELETE) COLOR_RED else COLOR_BLUE
        }

        fillPaint.color = fill
        canvas.drawRoundRect(k.rect, radius, radius, fillPaint)
        if (stroke != 0) {
            strokePaint.color = stroke
            canvas.drawRoundRect(k.rect, radius, radius, strokePaint)
        }

        val label = when (k.kind) {
            Kind.CHAR -> if (!symbols && shift != ShiftState.OFF) k.label.uppercase() else k.label
            Kind.LANG -> if (isRussian) "RU" else "EN"
            Kind.SPACE -> "NikeBoss"
            else -> k.label
        }
        if (label.isEmpty()) return

        textPaint.color = textColor
        textPaint.textSize = k.rect.height() * (if (label.length > 1) 0.3f else 0.4f)
        val centerY = k.rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(label, k.rect.centerX(), centerY, textPaint)
    }

    private fun drawPopup(canvas: Canvas) {
        if (popupKey == null || popupItems.isEmpty()) return

        fillPaint.color = COLOR_SPECIAL
        canvas.drawRoundRect(popupRect, radius, radius, fillPaint)
        strokePaint.color = COLOR_PURPLE
        canvas.drawRoundRect(popupRect, radius, radius, strokePaint)

        textPaint.textSize = popupRect.height() * 0.4f
        val cy = popupRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2

        popupItems.forEachIndexed { i, s ->
            val left = popupRect.left + i * popupItemW
            if (i == popupSel) {
                fillPaint.color = COLOR_PURPLE
                tmpRect.set(
                    left + pad / 2, popupRect.top + pad / 2,
                    left + popupItemW - pad / 2, popupRect.bottom - pad / 2
                )
                canvas.drawRoundRect(tmpRect, radius, radius, fillPaint)
            }
            textPaint.color = COLOR_TEXT
            canvas.drawText(s, left + popupItemW / 2, cy, textPaint)
        }
    }

    // ───────────────────────── долгое нажатие ─────────────────────────

    private fun alternativesFor(k: Key): List<String> {
        if (k.kind != Kind.CHAR || symbols) return emptyList()
        val result = ArrayList<String>()
        val letters = if (isRussian) ruRows else enRows
        val idx = letters[0].indexOf(k.label)
        if (idx in 0..9) result.add(((idx + 1) % 10).toString()) // цифры верхнего ряда
        extraAlts[k.label]?.let { result.addAll(it) }
        return if (shift != ShiftState.OFF) result.map { it.uppercase() } else result
    }

    private fun showPopup() {
        val key = pendingKey ?: return
        val items = alternativesFor(key)
        if (items.isEmpty()) return

        popupKey = key
        popupItems = items
        popupPointer = pendingPointer
        popupItemW = key.rect.width()

        val total = popupItemW * items.size
        var left = key.rect.left
        if (left + total > width - pad) left = width - pad - total
        if (left < pad) left = pad

        val h = key.rect.height()
        var top = key.rect.top - h - pad
        if (top < 0f) top = key.rect.top // верхний ряд: меню поверх самого ряда
        popupRect.set(left, top, left + total, top + h)

        popupSel = ((pendingX - left) / popupItemW).toInt().coerceIn(0, items.size - 1)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        invalidate()
    }

    private fun updatePopupSelection(x: Float) {
        if (popupItems.isEmpty()) return
        val sel = ((x - popupRect.left) / popupItemW).toInt().coerceIn(0, popupItems.size - 1)
        if (sel != popupSel) {
            popupSel = sel
            invalidate()
        }
    }

    private fun closePopup() {
        if (popupKey == null) return
        popupKey = null
        popupPointer = -1
        popupItems = emptyList()
        invalidate()
    }

    private fun cancelKeyLongPress() {
        handler.removeCallbacks(longPressRunnable)
        pendingKey = null
        pendingPointer = -1
    }

    // ───────────────────────── касания (мультитач) ─────────────────────────

    private fun keyAt(x: Float, y: Float): Key? {
        if (rows.isEmpty() || height == 0) return null
        val rowHeight = height.toFloat() / rows.size
        val r = (y / rowHeight).toInt().coerceIn(0, rows.size - 1)
        var best: Key? = null
        var bestDist = Float.MAX_VALUE
        for (k in rows[r]) {
            if (k.kind == Kind.SPACER) continue
            val d = when {
                x < k.rect.left -> k.rect.left - x
                x > k.rect.right -> x - k.rect.right
                else -> 0f
            }
            if (d < bestDist) {
                bestDist = d
                best = k
            }
        }
        return best
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                val key = keyAt(event.getX(i), event.getY(i))
                if (key != null) {
                    val id = event.getPointerId(i)
                    active[id] = key
                    onKeyDown(key, id, event.getX(i))
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (popupKey != null) {
                    for (i in 0 until event.pointerCount) {
                        if (event.getPointerId(i) == popupPointer) {
                            updatePopupSelection(event.getX(i))
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = event.getPointerId(event.actionIndex)
                val key = active.remove(id)
                if (key != null) onKeyUp(key, id)
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                active.clear()
                stopRepeat()
                cancelKeyLongPress()
                closePopup()
                invalidate()
            }
        }
        return true
    }

    private fun onKeyDown(k: Key, id: Int, x: Float) {
        val service = context as? KeyboardService
        when (k.kind) {
            Kind.CHAR -> {
                // Буква печатается при отпускании (см. onKeyUp), чтобы работало долгое нажатие
                if (alternativesFor(k).isNotEmpty()) {
                    cancelKeyLongPress()
                    pendingKey = k
                    pendingPointer = id
                    pendingX = x
                    handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                }
            }
            Kind.SPACE -> service?.onKeyPress(KEY_SPACE)
            Kind.ENTER -> service?.onKeyPress(KEY_ENTER)
            Kind.DELETE -> {
                service?.onKeyPress(KEY_DELETE)
                startRepeat()
            }
            Kind.SHIFT -> toggleShift()
            Kind.LANG -> {
                isRussian = !isRussian
                prefs().edit().putBoolean(PREF_LANG_RU, isRussian).apply()
                rebuild()
            }
            Kind.SYMBOLS -> {
                symbols = true
                rebuild()
            }
            Kind.LETTERS -> {
                symbols = false
                rebuild()
            }
            Kind.SPACER -> {}
        }
    }

    private fun onKeyUp(k: Key, id: Int) {
        val service = context as? KeyboardService
        if (id == pendingPointer) cancelKeyLongPress()

        if (k.kind == Kind.DELETE) {
            stopRepeat()
            return
        }
        if (k.kind != Kind.CHAR) return

        // Отпустили палец над меню долгого нажатия: вставляем выбранный символ
        if (popupKey != null && id == popupPointer) {
            val text = popupItems.getOrNull(popupSel)
            closePopup()
            if (text != null) service?.onKeyPress(text.codePointAt(0))
            return
        }

        val upper = !symbols && shift != ShiftState.OFF
        val text = if (upper) k.label.uppercase() else k.label
        service?.onKeyPress(text.codePointAt(0), commaSpace = !symbols && text == ",")
    }

    // Один тап: Shift на одну букву. Двойной тап: Caps Lock. Ещё тап: выключить.
    private fun toggleShift() {
        val now = SystemClock.uptimeMillis()
        shift = when {
            shift == ShiftState.LOCK -> ShiftState.OFF
            shift == ShiftState.ONCE && now - lastShiftTap < 350 -> ShiftState.LOCK
            shift == ShiftState.ONCE -> ShiftState.OFF
            else -> ShiftState.ONCE
        }
        lastShiftTap = now
    }

    private fun startRepeat() {
        handler.removeCallbacks(repeatRunnable)
        handler.postDelayed(repeatRunnable, 400)
    }

    private fun stopRepeat() {
        handler.removeCallbacks(repeatRunnable)
    }

    override fun onDetachedFromWindow() {
        stopRepeat()
        cancelKeyLongPress()
        closePopup()
        active.clear()
        super.onDetachedFromWindow()
    }
}
