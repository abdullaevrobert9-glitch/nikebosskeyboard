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
    }

    private val rows = listOf(
        listOf("й", "ц", "у", "к", "е", "н", "г", "ш", "щ", "з", "х", "ъ"),
        listOf("ф", "ы", "в", "а", "п", "р", "о", "л", "д", "ж", "э"),
        listOf("я", "ч", "с", "м", "и", "т", "ь", "б", "ю", "ё")
    )

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyRects = mutableListOf<Pair<RectF, String>>()

    init {
        keyPaint.color = Color.parseColor("#1A1A2E")
        textPaint.color = Color.parseColor("#E8E8F0")
        textPaint.textAlign = Paint.Align.CENTER
        setBackgroundColor(Color.parseColor("#0A0A12"))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        keyRects.clear()
        val w = width.toFloat()
        val h = height.toFloat()
        val padding = 6f
        val keyHeight = (h - padding * 4) / 4f

        rows.forEachIndexed { rowIndex, row ->
            val keyWidth = (w - padding * (row.size + 1)) / row.size
            row.forEachIndexed { colIndex, letter ->
                val left = padding + colIndex * (keyWidth + padding)
                val top = padding + rowIndex * (keyHeight + padding)
                val rect = RectF(left, top, left + keyWidth, top + keyHeight)
                drawKey(canvas, rect, letter)
                keyRects.add(rect to letter)
            }
        }

        val bottomTop = padding + 3 * (keyHeight + padding)
        val spaceRect = RectF(padding, bottomTop, w * 0.6f, bottomTop + keyHeight)
        drawKey(canvas, spaceRect, "ПРОБЕЛ")
        keyRects.add(spaceRect to "space")

        val delRect = RectF(w * 0.6f + padding, bottomTop, w * 0.8f, bottomTop + keyHeight)
        drawKey(canvas, delRect, "⌫")
        keyRects.add(delRect to "delete")

        val enterRect = RectF(w * 0.8f + padding, bottomTop, w - padding, bottomTop + keyHeight)
        drawKey(canvas, enterRect, "➤")
        keyRects.add(enterRect to "enter")
    }

    private fun drawKey(canvas: Canvas, rect: RectF, label: String) {
        canvas.drawRoundRect(rect, 12f, 12f, keyPaint)
        textPaint.textSize = rect.height() * 0.35f
        val centerY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(label, rect.centerX(), centerY, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            keyRects.forEach { (rect, label) ->
                if (rect.contains(event.x, event.y)) {
                    (context as? KeyboardService)?.let { service ->
                        when (label) {
                            "space" -> service.onKeyPress(KEY_SPACE)
                            "delete" -> service.onKeyPress(KEY_DELETE)
                            "enter" -> service.onKeyPress(KEY_ENTER)
                            else -> service.onKeyPress(label[0].code)
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
