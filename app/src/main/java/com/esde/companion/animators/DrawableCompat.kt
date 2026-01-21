package com.esde.companion.animators

import android.animation.Animator

fun Animator.doOnEndCompat(action: () -> Unit) {
    addListener(object : Animator.AnimatorListener {
        override fun onAnimationEnd(animation: Animator) = action()
        override fun onAnimationStart(animation: Animator) {}
        override fun onAnimationCancel(animation: Animator) {}
        override fun onAnimationRepeat(animation: Animator) {}
    })
}