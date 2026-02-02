package com.esde.companion

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.media3.exoplayer.ExoPlayer
import com.esde.companion.ost.MusicPlayer

class VolumeFader(private var player: Any?) { // Changed to Any
    private var animator: ValueAnimator? = null
    private var defaultDuration: Long = 400

    var targetVolume: Float = 1f
        private set

    fun setPlayer(newPlayer: Any?) {
        this.player = newPlayer
    }

    private fun getVolume(): Float {
        return when (val p = player) {
            is MusicPlayer -> p.getMasterVolume()
            is ExoPlayer -> p.volume
            else -> 0f
        }
    }

    private fun setVolume(vol: Float) {
        when (val p = player) {
            is MusicPlayer ->
            {
                p.setMasterVolume(vol)
            }
            is ExoPlayer -> p.volume = vol
            else -> {}
        }
    }

    fun fadeTo(goal: Float, duration: Long = defaultDuration, onComplete: (() -> Unit)? = null) {
        val startVol = getVolume()
        targetVolume = goal
        animator?.cancel()

        animator = ValueAnimator.ofFloat(startVol, goal).apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()

            addUpdateListener { animation ->
                setVolume(animation.animatedValue as Float)
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (getVolume() == targetVolume) {
                        onComplete?.invoke()
                    }
                }
            })
            start()
        }
    }
}