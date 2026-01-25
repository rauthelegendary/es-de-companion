package com.esde.companion

import android.graphics.Rect
import android.view.View
import androidx.core.view.children

class WidgetViewBinder {

    /**
     * synchronizes the UI views in the container with the provided data list.
     * reuses existing views to prevent memory churn.
     */
    fun sync(
        container: ResizableWidgetContainer,
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
        val existingViews = container.children.filterIsInstance<WidgetView>().toList()

        // 2. Only perform "Heavy Cleanup" if we are actually switching pages
        if (pageSwap) {
            existingViews.forEach { view ->
                view.onPageHide() // Stops videos/audio
                view.visibility = View.GONE
            }
        }

        // 3. Match Data to Views
        dataList.forEach { data ->
            var view = existingViews.find { it.widget.id == data.id }

            if (view == null) {
                view = WidgetView(container.context, data, onUpdate, onSelect, onEditRequested)
                container.addView(view)
            }

            view.visibility = View.VISIBLE
            view.setSnapToGrid(snapToGrid, gridSize)
            view.setLocked(locked)

            // Only trigger updateContent if it's a page swap OR the data changed
            // Passing isFullPageSwap down tells the view whether to force-reload media
            if (pageSwap || view.widget != data) {
                view.updateContent(data)
            }

            // Keep Z-order in sync with the list order
            view.bringToFront()
        }

        val dataIds = dataList.map { it.id }.toSet() // Using a Set is faster for lookups
        container.children.filterIsInstance<WidgetView>()
            .toList()
            .forEach { view ->
                // Only remove if it's NOT in the new data list AND it's actually a child of this container
                if (view.widget.id !in dataIds) {
                    if (view.parent == container) {
                        container.removeView(view)
                        view.onPageHide() // Clean up media for the removed view
                    }
                }
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