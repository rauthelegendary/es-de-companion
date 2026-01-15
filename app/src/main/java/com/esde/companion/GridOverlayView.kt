package com.esde.companion

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

class GridOverlayView(context: Context, private val gridSize: Float) : View(context) {

    private val gridPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = 0x60FFFFFF  // White with 38% opacity
        isAntiAlias = false
    }

    init {
        setWillNotDraw(false)
        // Make this view non-interactive so touches pass through
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()

        // Draw vertical lines
        var x = 0f
        while (x <= width) {
            canvas.drawLine(x, 0f, x, height, gridPaint)
            x += gridSize
        }

        // Draw horizontal lines
        var y = 0f
        while (y <= height) {
            canvas.drawLine(0f, y, width, y, gridPaint)
            y += gridSize
        }
    }
}