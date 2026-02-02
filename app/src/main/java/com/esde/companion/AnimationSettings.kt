package com.esde.companion

import android.content.Context
import com.esde.companion.ui.PageAnimation
import kotlinx.coroutines.flow.MutableStateFlow

class AnimationSettings(context: Context) {
    private val prefs = context.getSharedPreferences("animation_settings", Context.MODE_PRIVATE)

    val transitionTarget = MutableStateFlow(
        PageAnimation.valueOf(prefs.getString("transition_target", PageAnimation.CONTEXT.name) ?: PageAnimation.PAGE.name)
    )
    val animateWidgets = MutableStateFlow(prefs.getBoolean("animate_widgets", true))
    val duration = MutableStateFlow(prefs.getInt("duration", 400))

    fun updateAnimation(type: PageAnimation) {
        transitionTarget.value = type
        prefs.edit().putString("transition_target", type.name).apply()
    }

    fun updateAnimateWidgets(enabled: Boolean) {
        animateWidgets.value = enabled
        prefs.edit().putBoolean("animate_widgets", enabled).apply()
    }

    fun updateDuration(ms: Int) {
        duration.value = ms
        prefs.edit().putInt("duration", ms).apply()
    }
}