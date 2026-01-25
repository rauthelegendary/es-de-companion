package com.esde.companion.animators

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator

class GlintDrawable(private val original: Drawable) : Drawable(), Animatable {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isDither = true
    }

    private val glintMatrix = Matrix()
    private var logoShader: BitmapShader? = null
    private var glintShader: LinearGradient? = null
    private var cachedBitmap: Bitmap? = null
    private var offset = -1f

    private val animator = ValueAnimator.ofFloat(-1f, 10f).apply {
        duration = 9000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            offset = it.animatedValue as Float
            invalidateSelf()
        }
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        if (bounds.isEmpty) return
        updateCache(bounds)

        val shineWidth = bounds.width() * 0.4f
        glintShader = LinearGradient(
            0f, 0f, shineWidth, 0f,
            intArrayOf(Color.TRANSPARENT, Color.parseColor("#99FFFFFF"), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private fun updateCache(bounds: Rect) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return
        cachedBitmap?.recycle()

        val bitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
        val tempCanvas = Canvas(bitmap)

        original.bounds = Rect(0, 0, bounds.width(), bounds.height())
        original.draw(tempCanvas)

        cachedBitmap = bitmap
        logoShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }



    override fun draw(canvas: Canvas) {
        val lShader = logoShader ?: return
        val gShader = glintShader ?: return
        val bitmap = cachedBitmap ?: return
        if (bitmap.isRecycled) return

        original.bounds = bounds
        original.draw(canvas)

        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()
        val shineWidth = width * 0.4f
        val currentX = bounds.left + (width * offset)

        glintMatrix.reset()
        glintMatrix.setTranslate(currentX, 0f)
        glintMatrix.preRotate(25f, shineWidth / 2f, height / 2f)
        gShader.setLocalMatrix(glintMatrix)

        paint.shader = ComposeShader(lShader, gShader, PorterDuff.Mode.SRC_ATOP)

        canvas.drawRect(bounds, paint)
    }

    override fun start() {
        if (!animator.isRunning) {
            animator.start()
        }
    }

    override fun stop() {
        animator.cancel()
        cachedBitmap?.recycle()
        cachedBitmap = null
        logoShader = null
        glintShader = null
    }

    override fun isRunning() = animator.isRunning
    override fun getIntrinsicWidth() = original.intrinsicWidth
    override fun getIntrinsicHeight() = original.intrinsicHeight
    override fun setAlpha(alpha: Int) { original.alpha = alpha }
    override fun setColorFilter(filter: ColorFilter?) { original.colorFilter = filter }
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}