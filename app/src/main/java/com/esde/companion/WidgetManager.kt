package com.esde.companion

import android.content.Context
import android.util.DisplayMetrics
import android.util.Log
import androidx.core.content.edit
import com.esde.companion.ui.ContentType
import com.esde.companion.ui.WidgetContext
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

class WidgetManager(
    private val context: Context,
    private val widgetContext: WidgetContext
) {
    private val systemJsonString = "system_widgets_json"
    private val gameJsonString = "game_widgets_json"
    private var preferenceKey = when (widgetContext) {
        WidgetContext.GAME -> gameJsonString
        WidgetContext.SYSTEM -> systemJsonString
    }
    private val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private var pages: MutableList<WidgetPage> = mutableListOf()
    var currentPageIndex: Int = 0


    fun load() {
        val json = prefs.getString(preferenceKey, null)
        try {
            if (json != null) {
                val type = object : TypeToken<MutableList<WidgetPage>>() {}.type
                pages = gson.fromJson(json, type)
            }

            if (pages.isEmpty()) {
                pages.add(WidgetPage())
            }
        } catch (e: Exception) {
            Log.e("WidgetManager", "Failed to load pages, resetting to default", e)
            pages = mutableListOf(WidgetPage())
        }
    }

    fun save() {
        val json = gson.toJson(pages)
        prefs.edit { putString(preferenceKey, json) }
    }

    fun getCurrentPage(): WidgetPage {
        return pages[currentPageIndex]
    }

    // Returns widgets for the current page
    fun getWidgetsForCurrentPage(): List<OverlayWidget> {
        return pages.getOrNull(currentPageIndex)?.widgets ?: emptyList()
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
        type: ContentType,
        displayMetrics: DisplayMetrics
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
        )
        pages[currentPageIndex].widgets.add(newWidget)
        save()
        return newWidget
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

    fun updatePage(updated: WidgetPage) {
        val idx = pages.indexOfFirst { it.id == updated.id }
        if (idx != -1) {
            pages[idx] = updated
            save()
        }
    }

    fun getAllPages(): List<WidgetPage> {
        return pages
    }
}