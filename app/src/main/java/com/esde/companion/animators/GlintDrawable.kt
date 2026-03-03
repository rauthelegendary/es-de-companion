package com.esde.companion.animators

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
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
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator

class GlintDrawable(private val original: Drawable) : Drawable(), Animatable {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val glintMatrix = Matrix()

    private var logoShader: BitmapShader? = null
    private var glintShader: LinearGradient? = null
    private var cachedBitmap: Bitmap? = null
    private var actualLogoRect = RectF()
    private var offset = -1f

    private val animator = ValueAnimator.ofFloat(-1f, 12f).apply {
        duration = 9000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            offset = it.animatedValue as Float
            if (offset < 2.5f) invalidateSelf()
        }
    }

    private fun updateCache(bounds: Rect) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return
        clearCache()

        val margin = 35
        val bitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
        val tempCanvas = Canvas(bitmap)

        val availableWidth = (bounds.width() - (margin * 2)).toFloat()
        val availableHeight = (bounds.height() - (margin * 2)).toFloat()
        val scale = minOf(availableWidth / original.intrinsicWidth, availableHeight / original.intrinsicHeight)

        val finalWidth = original.intrinsicWidth * scale
        val finalHeight = original.intrinsicHeight * scale
        val left = margin + (availableWidth - finalWidth) / 2f
        val top = margin + (availableHeight - finalHeight) / 2f

        actualLogoRect.set(left, top, left + finalWidth, top + finalHeight)

        val scratchBitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
        val scratchCanvas = Canvas(scratchBitmap)
        original.bounds = Rect(left.toInt(), top.toInt(), (left + finalWidth).toInt(), (top + finalHeight).toInt())
        original.draw(scratchCanvas)

        val alphaMask = scratchBitmap.extractAlpha()
        val isDark = isBitmapDark(scratchBitmap)

        val backPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (isDark) {
            backPaint.color = Color.WHITE
            backPaint.maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.OUTER)
            backPaint.alpha = 150
            tempCanvas.drawBitmap(alphaMask, 0f, 0f, backPaint)
        } else {
            backPaint.color = Color.BLACK

            backPaint.maskFilter = BlurMaskFilter(30f, BlurMaskFilter.Blur.NORMAL)
            backPaint.alpha = 140
            tempCanvas.drawBitmap(alphaMask, 0f, 0f, backPaint)

            backPaint.maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
            backPaint.alpha = 210
            tempCanvas.drawBitmap(alphaMask, 0f, 0f, backPaint)
        }

        original.draw(tempCanvas)

        alphaMask.recycle()
        scratchBitmap.recycle()

        cachedBitmap = bitmap
        val lShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        logoShader = lShader

        val shineWidth = actualLogoRect.width() * 0.4f
        val gShader = LinearGradient(0f, 0f, shineWidth, 0f,
            intArrayOf(Color.TRANSPARENT, Color.parseColor("#99FFFFFF"), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        glintShader = gShader

        paint.shader = ComposeShader(lShader, gShader, PorterDuff.Mode.SRC_ATOP)
    }

    private fun isBitmapDark(bitmap: Bitmap): Boolean {
        val pixelCount = 15
        var totalLuminance = 0f
        var samples = 0

        val stepX = (bitmap.width / pixelCount).coerceAtLeast(1)
        val stepY = (bitmap.height / pixelCount).coerceAtLeast(1)

        for (x in 0 until bitmap.width step stepX) {
            for (y in 0 until bitmap.height step stepY) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) > 180) { // Only check solid pixels
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    totalLuminance += (0.299f * r + 0.587f * g + 0.114f * b)
                    samples++
                }
            }
        }
        return if (samples > 0) (totalLuminance / samples) < 85 else false
    }

    override fun draw(canvas: Canvas) {
        if (cachedBitmap == null || cachedBitmap?.isRecycled == true) {
            if (!bounds.isEmpty) updateCache(bounds)
        }

        val bitmap = cachedBitmap ?: return
        val gShader = glintShader ?: return
        val logoRect = RectF(actualLogoRect)
        if (bitmap.isRecycled) return

        canvas.drawBitmap(bitmap, 0f, 0f, bitmapPaint)

        val shineWidth = logoRect.width() * 0.4f
        val currentX = logoRect.left + (logoRect.width() * offset)

        glintMatrix.reset()
        glintMatrix.setTranslate(currentX, 0f)
        glintMatrix.preRotate(25f, shineWidth / 2f, logoRect.height() / 2f)
        gShader.setLocalMatrix(glintMatrix)

        canvas.drawRect(bounds, paint)
    }

    private fun clearCache() {
        cachedBitmap?.recycle()
        cachedBitmap = null
        logoShader = null
        glintShader = null
        paint.shader = null
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        if (!bounds.isEmpty) updateCache(bounds)
    }

    override fun start() { if (!animator.isRunning) animator.start() }
    override fun isRunning() = animator.isRunning
    override fun getIntrinsicWidth() = original.intrinsicWidth
    override fun getIntrinsicHeight() = original.intrinsicHeight
    override fun getOpacity() = PixelFormat.TRANSLUCENT
    override fun stop() { animator.cancel(); clearCache() }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        bitmapPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(filter: ColorFilter?) {
        paint.colorFilter = filter
        bitmapPaint.colorFilter = filter
        invalidateSelf()
    }
}