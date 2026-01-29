package com.esde.companion.ui.contextmenu

import com.esde.companion.WidgetPage
import com.esde.companion.ui.ContentType

data class MenuUiState(
    val locked: Boolean,
    val snap: Boolean,
    val showGrid: Boolean
)

data class WidgetActions(
    val onToggleLock: () -> Unit,
    val onToggleSnap: () -> Unit,
    val onToggleGrid: () -> Unit,
    val onHelp: () -> Unit,
    val onAddPage: () -> Unit,
    val onRemovePage: () -> Unit,
    val onAddWidget: (ContentType) -> Unit,
    val onSavePageSettings: (WidgetPage) -> Unit
)