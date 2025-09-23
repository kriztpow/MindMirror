package com.example.mindmirror

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        textSize = 48f
        isAntiAlias = true
    }

    private var boxes: List<Pair<Rect, String>> = emptyList()

    fun setBoxes(boxes: List<Pair<Rect, String>>) {
        this.boxes = boxes
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for ((rect, label) in boxes) {
            paint.color = if (label.contains("Happy")) 0xFF00FF00.toInt() else 0xFFFFA500.toInt()
            textPaint.color = paint.color
            canvas.drawRect(rect, paint)
            canvas.drawText(label, rect.left.toFloat(), rect.top - 12f, textPaint)
        }
    }
}