package com.esde.companion.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.drawToBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.timeago.patterns.it

class GlintView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    companion object {
        private const val SHADOW_PADDING = 30
    }
    private val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    }
    private val glintMatrix = Matrix()
    private var glintShader: LinearGradient? = null
    private var glintOffset = -1f

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shadowBitmap: Bitmap? = null

    private var viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val animator = ValueAnimator.ofFloat(-1f, 2f).apply {
        duration = 1800
        startDelay = 1000
        interpolator = LinearInterpolator()
        addUpdateListener {
            glintOffset = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    private fun startGlintLoop() {
        viewScope.launch {
            while (isActive) {
                glintOffset = -1f
                animator.start()
                delay(animator.duration)
                delay(9000)
            }
        }
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        animator.cancel()
        viewScope.cancel()
        viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        if (width > 0 && height > 0) {
            scheduleShadowBuild(drawable)
        }
        startGlintLoop()
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        val shineW = w * 0.4f
        glintShader = LinearGradient(
            0f, 0f, shineW, 0f,
            intArrayOf(Color.TRANSPARENT, 0xAAFFFFFF.toInt(), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        scheduleShadowBuild(drawable)
    }

    private fun scheduleShadowBuild(drawable: Drawable?) {
        if (drawable == null || width <= 0 || height <= 0) return

        val w = width
        val h = height
        val p = SHADOW_PADDING

        val scratch = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val scratchCanvas = Canvas(scratch)

        if (drawable is BitmapDrawable) {
            val bmp = drawable.bitmap ?: return
            val softBmp = if (bmp.config == Bitmap.Config.HARDWARE) {
                bmp.copy(Bitmap.Config.ARGB_8888, false) ?: return
            } else {
                bmp
            }
            scratchCanvas.save()
            scratchCanvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())
            scratchCanvas.drawBitmap(softBmp, imageMatrix, null)
            scratchCanvas.restore()
            if (softBmp !== bmp) softBmp.recycle()
        } else {
            drawable.draw(scratchCanvas)
        }

        val isDark = isBitmapDark(scratch)

        // Extract alpha from padded bitmap so blur has room to spread on all sides
        val padded = Bitmap.createBitmap(w + p * 2, h + p * 2, Bitmap.Config.ARGB_8888)
        Canvas(padded).drawBitmap(scratch, p.toFloat(), p.toFloat(), null)
        scratch.recycle()

        val alphaMask = padded.extractAlpha()
        padded.recycle()

        viewScope.launch {
            val shadow = buildShadowBitmap(alphaMask, w + p * 2, h + p * 2, isDark)
            alphaMask.recycle()
            withContext(Dispatchers.Main) {
                shadowBitmap?.recycle()
                shadowBitmap = shadow
                invalidate()
            }
        }
    }

    private suspend fun buildShadowBitmap(
        alphaMask: Bitmap,
        w: Int,
        h: Int,
        isDark: Boolean
    ): Bitmap = withContext(Dispatchers.Default) {
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (isDark) {
            paint.color = Color.LTGRAY
            paint.maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.OUTER)
            paint.alpha = 60
            canvas.drawBitmap(alphaMask, 0f, 0f, paint)
        } else {
            paint.color = Color.BLACK
            paint.maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.OUTER)
            paint.alpha = 140
            canvas.drawBitmap(alphaMask, 0f, 0f, paint)

            paint.maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.OUTER)
            paint.alpha = 230
            canvas.drawBitmap(alphaMask, 0f, 0f, paint)
        }

        result
    }

    private fun isBitmapDark(bitmap: Bitmap): Boolean {
        val tiny = Bitmap.createScaledBitmap(bitmap, 10, 10, false)
        var lum = 0f
        var samples = 0
        for (x in 0 until 10) {
            for (y in 0 until 10) {
                val pixel = tiny.getPixel(x, y)
                if (Color.alpha(pixel) > 180) {
                    lum += 0.299f * Color.red(pixel) +
                            0.587f * Color.green(pixel) +
                            0.114f * Color.blue(pixel)
                    samples++
                }
            }
        }
        tiny.recycle()
        return if (samples > 0) lum / samples < 85f else false
    }

    override fun onDraw(canvas: Canvas) {
        shadowBitmap?.let {
            canvas.drawBitmap(it, -SHADOW_PADDING.toFloat(), -SHADOW_PADDING.toFloat(), shadowPaint)
        }
        super.onDraw(canvas)

        val shader = glintShader ?: return
        val shineW = width * 0.4f
        val currentX = width * glintOffset

        glintMatrix.reset()
        glintMatrix.setTranslate(currentX, 0f)
        glintMatrix.preRotate(20f, shineW / 2f, height / 2f)
        shader.setLocalMatrix(glintMatrix)
        glintPaint.shader = shader

        val count = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), glintPaint)
        canvas.restoreToCount(count)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (viewScope.coroutineContext[Job]?.isActive != true) {
            viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        }
        if (drawable != null) startGlintLoop()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            if (viewScope.coroutineContext[Job]?.isActive != true) {
                viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
            }
            if (drawable != null) startGlintLoop()
        } else {
            animator.cancel()
            glintOffset = -1f
            viewScope.cancel()
        }
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        glintOffset = -1f
        viewScope.cancel()
        shadowBitmap?.recycle()
        shadowBitmap = null
        super.onDetachedFromWindow()
    }

    @Deprecated("Deprecated in Java")
    override fun setAlpha(alpha: Int) {
        super.setAlpha(alpha)
        glintPaint.alpha = alpha
        shadowPaint.alpha = alpha
    }

    override fun setColorFilter(cf: ColorFilter?) {
        super.setColorFilter(cf)
        glintPaint.colorFilter = cf
    }
}
