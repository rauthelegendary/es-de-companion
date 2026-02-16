package com.esde.companion

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable

class TextDrawable(private val text: String) : Drawable() {
    private val paint = Paint().apply {
        color = Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        paint.textSize = bounds.height() * 0.25f
        val x = bounds.width() / 2f
        val y = bounds.height() / 2f - ((paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(text, x, y, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}