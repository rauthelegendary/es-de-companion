package com.esde.companion

import android.R
import android.R.attr.height
import android.R.attr.path
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.View.TEXT_ALIGNMENT_CENTER
import android.view.View.TEXT_ALIGNMENT_TEXT_END
import android.view.View.TEXT_ALIGNMENT_TEXT_START
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.os.HandlerCompat.postDelayed
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import be.tarsos.dsp.beatroot.Peaks.post
import coil.Coil.imageLoader
import coil.ImageLoader
import coil.dispose
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.esde.companion.data.Widget.MediaSlot
import com.esde.companion.animators.GlintDrawable
import com.esde.companion.data.Widget
import com.esde.companion.managers.ImageManager
import com.esde.companion.ui.AnimationHelper
import com.esde.companion.ui.AnimationStyle
import com.esde.companion.ui.ContentType
import com.esde.companion.ui.PageContentType
import com.esde.companion.ui.ScaleType
import com.esde.companion.ui.TextAlignment
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.net.URI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * ScrollView that never intercepts touch events - only auto-scrolls programmatically
 */
class AutoScrollOnlyView(context: Context) : android.widget.ScrollView(context) {
    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        // Never intercept - let parent handle all touches
        return false
    }

    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        // Never handle touches - only programmatic scrolling allowed
        return false
    }
}
class WidgetView(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    var widget: Widget,
    var page: WidgetPage,
    private val onUpdate: (Widget) -> Unit,
    private val onSelect: (WidgetView) -> Unit,
    private val onEditRequested: (Widget) -> Unit,
    private val animationSettings: AnimationSettings,
    private val imageManager: ImageManager,
    private var system: String,
    private var game: String
) : RelativeLayout(context) {

    private var imageList: List<File?> = emptyList()
    private var currentImageIndex: MediaSlot = MediaSlot.Default
    private val imageView: ImageView
    private val textView: TextView

    private var player: ExoPlayer? = null

    private val playerView: PlayerView = PlayerView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        useController = false
        visibility = GONE
    }

    private val videoCover = View(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        setBackgroundColor(Color.BLACK)
        visibility = GONE
    }
    private val scrollView: AutoScrollOnlyView
    private val settingsButton: ImageButton

    private var isPausing = false

    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = 0xFF4CAF50.toInt()
    }
    private val handlePaint = Paint().apply {
        style = Paint.Style.FILL
        color = 0xFF4CAF50.toInt()
    }
    private val handleSize = 60f
    private val handleHitZone = 200f  // ADDED: Much larger invisible hit area

    var isDragging = false
    var isResizing = false
    private var resizeCorner = ResizeCorner.NONE
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var initialX = 0f
    private var initialY = 0f
    private var initialWidth = 0f
    private var initialHeight = 0f

    var isWidgetSelected = false
    private var isLocked = false

    // Snap to grid settings
    private var snapToGrid = false
    private var gridSize = 50f
    private var scrollJob: Runnable? = null
    private val scrollSpeed = 1  // pixels per frame
    private val scrollDelay = 30L  // milliseconds between scroll updates
    private var currentVideoPath = ""
    private var allowedVolume: Float = 1f

    private var volumeFader = VolumeFader(player)

    private var audioRefereeListener: Job? = null

    enum class ResizeCorner {
        NONE,
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    init {
        addAudioRefereeListener()
        Log.d("WIDGET_LIFECYCLE", "View Created: ${widget.id}")

        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) {
                player?.pause()
            }

            override fun onResume(owner: LifecycleOwner) {
                if (AudioReferee.currentPriority.value == AudioReferee.AudioSource.WIDGET) {
                    player?.play()
                }
            }
        })

        // Create scroll view for text (will be hidden for image widgets)
        scrollView = AutoScrollOnlyView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE
            isVerticalScrollBarEnabled = false  // Hide scrollbar for cleaner look

            // NEW: Completely disable all touch interaction - auto-scroll only
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false

            // Never intercept touch events
            setOnTouchListener { _, _ -> false }
        }

        // Also make TextView non-interactive
        textView = TextView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
            // Make completely non-interactive
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
        }
        scrollView.addView(textView)

        // Create ImageView for the widget content
        imageView = ImageView(context).apply {
            // Scale type will be set dynamically in loadWidgetImage()
            // Remove any max dimensions that might constrain scaling
            maxHeight = Int.MAX_VALUE
            maxWidth = Int.MAX_VALUE
        }

        // Add both views (only one will be visible at a time)
        addView(scrollView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(imageView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(playerView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(videoCover, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
       // }

        // Make sure onDraw (which draws handles) happens after child views
        setWillNotDraw(false)

        val buttonSize = (handleSize * 2f).toInt()

        // Create a container for the buttons at the top center
        val buttonContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            isClickable = false
            isFocusable = false
        }
        val containerParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        containerParams.addRule(ALIGN_PARENT_TOP)
        containerParams.addRule(CENTER_HORIZONTAL)
        containerParams.topMargin = 0


        // Create settings button (cog icon)
        settingsButton = ImageButton(context).apply {
            setImageResource(android.R.drawable.ic_menu_preferences)
            setBackgroundColor(0xFF2196F3.toInt())  // Blue background
            visibility = GONE
            setOnClickListener {
                onEditRequested.invoke(this@WidgetView.widget)
            }
        }
        val settingsButtonParams = LinearLayout.LayoutParams(buttonSize, buttonSize)
        buttonContainer.addView(settingsButton, settingsButtonParams)
        settingsButton.bringToFront()

        addView(buttonContainer, containerParams)

        // Make this view clickable but not focusable (touch-only, no D-pad focus border)
        isClickable = true
        isFocusable = false
        isFocusableInTouchMode = false

        // Load image based on widget data
        loadWidgetContent()

        // Set initial position and size
        updateLayout()

        // Apply initial background opacity for Game Description
        if (widget.contentType == ContentType.GAME_DESCRIPTION) {
            val alpha = (widget.backgroundOpacity * 255).toInt().coerceIn(0, 255)
            scrollView.setBackgroundColor(android.graphics.Color.argb(alpha, 0, 0, 0))
            textView.setBackgroundColor(android.graphics.Color.TRANSPARENT)

            if (alpha == 0) {
                this.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        }
    }

    fun updateContent(newWidget: Widget, page: WidgetPage, game: String, system: String) {
        this.game = game
        this.system = system
        if (widget.id != newWidget.id) {
            prepareForReuse()
            currentImageIndex = newWidget.slot
        }
        this.widget = newWidget
        this.page = page

        if (widget.contentType.isTextWidget()) {
            scrollView.scrollTo(0, 0)
        }

        updateLayout()
        loadWidgetContent()
    }

    private fun updateLayout() {
        val params = layoutParams as? LayoutParams ?: LayoutParams(
            widget.width.toInt(),
            widget.height.toInt()
        )
        params.width = widget.width.toInt()
        params.height = widget.height.toInt()
        params.leftMargin = widget.x.toInt()
        params.topMargin = widget.y.toInt()
        layoutParams = params
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Nothing here - we'll use dispatchDraw instead
    }

    override fun dispatchDraw(canvas: Canvas) {
        // First draw all child views (images, etc.)
        super.dispatchDraw(canvas)

        // Then draw border and handles ON TOP when selected (and not locked)
        if (isWidgetSelected && !isLocked) {
            // Draw green border
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), borderPaint)

            // Draw L-shaped corner handles
            val handleLength = handleSize * 1.5f  // CHANGED: Longer handles
            val handleThickness = 16f  // CHANGED: Much thicker (was 8f)

            val handlePaintThick = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = handleThickness
                color = 0xFFFFFFFF.toInt()  // CHANGED: White color (was green)
                strokeCap = Paint.Cap.ROUND
            }

            // Top-left corner (⌐ shape)
            canvas.drawLine(0f, handleLength, 0f, 0f, handlePaintThick)  // Vertical
            canvas.drawLine(0f, 0f, handleLength, 0f, handlePaintThick)  // Horizontal

            // Top-right corner (¬ shape)
            canvas.drawLine(width.toFloat(), 0f, width - handleLength, 0f, handlePaintThick)  // Horizontal
            canvas.drawLine(width.toFloat(), 0f, width.toFloat(), handleLength, handlePaintThick)  // Vertical

            // Bottom-left corner (L shape)
            canvas.drawLine(0f, height.toFloat(), 0f, height - handleLength, handlePaintThick)  // Vertical
            canvas.drawLine(0f, height.toFloat(), handleLength, height.toFloat(), handlePaintThick)  // Horizontal

            // Bottom-right corner (⌙ shape)
            canvas.drawLine(width.toFloat(), height - handleLength, width.toFloat(), height.toFloat(), handlePaintThick)  // Vertical
            canvas.drawLine(width - handleLength, height.toFloat(), width.toFloat(), height.toFloat(), handlePaintThick)  // Horizontal
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isLocked) {
            if (event.action == MotionEvent.ACTION_DOWN) {
                return true
            }
            if (event.action == MotionEvent.ACTION_UP) {
                cycleImage()
                return true
            }
            return super.onTouchEvent(event)
        } else {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    isResizing = false
                    resizeCorner = ResizeCorner.NONE
                    dragStartX = event.rawX
                    dragStartY = event.rawY
                    initialX = widget.x
                    initialY = widget.y
                    initialWidth = widget.width
                    initialHeight = widget.height

                    // Check if touching any resize handle
                    val touchX = event.x
                    val touchY = event.y
                    if (isWidgetSelected) {
                        resizeCorner = getTouchedResizeCorner(touchX, touchY)
                        if (resizeCorner != ResizeCorner.NONE) {
                            isResizing = true
                            parent.requestDisallowInterceptTouchEvent(true)
                            val mainActivity = context as? MainActivity
                            mainActivity?.cancelLongPress()
                            return true
                        }
                    }
                    isDragging = true
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - dragStartX
                    val deltaY = event.rawY - dragStartY

                    val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop

                    if (isResizing) {
                        parent.requestDisallowInterceptTouchEvent(true)
                    }

                    if (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop) {
                        if (isResizing || (isWidgetSelected && isDragging)) {
                            parent.requestDisallowInterceptTouchEvent(true)
                        }
                    }

                    if (isResizing) {
                        // Resize based on which corner is being dragged
                        resizeFromCorner(resizeCorner, deltaX, deltaY)
                        updateLayout()
                    } else if (isDragging && isWidgetSelected) {
                        // Move the widget only if selected
                        val newX = initialX + deltaX
                        val newY = initialY + deltaY

                        widget.x = if (snapToGrid) snapXToGrid(newX) else newX
                        widget.y = if (snapToGrid) snapYToGrid(newY) else newY
                        updateLayout()
                    }

                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    parent.requestDisallowInterceptTouchEvent(false)

                    val deltaX = event.rawX - dragStartX
                    val deltaY = event.rawY - dragStartY
                    val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
                    val wasMoved = abs(deltaX) > touchSlop || abs(deltaY) > touchSlop
                    val wasResized = isResizing  // Track if we were resizing

                    // Check for tap (to select/deselect)
                    if (!wasMoved && !isResizing) {
                        if (isWidgetSelected) {
                            // Already selected - deselect this one
                            isWidgetSelected = false
                            updateButtonVisibility()
                            invalidate()
                        } else {
                            // Not selected - deselect all others first, then select this one
                            onSelect(this)
                            isWidgetSelected = true
                            updateButtonVisibility()
                            invalidate()
                        }
                    }

                    // Apply final snap on release if enabled
                    if (snapToGrid && (isDragging || isResizing)) {
                        widget.x = snapXToGrid(widget.x)
                        widget.y = snapYToGrid(widget.y)
                        widget.width = snapToGridValue(widget.width)
                        widget.height = snapToGridValue(widget.height)
                        updateLayout()
                    }

                    isDragging = false
                    isResizing = false
                    resizeCorner = ResizeCorner.NONE
                    // Save widget state
                    onUpdate(widget)

                    // ADDED: Reload image after resize to fit new dimensions
                    if (wasResized) {
                        loadWidgetContent()
                    }

                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun cycleImage() {
        if(widget.images != null && widget.cycle) {
            val activeSlots = MediaSlot.entries.filter { slot ->
                widget.images!!.containsKey(slot)
                widget.images!![slot] != null
            }

            if (activeSlots.isEmpty()) return

            val currentActiveIndex = activeSlots.indexOf(currentImageIndex)

            val nextIndex = (currentActiveIndex + 1) % activeSlots.size
            val nextSlot = activeSlots[nextIndex]

            if(currentActiveIndex != nextIndex) {
                currentImageIndex = nextSlot

                val imageFile = widget.images!![nextSlot]

                imageFile?.let {
                    loadImage(it, true)
                }
            }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // If locked, don't intercept
        if (isLocked) {
            return false
        }

        // If selected, check if touching extended corner hit zones (including outside bounds)
        if (isWidgetSelected && ev.action == MotionEvent.ACTION_DOWN) {
            val touchX = ev.x
            val touchY = ev.y

            // Check extended hit zones that go outside the widget bounds
            if (isTouchingExtendedCorner(touchX, touchY)) {
                return true  // Intercept this touch
            }
        }

        return false
    }

   private fun loadWidgetContent() {
       imageView.visibility = GONE
       playerView.visibility = GONE
       scrollView.visibility = GONE

        if (widget.contentType.isTextWidget()) {
            handleTextWidget()
            AudioReferee.updateWidgetState(widget.id,false)
            return
        }

        if (widget.contentType == ContentType.VIDEO) {
            loadVideo(widget.contentPath!!)
            return
        }

        imageView.scaleType = when (widget.scaleType ?: ScaleType.FIT) {
            ScaleType.FIT -> ImageView.ScaleType.FIT_CENTER
            ScaleType.CROP -> ImageView.ScaleType.CENTER_CROP
        }

        val path = widget.contentPath

        if(widget.contentType == ContentType.COLOR_BACKGROUND) {
            loadImage(widget.solidColor!!)
        } else if (widget.contentType == ContentType.CUSTOM_IMAGE) {
            loadImage(path)
         //else if (path!!.isEmpty() || (path.startsWith("/") && !File(path).exists())) {
          //  imageView.dispose()
           // imageView.setImageDrawable(null)        }
        //else if (path != null && path.startsWith("builtin://")) {
       //     val systemName = path.removePrefix("builtin://")
        //    val drawable = mainActivity?.loadSystemLogoFromAssets(
        //        systemName, widget.width.toInt(), widget.height.toInt()
       //     )
       //     loadImage(drawable)
        } else {
            var currentFile = if(path != null) File(path) else null
            if(widget.cycle && widget.images != null) {
                currentFile = widget.images!![currentImageIndex] ?: currentFile
            }
            loadImage(currentFile)
        }
       imageView.visibility = VISIBLE
       AudioReferee.updateWidgetState(widget.id,false)
    }

    private fun loadImage(data: Any?, animate: Boolean = animationSettings.animateWidgets.value) {
        imageManager.load(
            imageView = imageView,
            data = data,
            playAnimation = animate,
            isMarquee = widget.contentType == ContentType.MARQUEE,
            glint = widget.glint,
            system = system,
            game = game,
            textFallback = widget.contentType == ContentType.MARQUEE || widget.contentType == ContentType.SYSTEM_LOGO
        )
    }

    /**
     * Handles the logic for the text type widget
     */
    private fun handleTextWidget() {
        imageView.visibility = View.GONE
        stopAutoScroll()

        if (widget.text.isNotEmpty()) {
            scrollView.visibility = View.VISIBLE
            scrollView.clipToPadding = false
            textView.setTextColor(android.graphics.Color.WHITE)
            textView.gravity = Gravity.CENTER
            textView.setPadding(widget.textPadding, widget.textPadding, widget.textPadding, widget.textPadding)
            textView.setShadowLayer(
                widget.shadowRadius,
                0f,
                0f,
                Color.parseColor("#CC000000")
            )

            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, widget.fontSize)

            val alignment = when (widget.textAlignment) {
                TextAlignment.CENTER -> TEXT_ALIGNMENT_CENTER
                TextAlignment.LEFT -> TEXT_ALIGNMENT_TEXT_START
                TextAlignment.RIGHT -> TEXT_ALIGNMENT_TEXT_END
            }
            textView.textAlignment = alignment

            val style = when {
                widget.isBold && widget.isItalic -> Typeface.BOLD_ITALIC
                widget.isBold -> Typeface.BOLD
                widget.isItalic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }

            val baseTypeface = when (widget.fontType) {
                PageContentType.FontType.SERIF -> Typeface.SERIF
                PageContentType.FontType.MONO -> Typeface.MONOSPACE
                PageContentType.FontType.SANSSERIF -> Typeface.SANS_SERIF
                else -> Typeface.DEFAULT
            }
            textView.typeface = Typeface.create(baseTypeface, style)
            textView.letterSpacing = if (widget.fontSize <= 14f) 0.1f else 0f
            textView.setBackgroundColor(Color.parseColor("#4D000000"))
            setBackgroundOpacity(widget.backgroundOpacity)

            textView.text = widget.text
            if(widget.scrollText) {
                Handler(Looper.getMainLooper()).postDelayed({
                    startAutoScroll()
                }, 5000)
            } else {
                stopAutoScroll()
            }
        } else {
            scrollView.visibility = View.GONE
        }
    }

    private fun loadVideo(videoPath: String) {
        playerView.visibility = View.VISIBLE

        if (player == null) {
            player = ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_ALL
                playWhenReady = true
            }
            playerView.player = player
            volumeFader.setPlayer(player)
        }
        if(currentVideoPath != videoPath) {
            videoCover.animate()?.cancel()
            if (animationSettings.animateWidgets.value) {
                videoCover.alpha = 1f
                videoCover.visibility = View.VISIBLE
            }

            player?.addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    if (animationSettings.animateWidgets.value) {
                        videoCover.animate()
                            .alpha(0f)
                            .setDuration(animationSettings.duration.value.toLong())
                            .setInterpolator(DecelerateInterpolator())
                            .withEndAction { videoCover.visibility = View.GONE }
                            .start()
                    }
                    player?.removeListener(this)
                }
            })

            val mediaItem = MediaItem.fromUri(videoPath)
            player?.setMediaItem(mediaItem)
            player?.prepare()
            currentVideoPath = videoPath
            player?.volume = 0f
        }

        if(widget.playAudio) {
            AudioReferee.updateWidgetState(widget.id, true)
            if(AudioReferee.currentPriority.value == AudioReferee.AudioSource.WIDGET) {
                volumeFader.fadeTo(widget.videoVolume, 500)
            }
        } else {
            AudioReferee.updateWidgetState(widget.id,false)
        }
    }

    fun isTouchingExtendedCorner(x: Float, y: Float): Boolean {
        val extend = handleHitZone / 10

        // Top-left extended zone
        if (x >= -extend && x <= handleHitZone - extend &&
            y >= -extend && y <= handleHitZone - extend) {
            return true
        }
        // Top-right extended zone
        if (x >= width - handleHitZone + extend && x <= width + extend &&
            y >= -extend && y <= handleHitZone - extend) {
            return true
        }
        // Bottom-left extended zone
        if (x >= -extend && x <= handleHitZone - extend &&
            y >= height - handleHitZone + extend && y <= height + extend) {
            return true
        }
        // Bottom-right extended zone
        if (x >= width - handleHitZone + extend && x <= width + extend &&
            y >= height - handleHitZone + extend && y <= height + extend) {
            return true
        }

        return false
    }

    private fun getTouchedResizeCorner(x: Float, y: Float): ResizeCorner {
        val extend = handleHitZone / 10

        // Check top-left (extended outside)
        if (x >= -extend && x <= handleHitZone - extend &&
            y >= -extend && y <= handleHitZone - extend) {
            return ResizeCorner.TOP_LEFT
        }
        // Check top-right (extended outside)
        if (x >= width - handleHitZone + extend && x <= width + extend &&
            y >= -extend && y <= handleHitZone - extend) {
            return ResizeCorner.TOP_RIGHT
        }
        // Check bottom-left (extended outside)
        if (x >= -extend && x <= handleHitZone - extend &&
            y >= height - handleHitZone + extend && y <= height + extend) {
            return ResizeCorner.BOTTOM_LEFT
        }
        // Check bottom-right (extended outside)
        if (x >= width - handleHitZone + extend && x <= width + extend &&
            y >= height - handleHitZone + extend && y <= height + extend) {
            return ResizeCorner.BOTTOM_RIGHT
        }

        return ResizeCorner.NONE
    }

    private fun resizeFromCorner(corner: ResizeCorner, deltaX: Float, deltaY: Float) {
        when (corner) {
            ResizeCorner.TOP_LEFT -> {
                val newWidth = max(100f, initialWidth - deltaX)
                val newHeight = max(100f, initialHeight - deltaY)
                val widthDiff = initialWidth - newWidth
                val heightDiff = initialHeight - newHeight

                widget.width = if (snapToGrid) snapToGridValue(newWidth) else newWidth
                widget.height = if (snapToGrid) snapToGridValue(newHeight) else newHeight
                widget.x = if (snapToGrid) snapXToGrid(initialX + widthDiff) else initialX + widthDiff
                widget.y = if (snapToGrid) snapYToGrid(initialY + heightDiff) else initialY + heightDiff
            }
            ResizeCorner.TOP_RIGHT -> {
                val newWidth = max(100f, initialWidth + deltaX)
                val newHeight = max(100f, initialHeight - deltaY)
                val heightDiff = initialHeight - newHeight

                widget.width = if (snapToGrid) snapToGridValue(newWidth) else newWidth
                widget.height = if (snapToGrid) snapToGridValue(newHeight) else newHeight
                widget.y = if (snapToGrid) snapYToGrid(initialY + heightDiff) else initialY + heightDiff
            }
            ResizeCorner.BOTTOM_LEFT -> {
                val newWidth = max(100f, initialWidth - deltaX)
                val newHeight = max(100f, initialHeight + deltaY)
                val widthDiff = initialWidth - newWidth

                widget.width = if (snapToGrid) snapToGridValue(newWidth) else newWidth
                widget.height = if (snapToGrid) snapToGridValue(newHeight) else newHeight
                widget.x = if (snapToGrid) snapXToGrid(initialX + widthDiff) else initialX + widthDiff
            }
            ResizeCorner.BOTTOM_RIGHT -> {
                val newWidth = max(100f, initialWidth + deltaX)
                val newHeight = max(100f, initialHeight + deltaY)

                widget.width = if (snapToGrid) snapToGridValue(newWidth) else newWidth
                widget.height = if (snapToGrid) snapToGridValue(newHeight) else newHeight
            }
            ResizeCorner.NONE -> {}
        }
    }

    private fun snapXToGrid(x: Float): Float {
        val displayMetrics = context.resources.displayMetrics
        val screenCenterX = displayMetrics.widthPixels / 2f
        // Snap the center itself to grid
        val snappedCenterX = (screenCenterX / gridSize).roundToInt() * gridSize
        val distanceFromCenter = x - snappedCenterX
        val snappedDistance = (distanceFromCenter / gridSize).roundToInt() * gridSize
        return snappedCenterX + snappedDistance
    }

    private fun snapYToGrid(y: Float): Float {
        val displayMetrics = context.resources.displayMetrics
        val screenCenterY = displayMetrics.heightPixels / 2f
        // Snap the center itself to grid
        val snappedCenterY = (screenCenterY / gridSize).roundToInt() * gridSize
        val distanceFromCenter = y - snappedCenterY
        val snappedDistance = (distanceFromCenter / gridSize).roundToInt() * gridSize
        return snappedCenterY + snappedDistance
    }

    private fun snapToGridValue(value: Float): Float {
        return (value / gridSize).roundToInt() * gridSize
    }

    fun setSnapToGrid(snap: Boolean, size: Float) {
        snapToGrid = snap
        gridSize = size

        if (snap) {
            widget.x = snapXToGrid(widget.x)
            widget.y = snapYToGrid(widget.y)
            widget.width = snapToGridValue(widget.width)
            widget.height = snapToGridValue(widget.height)
            updateLayout()
        }
    }

    fun setLocked(locked: Boolean) {
        isLocked = locked
        if (locked) {
            isWidgetSelected = false
        }
        invalidate()
    }

    fun deselect() {
        isWidgetSelected = false
        updateButtonVisibility()
        invalidate()
    }

    private fun updateButtonVisibility() {
        val shouldShow = isWidgetSelected && !isLocked
        android.util.Log.d("WidgetView", "updateDeleteButtonVisibility: shouldShow=$shouldShow, isWidgetSelected=$isWidgetSelected, isLocked=$isLocked")
        settingsButton.visibility = if (shouldShow) VISIBLE else GONE
    }

     fun setBackgroundOpacity(opacity: Float) {
        if (!widget.contentType.isTextWidget()) {
            return
        }
        widget.backgroundOpacity = opacity
        val alpha = (opacity * 255).toInt().coerceIn(0, 255)
        scrollView.setBackgroundColor(android.graphics.Color.argb(alpha, 0, 0, 0))
        textView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        if (alpha == 0) {
            this.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
     }

     private fun startAutoScroll() {
         stopAutoScroll() // Clears the previous job
         isPausing = false

         scrollJob = object : Runnable {
             override fun run() {
                 if (scrollJob == null) return

                 val maxScroll = textView.height - scrollView.height
                 if (maxScroll <= 0 || isPausing) return

                 val currentScroll = scrollView.scrollY

                 if (currentScroll < maxScroll) {
                     scrollView.scrollTo(0, currentScroll + scrollSpeed)
                     postDelayed(this, scrollDelay)
                 } else {
                     isPausing = true

                     postDelayed({
                         if (scrollJob == null) return@postDelayed
                         scrollView.scrollTo(0, 0)

                         postDelayed({
                             if (scrollJob == null) return@postDelayed
                             isPausing = false
                             run() // Restart the loop
                         }, 3000)
                     }, 3000)
                 }
             }
         }
         post(scrollJob!!)
     }

    private fun stopAutoScroll() {
        isPausing = true
        scrollJob?.let {
            removeCallbacks(it)
        }
        handler?.removeCallbacksAndMessages(null)

        scrollJob = null
        scrollView.scrollTo(0, 0)
    }

    // Update onDetachedFromWindow to stop scrolling
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAutoScroll()
        prepareForReuse()
    }

    fun prepareForReuse() {
        playerView.player = null
        player?.let {
            it.stop()
            it.release()
        }
        player = null
        currentVideoPath = ""
        imageList = emptyList<File?>()

        (imageView.drawable as? GlintDrawable)?.let { glint ->
            glint.stop()
        }
        imageView.setImageDrawable(null)
        imageView.dispose()
        volumeFader.setPlayer(null)
        //volumeFader.cancel()

        stopAutoScroll()
        isWidgetSelected = false
        updateButtonVisibility()

        AudioReferee.updateWidgetState(widget.id, false)
    }

    private fun addAudioRefereeListener() {
        if (audioRefereeListener?.isActive == true) return

        audioRefereeListener = lifecycleOwner.lifecycleScope.launch {
            AudioReferee.currentPriority
                .flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .distinctUntilChanged()
                .collect { priority ->
                    if (widget.contentType == ContentType.VIDEO) {
                        allowedVolume = if (priority == AudioReferee.AudioSource.WIDGET && widget.playAudio) widget.videoVolume else 0f
                        volumeFader.fadeTo(allowedVolume)
                    }
                }
        }
    }


}