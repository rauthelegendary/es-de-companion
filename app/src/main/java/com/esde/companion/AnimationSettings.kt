package com.esde.companion

import android.R.attr.enabled
import android.R.attr.type
import android.content.Context
import com.esde.companion.managers.PreferencesManager
import com.esde.companion.ui.AnimationStyle
import com.esde.companion.ui.PageAnimation
import kotlinx.coroutines.flow.MutableStateFlow

class AnimationSettings(val preferencesManager: PreferencesManager) {
    val transitionTarget = MutableStateFlow(
        PageAnimation.entries[preferencesManager.pageTransitionTarget]
    )

    val animationStyle = MutableStateFlow(
        AnimationStyle.entries[preferencesManager.animate]
    )
    val animateWidgets = MutableStateFlow(preferencesManager.animateWidgets)
    val duration = MutableStateFlow(preferencesManager.animationDuration)

    fun updateTarget(type: PageAnimation) {
        transitionTarget.value = type
        preferencesManager.pageTransitionTarget = type.ordinal
    }

    fun updateAnimation(type: AnimationStyle) {
        animationStyle.value = type
        preferencesManager.animate = type.ordinal
    }

    fun updateAnimateWidgets(enabled: Boolean) {
        animateWidgets.value = enabled
        preferencesManager.animateWidgets = enabled
    }

    fun updateDuration(ms: Int) {
        duration.value = ms
        preferencesManager.animationDuration = ms
    }
}