package com.esde.companion

import android.content.Context
import android.util.DisplayMetrics
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

data class WidgetPage(
    val id: String = java.util.UUID.randomUUID().toString(),
    var widgets: MutableList<OverlayWidget> = mutableListOf()
)

class WidgetManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private var pages: MutableList<WidgetPage> = mutableListOf()
    var currentPageIndex: Int = 0

    // Load all pages from Prefs
    fun load() {
        val json = prefs.getString("widgets_json", null)
        if (json != null) {
            val type = object : TypeToken<List<WidgetPage>>() {}.type
            pages = gson.fromJson(json, type)
        }

        // Safety: ensure at least one page exists
        if (pages.isEmpty()) pages.add(WidgetPage())
    }

    fun save() {
        val json = gson.toJson(pages)
        prefs.edit().putString("widgets_json", json).apply()
    }

    // Returns widgets for the current page, filtered by context (GAME vs SYSTEM)
    fun getWidgetsForCurrentPage(viewContext: OverlayWidget.WidgetContext): List<OverlayWidget> {
        return pages.getOrNull(currentPageIndex)?.widgets?.filter { it.widgetContext == viewContext } ?: emptyList()
    }

    fun goToNextPage() {
        if (pages.isEmpty()) return
        currentPageIndex = (currentPageIndex + 1) % pages.size
    }

    fun goToPreviousPage() {
        if (pages.isEmpty()) return
        currentPageIndex = if (currentPageIndex <= 0) pages.size - 1 else currentPageIndex - 1
    }

    fun addNewPage() {
        val newPage = WidgetPage(id = UUID.randomUUID().toString())
        pages.add(newPage)
        currentPageIndex = pages.size - 1 // Jump to the new page
        save()
    }

    fun removeCurrentPage() {
        if (pages.size <= 1) {
            // Don't delete the last page, just clear its widgets
            pages[0].widgets.clear()
        } else {
            pages.removeAt(currentPageIndex)
            // Adjust index so we don't point out of bounds
            if (currentPageIndex >= pages.size) {
                currentPageIndex = pages.size - 1
            }
        }
        save()
    }

    fun getPageCount(): Int = pages.size

    fun updateWidget(updated: OverlayWidget, metrics: DisplayMetrics) {
        updated.toPercentages(metrics.widthPixels, metrics.heightPixels)
        val page = pages[currentPageIndex]
        val idx = page.widgets.indexOfFirst { it.id == updated.id }
        if (idx != -1) {
            page.widgets[idx] = updated
            save()
        }
    }

    fun deleteWidget(id: String) {
        pages[currentPageIndex].widgets.removeAll { it.id == id }
        save()
    }

    fun hasWidgets(): Boolean {
        return pages.getOrNull(currentPageIndex)?.widgets?.size!! > 0
    }

    fun addNewWidgetToCurrentPage(
        type: OverlayWidget.ContentType,
        widgetContext: OverlayWidget.WidgetContext, displayMetrics: DisplayMetrics
    ): OverlayWidget {
        val centerX = displayMetrics.widthPixels / 2f
        val centerY = displayMetrics.heightPixels / 2f
        val newWidget = OverlayWidget(
            contentType = type,
            contentPath = "",
            description = "",
            x = centerX,
            y = centerY,
            width = 300f,
            height = 400f,
            zIndex = 10, //TODO: FIX THIS Z-INDEX
            widgetContext = widgetContext
        )
        pages[currentPageIndex].widgets.add(newWidget)
        save()
        return newWidget
    }

    fun createDefaultWidgets(displayMetrics: DisplayMetrics) {
        val centerX = displayMetrics.widthPixels / 2f
        val centerY = displayMetrics.heightPixels / 2f

        // System logo size (medium equivalent - adjust as needed)
        val systemLogoWidth = 800f
        val systemLogoHeight = 300f

        // Game marquee size (medium equivalent - typically wider than system logo)
        val gameMarqueeWidth = 800f
        val gameMarqueeHeight = 300f

        // Create default system logo widget (centered)
        val systemLogoWidget = OverlayWidget(
            contentType = OverlayWidget.ContentType.SYSTEM_LOGO,
            description = "",
            contentPath = "",  // Will be updated when system loads
            x = centerX - (systemLogoWidth / 2),
            y = centerY - (systemLogoHeight / 2),
            width = systemLogoWidth,
            height = systemLogoHeight,
            zIndex = 0,
            widgetContext = OverlayWidget.WidgetContext.SYSTEM
        )

        // Create default game marquee widget (centered)
        val gameMarqueeWidget = OverlayWidget(
            contentType = OverlayWidget.ContentType.MARQUEE,
            description = "",
            contentPath = "",  // Will be updated when game loads
            x = centerX - (gameMarqueeWidth / 2),
            y = centerY - (gameMarqueeHeight / 2),
            width = gameMarqueeWidth,
            height = gameMarqueeHeight,
            zIndex = 0,
            widgetContext = OverlayWidget.WidgetContext.GAME
        )

        // 2. Create Page 1 and add widgets to it
        val firstPage = WidgetPage(id = "default_page_1")
        firstPage.widgets.add(systemLogoWidget)
        firstPage.widgets.add(gameMarqueeWidget)

        // 3. Set pages and save
        this.pages = mutableListOf(firstPage)
        save()
    }

    fun moveWidgetZOrder(widgetId: String, moveForward: Boolean) {
        val pageWidgets = pages[currentPageIndex].widgets
        val index = pageWidgets.indexOfFirst { it.id == widgetId }
        if (index == -1) return

        val newIndex = if (moveForward) index + 1 else index - 1

        if (newIndex in 0 until pageWidgets.size) {
            // Swap them in the data list
            java.util.Collections.swap(pageWidgets, index, newIndex)
            save()
        }
    }
}