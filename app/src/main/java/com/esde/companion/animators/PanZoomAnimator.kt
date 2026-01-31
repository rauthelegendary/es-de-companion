package com.esde.companion.animators

import android.animation.ValueAnimator
import android.graphics.Matrix
import android.view.ViewTreeObserver
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import com.esde.companion.R
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

object PanZoomAnimator {
    fun startAnimation(view: ImageView) {
        stopPanZoom(view)

        val baseScale = view.getTag(R.id.tag_base_scale) as? Float ?: 1f

        val zoomExtra = 0.12f
        val durationMs = 20_000L

        val dirX = if (Random.nextBoolean()) 1 else -1
        val dirY = dirX * if (Random.nextBoolean()) 1 else -1

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()

            addUpdateListener {
                val t = it.animatedFraction

                val scale = baseScale * (1f + zoomExtra * t)
                view.scaleX = scale
                view.scaleY = scale

                val overflowX = (view.width * (scale - 1f)) / 2f
                val overflowY = (view.height * (scale - 1f)) / 2f

                view.translationX = dirX * overflowX * t
                view.translationY = dirY * overflowY * t
            }
            start()
        }
        view.setTag(R.id.tag_pan_zoom_animator, animator)
    }
    fun applyBaseScaleOnce(view: ImageView) {
        if (view.getTag(R.id.tag_base_scale_applied) == true) return

        view.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    view.viewTreeObserver.removeOnPreDrawListener(this)
                    val drawable = view.drawable ?: return true

                    val vw = view.width.toFloat()
                    val vh = view.height.toFloat()
                    val dw = drawable.intrinsicWidth.toFloat()
                    val dh = drawable.intrinsicHeight.toFloat()

                    if (dw <= 0f || dh <= 0f || vw <= 0f || vh <= 0f) return true

                    val widthRatio = vw / dw
                    val heightRatio = vh / dh

                    val viewAspect = vw / vh
                    val imageAspect = dw / dh
                    val aspectDiff = Math.abs(viewAspect - imageAspect)

                    // STEP 1: Calculate the internal Matrix scale
                    // If the image is close to screen aspect, we fill (max).
                    // If it's 4:3 or similar, we fit (min).
                    val internalScale = if (aspectDiff < 0.1f) {
                        max(widthRatio, heightRatio) * 1.005f
                    } else {
                        min(widthRatio, heightRatio)
                    }

                    // STEP 2: Apply via MATRIX.
                    // This solves the 4K issue by scaling the BITMAP to the VIEW size
                    // before the View's own scaleX/scaleY are applied.
                    view.scaleType = ImageView.ScaleType.MATRIX
                    val matrix = Matrix()
                    matrix.postTranslate((vw - dw) / 2f, (vh - dh) / 2f)
                    matrix.postScale(internalScale, internalScale, vw / 2f, vh / 2f)
                    view.imageMatrix = matrix

                    // STEP 3: Reset View properties to identity
                    // Because the Matrix handled the "Fit", our base scale for animation is now 1.0
                    view.scaleX = 1f
                    view.scaleY = 1f
                    view.translationX = 0f
                    view.translationY = 0f

                    view.setTag(R.id.tag_base_scale_applied, true)
                    view.setTag(R.id.tag_base_scale, 1.0f)

                    return true
                }
            }
        )
    }

    private fun lerp(a: Float, b: Float, t: Float): Float =
        a + (b - a) * t


    fun stopPanZoom(view: ImageView) {
        (view.getTag(R.id.tag_pan_zoom_animator) as? ValueAnimator)?.cancel()
        view.setTag(R.id.tag_pan_zoom_animator, null)
    }

    private fun ClosedFloatingPointRange<Float>.random(): Float =
        start + Math.random().toFloat() * (endInclusive - start)
}