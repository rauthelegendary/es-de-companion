package com.esde.companion.ui.contextmenu

import com.esde.companion.OverlayWidget

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
    val onAddWidget: (OverlayWidget.ContentType) -> Unit
)