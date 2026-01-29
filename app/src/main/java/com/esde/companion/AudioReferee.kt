package com.esde.companion

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

//Determines which audio source gets priority. If the user has multiple video widgets playing at the same time that's on them
object AudioReferee {
    private val _currentPriority = MutableStateFlow(AudioSource.MUSIC)
    val currentPriority = _currentPriority.asStateFlow()

    private var menuActive : Boolean = false

    private val activeAudioWidgets = mutableSetOf<String>()
    private var backgroundActive: Boolean = false

    enum class AudioSource { WIDGET, BACKGROUND, MUSIC, FORCED_UPDATE }

    fun updateMenuState(active: Boolean) {
        menuActive = active
        update()
    }

    fun getMenuState(): Boolean {
        return menuActive
    }

    fun updateWidgetState(widgetId: String, isActive: Boolean) {
        if (isActive) {
            activeAudioWidgets.add(widgetId)
        } else {
            activeAudioWidgets.remove(widgetId)
        }
        update()
    }

    fun updateBackgroundState(active: Boolean) {
        backgroundActive = active
        update()
    }

    fun forceUpdate() {
        _currentPriority.value = AudioSource.FORCED_UPDATE
        update()
    }

    fun update() {
        val getsTheAux = when {
            menuActive -> AudioSource.MUSIC
            activeAudioWidgets.isNotEmpty() -> AudioSource.WIDGET
            backgroundActive -> AudioSource.BACKGROUND
            else -> AudioSource.MUSIC
        }
        _currentPriority.value = getsTheAux
    }

    fun resetWidgetState() {
        activeAudioWidgets.clear()
        update()
    }
}