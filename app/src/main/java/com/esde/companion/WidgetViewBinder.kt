package com.esde.companion

import android.graphics.Rect
import android.view.View
import androidx.core.view.children
import androidx.lifecycle.LifecycleOwner

class WidgetViewBinder {
    private val viewPool = mutableListOf<WidgetView>()

    /**
     * synchronizes the UI views in the container with the provided data list.
     * reuses existing views to prevent memory churn.
     */
    fun sync(
        container: ResizableWidgetContainer,
        lifecycleOwner: LifecycleOwner,
        dataList: List<OverlayWidget>,
        locked: Boolean,
        snapToGrid: Boolean,
        gridSize: Float,
        pageSwap: Boolean,
        onUpdate: (OverlayWidget) -> Unit,
        onSelect: (WidgetView) -> Unit = { selectedView: WidgetView ->
                deselectAll(container)
                selectedView.isWidgetSelected = true
        },
        onEditRequested: (OverlayWidget) -> Unit
    ) {


        /**if (pageSwap) {
            existingViews.forEach { view ->
                view.onPageHide() // Stops videos/audio
                view.visibility = View.GONE
            }
        }*/

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
                    view.updateContent(data)
                } else {
                    view = WidgetView(container.context, lifecycleOwner, data, onUpdate, onSelect, onEditRequested)
                }
                container.addView(view)
            } else {
                view.updateContent(data)
            }

            view.setSnapToGrid(snapToGrid, gridSize)
            view.setLocked(locked)

            //TODO: this will go wrong with z-index, no?
            view.bringToFront()
        }

        while (viewPool.size > 8) {
            viewPool.removeAt(0)
        }
    }

    /**
     * Loops through all WidgetView children and triggers their internal deselect logic.
     */
    fun deselectAll(container: ResizableWidgetContainer) {
        for (i in 0 until container.childCount) {
            (container.getChildAt(i) as? WidgetView)?.deselect()
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

    fun isAnyWidgetBusy(container: ResizableWidgetContainer): Boolean {
        return (0 until container.childCount)
            .map { container.getChildAt(it) }
            .filterIsInstance<WidgetView>()
            .any { it.isDragging || it.isResizing || it.isWidgetSelected}
    }

    /**
     * Utility to find which widget is under a touch coordinate.
     * Searches in reverse order to respect Z-index (top-most widget first).
     */
    fun findWidgetAt(container: ResizableWidgetContainer, x: Float, y: Float): WidgetView? {
        val rect = Rect()
        for (i in container.childCount - 1 downTo 0) {
            val child = container.getChildAt(i) as? WidgetView ?: continue
            if (child.visibility != View.VISIBLE) continue

            child.getHitRect(rect)
            if (rect.contains(x.toInt(), y.toInt())) return child
        }
        return null
    }

    fun isWidgetOnLocation(container: ResizableWidgetContainer, x: Float, y: Float): Boolean {
        return findWidgetAt(container, x, y) != null
    }
}