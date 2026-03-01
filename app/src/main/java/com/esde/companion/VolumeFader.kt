package com.esde.companion

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.media3.exoplayer.ExoPlayer

class VolumeFader(private var player: Any?) {
    private var animator: ValueAnimator? = null
    private var defaultDuration: Long = 400

    private var pendingCallback: (() -> Unit)? = null

    var targetVolume: Float = 1f
        private set

    fun setPlayer(newPlayer: Any?) {
        this.player = newPlayer
    }

    private fun getVolume(): Float {
        return when (val p = player) {
            is ExoPlayer -> p.volume
            else -> 0f
        }
    }

    private fun setVolume(vol: Float) {
        when (val p = player) {
            is ExoPlayer -> p.volume = vol
            else -> {}
        }
    }

    fun fadeTo(goal: Float, duration: Long = defaultDuration, onComplete: (() -> Unit)? = null) {
        val startVol = getVolume()
        targetVolume = goal
        animator?.cancel()
        pendingCallback = onComplete

        animator = ValueAnimator.ofFloat(startVol, goal).apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()

            addUpdateListener { animation ->
                setVolume(animation.animatedValue as Float)
            }

            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                    pendingCallback = null
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) {
                        val cb = pendingCallback
                        pendingCallback = null
                        cb?.invoke()
                    }
                }
            })
            start()
        }
    }

    fun cancel() {
        animator?.cancel()
        animator = null
        pendingCallback = null
    }
}