package com.esde.companion

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.media3.exoplayer.ExoPlayer

class VolumeFader(private var player: ExoPlayer?) {
    private var animator: ValueAnimator? = null
    private var defaultDuration: Long = 400

    // Track the "Goal" so we don't pause if the user changed their mind mid-fade
    var targetVolume: Float = player?.volume ?: 1f
        private set

    fun setPlayer(player: ExoPlayer?) {
        this.player = player
    }

    fun fadeTo(goal: Float, duration: Long = defaultDuration, onComplete: (() -> Unit)? = null) {
        val startVol = player?.volume ?: 0f
        targetVolume = goal
        animator?.cancel()

        animator = ValueAnimator.ofFloat(startVol, goal).apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()

            addUpdateListener { animation ->
                // ALWAYS use the class-level 'player' property
                // If it was swapped via setPlayer(), we target the new one instantly
                player?.volume = animation.animatedValue as Float
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    //only fire onComplete if the fade wasn't interrupted
                    if (player?.volume == targetVolume || player?.isPlaying == true) {
                        onComplete?.invoke()
                    }
                }
            })
            start()
        }
    }
}