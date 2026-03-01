package com.esde.companion

import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

object PlayerDebug {
    private val count = AtomicInteger(0)

    fun created(tag: String) {
        val total = count.incrementAndGet()
        Log.d("PlayerDebug", "CREATED [$tag] | total active: $total")
    }

    fun released(tag: String) {
        val total = count.decrementAndGet()
        Log.d("PlayerDebug", "RELEASED [$tag] | total active: $total")
    }

    fun dump() {
        Log.d("PlayerDebug", "Current active player count: ${count.get()}")
    }
}