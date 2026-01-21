package com.esde.companion.animators

import android.animation.ValueAnimator
import android.view.ViewTreeObserver
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import com.esde.companion.R
import kotlin.math.max
import kotlin.random.Random

object PanZoomAnimator {
    fun startAnimation(view: ImageView) {
        stopPanZoom(view)

        val baseScale = view.getTag(R.id.tag_base_scale) as? Float ?: return

        val zoomExtra = 0.14f
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

                val scaleRatio = scale / baseScale
                val overflowX = (view.width * (scaleRatio - 1f)) / 2f
                val overflowY = (view.height * (scaleRatio - 1f)) / 2f

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

                    if (dw <= 0f || dh <= 0f) return true

                    val scale = max(vw / dw, vh / dh)

                    view.scaleX = scale
                    view.scaleY = scale
                    view.translationX = 0f
                    view.translationY = 0f

                    view.setTag(R.id.tag_base_scale_applied, true)
                    view.setTag(R.id.tag_base_scale, scale)

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