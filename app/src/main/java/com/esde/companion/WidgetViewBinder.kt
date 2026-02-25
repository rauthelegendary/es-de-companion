package com.esde.companion

import android.graphics.Rect
import android.view.View
import androidx.core.view.children
import androidx.lifecycle.LifecycleOwner
import com.esde.companion.data.Widget
import com.esde.companion.managers.ImageManager
import com.esde.companion.managers.MediaManager

class WidgetViewBinder {
    private val viewPool = mutableListOf<WidgetView>()

    /**
     * synchronizes the UI views in the container with the provided data list.
     * reuses existing views to prevent memory churn.
     */
    fun sync(
        container: ResizableWidgetContainer,
        lifecycleOwner: LifecycleOwner,
        dataList: List<Widget>,
        page: WidgetPage,
        locked: Boolean,
        snapToGrid: Boolean,
        gridSize: Float,
        onTouch: (WidgetView, Boolean) -> Unit,
        onUpdate: (Widget) -> Unit,
        onSelect: (WidgetView) -> Unit,
        animationSettings: AnimationSettings,
        imageManager: ImageManager,
        game: String = "",
        system: String,
        forcedRefresh: Boolean,
        pendingWidgetId: String?,
        mediaManager: MediaManager
    ) {
        val existingViews = container.children.filterIsInstance<WidgetView>().toList()
        val dataIds = dataList.map { it.id }.toSet()

        existingViews.forEach { view ->
            if (view.widget.id !in dataIds) {
                view.prepareForReuse()
                view.visibility = View.GONE
                container.removeView(view)
                viewPool.add(view)
            }
        }

        dataList.forEach { data ->
            var view = existingViews.find { it.widget.id == data.id }

            if (view == null) {
                if (viewPool.isNotEmpty()) {
                    view = viewPool.removeAt(0)
                    view.visibility = View.VISIBLE
                    view.updateContent(data, page, game, system, forcedRefresh)
                } else {
                    view = WidgetView(container.context, lifecycleOwner, data, page, onTouch, onUpdate, onSelect, animationSettings, imageManager, game, system, mediaManager)
                }
                container.addView(view)
            } else {
                view.updateContent(data, page, game, system, forcedRefresh)
            }

            view.setSnapToGrid(snapToGrid, gridSize)
            view.setLocked(locked)
            view.bringToFront()

            if(pendingWidgetId != null && view.widget.id == pendingWidgetId) {
                view.setSelected()
            }
        }

        while (viewPool.size > 8) {
            viewPool.removeAt(0)
        }
    }

    /**
     * Loops through all WidgetView children and triggers their internal deselect logic.
     */
    //TODO: remove?
    fun deselectAll(container: ResizableWidgetContainer) {
        for (i in 0 until container.childCount) {
            (container.getChildAt(i) as? WidgetView)?.currentMode = WidgetMode.IDLE
        }
    }

    /**
     * Mass visibility toggle for when switching to modes that don't use widgets.
     */
    fun setAllVisibility(container: ResizableWidgetContainer, isVisible: Boolean) {
        val visibility = if (isVisible) View.VISIBLE else View.GONE
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is WidgetView) {
                child.visibility = visibility
            }
        }
    }

    fun setAllSnapToGrid(container: ResizableWidgetContainer, snap: Boolean, gridSize: Float) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is WidgetView) {
                child.setSnapToGrid(snap, gridSize)
            }
        }
    }

    fun setAlLocked(container: ResizableWidgetContainer, locked: Boolean) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is WidgetView) {
                child.setLocked(locked)
            }
        }
    }

    /**
     * Utility to find which widget is under a touch coordinate.
     * Searches in reverse order to respect Z-index (top-most widget first).
     */
    fun findWidgetAt(container: ResizableWidgetContainer, x: Float, y: Float): List<WidgetView> {
        val rect = Rect()
        val results = mutableListOf<WidgetView>()
        for (i in container.childCount - 1 downTo 0) {
            val child = container.getChildAt(i) as? WidgetView ?: continue
            if (child.visibility != View.VISIBLE) continue

            child.getHitRect(rect)
            if (rect.contains(x.toInt(), y.toInt())) {
                results.add(child)
            }
        }
        return results
    }

    fun syncSingleWidget(widget: Widget, widgetContainer: ResizableWidgetContainer, widgetPage: WidgetPage, game: String, system: String) {
        val existingViews = widgetContainer.children.filterIsInstance<WidgetView>().toList()
        val view = existingViews.find { it.widget.id == widget.id }
        view?.updateContent(widget, widgetPage, game, system)
    }

    fun onBlackscreen(widgetContainer: ResizableWidgetContainer) {
        widgetContainer.children.filterIsInstance<WidgetView>().forEach { it.onBlackScreen() }
    }
}