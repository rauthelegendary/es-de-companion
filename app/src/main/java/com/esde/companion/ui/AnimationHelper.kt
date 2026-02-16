package com.esde.companion.ui

import android.view.View
import android.view.View.VISIBLE
import android.widget.ImageView
import com.esde.companion.animators.PanZoomAnimator

object AnimationHelper {
    fun applyAnimation(view: View, duration: Long, style: AnimationStyle, shouldPanZoom: Boolean = false) {
        view.animate().cancel()
        view.visibility = VISIBLE
        resetFromPanZoom(view)

        when (style) {
            AnimationStyle.NONE -> {
                view.alpha = 1f
                view.scaleX = 1f
                view.scaleY = 1f
                finalizeContentEntrance(view, shouldPanZoom)
            }
            AnimationStyle.FADE -> {
                view.alpha = 0f
                view.animate()
                    .alpha(1f)
                    .setDuration(duration)
                    .withEndAction { finalizeContentEntrance(view, shouldPanZoom) }
                    .start()
            }
            AnimationStyle.SCALE_FADE -> {
                view.alpha = 0f
                view.scaleX = 0.8f
                view.scaleY = 0.8f
                view.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(duration)
                    .withEndAction { finalizeContentEntrance(view, shouldPanZoom) }
                    .start()
            }
        }
    }
}

private fun resetFromPanZoom(view: View) {
    view.translationX = 0f
    view.translationY = 0f
    view.scaleX = 1f
    view.scaleY = 1f
    view.pivotX = view.width / 2f
    view.pivotY = view.height / 2f
}


private fun finalizeContentEntrance(view: View, shouldPanZoom: Boolean) {
    if (view is ImageView && shouldPanZoom) {
        view.post {
            //PanZoomAnimator.applyBaseScaleOnce(view)
            PanZoomAnimator.startAnimation(view)
        }
    }
}