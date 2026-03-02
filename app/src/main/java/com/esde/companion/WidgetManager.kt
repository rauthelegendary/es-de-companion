package com.esde.companion

import android.content.Context
import android.util.DisplayMetrics
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.edit
import com.esde.companion.data.Widget
import com.esde.companion.ui.ContentType
import com.esde.companion.ui.WidgetContext
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    private val jsonEngine = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        prettyPrint = false
    }
    var pages = mutableStateListOf<WidgetPage>()
    var currentPageIndex: Int = 0

    private var defaultPageId: String? = null


    fun load() {
        val json = prefs.getString(preferenceKey, null)
        try {
            if (json != null) {
                val loadedPages = jsonEngine.decodeFromString<List<WidgetPage>>(json)

                pages.clear()
                pages.addAll(loadedPages)
            }

            if (pages.isEmpty()) {
                pages.add(WidgetPage(name = "Base"))
            }
        } catch (e: Exception) {
            Log.e("WidgetManager", "Failed to load pages, resetting to default", e)
            pages.clear()
            pages.add(WidgetPage(name = "Base"))
        }

        defaultPageId = pages.firstOrNull { it.isDefault }?.id

    }

    private fun save() {
        val json = jsonEngine.encodeToString(pages.toList())
        prefs.edit { putString(preferenceKey, json) }
    }

    fun getCurrentPage(): WidgetPage {
        return pages[currentPageIndex]
    }

    fun getPageById(id: String): WidgetPage? {
        return pages.firstOrNull { it.id == id }
    }

    // Returns widgets for the current page
    fun getWidgetsForCurrentPage(): List<Widget> {
        return pages.getOrNull(currentPageIndex)?.widgets ?: emptyList()
    }

    fun reorderPagesByEditorList(editorPages: List<PageEditorItem>) {
        val pageMap = pages.associateBy { it.id }

        val newPages = editorPages.map { editorItem ->
            val originalPage = pageMap[editorItem.id]
                ?: throw IllegalStateException("Page with ID ${editorItem.id} not found!")

            originalPage.copy(name = editorItem.name)
        }

        pages.clear()
        pages.addAll(newPages)
        save()
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
            val removedId = pages[currentPageIndex].id
            pages.removeAt(currentPageIndex)
            // Adjust index so we don't point out of bounds
            if (currentPageIndex >= pages.size) {
                currentPageIndex = pages.size - 1
            }
            pages.forEach { page ->
                if (page.transitionTargetPageId == removedId) {
                    page.transitionTargetPageId = ""
                    page.transitionToPage = false
                }
            }
        }
        save()
    }

    fun getPageCount(): Int = pages.size

    fun updateWidget(updated: Widget, metrics: DisplayMetrics) {
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
    ): Widget {
        val centerX = displayMetrics.widthPixels / 2f
        val centerY = displayMetrics.heightPixels / 2f
        val newWidget = Widget(
            contentType = type,
            contentPath = "",
            text = "",
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
            //this is a bit of a hack, names are getting updated elsewhere and overwritten by the update method, so we force it to the name in our state
            updated.name = pages[idx].name
            pages[idx] = updated
            if (updated.isDefault) {
                if (defaultPageId != null && defaultPageId != updated.id) {
                    val oldIdx = pages.indexOfFirst { it.id == defaultPageId }
                    if (oldIdx != -1) {
                        pages[oldIdx] = pages[oldIdx].copy(isDefault = false)
                    }
                }
                defaultPageId = updated.id
            } else if (defaultPageId == updated.id) {
                defaultPageId = null
            }
            save()
        }
    }

    fun getDefaultPage(): WidgetPage? {
        return pages.firstOrNull { it.isDefault && it.id == defaultPageId }
    }

    fun getFirstPage(): WidgetPage? {
        if(pages.isNotEmpty()) {
            return pages[0]
        }
        return null
    }

    fun getAllPages(): List<WidgetPage> {
        return pages
    }

    fun renameCurrentPage(name: String) {
        val newPage = pages[currentPageIndex].copy(name = name)
        pages[currentPageIndex] = newPage
        save()
    }
}