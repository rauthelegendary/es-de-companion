package com.esde.companion

import android.content.Context
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.net.Uri
import java.io.File

object CoilUtils {

    fun isPotentialAnimation(file: File): Boolean {
        return file.extension.lowercase() in listOf("gif", "webp")
    }

    fun isPotentialAnimation(context: Context, path: String?): Boolean {
        if (path == null) return false

        return if (path.startsWith("content://")) {
            val type = context.contentResolver.getType(Uri.parse(path))
            type == "image/gif" || type == "image/webp"
        } else {
            val extension = path.substringAfterLast('.', "").lowercase()
            extension in listOf("gif", "webp")
        }
    }

    fun startIfAnimated(drawable: Drawable?) {
        (drawable as? Animatable)?.start()
    }

    fun isAnimated(drawable: Drawable?): Boolean = drawable is Animatable
}