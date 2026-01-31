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
            // Performance: only invalidate when the glint is actually visible
            if (offset < 2.5f) invalidateSelf()
        }
    }

    private fun updateCache(bounds: Rect) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return
        clearCache()

        val margin = 45
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

        // 1. Bake Shadow
        val scratchBitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
        val scratchCanvas = Canvas(scratchBitmap)
        original.bounds = Rect(left.toInt(), top.toInt(), (left + finalWidth).toInt(), (top + finalHeight).toInt())
        original.draw(scratchCanvas)

        val alphaMask = scratchBitmap.extractAlpha()
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }

        shadowPaint.maskFilter = BlurMaskFilter(35f, BlurMaskFilter.Blur.NORMAL)
        shadowPaint.alpha = 160
        tempCanvas.drawBitmap(alphaMask, 0f, 0f, shadowPaint)

        shadowPaint.maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
        shadowPaint.alpha = 230
        tempCanvas.drawBitmap(alphaMask, 0f, 0f, shadowPaint)

        // 2. Bake Logo
        original.draw(tempCanvas)

        alphaMask.recycle()
        scratchBitmap.recycle()

        // 3. Setup Shaders
        cachedBitmap = bitmap
        val lShader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        logoShader = lShader

        val shineWidth = actualLogoRect.width() * 0.4f
        val gShader = LinearGradient(0f, 0f, shineWidth, 0f,
            intArrayOf(Color.TRANSPARENT, Color.parseColor("#99FFFFFF"), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        glintShader = gShader

        // Performance Fix: Pre-create ComposeShader so draw() doesn't allocate memory
        paint.shader = ComposeShader(lShader, gShader, PorterDuff.Mode.SRC_ATOP)
    }

    override fun draw(canvas: Canvas) {
        val bitmap = cachedBitmap ?: return
        val gShader = glintShader ?: return
        if (bitmap.isRecycled) return

        // 1. Draw Static Layer (Logo + Shadow)
        canvas.drawBitmap(bitmap, 0f, 0f, bitmapPaint)

        // 2. Update Glint Position
        val shineWidth = actualLogoRect.width() * 0.4f
        val currentX = actualLogoRect.left + (actualLogoRect.width() * offset)

        glintMatrix.reset()
        glintMatrix.setTranslate(currentX, 0f)
        glintMatrix.preRotate(25f, shineWidth / 2f, actualLogoRect.height() / 2f)
        gShader.setLocalMatrix(glintMatrix)

        // 3. Draw Glint (Shader is already set in updateCache)
        canvas.drawRect(bounds, paint)
    }

    private fun clearCache() {
        cachedBitmap?.recycle()
        cachedBitmap = null
        logoShader = null
        glintShader = null
        paint.shader = null // Crucial to prevent holding onto recycled bitmaps
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        if (!bounds.isEmpty) updateCache(bounds)
    }

    override fun start() { if (!animator.isRunning) animator.start() }
    override fun stop() { animator.cancel(); clearCache() }
    override fun isRunning() = animator.isRunning
    override fun getIntrinsicWidth() = original.intrinsicWidth
    override fun getIntrinsicHeight() = original.intrinsicHeight

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

    override fun getOpacity() = PixelFormat.TRANSLUCENT
}