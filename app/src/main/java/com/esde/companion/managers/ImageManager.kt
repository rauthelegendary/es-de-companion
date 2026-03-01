package com.esde.companion.managers

import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.Animatable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.Disposable
import coil.request.ImageRequest
import coil.size.Precision
import com.esde.companion.AnimationSettings
import com.esde.companion.CoilUtils
import com.esde.companion.R
import com.esde.companion.TextDrawable
import com.esde.companion.animators.PanZoomAnimator
import com.esde.companion.ui.AnimationHelper
import com.esde.companion.ui.AnimationStyle
import com.esde.companion.ui.ScaleType
import java.io.File

class ImageManager(
    private val context: Context,
    private val animationSettings: AnimationSettings,
    private val prefsManager: PreferencesManager
) {
    private val activeRequests = mutableMapOf<ImageView, Disposable>()

    private val backupSize = 500

    /**
     * The primary entry point.
     * @param data Can be a String (URI/Path), File, Int (Color), or Drawable.
     */
    fun load(
        imageView: ImageView,
        data: Any?,
        playAnimation: Boolean = true,
        isBackground: Boolean = false,
        panZoom: Boolean = false,
        isMarquee: Boolean = false,
        textFallback: Boolean = false,
        glint: Boolean = false,
        system: String = "",
        game: String = "",
        isSystemLogo: Boolean = false,
        scaleType: ScaleType? = null
    ) {
        activeRequests[imageView]?.dispose()
        activeRequests.remove(imageView)

        imageView.animate().cancel()
        imageView.translationX = 0f
        imageView.translationY = 0f
        imageView.scaleX = 1f
        imageView.scaleY = 1f
        imageView.imageMatrix = Matrix()
        imageView.visibility = View.VISIBLE
        if(isBackground) PanZoomAnimator.stopPanZoom(imageView)
        (imageView.drawable as? Animatable)?.stop()

        val useGlint = isMarquee && glint

        //fallback chain
        val attempts = mutableListOf<Any?>()
        attempts.add(data)
        if(isBackground) {
            attempts.add(getBackgroundData(context))
        }
        if(isSystemLogo) {
            attempts.add(getDefaultLogo(system, context))
        }
        if(textFallback) {
            attempts.add(getTextDrawable(system, game))
        }

        executeLoadChain(imageView, attempts, isBackground, panZoom, useGlint, playAnimation, scaleType)
    }

    private fun executeLoadChain(
        imageView: ImageView,
        attempts: MutableList<Any?>,
        isBackground: Boolean,
        panZoom: Boolean,
        useGlint: Boolean,
        playAnimation: Boolean,
        scaleType: ScaleType?
    ) {
        val current = attempts.removeFirstOrNull()
        if(current == null) {
            if(attempts.isEmpty()) {
                imageView.alpha = 0f
                return
            } else {
                executeLoadChain(imageView, attempts, isBackground, panZoom, useGlint, playAnimation, scaleType)
                return
            }
        }
        val displayMetrics = context.resources.displayMetrics
        val backupWidth = if (isBackground) displayMetrics.widthPixels else backupSize
        val backupHeight = if (isBackground) displayMetrics.heightPixels else backupSize
        val width = if (imageView.width > 300) imageView.width else backupWidth
        val height = if (imageView.height > 300) imageView.height else backupHeight

        when (current) {
            is Int -> applySolidColor(imageView, current, playAnimation, isBackground)
            is Drawable -> applyDrawable(imageView, current, playAnimation)
            else -> {
                val request = ImageRequest.Builder(context)
                    .data(current)
                    .size(width, height)
                    .allowHardware(true)
                    .precision(Precision.EXACT)
                    .apply {
                        val isPotentialAnim = when (current) {
                            is File -> CoilUtils.isPotentialAnimation(current)
                            is String -> CoilUtils.isPotentialAnimation(context, current)
                            else -> false
                        }
                        if (isPotentialAnim) memoryCachePolicy(CachePolicy.READ_ONLY)
                    }
                    .target(
                        onStart = { placeholder ->
                            imageView.setImageDrawable(placeholder)
                            if (playAnimation) imageView.alpha = 0f
                        },
                        onSuccess = { result ->
                            activeRequests.remove(imageView)
                            imageView.setImageDrawable(result)

                            if(isBackground) {
                                applySmartScale(imageView, scaleType)
                            }
                            handleEntranceAnimation(imageView,
                                result, isBackground, panZoom, useGlint, playAnimation)
                        },
                        onError = { error ->
                            executeLoadChain(imageView, attempts, isBackground, panZoom, useGlint, playAnimation, scaleType)
                        }
                    )
                    .build()
                activeRequests[imageView] = context.imageLoader.enqueue(request)
            }
        }
    }

    private fun handleEntranceAnimation(view: ImageView, drawable: Drawable, isBackground: Boolean, panZoom: Boolean, useGlint: Boolean, playAnimation: Boolean) {
        CoilUtils.startIfAnimated(drawable)

        val isAnimated = CoilUtils.isAnimated(drawable)
        val shouldPanZoom = !useGlint && !isAnimated && panZoom

        var style = AnimationStyle.NONE
        var duration = animationSettings.duration.value.toLong()
        if (playAnimation && isBackground) {
            style = animationSettings.animationStyle.value
        } else if(playAnimation) {
            style = AnimationStyle.FADE
            duration += 150L
        }

        AnimationHelper.applyAnimation(
            view = view,
            duration = duration,
            style = style,
            shouldPanZoom = shouldPanZoom
        )
    }

    private fun applySolidColor(view: ImageView, color: Int, playAnimation: Boolean, isBackground: Boolean) {
        val colorDrawable = ColorDrawable(color)
        view.setImageDrawable(colorDrawable)

        if (playAnimation) {
            if(isBackground) {
                AnimationHelper.applyAnimation(
                    view,
                    animationSettings.duration.value.toLong(),
                    animationSettings.animationStyle.value
                )
            } else {
                AnimationHelper.applyAnimation(
                    view,
                    animationSettings.duration.value.toLong() + 150L,
                    AnimationStyle.FADE
                )
            }
        } else {
            view.alpha = 1f
        }
    }

    private fun applyDrawable(view: ImageView, drawable: Drawable, playAnimation: Boolean) {
        view.setImageDrawable(drawable)
        val style = if (playAnimation) AnimationStyle.FADE else AnimationStyle.NONE
        val duration = if (playAnimation) animationSettings.duration.value.toLong() + 150L else 0L
        AnimationHelper.applyAnimation(view, duration, style)
    }

    fun getBackgroundData(context: Context): Any? {
        val customPath = prefsManager.customBackgroundPath

        if (customPath.isNotEmpty()) {
            if (customPath.startsWith("content://")) return customPath
            val file = File(customPath)
            if (file.exists() && file.canRead()) return file
        }

        // Fallback: Ensure it exists in cache
        val fallbackFile = File(context.cacheDir, "default_background.webp")
        if (!fallbackFile.exists()) {
            try {
                context.assets.open("fallback/default_background.webp").use { input ->
                    fallbackFile.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: Exception) {
                return null
            }
        }
        return fallbackFile
    }

    fun getDefaultLogo(systemName: String, context: Context): File? {
        val baseName = getBaseName(systemName)
        val fallbackFile = File(context.cacheDir, "${baseName}.svg")
        if (!fallbackFile.exists()) {
            val tempFile = File(context.cacheDir, "${baseName}_tmp.svg")
            try {
                context.assets.open("system_logos/${baseName}.svg").use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                tempFile.renameTo(fallbackFile)
            } catch (e: Exception) {
                tempFile.delete()
                return null
            }
        }
        return fallbackFile
    }

    private fun getBaseName(systemName: String): String {
        return when (systemName.lowercase()) {
            "allgames" -> "auto-allgames"
            "all" -> "auto-allgames"
            "favorites" -> "auto-favorites"
            "lastplayed" -> "auto-lastplayed"
            "recent" -> "auto-lastplayed"
            else -> systemName.lowercase()
        }
    }

    fun getTextDrawable(systemName: String, gameName: String): Drawable {
        var text = gameName
        if(text.isEmpty()) {
            text = systemName
                .replace("auto-", "")
                .replace("-", " ")
                .split(" ")
                .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
        }
        return TextDrawable(text)
    }

    fun applySmartScale(view: ImageView, scaleType: ScaleType?) {
        val drawable = view.drawable ?: return
        if (view.width == 0 || view.height == 0) {
            view.post { applySmartScale(view, scaleType) }
            return
        }

        val vw = view.width.toFloat()
        val vh = view.height.toFloat()
        val dw = drawable.intrinsicWidth.toFloat()
        val dh = drawable.intrinsicHeight.toFloat()

        val widthRatio = vw / dw
        val heightRatio = vh / dh

        val baseScale = when (scaleType) {
            ScaleType.FIT -> widthRatio.coerceAtMost(heightRatio)
            ScaleType.CROP -> widthRatio.coerceAtLeast(heightRatio)
            else -> {
                val viewAspect = vw / vh
                val imageAspect = dw / dh
                if (imageAspect > viewAspect) {
                    widthRatio.coerceAtLeast(heightRatio)
                } else {
                    widthRatio.coerceAtMost(heightRatio)
                }
            }
        }

        view.scaleType = ImageView.ScaleType.MATRIX
        val matrix = Matrix()
        matrix.setTranslate((vw - dw) / 2f, (vh - dh) / 2f)
        matrix.postScale(baseScale, baseScale, vw / 2f, vh / 2f)

        view.imageMatrix = matrix
        view.setTag(R.id.tag_base_scale, 1.0f)
    }
}