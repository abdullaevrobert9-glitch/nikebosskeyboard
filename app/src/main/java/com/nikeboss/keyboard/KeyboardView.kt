package com.nikeboss.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

class KeyboardView(context: Context) : View(context) {

    companion object {
        const val KEY_DELETE = -1
        const val KEY_ENTER = -2
        const val KEY_SPACE = -3
        const val KEY_SHIFT = -4
        const val KEY_LANG = -5
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

    private var isRussian = true
    private var isShift = false

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyRects = mutableListOf<Pair<RectF, String>>()

    init {
        keyPaint.color = Color.parseColor("#1A1A2E")
        textPaint.color = Color.parseColor("#E8E8F0")
        textPaint.textAlign = Paint.Align.CENTER
        setBackgroundColor(Color.parseColor("#0A0A12"))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = (width * 0.65).toInt() // высота = 65% от ширины
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        keyRects.clear()

        val w = width.toFloat()
        val h = height.toFloat()
        val padding = 4f
        val rows = if (isRussian) ruRows else enRows
        val rowCount = rows.size + 1 // + нижний ряд
        val keyHeight = (h - padding * (rowCount + 1)) / rowCount

        // Буквенные ряды
        rows.forEachIndexed { rowIndex, row ->
            val keyWidth = (w - padding * (row.size + 1)) / row.size
            row.forEachIndexed { colIndex, letter ->
                val left = padding + colIndex * (keyWidth + padding)
                val top = padding + rowIndex * (keyHeight + padding)
                val rect = RectF(left, top, left + keyWidth, top + keyHeight)
                val display = if (isShift) letter.uppercase() else letter
                drawKey(canvas, rect, display)
                keyRects.add(rect to letter)
            }
        }

        // Нижний ряд: Shift, язык, пробел, удалить, ввод
        val bottomTop = padding + rows.size * (keyHeight + padding)
        val bottomHeight = keyHeight

        val shiftW = w * 0.15f
        val shiftRect = RectF(padding, bottomTop, padding + shiftW, bottomTop + bottomHeight)
        drawKey(canvas, shiftRect, "⇧")
        keyRects.add(shiftRect to "SHIFT")

        val langRect = RectF(padding + shiftW + padding, bottomTop, padding + shiftW * 2 + padding, bottomTop + bottomHeight)
        drawKey(canvas, langRect, if (isRussian) "RU" else "EN")
        keyRects.add(langRect to "LANG")

        val spaceW = w * 0.4f
        val spaceRect = RectF(padding + shiftW * 2 + padding * 2, bottomTop, padding + shiftW * 2 + padding * 2 + spaceW, bottomTop + bottomHeight)
        drawKey(canvas, spaceRect, "ПРОБЕЛ")
        keyRects.add(spaceRect to "SPACE")

        val delW = w * 0.15f
        val delRect = RectF(w - padding - delW * 2 - padding, bottomTop, w - padding - delW - padding, bottomTop + bottomHeight)
        drawKey(canvas, delRect, "⌫")
        keyRects.add(delRect to "DELETE")

        val enterRect = RectF(w - padding - delW, bottomTop, w - padding, bottomTop + bottomHeight)
        drawKey(canvas, enterRect, "➤")
        keyRects.add(enterRect to "ENTER")
    }

    private fun drawKey(canvas: Canvas, rect: RectF, label: String) {
        canvas.drawRoundRect(rect, 10f, 10f, keyPaint)
        textPaint.textSize = rect.height() * 0.4f
        val centerY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(label, rect.centerX(), centerY, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            keyRects.forEach { (rect, label) ->
                if (rect.contains(event.x, event.y)) {
                    (context as? KeyboardService)?.let { service ->
                        when (label) {
                            "SPACE" -> service.onKeyPress(KEY_SPACE)
                            "DELETE" -> service.onKeyPress(KEY_DELETE)
                            "ENTER" -> service.onKeyPress(KEY_ENTER)
                            "SHIFT" -> {
                                isShift = !isShift
                                invalidate()
                            }
                            "LANG" -> {
                                isRussian = !isRussian
                                invalidate()
                            }
                            else -> {
                                val ch = if (isShift) label.uppercase() else label
                                service.onKeyPress(ch[0].code)
                                if (isShift) {
                                    isShift = false
                                    invalidate()
                                }
                            }
                        }
                    }
                    performClick()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()
}

