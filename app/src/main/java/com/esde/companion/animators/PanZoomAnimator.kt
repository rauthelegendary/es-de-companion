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

                val scale = 1.0f + (zoomExtra * t)
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

    fun stopPanZoom(view: ImageView) {
        (view.getTag(R.id.tag_pan_zoom_animator) as? ValueAnimator)?.cancel()
        view.setTag(R.id.tag_pan_zoom_animator, null)

        view.scaleX = 1f
        view.scaleY = 1f
        view.translationX = 0f
        view.translationY = 0f
    }
}